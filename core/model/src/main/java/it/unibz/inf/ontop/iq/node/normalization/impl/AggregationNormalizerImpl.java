package it.unibz.inf.ontop.iq.node.normalization.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.injection.CoreSingletons;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.IQProperties;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.UnaryIQTree;
import it.unibz.inf.ontop.iq.node.AggregationNode;
import it.unibz.inf.ontop.iq.node.ConstructionNode;
import it.unibz.inf.ontop.iq.node.QueryNode;
import it.unibz.inf.ontop.iq.node.normalization.AggregationNormalizer;
import it.unibz.inf.ontop.model.term.ImmutableFunctionalTerm;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.VariableGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

public class AggregationNormalizerImpl implements AggregationNormalizer {

    private final CoreSingletons coreSingletons;
    private final IntermediateQueryFactory iqFactory;

    @Inject
    protected AggregationNormalizerImpl(CoreSingletons coreSingletons) {
        this.coreSingletons = coreSingletons;
        this.iqFactory = coreSingletons.getIQFactory();
    }

    /**
     * Blocks distinct. May block some bindings and some filter conditions.
     *
     * TODO: enable lifting some filter conditions
     * TODO: we may consider remove distincts in the sub-tree when cardinality does not affect the substitution definitions
     */
    @Override
    public IQTree normalizeForOptimization(AggregationNode aggregationNode, IQTree child,
                                           VariableGenerator variableGenerator, IQProperties currentIQProperties) {
        IQTree normalizedChild = child.normalizeForOptimization(variableGenerator);

        QueryNode rootNode = normalizedChild.getRootNode();

        // State after lifting the bindings
        Optional<AggregationNormalizationState> state = Optional.of(rootNode)
                .filter(n -> n instanceof ConstructionNode)
                .map(n -> (ConstructionNode) n)
                .map(n -> normalizeChildConstructionNode(aggregationNode, n,
                        ((UnaryIQTree) normalizedChild).getChild(), variableGenerator));


        IQProperties normalizedProperties = currentIQProperties.declareNormalizedForOptimization();
        // TODO: consider filters

        return state
                .map(s -> s.createNormalizedTree(normalizedProperties))
                .orElseGet(() -> iqFactory.createUnaryIQTree(aggregationNode, normalizedChild));
    }

    private AggregationNormalizationState normalizeChildConstructionNode(AggregationNode aggregationNode,
                                                                         ConstructionNode childConstructionNode,
                                                                         IQTree grandChild,
                                                                         VariableGenerator variableGenerator) {
        return new AggregationNormalizationState(aggregationNode, childConstructionNode, grandChild, variableGenerator)
                .liftNonGroupingBindings()
                .liftGroupingBindings();

    }


    protected class AggregationNormalizationState {

        private static final int MAX_ITERATIONS = 1000;
        private final AggregationNode aggregationNode;
        @Nullable
        private final ConstructionNode childConstructionNode;
        private final IQTree grandChild;
        private final VariableGenerator variableGenerator;
        // The oldest ancestor is first
        private final ImmutableList<ConstructionNode> ancestors;

        /**
         * Initial state
         */
        protected AggregationNormalizationState(AggregationNode aggregationNode, @Nonnull ConstructionNode childConstructionNode,
                                                IQTree grandChild, VariableGenerator variableGenerator) {

            this.aggregationNode = aggregationNode;
            this.childConstructionNode = childConstructionNode;
            this.grandChild = grandChild;
            this.variableGenerator = variableGenerator;
            this.ancestors = ImmutableList.of();
        }

        private AggregationNormalizationState(ImmutableList<ConstructionNode> ancestors,
                                              AggregationNode aggregationNode, @Nullable ConstructionNode childConstructionNode,
                                              IQTree grandChild, VariableGenerator variableGenerator) {
            this.ancestors = ancestors;
            this.aggregationNode = aggregationNode;
            this.childConstructionNode = childConstructionNode;
            this.grandChild = grandChild;
            this.variableGenerator = variableGenerator;
        }

        /**
         * TODO: implement
         */
        public AggregationNormalizationState liftNonGroupingBindings() {
            throw new RuntimeException("TODO: implement it");
        }

