package org.glowroot.agent.plugin.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.N1qlQueryResult;
import com.couchbase.client.java.query.N1qlQueryRow;
import org.glowroot.agent.it.harness.AppUnderTest;
import org.glowroot.agent.it.harness.Container;
import org.glowroot.agent.it.harness.TransactionMarker;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;
import org.glowroot.wire.api.model.TraceOuterClass.Trace.SharedQueryText;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CouchbaseSyncIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = SharedSetupRunListener.getContainer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        SharedSetupRunListener.close(container);
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void test() throws Exception {
        Trace trace = container.execute(Blah.class);

        Iterator<Trace.Entry> i = trace.getEntryList().iterator();
        List<SharedQueryText> sharedQueryTexts = trace.getSharedQueryTextList();

        Trace.Entry entry = i.next();
        assertThat(entry.getDepth()).isEqualTo(0);
        assertThat(entry.getMessage()).isEmpty();
    }


    public static class Blah implements AppUnderTest, TransactionMarker {

        private Bucket bucket;

        @Override
        public void executeApp() throws Exception {
            bucket = Sessions.createBucket();
            transactionMarker();
            Sessions.closeBucket(bucket);
        }

        @Override
        public void transactionMarker() throws Exception {
            N1qlQueryResult results = bucket.query(N1qlQuery.simple("select * from test"));

            for (N1qlQueryRow row : results.allRows()) {
                row.value();
            }
        }
    }
}
