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
package org.apache.aries.rsa.provider.fastbin.api;


import org.apache.aries.rsa.provider.fastbin.FastBinProvider;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class UUIDTest
{
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        System.out.println("Setting up UUID tests...");
        System.setProperty("system.id", "my-system-id");
        System.setProperty("instance.id", "my-instance-id");
    }

    @Test
    public void testUUID() {
        String testString = "testString";
        UUID uuid = UUID.nameUUIDFromBytes(testString.getBytes(StandardCharsets.UTF_8));
        System.out.println("Generated UUID: " + uuid.toString());

        UUID uuid2 = UUID.nameUUIDFromBytes(testString.getBytes(StandardCharsets.UTF_8));
        System.out.println("Generated UUID: " + uuid2.toString());

        Assert.assertEquals(uuid, uuid2);
        Assert.assertEquals("536788f4-dbdf-3eec-bbb8-f350a941eea3", uuid.toString());

        String testString2 = "testString2";
        UUID uuid3 = UUID.nameUUIDFromBytes(testString2.getBytes(StandardCharsets.UTF_8));
        System.out.println("Generated UUID: " + uuid3.toString());

        UUID uuid4 = UUID.nameUUIDFromBytes(testString2.getBytes(StandardCharsets.UTF_8));
        System.out.println("Generated UUID: " + uuid4.toString());

        Assert.assertEquals(uuid3, uuid4);
        Assert.assertEquals("3149ebeb-00e7-3be4-ab6f-590775b82520", uuid3.toString());
    }


    @Test
    public void testUUID5() {
        UUID endpointID = FastbinEndpoint.getEndpointID(Collections.emptyMap());
        UUID endpointID2 = FastbinEndpoint.getEndpointID(Collections.emptyMap());

        System.out.println("Generated UUID5: " + endpointID);
        Assert.assertEquals(endpointID, endpointID2);
        Assert.assertEquals("9f412c86-aaac-520f-8dfd-7977de3716f4", endpointID.toString());
    }


    @Test
    public void testUUID5WithProperties() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put(Constants.SERVICE_PID, "org.apache.aries.rsa.provider.fastbin.InterfaceName");
        map.put("component.name", "ComponentName");
        map.put(RemoteConstants.SERVICE_EXPORTED_INTERFACES, "org.apache.aries.rsa.provider.fastbin.InterfaceName");
        map.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "aries.fastbin");
        map.put(FastBinProvider.SERVER_ADDRESS, "tcp://10.14.35.200:4000");

        UUID endpointID = FastbinEndpoint.getEndpointID(map);
        UUID endpointID2 = FastbinEndpoint.getEndpointID(map);

        System.out.println("Generated UUID5: " + endpointID);
        Assert.assertEquals(endpointID, endpointID2);
        Assert.assertEquals("fe6cafd0-1b69-56e7-aad5-48069a9ef29f", endpointID.toString());
    }

}
