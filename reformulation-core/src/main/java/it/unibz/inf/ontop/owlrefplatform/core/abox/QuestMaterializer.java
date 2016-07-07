package it.unibz.inf.ontop.owlrefplatform.core.abox;

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

import com.google.inject.Injector;
import it.unibz.inf.ontop.model.Function;
import it.unibz.inf.ontop.model.GraphResultSet;
import it.unibz.inf.ontop.model.OBDAException;
import it.unibz.inf.ontop.model.OBDAMappingAxiom;
import it.unibz.inf.ontop.model.OBDAModel;
import it.unibz.inf.ontop.model.Predicate;
import it.unibz.inf.ontop.model.ResultSet;
import it.unibz.inf.ontop.ontology.Assertion;
import it.unibz.inf.ontop.ontology.DataPropertyExpression;
import it.unibz.inf.ontop.ontology.AnnotationProperty;
import it.unibz.inf.ontop.ontology.OClass;
import it.unibz.inf.ontop.ontology.ObjectPropertyExpression;
import it.unibz.inf.ontop.ontology.Ontology;
import it.unibz.inf.ontop.ontology.OntologyFactory;
import it.unibz.inf.ontop.ontology.OntologyVocabulary;
import it.unibz.inf.ontop.ontology.impl.OntologyFactoryImpl;
import it.unibz.inf.ontop.owlrefplatform.core.*;

import java.net.URI;
import java.sql.SQLException;
import java.util.*;

import it.unibz.inf.ontop.owlrefplatform.injection.QuestComponentFactory;
import it.unibz.inf.ontop.owlrefplatform.injection.QuestCoreConfiguration;
import it.unibz.inf.ontop.owlrefplatform.injection.QuestCorePreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Allows you to work with the virtual triples defined by an OBDA model. In
 * particular you will be able to generate ABox assertions from it and get
 * statistics.
 * 
 * The class works "online", that is, the triples are never kept in memory by
 * this class, instead a connection to the data sources will be established when
 * needed and the data will be streamed or retrieved on request.
 * 
 * In order to compute the SQL queries relevant for each predicate we use the
 * ComplexMapping Query unfolder. This allow us to reuse all the infrastructure
 * for URI generation and to avoid manually processing each mapping. This also
 * means that all features and limitations of the complex unfolder are present
 * here.
 * 
 * @author Mariano Rodriguez Muro
 * 
 */
public class QuestMaterializer {

    private final QuestComponentFactory questComponentFactory;
    private OBDAModel model;
	private IQuest questInstance;
	private Ontology ontology;

	/**
	 * Puts the JDBC connection in streaming mode.
	 */
	private final boolean doStreamResults;
	
	private final Set<Predicate> vocabulary;

	private long counter = 0;
	private VirtualTripleIterator iterator;

	private static final OntologyFactory ofac = OntologyFactoryImpl.getInstance();
	private static int FETCH_SIZE = 50000;

	/***
	 * 
	 * 
	 * @param model
	 * @throws Exception
	 */
	public QuestMaterializer(OBDAModel model, boolean doStreamResults) throws Exception {
		this(model, null, null, new Properties(), doStreamResults);
	}
	
	public QuestMaterializer(OBDAModel model, Ontology onto, boolean doStreamResults) throws Exception {
		this(model, onto, null, new Properties(), doStreamResults);
	}

    public QuestMaterializer(OBDAModel model, Ontology onto, Collection<Predicate> predicates, boolean doStreamResults) throws Exception {
        this(model, onto, predicates, new Properties(), doStreamResults);
    }

