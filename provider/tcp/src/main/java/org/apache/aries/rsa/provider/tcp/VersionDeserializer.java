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
package org.apache.aries.rsa.provider.tcp;

import org.osgi.framework.Version;

public class VersionDeserializer {

    public static Object[] replaceAr(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] result = new Object[args.length];
        for (int c=0; c<args.length; c++) {
            result[c] = replace(args[c]);
        }
        return result;
    }
    
    public static Object replace(Object obj) {
        if (obj == null) {
            return obj;
        }
        if (obj.getClass().isArray()) {
            if (obj.getClass().getComponentType() == SerVersion.class) {
                return replaceVersionAr((SerVersion[]) obj);
            } else if (obj.getClass().getComponentType() == Object.class) {
                return replaceAr((Object[]) obj);
            } else {
                return obj;
            }
        } else if (obj instanceof SerVersion) {
            SerVersion serVersion = (SerVersion) obj;
            return Version.parseVersion(serVersion.getVersion());
        } else {
            return obj;
        }
    }
    
    private static Version[] replaceVersionAr(SerVersion[] obj) {
        if (obj == null) {
            return null;
        }
        Version[] result = new Version[obj.length];
        for (int c=0; c<obj.length; c++) {
            result[c] = Version.parseVersion(obj[c].getVersion());
        }
        return result;
    }

}
