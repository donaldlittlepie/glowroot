package org.glowroot.agent.plugin.couchbase;

import com.couchbase.client.core.utils.Base64;
import com.couchbase.client.deps.com.fasterxml.jackson.databind.JsonNode;
import com.couchbase.client.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.rnorth.ducttape.TimeoutException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.containers.wait.HttpWaitStrategy;
import org.testcontainers.shaded.com.google.common.base.Strings;
import org.testcontainers.shaded.com.google.common.io.BaseEncoding;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.rnorth.ducttape.unreliables.Unreliables.retryUntilSuccess;

/*
 * https://github.com/ldoguin/couchbase-test-docker/blob/master/src/test/java/com/couchbase/CouchbaseContainer.java
 */
public class CouchbaseContainer<SELF extends CouchbaseContainer<SELF>> extends GenericContainer<SELF> implements LinkableContainer {

    public CouchbaseContainer() {
        super("couchbase/server:4.6.0");
    }

    public CouchbaseContainer(String containerName) {
        super(containerName);
    }

    @Override
    protected Integer getLivenessCheckPort() {
        return getMappedPort(8091);
    }

    @Override
    protected void configure() {
        addExposedPorts(8091, 8092, 8093, 8094, 8095, 11207, 11210, 11211, 18091, 18092, 18093);
        setWaitStrategy(new HttpWaitStrategy().forPath("/ui/index.html#/"));
    }

    @Override
    public String getContainerName() {
        return "couchbase";
    }

    public void initCluster() {
        try {
            String baseUrl = String.format("http://%s:%s", getContainerIpAddress(), getMappedPort(8091));
            String poolUrl = baseUrl + "/pools/default";
            String poolPayload = "memoryQuota=" + URLEncoder.encode("256", "UTF-8") + "&indexMemoryQuota=" +
                    URLEncoder.encode("256", "UTF-8");
            callCouchbaseRestAPI(poolUrl, poolPayload);

            String setupServicesUrl = baseUrl + "/node/controller/setupServices";
            String servicesPayload = "services=" + URLEncoder.encode("kv,n1ql,index", "UTF-8");
            callCouchbaseRestAPI(setupServicesUrl, servicesPayload);

            String webSettingsUrl = baseUrl + "/settings/web";
            String webSettingsPayload = "username=" + URLEncoder.encode("Administrator", "UTF-8")
                    + "&password=" + URLEncoder.encode("password", "UTF-8") + "&port=8091";
            callCouchbaseRestAPI(webSettingsUrl, webSettingsPayload);

            String indexSettingsUrl = baseUrl + "/settings/indexes";
            String indexSettingsPayload = "storageMode=" + URLEncoder.encode("forestdb", "UTF-8");
            callCouchbaseRestAPI(indexSettingsUrl, indexSettingsPayload, "Administrator", "password");

            CouchbaseWaitStrategy couchbaseWaitStrategy = new CouchbaseWaitStrategy();
            couchbaseWaitStrategy
                    .withBasicCredentials("Administrator", "password")
                    .waitUntilReady(this);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void callCouchbaseRestAPI(String url, String payload) throws IOException {
        callCouchbaseRestAPI(url, payload, null, null);
    }

    private void callCouchbaseRestAPI(String url, String payload, String username, String password) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) ((new URL(url).openConnection()));
        httpConnection.setDoOutput(true);
        httpConnection.setRequestMethod("POST");
        httpConnection.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");
        if (username != null) {
            String encoded = Base64.encode((username + ":" + password).getBytes("UTF-8"));
            httpConnection.setRequestProperty("Authorization", "Basic " + encoded);
        }
        DataOutputStream out = new DataOutputStream(httpConnection.getOutputStream());
        out.writeBytes(payload);
        out.flush();
        out.close();
        System.out.println("RC: " + httpConnection.getResponseCode());
        httpConnection.disconnect();
    }

    public static class CouchbaseWaitStrategy extends GenericContainer.AbstractWaitStrategy {

        /**
         * Authorization HTTP header.
         */
        private static final String HEADER_AUTHORIZATION = "Authorization";

        /**
         * Basic Authorization scheme prefix.
         */
        private static final String AUTH_BASIC = "Basic ";

        private String path = "/pools/default/";
        private int statusCode = HttpURLConnection.HTTP_OK;
        private boolean tlsEnabled;
        private String username;
        private String password;
        private ObjectMapper om = new ObjectMapper();

        /**
         * Indicates that the status check should use HTTPS.
         *
         * @return this
         */
        public CouchbaseWaitStrategy usingTls() {
            this.tlsEnabled = true;
            return this;
        }

        /**
         * Authenticate with HTTP Basic Authorization credentials.
         *
         * @param username the username
         * @param password the password
         * @return this
         */
        public CouchbaseWaitStrategy withBasicCredentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        @Override
        protected void waitUntilReady() {
            final Integer livenessCheckPort = getLivenessCheckPort();
            if (livenessCheckPort == null) {
                logger().warn("No exposed ports or mapped ports - cannot wait for status");
                return;
            }

            final String uri = buildLivenessUri(livenessCheckPort).toString();
            logger().info("Waiting for {} seconds for URL: {}", 60 /*startupTimeout.getSeconds()*/, uri);

            // try to connect to the URL
            try {
                retryUntilSuccess((int) 60/*startupTimeout.getSeconds()*/, TimeUnit.SECONDS, new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        getRateLimiter().doWhenReady(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();

                                    // authenticate
                                    if (!Strings.isNullOrEmpty(username)) {
                                        connection.setRequestProperty(HEADER_AUTHORIZATION, buildAuthString(username, password));
                                        connection.setUseCaches(false);
                                    }

                                    connection.setRequestMethod("GET");
                                    connection.connect();

                                    if (statusCode != connection.getResponseCode()) {
                                        throw new RuntimeException(String.format("HTTP response code was: %s",
                                                connection.getResponseCode()));
                                    }

                                    // Specific Couchbase wait strategy to be sure the node is online and healthy
                                    JsonNode node = om.readTree(connection.getInputStream());
                                    JsonNode statusNode = node.at("/nodes/0/status");
                                    String status = statusNode.asText();
                                    if (!"healthy".equals(status)) {
                                        throw new RuntimeException(String.format("Couchbase Node status was: %s", status));
                                    }

                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });

                        return true;
                    }
                });

            } catch (TimeoutException e) {
                throw new ContainerLaunchException(String.format(
                        "Timed out waiting for URL to be accessible (%s should return HTTP %s)", uri, statusCode));
            }
        }

        /**
         * Build the URI on which to check if the container is ready.
         *
         * @param livenessCheckPort the liveness port
         * @return the liveness URI
         */
        private URI buildLivenessUri(int livenessCheckPort) {
            final String scheme = (tlsEnabled ? "https" : "http") + "://";
            final String host = container.getContainerIpAddress();

            final String portSuffix;
            if ((tlsEnabled && 443 == livenessCheckPort) || (!tlsEnabled && 80 == livenessCheckPort)) {
                portSuffix = "";
            } else {
                portSuffix = ":" + String.valueOf(livenessCheckPort);
            }

            return URI.create(scheme + host + portSuffix + path);
        }

        /**
         * @param username the username
         * @param password the password
         * @return a basic authentication string for the given credentials
         */
        private String buildAuthString(String username, String password) {
            return AUTH_BASIC + BaseEncoding.base64().encode((username + ":" + password).getBytes());
        }
    }

}