	/***
	 * 
	 * 
	 * @param model
	 * @throws Exception
	 */
	public QuestMaterializer(OBDAModel model, Ontology onto, Collection<Predicate> predicates, Properties properties,
							 boolean doStreamResults) throws Exception {
		this.doStreamResults = doStreamResults;
		this.model = model;
		this.ontology = onto;

        if(predicates != null && !predicates.isEmpty()){
            this.vocabulary = new HashSet<>(predicates);
        } else {
           this.vocabulary = extractVocabulary(model, onto);
        }

		QuestCoreConfiguration configuration = QuestCoreConfiguration.defaultBuilder()
				.enableOntologyAnnotationQuerying(true)
				.properties(properties).build();

		Injector injector = configuration.getInjector();
		questComponentFactory = injector.getInstance(QuestComponentFactory.class);

		if (this.model.getSources()!= null && this.model.getSources().size() > 1)
			throw new Exception("Cannot materialize with multiple data sources!");


        //start a quest instance
		if (ontology == null) {
			OntologyVocabulary vb = ofac.createVocabulary();
			
			// TODO: use Vocabulary for OBDAModel as well
			
			for (OClass pred : model.getOntologyVocabulary().getClasses()) 
				vb.createClass(pred.getName());				
			
			for (ObjectPropertyExpression prop : model.getOntologyVocabulary().getObjectProperties()) 
				vb.createObjectProperty(prop.getName());

			for (DataPropertyExpression prop : model.getOntologyVocabulary().getDataProperties()) 
				vb.createDataProperty(prop.getName());

			for (AnnotationProperty prop : model.getOntologyVocabulary().getAnnotationProperties())
				vb.createAnnotationProperty(prop.getName());

			ontology = ofac.createOntology(vb);			
		}
		
		
		//preferences.setCurrentValueOf(QuestPreferences.ABOX_MODE, QuestConstants.VIRTUAL);

		questInstance = questComponentFactory.create(ontology, Optional.of(this.model), Optional.empty());
		// Was an ugly way to ask for also querying the annotations

		questInstance.setupRepository();
	}


    private Set<Predicate> extractVocabulary(OBDAModel model, Ontology onto) {
        Set<Predicate> vocabulary = new HashSet<Predicate>();

        //add all class/data/object predicates to vocabulary
        //add declared predicates in model
        for (OClass cl : model.getOntologyVocabulary().getClasses()) {
            Predicate p = cl.getPredicate();
            if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#"))
                vocabulary.add(p);
        }
        for (ObjectPropertyExpression prop : model.getOntologyVocabulary().getObjectProperties()) {
            Predicate p = prop.getPredicate();
            if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#"))
                vocabulary.add(p);
        }
        for (DataPropertyExpression prop : model.getOntologyVocabulary().getDataProperties()) {
            Predicate p = prop.getPredicate();
            if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#"))
                vocabulary.add(p);
        }
		for (AnnotationProperty prop : model.getOntologyVocabulary().getAnnotationProperties()) {
			Predicate p = prop.getPredicate();
			if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#"))
				vocabulary.add(p);
		}
        if (onto != null) {
            //from ontology
            for (OClass cl : onto.getVocabulary().getClasses()) {
                Predicate p = cl.getPredicate();
                if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#")
                        && !vocabulary.contains(p))
                    vocabulary.add(p);
            }
            for (ObjectPropertyExpression role : onto.getVocabulary().getObjectProperties()) {
                Predicate p = role.getPredicate();
                if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#")
                        && !vocabulary.contains(p))
                    vocabulary.add(p);
            }
            for (DataPropertyExpression role : onto.getVocabulary().getDataProperties()) {
                Predicate p = role.getPredicate();
                if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#")
                        && !vocabulary.contains(p))
                    vocabulary.add(p);
            }
			for (AnnotationProperty role : onto.getVocabulary().getAnnotationProperties()) {
				Predicate p = role.getPredicate();
				if (!p.toString().startsWith("http://www.w3.org/2002/07/owl#")
							&& !vocabulary.contains(p))
					vocabulary.add(p);
				}
        }
        else {
            //from mapping undeclared predicates (can happen)
            for (URI uri : this.model.getMappings().keySet()){
                for (OBDAMappingAxiom axiom : this.model.getMappings(uri))
                {
                    List<Function> rule = axiom.getTargetQuery();
                    for (Function f: rule)
                        vocabulary.add(f.getFunctionSymbol());
                }
            }
        }

        return vocabulary;
    }

