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

import static org.easymock.EasyMock.getCurrentArguments;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

public class InterfaceMonitorManagerTest {

    @Test
    public void testEndpointListenerTrackerCustomizer() {
        IMocksControl c = EasyMock.createNiceControl();
        BundleContext ctx = c.createMock(BundleContext.class);
        ServiceReference<EndpointEventListener> sref = createService(c, "(objectClass=mine)", "mine");
        ServiceReference<EndpointEventListener> sref2 = createService(c, "(objectClass=mine)", "mine");
        ZooKeeper zk = c.createMock(ZooKeeper.class);
        InterfaceMonitorManager eltc = new InterfaceMonitorManager(ctx, zk);

        c.replay();

        // sref has no scope -> nothing should happen
        assertEquals(0, eltc.getEndpointListenerScopes().size());
        assertEquals(0, eltc.getInterests().size());

        eltc.addInterest(sref);
        assertScopeIncludes(sref, eltc);
        assertEquals(1, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.addInterest(sref);
        assertScopeIncludes(sref, eltc);
        assertEquals(1, eltc.getEndpointListenerScopes().size());
        assertEquals(1, eltc.getInterests().size());

        eltc.addInterest(sref2);
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
    private ServiceReference<EndpointEventListener> createService(IMocksControl c, String scope, String objectClass) {
        ServiceReference<EndpointEventListener> sref = c.createMock(ServiceReference.class);
        final Dictionary<String, String> props = new Hashtable<>();
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scope);
        props.put(Constants.OBJECTCLASS, objectClass);
        String[] keys = Collections.list(props.keys()).toArray(new String[]{});
        EasyMock.expect(sref.getPropertyKeys()).andReturn(keys).anyTimes();
        EasyMock.expect(sref.getProperty((String)EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                return props.get(getCurrentArguments()[0]);
            }
        }).anyTimes();
        return sref;
    }

    private void assertScopeIncludes(ServiceReference<EndpointEventListener> sref, InterfaceMonitorManager imm) {
        List<String> srefScope = imm.getEndpointListenerScopes().get(sref);
        assertEquals(1, srefScope.size());
        assertEquals("(objectClass=mine)", srefScope.get(0));
        
    }

}
