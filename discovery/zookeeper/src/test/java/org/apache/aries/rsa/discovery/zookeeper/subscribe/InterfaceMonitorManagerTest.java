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
package org.apache.aries.rsa.discovery.zookeeper.subscribe;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.discovery.zookeeper.subscribe.InterfaceMonitorManager;
import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointListener;

public class InterfaceMonitorManagerTest {

    @Test
    public void testEndpointListenerTrackerCustomizer() {
        IMocksControl c = EasyMock.createNiceControl();
        BundleContext ctx = c.createMock(BundleContext.class);
        ServiceReference<EndpointListener> sref = createService(c);
        ServiceReference<EndpointListener> sref2 = createService(c);
        ZooKeeper zk = c.createMock(ZooKeeper.class);
        InterfaceMonitorManager eltc = new InterfaceMonitorManager(ctx, zk);

        c.replay();

        // sref has no scope -> nothing should happen
        assertEquals(0, eltc.getEndpointListenerScopes().size());
        assertEquals(0, eltc.getInterests().size());

        eltc.addInterest(sref, "(objectClass=mine)", "mine");
        assertScopeIncludes(sref, eltc);
        assertEquals(1, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.addInterest(sref, "(objectClass=mine)", "mine");
        assertScopeIncludes(sref, eltc);
        assertEquals(1, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.addInterest(sref2, "(objectClass=mine)", "mine");
        assertScopeIncludes(sref, eltc);
        assertScopeIncludes(sref2, eltc);
        assertEquals(2, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.removeInterest(sref);
        assertScopeIncludes(sref2, eltc);
        assertEquals(1, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.removeInterest(sref);
        assertScopeIncludes(sref2, eltc);
        assertEquals(1, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.removeInterest(sref2);
        assertEquals(0, eltc.getEndpointListenerScopes().size());
        assertEquals(0, eltc.getInterests().size());

        c.verify();
    }

    @SuppressWarnings("unchecked")
    private ServiceReference<EndpointListener> createService(IMocksControl c) {
        final Map<String, ?> p = new HashMap<String, Object>();
        ServiceReference<EndpointListener> sref = c.createMock(ServiceReference.class);
        EasyMock.expect(sref.getPropertyKeys()).andAnswer(new IAnswer<String[]>() {
            public String[] answer() throws Throwable {
                return p.keySet().toArray(new String[p.size()]);
            }
        }).anyTimes();

        EasyMock.expect(sref.getProperty((String)EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                String key = (String)(EasyMock.getCurrentArguments()[0]);
                return p.get(key);
            }
        }).anyTimes();
        return sref;
    }

    private void assertScopeIncludes(ServiceReference<EndpointListener> sref, InterfaceMonitorManager eltc) {
        List<String> srefScope = eltc.getEndpointListenerScopes().get(sref);
        assertEquals(1, srefScope.size());
        assertEquals("(objectClass=mine)", srefScope.get(0));
        
    }

}
