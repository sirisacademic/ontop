package it.unibz.inf.ontop;

import com.google.inject.Injector;
import it.unibz.inf.ontop.datalog.DatalogFactory;
import it.unibz.inf.ontop.dbschema.BasicDBMetadata;
import it.unibz.inf.ontop.dbschema.DummyBasicDBMetadata;
import it.unibz.inf.ontop.injection.OntopModelConfiguration;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.iq.tools.ExecutorRegistry;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;

/**
 *
 */
public class OntopModelTestingTools {

    public static final IntermediateQueryFactory IQ_FACTORY;
    public static final ExecutorRegistry EXECUTOR_REGISTRY;
    public static final TermFactory TERM_FACTORY;
    public static final AtomFactory ATOM_FACTORY;
    public static final SubstitutionFactory SUBSTITUTION_FACTORY;
    public static final DatalogFactory DATALOG_FACTORY;
    public static final TypeFactory TYPE_FACTORY;

    private static final DummyBasicDBMetadata DEFAULT_DUMMY_DB_METADATA;

    static {
        OntopModelConfiguration defaultConfiguration = OntopModelConfiguration.defaultBuilder()
                .enableTestMode()
                .build();
        Injector injector = defaultConfiguration.getInjector();

        IQ_FACTORY = injector.getInstance(IntermediateQueryFactory.class);
        ATOM_FACTORY = injector.getInstance(AtomFactory.class);
        SUBSTITUTION_FACTORY = injector.getInstance(SubstitutionFactory.class);
        DATALOG_FACTORY = injector.getInstance(DatalogFactory.class);
        TERM_FACTORY = injector.getInstance(TermFactory.class);
        TYPE_FACTORY = injector.getInstance(TypeFactory.class);
        DEFAULT_DUMMY_DB_METADATA = injector.getInstance(DummyBasicDBMetadata.class);

        EXECUTOR_REGISTRY = defaultConfiguration.getExecutorRegistry();
    }

    public static BasicDBMetadata createDummyMetadata() {
        return DEFAULT_DUMMY_DB_METADATA.clone();
    }

}
