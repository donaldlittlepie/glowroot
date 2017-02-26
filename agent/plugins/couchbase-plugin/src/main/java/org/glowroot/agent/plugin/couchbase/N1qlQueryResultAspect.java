package org.glowroot.agent.plugin.couchbase;

import org.glowroot.agent.plugin.api.QueryEntry;
import org.glowroot.agent.plugin.api.weaving.*;

import javax.annotation.Nullable;

public class N1qlQueryResultAspect {

    @Mixin("com.couchbase.client.java.query.N1qlQueryResult")
    public static class N1qlQueryResultImpl implements N1qlQueryResult {

        // Ask Trask about this - see cassandra ResultSetAspect
        private volatile @Nullable QueryEntry glowroot$lastQueryEntry;

        @Nullable
        @Override
        public QueryEntry glowroot$getLastQueryEntry() {
            return glowroot$lastQueryEntry;
        }

        @Override
        public void glowroot$setLastQueryEntry(@Nullable QueryEntry lastQueryEntry) {
            this.glowroot$lastQueryEntry = lastQueryEntry;
        }

        @Override
        public boolean glowroot$hasLastQueryEntry() {
            return glowroot$lastQueryEntry != null;
        }
    }

    // method name are verbose to avoid conflict since they will become methods in all
    // classes that extend com.couchbase.client.java.query.N1qlQueryResult
    public interface N1qlQueryResult {

        @Nullable
        QueryEntry glowroot$getLastQueryEntry();

        void glowroot$setLastQueryEntry(@Nullable QueryEntry lastQueryEntry);

        boolean glowroot$hasLastQueryEntry();
    }

    @Pointcut(className = "com.couchbase.client.java.query.N1qlQueryResult", methodName = "allRows",
        methodParameterTypes = {})
    public static class AllRowsAdvice {
        @OnReturn
        public static void onReturn(@BindReceiver N1qlQueryResult queryResult) {
            QueryEntry lastQueryEntry = queryResult.glowroot$getLastQueryEntry();
            if (lastQueryEntry == null) {
                // tracing must be disabled (e.g. exceeded trace entry limit)
                return;
            }
            lastQueryEntry.rowNavigationAttempted();
        }
    }
}
