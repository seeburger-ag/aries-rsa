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

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.apache.aries.rsa.provider.fastbin.FastBinProvider;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker.ServiceFactory;
import org.apache.aries.rsa.provider.fastbin.tcp.TcpTransportServer;
import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

/**
 * FastbinEndpoint.
 * <p>
 * Long description for FastbinEndpoint.
 *
 * @author utzig
 */
public class FastbinEndpoint implements Endpoint
{

    private EndpointDescription endpointDescription;
    private ServerInvoker server;

    public FastbinEndpoint(ServerInvoker server, Map<String, Object> effectiveProperties, final Object serviceBean)
    {
        this.server = server;
        effectiveProperties.put(FastBinProvider.PROTOCOL_VERSION_PROPERTY, String.valueOf(FastBinProvider.PROTOCOL_VERSION));
        String fastbinAddress = server.getConnectAddress();
        effectiveProperties.put(FastBinProvider.SERVER_ADDRESS, fastbinAddress);
        String endpointID = effectiveProperties.getOrDefault(RemoteConstants.ENDPOINT_ID, UUID.randomUUID()).toString();
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



