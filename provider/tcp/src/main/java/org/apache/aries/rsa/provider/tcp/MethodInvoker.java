/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.provider.tcp;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Reflectively invokes the methods of a service object
 * given the method name and parameter values.
 */
public class MethodInvoker {

    private HashMap<Object, Object> primTypes;
    private Object service;

    public MethodInvoker(Object service) {
        this.service = service;
        this.primTypes = new HashMap<>();
        this.primTypes.put(Byte.TYPE, Byte.class);
        this.primTypes.put(Short.TYPE, Short.class);
        this.primTypes.put(Integer.TYPE, Integer.class);
        this.primTypes.put(Long.TYPE, Long.class);
        this.primTypes.put(Float.TYPE, Float.class);
        this.primTypes.put(Double.TYPE, Double.class);
        this.primTypes.put(Boolean.TYPE, Boolean.class);
        this.primTypes.put(Character.TYPE, Character.class);
    }

    public Object getService() {
        return service;
    }

    public Object invoke(String methodName, Object[] args) throws Exception {
        Class<?>[] parameterTypesAr = getTypes(args);
        Method method = getMethod(methodName, parameterTypesAr);
        return method.invoke(service, args);
    }

    private Method getMethod(String methodName, Class<?>[] parameterTypesAr) throws NoSuchMethodException {
        try {
            return service.getClass().getMethod(methodName, parameterTypesAr);
        } catch (NoSuchMethodException e) {
            Method[] methods = service.getClass().getMethods();
            for (Method method : methods) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (allParamsMatch(method.getParameterTypes(), parameterTypesAr)) {
                    return method;
                }
            }
            throw new NoSuchMethodException(String.format("No method found that matches name %s, types %s",
                                                             methodName, Arrays.toString(parameterTypesAr)));
        }
    }

    private boolean allParamsMatch(Class<?>[] methodParamTypes, Class<?>[] parameterTypesAr) {
        if (parameterTypesAr.length != methodParamTypes.length)
            return false;
        for (int i = 0; i < methodParamTypes.length; i++) {
            if (!matches(methodParamTypes[i], parameterTypesAr[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Class<?> type, Class<?> paramType) {
        if (type.isPrimitive()) {
            return paramType == primTypes.get(type);
        }
        return paramType == null || type.isAssignableFrom(paramType);
    }

    private Class<?>[] getTypes(Object[] args) {
        int len = args == null ? 0 : args.length;
        Class<?>[] types = new Class<?>[len];
        for (int i = 0; i < len; i++) {
            types[i] = args[i] == null ? null : args[i].getClass();
        }
        return types;
    }
}
