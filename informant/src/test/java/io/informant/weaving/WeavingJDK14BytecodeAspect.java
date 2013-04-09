/**
 * Copyright 2012-2013 the original author or authors.
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
package io.informant.weaving;

import io.informant.api.weaving.BindTarget;
import io.informant.api.weaving.OnBefore;
import io.informant.api.weaving.Pointcut;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class WeavingJDK14BytecodeAspect {

    @Pointcut(typeName = "org.apache.commons.lang.StringUtils", methodName = "isEmpty",
            methodArgs = {"java.lang.String"}, metricName = "is empty")
    public static class BasicAdvice {
        @OnBefore
        public static void onBefore(@SuppressWarnings("unused") @BindTarget Class<?> target) {}
    }
}