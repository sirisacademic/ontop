package it.unibz.krdb.obda.testsuite;

import it.unibz.krdb.obda.quest.sparql.QuestMemorySPARQLQueryTest;
import junit.framework.Test;
import junit.framework.TestSuite;

public class TestQuestSparqlSuite extends TestSuite {

	public static Test suite() throws Exception {
		TestSuite suite = new TestSuite("SPARQL Compliance Tests for Quest");
		suite.addTest(QuestMemorySPARQLQueryTest.suite());
		return suite;
	}
}
