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

import static org.hamcrest.core.StringStartsWith.startsWith;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.provider.tcp.myservice.PrimitiveService;
import org.apache.aries.rsa.provider.tcp.myservice.PrimitiveServiceImpl;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.util.EndpointHelper;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.BundleContext;

public class TcpProviderPrimitiveTest {

    private static PrimitiveService myServiceProxy;
    private static Endpoint ep;
    
    @BeforeClass
    public static void createServerAndProxy() {
        Class<?>[] exportedInterfaces = new Class[] {PrimitiveService.class};
        TCPProvider provider = new TCPProvider();
        Map<String, Object> props = new HashMap<String, Object>();
        EndpointHelper.addObjectClass(props, exportedInterfaces);
        props.put("aries.rsa.hostname", "localhost");
        props.put("aries.rsa.numThreads", "10");
        PrimitiveServiceImpl myService = new PrimitiveServiceImpl();
        BundleContext bc = EasyMock.mock(BundleContext.class);
        ep = provider.exportService(myService, bc, props, exportedInterfaces);
        Assert.assertThat(ep.description().getId(), startsWith("tcp://localhost:"));
        System.out.println(ep.description());
        myServiceProxy = (PrimitiveService)provider.importEndpoint(PrimitiveService.class.getClassLoader(), 
                                                            bc,
                                                            exportedInterfaces, 
                                                            ep.description());
    }

    @Test
    public void testByte() {
        Assert.assertEquals((byte)1, myServiceProxy.callByte((byte) 1));
    }
    
    @Test
    public void testShort() {
        Assert.assertEquals((short)1, myServiceProxy.callShort((short) 1));
    }
    
    @Test
    public void testInteger() {
        Assert.assertEquals(1, myServiceProxy.callInt(1));
    }
    
    @Test
    public void testLong() {
        Assert.assertEquals(1l, myServiceProxy.callLong(1l));
    }

    @Test
    public void testFloat() {
        Assert.assertEquals(1f, myServiceProxy.callFloat(1f), 0.001);
    }
    
    @Test
    public void testDouble() {
        Assert.assertEquals(1d, myServiceProxy.callDouble(1d), 0.001);
    }
    
    @Test
    public void testBoolean() {
        Assert.assertEquals(true, myServiceProxy.callBoolean(true));
    }
    
    @Test
    public void testByteAr() {
        Assert.assertArrayEquals(new byte[]{1}, myServiceProxy.callByteAr(new byte[]{1}));
    }

    @AfterClass
    public static void close() throws IOException {
        ep.close();
    }

}
