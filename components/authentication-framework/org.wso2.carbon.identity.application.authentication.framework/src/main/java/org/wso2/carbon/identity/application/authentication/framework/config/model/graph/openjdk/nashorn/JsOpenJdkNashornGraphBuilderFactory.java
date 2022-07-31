/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.config.model.graph.openjdk.nashorn;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.wso2.carbon.identity.application.authentication.framework.config.model.StepConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.graph.AuthGraphNode;
import org.wso2.carbon.identity.application.authentication.framework.config.model.graph.JsBaseGraphBuilderFactory;
import org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js.AbstractJSObjectWrapper;
import org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js.JsLogger;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.impl.OpenJdkSelectAcrFromFunction;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.impl.SelectOneFunction;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Factory to create a Javascript based sequence builder.
 * This factory is there to reuse of Open JDk Nashorn engine and any related expnsive objects.
 *
 * Since Nashorn is deprecated in JDK 11 and onwards. We are introducing OpenJDK Nashorn engine.
 */
public class JsOpenJdkNashornGraphBuilderFactory implements JsBaseGraphBuilderFactory {

    private static final Log LOG = LogFactory.getLog(JsOpenJdkNashornGraphBuilderFactory.class);
    private static final String JS_BINDING_CURRENT_CONTEXT = "JS_BINDING_CURRENT_CONTEXT";
    private static final String[] NASHORN_ARGS = {"--no-java"};
    private ClassFilter classFilter;

    // Suppress the Nashorn deprecation warnings in jdk 11
    @SuppressWarnings("removal")
    private NashornScriptEngineFactory factory;


    public void init() {

        factory = new NashornScriptEngineFactory();
        classFilter = new OpenJdkNashornRestrictedClassFilter();
    }

    public static void restoreCurrentContext(AuthenticationContext context, ScriptEngine engine)
        throws FrameworkException {

        Map<String, Object> map = (Map<String, Object>) context.getProperty(JS_BINDING_CURRENT_CONTEXT);
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object deserializedValue = fromJsSerializable(entry.getValue(), engine);
                if (deserializedValue instanceof AbstractJSObjectWrapper) {
                    ((AbstractJSObjectWrapper) deserializedValue).initializeContext(context);
                }
                bindings.put(entry.getKey(), deserializedValue);
            }
        }
    }

    private static Object fromJsSerializable(Object value, ScriptEngine engine) throws FrameworkException {

        if (value instanceof OpenJdkNashornSerializableJsFunction) {
            OpenJdkNashornSerializableJsFunction serializableJsFunction = (OpenJdkNashornSerializableJsFunction) value;
            try {
                return engine.eval(serializableJsFunction.getSource());
            } catch (ScriptException e) {
                throw new FrameworkException("Error in resurrecting a Javascript Function : " + serializableJsFunction);
            }

        } else if (value instanceof Map) {
            Map<String, Object> deserializedMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                Object deserializedObj = fromJsSerializable(entry.getValue(), engine);
                deserializedMap.put(entry.getKey(), deserializedObj);
            }
            return deserializedMap;
        }
        return value;
    }

    public static void persistCurrentContext(AuthenticationContext context, ScriptEngine engine) {

        Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        Map<String, Object> persistableMap = new HashMap<>();
        engineBindings.forEach((key, value) -> persistableMap.put(key, toJsSerializable(value)));
        context.setProperty(JS_BINDING_CURRENT_CONTEXT, persistableMap);
    }

    private static Object toJsSerializable(Object value) {

        if (value instanceof Serializable) {
            if (value instanceof HashMap) {
                Map<String, Object> map = new HashMap<>();
                ((HashMap) value).forEach((k, v) -> map.put((String) k, toJsSerializable(v)));
                return map;
            } else {
                return value;
            }
        } else if (value instanceof ScriptObjectMirror) {
            ScriptObjectMirror scriptObjectMirror = (ScriptObjectMirror) value;
            if (scriptObjectMirror.isFunction()) {
                return OpenJdkNashornSerializableJsFunction.toSerializableForm(scriptObjectMirror);
            } else if (scriptObjectMirror.isArray()) {
                List<Serializable> arrayItems = new ArrayList<>(scriptObjectMirror.size());
                scriptObjectMirror.values().forEach(v -> {
                    Object serializedObj = toJsSerializable(v);
                    if (serializedObj instanceof Serializable) {
                        arrayItems.add((Serializable) serializedObj);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Serialized the value of array item as : " + serializedObj);
                        }
                    } else {
                        LOG.warn(String.format("Non serializable array item: %s. and will not be persisted.",
                                serializedObj));
                    }
                });
                return arrayItems;
            } else if (!scriptObjectMirror.isEmpty()) {
                Map<String, Serializable> serializedMap = new HashMap<>();
                scriptObjectMirror.forEach((k, v) -> {
                    Object serializedObj = toJsSerializable(v);
                    if (serializedObj instanceof Serializable) {
                        serializedMap.put(k, (Serializable) serializedObj);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Serialized the value for key : " + k);
                        }
                    } else {
                        LOG.warn(String.format("Non serializable object for key : %s, and will not be persisted.", k));
                    }

                });
                return serializedMap;
            } else {
                return Collections.EMPTY_MAP;
            }
        }
        return value;
    }

    public ScriptEngine createEngine(AuthenticationContext authenticationContext) {

        ScriptEngine engine = factory.getScriptEngine(NASHORN_ARGS, getClassLoader(), classFilter);
        Bindings bindings = engine.createBindings();
        engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
        engine.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        OpenJdkSelectAcrFromFunction selectAcrFromFunction = new OpenJdkSelectAcrFromFunction();
//        todo move to functions registry
        bindings.put(FrameworkConstants.JSAttributes.JS_FUNC_SELECT_ACR_FROM,
            (SelectOneFunction) selectAcrFromFunction::evaluate);

        JsLogger jsLogger = new JsLogger();
        bindings.put(FrameworkConstants.JSAttributes.JS_LOG, jsLogger);
        return engine;
    }

    private ClassLoader getClassLoader() {

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? NashornScriptEngineFactory.class.getClassLoader() : classLoader;
    }

    public JsOpenJdkNashornGraphBuilder createBuilder(AuthenticationContext authenticationContext,
                                        Map<Integer, StepConfig> stepConfigMap) {

        return new JsOpenJdkNashornGraphBuilder(authenticationContext, stepConfigMap,
                createEngine(authenticationContext));
    }

    public JsOpenJdkNashornGraphBuilder createBuilder(AuthenticationContext authenticationContext,
                                        Map<Integer, StepConfig> stepConfigMap, AuthGraphNode currentNode) {

        return new JsOpenJdkNashornGraphBuilder(authenticationContext, stepConfigMap,
                createEngine(authenticationContext), currentNode);
    }
}
