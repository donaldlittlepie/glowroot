package org.glowroot.agent.plugin.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.DefaultBucketSettings;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;

import java.util.concurrent.TimeUnit;

class Sessions {

    static Bucket createBucket() throws Exception {
        Cluster cluster = CouchbaseCluster.create("127.0.0.1");
        BucketSettings bucketSettings = DefaultBucketSettings.builder()
                .name("test")
                .enableFlush(true);
        cluster.clusterManager().insertBucket(bucketSettings, 1, TimeUnit.MINUTES);

        Bucket bucket = cluster.openBucket("test", 1, TimeUnit.MINUTES);
        bucket.bucketManager().createN1qlPrimaryIndex(true, false, 1, TimeUnit.MINUTES);
        for (int i = 0; i < 10; i++) {
            bucket.insert(JsonDocument.create(String.valueOf(i), JsonObject.create()
                    .put("fname", "f" + i)
                    .put("lname", "l" + i)));
        }

        return bucket;
    }

    static void closeBucket(Bucket bucket) {
        bucket.close(1, TimeUnit.MINUTES);
    }
}