        /**
         * Lifts (fragments of) bindings that are injective.
         *
         * liftNonGroupingBindings() is expected to have been called before
         *
         */
        public AggregationNormalizationState liftGroupingBindings() {
            if (childConstructionNode == null)
                return this;

            ImmutableSubstitution<ImmutableTerm> substitution = childConstructionNode.getSubstitution();
            ImmutableSet<Variable> groupingVariables = aggregationNode.getGroupingVariables();

            if (substitution.isEmpty())
                return this;
            if (!groupingVariables.containsAll(substitution.getDomain())) {
                throw new MinorOntopInternalBugException("Was expecting all the non-grouping bindings to be lifted");
            }

            // Only projecting grouping variables
            ConstructionNode groupingConstructionNode = iqFactory.createConstructionNode(groupingVariables, substitution);

            // Non-final
            InjectiveBindingLiftState subState = new InjectiveBindingLiftState(groupingConstructionNode, grandChild,
                    variableGenerator, coreSingletons);

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                InjectiveBindingLiftState newSubState = subState.liftBindings();

                // Convergence
                if (newSubState.equals(subState)) {
                    return convertIntoState(subState);
                }
                else
                    subState = newSubState;
            }
            throw new MinorOntopInternalBugException("AggregationNormalizerImpl.liftGroupingBindings() " +
                    "did not converge after " + MAX_ITERATIONS);
        }

        private AggregationNormalizationState convertIntoState(InjectiveBindingLiftState subState) {
            ImmutableSet<Variable> aggregateVariables = aggregationNode.getSubstitution().getDomain();

            ImmutableList<ConstructionNode> subStateAncestors = subState.getAncestors();

            ImmutableList<ConstructionNode> newAncestors = Stream.concat(
                    // Already defined
                    ancestors.stream(),
                    // Ancestors of the sub-state modified so as to project the aggregation variables
                    subStateAncestors.stream()
                            .map(a -> iqFactory.createConstructionNode(Sets.union(a.getVariables(),
                                    aggregateVariables).immutableCopy(), a.getSubstitution())))
                    .collect(ImmutableCollectors.toList());

            ImmutableSet<Variable> groupingVariables = aggregationNode.getGroupingVariables();

            // The closest parent informs us about the new grouping variables
            ImmutableSet<Variable> newGroupingVariables = subStateAncestors.isEmpty()
                    ? groupingVariables
                    : subStateAncestors.get(subStateAncestors.size() - 1).getChildVariables();

            // Applies all the substitutions of the ancestors to the substitution of the aggregation node
            // Needed when some grouping variables are also used in the aggregates
            ImmutableSubstitution<ImmutableFunctionalTerm> newAggregationSubstitution = subStateAncestors.stream()
                    .reduce(aggregationNode.getSubstitution(),
                            (s, a) -> (ImmutableSubstitution<ImmutableFunctionalTerm>) (ImmutableSubstitution<?>)
                                    a.getSubstitution().composeWith(s),
                            (s1, s2) -> {
                                throw new MinorOntopInternalBugException("Substitution merging was not expected");
                            });

            AggregationNode newAggregationNode = iqFactory.createAggregationNode(newGroupingVariables, newAggregationSubstitution);

            // Nullable
            ConstructionNode newChildConstructionNode = subState.getChildConstructionNode()
                    .filter(n -> !n.getSubstitution().isEmpty())
                    .map(n -> iqFactory.createConstructionNode(
                            Stream.concat(
                                    n.getVariables().stream(),
                                    // Appends the variables required by the substitution of the aggregation node
                                    newAggregationNode.getSubstitution().getImmutableMap().values().stream()
                                        .flatMap(ImmutableTerm::getVariableStream))
                                    .collect(ImmutableCollectors.toSet()),
                            n.getSubstitution()))
                    .orElse(null);


            return new AggregationNormalizationState(newAncestors, newAggregationNode, newChildConstructionNode,
                    subState.getGrandChildTree(), variableGenerator);
        }


        protected IQTree createNormalizedTree(IQProperties normalizedProperties) {
            throw new RuntimeException("TODO: continue");
        }
    }

}
