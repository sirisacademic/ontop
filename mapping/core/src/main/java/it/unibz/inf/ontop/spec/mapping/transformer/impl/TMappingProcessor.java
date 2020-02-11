package it.unibz.inf.ontop.spec.mapping.transformer.impl;

/*
 * #%L
 * ontop-reformulation-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.*;
import com.google.inject.Inject;
import it.unibz.inf.ontop.constraints.impl.ImmutableCQContainmentCheckUnderLIDs;
import it.unibz.inf.ontop.datalog.*;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.tools.UnionBasedQueryMerger;
import it.unibz.inf.ontop.iq.transform.NoNullValueEnforcer;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.atom.RDFAtomPredicate;
import it.unibz.inf.ontop.model.atom.RelationPredicate;
import it.unibz.inf.ontop.model.term.IRIConstant;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.vocabulary.RDF;
import it.unibz.inf.ontop.spec.mapping.MappingAssertion;
import it.unibz.inf.ontop.spec.mapping.MappingAssertionIndex;
import it.unibz.inf.ontop.spec.mapping.TMappingExclusionConfig;
import it.unibz.inf.ontop.spec.mapping.transformer.MappingCQCOptimizer;
import it.unibz.inf.ontop.spec.ontology.*;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.apache.commons.rdf.api.IRI;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class TMappingProcessor {

	// TODO: the implementation of EXCLUDE ignores equivalent classes / properties

	private final AtomFactory atomFactory;
	private final TermFactory termFactory;
    private final QueryUnionSplitter unionSplitter;
    private final UnionFlattener unionNormalizer;
    private final MappingCQCOptimizer mappingCqcOptimizer;
    private final NoNullValueEnforcer noNullValueEnforcer;
    private final IntermediateQueryFactory iqFactory;
    private final UnionBasedQueryMerger queryMerger;
    private final SubstitutionFactory substitutionFactory;

    @Inject
	private TMappingProcessor(AtomFactory atomFactory,
                              TermFactory termFactory,
                              QueryUnionSplitter unionSplitter,
                              UnionFlattener unionNormalizer,
                              MappingCQCOptimizer mappingCqcOptimizer,
                              NoNullValueEnforcer noNullValueEnforcer,
                              IntermediateQueryFactory iqFactory,
                              UnionBasedQueryMerger queryMerger,
                              SubstitutionFactory substitutionFactory) {
		this.atomFactory = atomFactory;
		this.termFactory = termFactory;
        this.unionSplitter = unionSplitter;
        this.unionNormalizer = unionNormalizer;
        this.mappingCqcOptimizer = mappingCqcOptimizer;
        this.noNullValueEnforcer = noNullValueEnforcer;
        this.iqFactory = iqFactory;
        this.queryMerger = queryMerger;
        this.substitutionFactory = substitutionFactory;
    }


	/**
	 * constructs the TMappings using DAG
	 * @param mapping
	 * @param reasoner
	 * @return
	 */

	public ImmutableList<MappingAssertion> getTMappings(ImmutableList<MappingAssertion> mapping, ClassifiedTBox reasoner, TMappingExclusionConfig excludeFromTMappings, ImmutableCQContainmentCheckUnderLIDs<RelationPredicate> cqc) {

	    if (mapping.isEmpty())
	        return mapping;

	    // index mapping assertions by the predicate type
        //     same IRI can be a class name and a property name
        //     but the same IRI cannot be an object and a data or annotation property name at the same time
        // see https://www.w3.org/TR/owl2-new-features/#F12:_Punning

        ImmutableMap<MappingAssertionIndex, Collection<TMappingRule>> original = mapping.stream()
                .flatMap(a -> unionSplitter.splitUnion(unionNormalizer.optimize(a.getQuery()))
                        .map(IQ::normalizeForOptimization) // replaces join equalities
                        .map(q -> mappingCqcOptimizer.optimize(cqc, q))
                        .map(q -> Maps.immutableEntry(a.getIndex(), new TMappingRule(q, termFactory, atomFactory))))
                .collect(ImmutableCollectors.toMultimap()).asMap();

        RDFAtomPredicate rdfTriple = original.keySet().iterator().next().getPredicate();
        TMappingRuleHeadConstructorProvider provider = new TMappingRuleHeadConstructorProvider(rdfTriple, termFactory);

        ImmutableMap<MappingAssertionIndex, ImmutableList<TMappingRule>> saturated = Stream.concat(Stream.concat(
                reasoner.objectPropertiesDAG().stream()
                        .filter(node -> !node.getRepresentative().isInverse() && !excludeFromTMappings.contains(node.getRepresentative()))
                        .flatMap(node -> node.getMembers().stream()
                                .filter(d -> !d.isInverse() || d.getInverse() != node.getRepresentative())
                                .map(saturator(node, reasoner.objectPropertiesDAG(), original, provider::constructor, cqc))),

                reasoner.dataPropertiesDAG().stream()
                        .filter(node -> !excludeFromTMappings.contains(node.getRepresentative()))
                        .flatMap(node -> node.getMembers().stream()
                                .map(saturator(node, reasoner.dataPropertiesDAG(), original, provider::constructor, cqc)))),

                reasoner.classesDAG().stream()
                        .filter(node -> (node.getRepresentative() instanceof OClass) && !excludeFromTMappings.contains((OClass)node.getRepresentative()))
                        .flatMap(node -> node.getMembers().stream()
                                .filter(d -> d instanceof OClass)
                                .map(saturator(node, reasoner.classesDAG(), original, provider::constructor, cqc))))

                .filter(e -> !e.getValue().isEmpty())
                .collect(ImmutableCollectors.toMap());

        ImmutableMap<MappingAssertionIndex, ImmutableList<TMappingRule>> combined = Stream.concat(
                saturated.entrySet().stream(),
                original.entrySet().stream()
                        .filter(e -> !saturated.containsKey(e.getKey()))
                        .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().stream()
                                        .collect(TMappingEntry.toTMappingEntry(cqc, termFactory)))))
                .collect(ImmutableCollectors.toMap());

        return combined.entrySet().stream()
                .map(e -> new MappingAssertion(e.getKey(), toIQ(e.getValue()), null))
                .collect(ImmutableCollectors.toList());
    }

    private IQ toIQ(Collection<TMappingRule> rules) {
        // In case some legacy implementations do not preserve IS_NOT_NULL conditions
        return noNullValueEnforcer.transform(
                queryMerger.mergeDefinitions(rules.stream()
                        .map(r -> r.asIQ(iqFactory, termFactory, substitutionFactory))
                        .collect(ImmutableCollectors.toList())).get())
                .normalizeForOptimization();
    }

    private <T> Function<T, Map.Entry<MappingAssertionIndex, ImmutableList<TMappingRule>>> saturator(Equivalences<T> node,
                                                     EquivalencesDAG<T> dag,
                                                     ImmutableMap<MappingAssertionIndex, Collection<TMappingRule>> original,
                                                     Function<T, TMappingRuleHeadConstructor> constructor,
                                                     ImmutableCQContainmentCheckUnderLIDs<RelationPredicate> cqc) {

	    IRI iri = constructor.apply(node.getRepresentative()).getIri();

        ImmutableList<TMappingRule> saturatedRepresentative = dag.getSub(node).stream()
                .flatMap(subnode -> subnode.getMembers().stream())
                .map(constructor)
                .flatMap(t -> original.getOrDefault(t.indexOf(), ImmutableList.of()).stream()
                        .map(m -> new TMappingRule(t.getArguments(m.getHeadTerms(), iri), m)))
                .collect(TMappingEntry.toTMappingEntry(cqc, termFactory));

        return constructor.andThen(
                t -> Maps.immutableEntry(
                        t.indexOf(),
                        saturatedRepresentative.stream()
                            .map(m -> new TMappingRule(t.getArguments(m.getHeadTerms(), t.getIri()), m))
                            .collect(ImmutableCollectors.toList())));
    }

    private static abstract class TMappingRuleHeadConstructor {
        final MappingAssertionIndex index;
        TMappingRuleHeadConstructor(MappingAssertionIndex index) {
            this.index = index;
        }
        MappingAssertionIndex indexOf() { return index; }

        IRI getIri() { return index.getIri(); }

        abstract ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri);
    }

    private static class TMappingRuleHeadConstructorProvider {
	    private final RDFAtomPredicate p;
	    private final TermFactory termFactory;
        private final IRIConstant rdfType;

        TMappingRuleHeadConstructorProvider(RDFAtomPredicate rdfAtomPredicate, TermFactory termFactory) {
            this.p = rdfAtomPredicate;
            this.termFactory = termFactory;
            this.rdfType = termFactory.getConstantIRI(RDF.TYPE);
        }

        TMappingRuleHeadConstructor constructor(ClassExpression ce) {
            if (ce instanceof OClass) {
                OClass oc = (OClass)ce;
                return new TMappingRuleHeadConstructor(MappingAssertionIndex.ofClass(p, oc.getIRI())) {
                    @Override
                    public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                        return p.updateObject(args, getConstantIRI(newIri));
                    }
                };
            }
            else if (ce instanceof ObjectSomeValuesFrom) {
                ObjectPropertyExpression ope = ((ObjectSomeValuesFrom) ce).getProperty();
                return new TMappingRuleHeadConstructor(MappingAssertionIndex.ofProperty(p, ope.getIRI())) {
                    @Override
                    public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                        return p.updateSPO(args, ope.isInverse() ? p.getObject(args) : p.getSubject(args), rdfType, getConstantIRI(newIri));
                    }
                };
            }
            else if (ce instanceof DataSomeValuesFrom) {
                DataPropertyExpression dpe = ((DataSomeValuesFrom) ce).getProperty();
                return new TMappingRuleHeadConstructor(MappingAssertionIndex.ofProperty(p, dpe.getIRI())) {
                    @Override
                    public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                        return p.updateSPO(args, p.getSubject(args), rdfType, getConstantIRI(newIri));
                    }
                };
            }
            else
                throw new MinorOntopInternalBugException("Unexpected type" + ce);
        }

        TMappingRuleHeadConstructor constructor(ObjectPropertyExpression ope) {
            return new TMappingRuleHeadConstructor(MappingAssertionIndex.ofProperty(p, ope.getIRI())) {
                @Override
                public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                    return ope.isInverse()
                            ? p.updateSPO(args, p.getObject(args), getConstantIRI(newIri), p.getSubject(args))
                            : p.updateSPO(args, p.getSubject(args), getConstantIRI(newIri), p.getObject(args));
                }
            };
        }

        TMappingRuleHeadConstructor constructor(DataPropertyExpression dpe) {
            return new TMappingRuleHeadConstructor(MappingAssertionIndex.ofProperty(p, dpe.getIRI())) {
                @Override
                public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                    return p.updateSPO(args, p.getSubject(args), getConstantIRI(newIri), p.getObject(args));
                }
            };
        }

        private IRIConstant getConstantIRI(IRI iri) {
            return termFactory.getConstantIRI(iri);
        }

    }

}
