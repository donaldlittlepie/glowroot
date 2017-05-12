package org.glowroot.agent.plugin.couchbase;

import java.util.Arrays;

public class CouchbaseWrapper {

    private static CouchbaseContainer couchbase = new CouchbaseContainer();

    static void start() throws Exception {
        couchbase.setPortBindings(Arrays.asList("8091:8091", "8092:8092", "8093:8093", "8094:8094", "8095:8095",
                "11207:11207", "11210:11210", "11211:11211", "18091:18091", "18092:18092", "18093:18093"));
        couchbase.start();
        couchbase.initCluster();
    }

    static void stop() throws Exception {
        couchbase.stop();
    }
}
