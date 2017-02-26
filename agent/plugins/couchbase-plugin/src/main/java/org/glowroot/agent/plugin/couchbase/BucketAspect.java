package org.glowroot.agent.plugin.couchbase;

import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.config.ConfigListener;
import org.glowroot.agent.plugin.api.config.ConfigService;
import org.glowroot.agent.plugin.api.weaving.*;
import org.glowroot.agent.plugin.couchbase.N1qlQueryResultAspect.N1qlQueryResult;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class BucketAspect {

    private static final String QUERY_TYPE = "N1QL";
    private static final ConfigService configService = Agent.getConfigService("couchbase");


    // visibility is provided by memoryBarrier in org.glowroot.config.ConfigService
    private static int stackTraceThresholdMillis;

    static {
        configService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                Double value = configService.getDoubleProperty("stackTraceThresholdMillis").value();
                stackTraceThresholdMillis = value == null ? Integer.MAX_VALUE : value.intValue();
            }
        });
    }

    @Shim("com.couchbase.client.java.query.Statement")
    public interface Statement {}

    @Shim("com.couchbase.client.java.query.N1qlQuery")
    public interface N1qlQuery {

        @Shim("com.couchbase.client.java.query.Statement statement()")
        @Nullable
        Statement glowroot$statement();
    }

    @Shim("com.couchbase.client.java.query.SimpleN1qlQuery")
    public interface SimpleN1qlQuery extends N1qlQuery {}

    @Shim("com.couchbase.client.java.query.ParameterizedN1qlQuery")
    public interface ParameterizedN1qlQuery extends N1qlQuery {

        @Shim("com.couchbase.client.java.document.json.JsonValue statementParameters()")
        @Nullable
        JsonValue glowroot$statementParameters();
    }

    @Shim("com.couchbase.client.java.document.json.JsonValue")
    public interface JsonValue {

        @Nullable
        String toString();
    }

    @Pointcut(className = "com.couchbase.client.java.Bucket", methodName = "query",
            methodParameterTypes = {"com.couchbase.client.java.query.N1qlQuery"},
            nestingGroup = "couchbase", timerName = "n1ql execute"
            //, suppression key - need to understands
    )
    public static class QueryAdvice {
        private static final TimerName timerName = Agent.getTimerName(QueryAdvice.class);

        @OnBefore
        public static @Nullable QueryEntry onBefore(ThreadContext context, @BindParameter @Nullable Object arg){
            QueryEntryInfo queryEntryInfo = getQueryEntryInfo(arg);
            if (queryEntryInfo == null) {
                return null;
            }

            return context.startQueryEntry(QUERY_TYPE, queryEntryInfo.queryText,
                    queryEntryInfo.queryMessageSupplier, timerName);
        }

        @OnReturn
        public static void onReturn(@BindReturn @Nullable N1qlQueryResult queryResult,
                @BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                if (queryResult != null) {
                    queryResult.glowroot$setLastQueryEntry(queryEntry);
                }
                queryEntry.endWithStackTrace(stackTraceThresholdMillis, TimeUnit.MILLISECONDS);
            }
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t, @BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                queryEntry.endWithError(t);
            }
        }
    }

    private static @Nullable QueryEntryInfo getQueryEntryInfo(@Nullable Object arg) {
        if (arg == null) {
            return null;
        }

        String queryText;
        String queryMessageSuffix = null;
        if (arg instanceof SimpleN1qlQuery) {
            Statement statement = ((SimpleN1qlQuery) arg).glowroot$statement();
            queryText = statement == null ? "" : statement.toString();
        }
        else if (arg instanceof ParameterizedN1qlQuery) {
            Statement statement = ((ParameterizedN1qlQuery) arg).glowroot$statement();
            queryText = statement == null ? "" : statement.toString();

            JsonValue parameters = ((ParameterizedN1qlQuery) arg).glowroot$statementParameters();
            queryMessageSuffix =  parameters == null ? null : " --> " + parameters.toString();
        }
        else {
            return null;
        }

        QueryMessageSupplier messageSupplier = queryMessageSuffix == null ?
                QueryMessageSupplier.create("n1ql execution: ") :
                QueryMessageSupplier.create("n1ql execution: ", queryMessageSuffix);

        return new QueryEntryInfo(queryText, messageSupplier);
    }

    private static class QueryEntryInfo {
        private final String queryText;
        private final QueryMessageSupplier queryMessageSupplier;

        public QueryEntryInfo(String queryText, QueryMessageSupplier queryMessageSupplier) {
            this.queryText = queryText;
            this.queryMessageSupplier = queryMessageSupplier;
        }
    }
}
