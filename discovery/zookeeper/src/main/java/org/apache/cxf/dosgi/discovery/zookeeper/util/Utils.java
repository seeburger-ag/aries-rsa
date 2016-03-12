/**
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
package org.apache.cxf.dosgi.discovery.zookeeper.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.ServiceReference;

public final class Utils {

    static final String PATH_PREFIX = "/osgi/service_registry";
    static final Pattern OBJECTCLASS_PATTERN = Pattern.compile(".*\\(objectClass=([^)]+)\\).*");

    private Utils() {
        // never constructed
    }

    public static String getZooKeeperPath(String name) {
        return name == null || name.isEmpty() ? PATH_PREFIX : PATH_PREFIX + '/' + name.replace('.', '/');
    }

    /**
     * Removes nulls and empty strings from the given string array.
     *
     * @param strings an array of strings
     * @return a new array containing the non-null and non-empty
     *         elements of the original array in the same order
     */
    public static List<String> removeEmpty(List<String> strings) {
        List<String> result = new ArrayList<String>();
        for (String s : strings) {
            if (s != null && !s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    public static String getObjectClass(String scope) {
        Matcher m = OBJECTCLASS_PATTERN.matcher(scope);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Returns a service's properties as a map.
     *
     * @param serviceReference a service reference
     * @return the service's properties as a map
     */
    public static Map<String, Object> getProperties(ServiceReference<?> serviceReference) {
        String[] keys = serviceReference.getPropertyKeys();
        Map<String, Object> props = new HashMap<String, Object>(keys.length);
        for (String key : keys) {
            Object val = serviceReference.getProperty(key);
            props.put(key, val);
        }
        return props;
    }
}
