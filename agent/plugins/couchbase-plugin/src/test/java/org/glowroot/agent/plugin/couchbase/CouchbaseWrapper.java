package org.glowroot.agent.plugin.couchbase;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.HttpWaitStrategy;

import java.util.Arrays;

public class CouchbaseWrapper {

    private static GenericContainer couchbase =
            new GenericContainer("couchbase/server:4.6.0")
            .withExposedPorts(8091, 8092, 8093, 8094, 8095, 11207, 11210, 11211, 18091, 18092, 18093);

    static void start() throws Exception {
        couchbase.setPortBindings(Arrays.asList("8091:8091", "8092:8092", "8093:8093", "8094:8094", "8095:8095",
                "11207:11207", "11210:11210", "11211:11211", "18091:18091", "18092:18092", "18093:18093"));
        couchbase.setWaitStrategy(new HttpWaitStrategy().forPath("/ui/index.html#/"));
        couchbase.start();
    }

    static void stop() throws Exception {
        couchbase.stop();
    }
}
