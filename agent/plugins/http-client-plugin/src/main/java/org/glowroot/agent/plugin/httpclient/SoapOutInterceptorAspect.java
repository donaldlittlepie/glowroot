/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.plugin.httpclient;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;

import org.glowroot.agent.plugin.api.Agent;
import org.glowroot.agent.plugin.api.MessageSupplier;
import org.glowroot.agent.plugin.api.ThreadContext;
import org.glowroot.agent.plugin.api.TimerName;
import org.glowroot.agent.plugin.api.TraceEntry;
import org.glowroot.agent.plugin.api.weaving.BindParameter;
import org.glowroot.agent.plugin.api.weaving.BindThrowable;
import org.glowroot.agent.plugin.api.weaving.BindTraveler;
import org.glowroot.agent.plugin.api.weaving.OnBefore;
import org.glowroot.agent.plugin.api.weaving.OnReturn;
import org.glowroot.agent.plugin.api.weaving.OnThrow;
import org.glowroot.agent.plugin.api.weaving.Pointcut;
import org.glowroot.agent.plugin.api.weaving.Shim;

public class SoapOutInterceptorAspect {

    @Shim("org.apache.cxf.binding.soap.SoapMessage")
    public interface SoapMessage {
        @Nullable
        Object getContextualProperty(String key);
    }

    @Pointcut(className = "org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor",
            methodName = "handleMessage",
            methodParameterTypes = {"org.apache.cxf.binding.soap.SoapMessage"},
            nestingGroup = "http-client", timerName = "cxf client soap request")
    public static class SoapOutInterceptorAdvice {

        private static final TimerName timerName =
                Agent.getTimerName(SoapOutInterceptorAdvice.class);

        @OnBefore
        public static @Nullable TraceEntry onBefore(ThreadContext context,
                @BindParameter @Nullable SoapMessage message) {
            if (message == null) {
                return null;
            }
            Object methodObj = message.getContextualProperty("javax.xml.ws.http.request.method");
            String method;
            if (methodObj == null) {
                method = "";
            } else {
                method = methodObj.toString() + " ";
            }
            Object uriObj = message.getContextualProperty("javax.xml.ws.wsdl.description");
            String uri;
            if (uriObj == null) {
                uri = "";
            } else {
                uri = uriObj.toString();
            }
            Object operationObj = message.getContextualProperty("javax.xml.ws.wsdl.operation");
            String operationName;
            if (operationObj instanceof QName) {
                operationName = ", operation=" + ((QName) operationObj).getLocalPart();
            } else {
                operationName = "";
            }
            return context.startServiceCallEntry("HTTP",
                    method + Uris.stripQueryString(uri), MessageSupplier
                            .create("cxf client soap request: {}{}{}", method, uri, operationName),
                    timerName);
        }

        @OnReturn
        public static void onReturn(@BindTraveler @Nullable TraceEntry traceEntry) {
            if (traceEntry != null) {
                traceEntry.end();
            }
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t,
                @BindTraveler @Nullable TraceEntry traceEntry) {
            if (traceEntry != null) {
                traceEntry.endWithError(t);
            }
        }
    }
}
