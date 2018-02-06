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
package org.apache.aries.rsa.discovery.zookeeper.publish;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Dictionary;
import java.util.List;

import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;

@SuppressWarnings("deprecation")
public class PublishingEndpointListenerFactoryTest {

    private IMocksControl c;
    private BundleContext ctx;
    private ZooKeeper zk;

    @Before
    public void before() {
        c = EasyMock.createNiceControl();
        zk = c.createMock(ZooKeeper.class);
        ctx = createBundleContext();
    }
    
    @Test
    public void testScope() {
        PublishingEndpointListenerFactory eplf = new PublishingEndpointListenerFactory(zk, ctx);

        c.replay();
        eplf.start();
        c.verify();

    }

    @Test
    public void testServiceFactory() {
        PublishingEndpointListenerFactory eplf = new PublishingEndpointListenerFactory(zk, ctx);

        PublishingEndpointListener eli = c.createMock(PublishingEndpointListener.class);
        eli.close();
        EasyMock.expectLastCall().once();

        c.replay();
        eplf.start();

        PublishingEndpointListener service = eplf.getService(null, null);
        assertTrue(service instanceof EndpointEventListener);

        List<PublishingEndpointListener> listeners = eplf.getListeners();
        assertEquals(1, listeners.size());
        assertEquals(service, listeners.get(0));

        eplf.ungetService(null, null, service);
        listeners = eplf.getListeners();
        assertEquals(0, listeners.size());

        eplf.ungetService(null, null, eli); // no call to close
        listeners.add(eli);
        eplf.ungetService(null, null, eli); // call to close
        listeners = eplf.getListeners();
        assertEquals(0, listeners.size());

        c.verify();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private BundleContext createBundleContext() {
        BundleContext ctx = c.createMock(BundleContext.class);
        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);
        String[] ifAr = {EndpointEventListener.class.getName(), EndpointListener.class.getName()};
        EasyMock.expect(ctx.registerService(EasyMock.aryEq(ifAr), EasyMock.anyObject(),
                                            (Dictionary<String, String>)EasyMock.anyObject())).andReturn(sreg).once();
    
        EasyMock.expect(ctx.getProperty(EasyMock.eq("org.osgi.framework.uuid"))).andReturn("myUUID").anyTimes();
        return ctx;
    }
}
