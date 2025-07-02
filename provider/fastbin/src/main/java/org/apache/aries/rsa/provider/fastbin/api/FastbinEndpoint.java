/**
 *  Copyright 2016 SEEBURGER AG
 *
 *  SEEBURGER licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.apache.aries.rsa.provider.fastbin.api;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.apache.aries.rsa.provider.fastbin.FastBinProvider;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker.ServiceFactory;
import org.apache.aries.rsa.provider.fastbin.util.Uuid5Generator;
import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * FastbinEndpoint.
 *
 * @author utzig
 */
public class FastbinEndpoint implements Endpoint
{
    private static final boolean FASTBIN_RANDOM_UUID = Boolean.getBoolean("org.apache.aries.rsa.provider.fastbin.random.uuid");
    private static final String SYSTEM_ID_INSTANCE_ID;
//    private static Set<String> uuids = new ConcurrentSkipListSet<String>();
    private static final Logger log = LoggerFactory.getLogger(FastbinEndpoint.class);

    private EndpointDescription endpointDescription;
    private ServerInvoker server;

    static {
        String temp = System.getProperty("system.id", "") + "#" + System.getProperty("instance.id", "");
        if ("#".equals(temp)) {
            SYSTEM_ID_INSTANCE_ID = null;
        }
        else {
            SYSTEM_ID_INSTANCE_ID = temp + "#";
        }
    }

    public FastbinEndpoint(ServerInvoker server, Map<String, Object> effectiveProperties, final Object serviceBean)
    {
        this.server = server;
        effectiveProperties.put(FastBinProvider.PROTOCOL_VERSION_PROPERTY, String.valueOf(FastBinProvider.PROTOCOL_VERSION));
        String fastbinAddress = server.getConnectAddress();
        effectiveProperties.put(FastBinProvider.SERVER_ADDRESS, fastbinAddress);
        String endpointID = effectiveProperties.getOrDefault(RemoteConstants.ENDPOINT_ID, getEndpointID(effectiveProperties)).toString();
        effectiveProperties.put(RemoteConstants.ENDPOINT_ID, endpointID);
        endpointDescription = new EndpointDescription(effectiveProperties);


        final ServiceFactory factory = new ServiceFactory() {

            @Override
            public void unget() {

            }

            @Override
            public Object get() {
                return serviceBean;
            }
        };

        server.registerService(endpointID, factory, serviceBean.getClass().getClassLoader());
    }


    static UUID getEndpointID(Map<String, Object> effectiveProperties) {
        if (!FASTBIN_RANDOM_UUID && SYSTEM_ID_INSTANCE_ID != null) {
            /*
            Not static:
            - service.id
            - endpoint.framework.uuid
            - endpoint.service.id
            - component.id
             */
            // those are static, servicePid and componentName might be not set:
            Object servicePid = effectiveProperties.getOrDefault(Constants.SERVICE_PID, "");        // com.seeburger.aqm.platform.AqmManagementService
            Object componentName = effectiveProperties.getOrDefault("component.name", "");  // com.seeburger.aqm.platform.AqmManagementService
            Object serviceExportedInterfaces = effectiveProperties.getOrDefault(RemoteConstants.SERVICE_EXPORTED_INTERFACES, ""); // com.seeburger.aqm.interfaces.AqmManagement
            Object serviceExportedConfigs = effectiveProperties.getOrDefault(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "");       // aries.fastbin
            Object fastbinAddress = effectiveProperties.getOrDefault(FastBinProvider.SERVER_ADDRESS, "");                // tcp://10.14.35.200:4000

            String uuidInput = SYSTEM_ID_INSTANCE_ID + servicePid + componentName + serviceExportedInterfaces + serviceExportedConfigs + fastbinAddress;

            UUID uuid = Uuid5Generator.generateType5UUID(uuidInput);

//            boolean added = uuids.add(uuid.toString());
//            if (!added) {
//                log.error("Endpoint ID {} already exists! Props: {}", uuid, effectiveProperties);
//            }
            log.info("Created endpoint ID {} for: {}.", uuid, uuidInput);
            return uuid;
        }
        UUID uuid = UUID.randomUUID();
        log.info("Created random endpoint ID: {}", uuid);
        return uuid;
    }


    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException
    {
        server.unregisterService((String)endpointDescription.getId());
    }


    /**
     * @see org.apache.aries.rsa.spi.Endpoint#description()
     */
    @Override
    public EndpointDescription description()
    {
        return endpointDescription;
    }

}