/*    public QuestMaterializer(OBDAModel model, Ontology onto, List<Predicate> predicates, QuestPreferences defaultPreferences) {
    }
*/


	public Iterator<Assertion> getAssertionIterator() throws Exception {
		//return the inner class  iterator
		iterator = new VirtualTripleIterator(questInstance, vocabulary.iterator());
		return iterator;
		
	}

	public List<Assertion> getAssertionList() throws Exception {
		Iterator<Assertion> it = getAssertionIterator();
		List<Assertion> assertions = new LinkedList<Assertion>();
		while (it.hasNext()) {
			assertions.add(it.next());
		}
		return assertions;
	}

	public int getTripleCount() throws Exception {
		int counter = 0;
		getAssertionIterator();
		while(iterator.hasNext) {
			counter++;
			iterator.next();
		}
		return counter;
	}

	public long getTriplesCount() throws Exception {
		if (iterator != null)
			return counter;
		else  
		return getTripleCount();
	}

	public int getVocabSize() {
		return vocabulary.size();
	}
	public void disconnect() {
		iterator.disconnect();
	}

	/***
	 * An iterator that will dynamically construct ABox assertions for the given
	 * predicate based on the results of executing the mappings for the
	 * predicate in each data source.
	 * 
	 */
	private class VirtualTripleIterator implements Iterator<Assertion> {


		private String query1 = "CONSTRUCT {?s <%s> ?o} WHERE {?s <%s> ?o}";
		private String query2 = "CONSTRUCT {?s a <%s>} WHERE {?s a <%s>}";

		private IQuestConnection questConn;
		private IQuestStatement stm;
		
		private boolean read = false, hasNext = false;

		private GraphResultSet results;
		
		private Iterator<Predicate> vocabularyIterator;
		
		private Logger log = LoggerFactory.getLogger(VirtualTripleIterator.class);

		public VirtualTripleIterator(IQuest questInstance, Iterator<Predicate> vocabIter)
				throws SQLException {
			try{
				questConn = questInstance.getNonPoolConnection();

				if (doStreamResults) {
					// Autocommit must be OFF (needed for autocommit)
					questConn.setAutoCommit(false);
				}

				vocabularyIterator = vocabIter;
				//execute first query to start the process
				counter = 0;
				stm = questConn.createStatement();

				if (doStreamResults) {
					// Fetch 50 000 lines at the same time
					stm.setFetchSize(FETCH_SIZE);
				}
				if (!vocabularyIterator.hasNext())
					throw new NullPointerException("Vocabulary is empty!");
				while (results == null) {
					if (vocabularyIterator.hasNext()) {
						Predicate pred = vocabularyIterator.next();
						String query = getQuery(pred);
						ResultSet execute = stm.execute(query);

						results = (GraphResultSet) execute;
//						if (results!=null){
//							hasNext = results.hasNext();
//
//						}					
					}else{
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		private String getPredicateQuery(Predicate p) {
			return String.format(query1, p.toString(), p.toString()); }
		
		private String getClassQuery(Predicate p) {
			return String.format(query2, p.toString(), p.toString()); }
		
		private String getQuery(Predicate p)
		{
			if (p.getArity() == 1)
				return getClassQuery(p);
			else if (p.getArity() == 2)
				return getPredicateQuery(p);
			return "";
		}

		@Override
		public boolean hasNext() {
			try{
			if (!read && results!=null) {
				hasNext = results.hasNext();
				while (vocabularyIterator.hasNext() && hasNext == false)
				{
						//close previous statement if open
						if (stm!= null && results!=null)
							{stm.close(); results.close(); }

						//execute next query
						stm = questConn.createStatement();
						if (doStreamResults) {
							stm.setFetchSize(FETCH_SIZE);
						}
						Predicate predicate = vocabularyIterator.next();
						String query = getQuery(predicate);
						ResultSet execute = stm.execute(query);

						results = (GraphResultSet) execute;
						if (results!=null){
							hasNext = results.hasNext();

						}


				}
				read = true;
				
			}
			} catch(Exception e)
			{e.printStackTrace();}


			return hasNext;
		}

		@Override
		public Assertion next() {
			try {
				counter+=1;
				if (read && hasNext)
				{
					read = false;
					return results.next().get(0);
				}
				else if (!read){
					hasNext();
					return next();
				}
				
			} catch (OBDAException e) {
				e.printStackTrace();
				log.warn("Exception in Assertion Iterator next");
			}
			return null;
		}
			

		/**
		 * Releases all the connection resources
		 */
		public void disconnect() {
			if (stm != null) {
				try {
					stm.close();
				} catch (Exception e) {
					// NO-OP
				}
			}

			if (questConn != null) {
				try {
					questConn.close();
				} catch (Exception e) {
					// NO-OP
				}
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
