package org.glowroot.agent.plugin.couchbase;

import org.glowroot.agent.it.harness.Container;
import org.glowroot.agent.it.harness.Containers;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

public class SharedSetupRunListener extends RunListener {

    private static volatile Container sharedContainer;

    public static Container getContainer() throws Exception {
        if (sharedContainer == null) {
            startCouchbase();
            return Containers.create();
        }

        return sharedContainer;
    }

    public static void close(Container container) throws Exception {
        if (sharedContainer == null) {
            container.close();
            CouchbaseWrapper.stop();
        }
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        startCouchbase();
        sharedContainer = Containers.create();
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        sharedContainer.close();
        CouchbaseWrapper.stop();
    }

    private static void startCouchbase() throws Exception {
        CouchbaseWrapper.start();
    }
}
