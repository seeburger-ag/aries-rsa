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
package org.apache.aries.rsa.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StringPlus {

    private static final Logger LOG = LoggerFactory.getLogger(StringPlus.class);

    private StringPlus() {
        // never constructed
    }

    @SuppressWarnings("rawtypes")
    public static List<String> normalize(Object object) {
        if (object instanceof String) {
            String s = (String)object;
            String[] values = s.split(",");
            List<String> list = new ArrayList<>();
            for (String val : values) {
                String actualValue = val.trim();
                if (!actualValue.isEmpty()) {
                    list.add(actualValue);
                }
            }
            return list;
        }

        if (object instanceof String[]) {
            return Arrays.asList((String[])object);
        }

        if (object instanceof Collection) {
            Collection col = (Collection)object;
            List<String> ar = new ArrayList<>(col.size());
            for (Object o : col) {
                if (o instanceof String) {
                    String s = (String)o;
                    ar.add(s);
                } else {
                    LOG.warn("stringPlus contained non string element in list! Element was skipped");
                }
            }
            return ar;
        }

        return null;
    }

}
