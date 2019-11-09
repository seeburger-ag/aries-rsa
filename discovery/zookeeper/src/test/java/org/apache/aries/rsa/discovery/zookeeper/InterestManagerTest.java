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
package org.apache.aries.rsa.discovery.zookeeper;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

public class InterestManagerTest {

    @Test
    public void testEndpointListenerTrackerCustomizer() {
        IMocksControl c = EasyMock.createControl();
        ServiceReference<EndpointEventListener> sref = createService(c, "(objectClass=mine)");
        ServiceReference<EndpointEventListener> sref2 = createService(c, "(objectClass=mine)");
        EndpointEventListener epListener1 = c.createMock(EndpointEventListener.class); 
        EndpointEventListener epListener2 = c.createMock(EndpointEventListener.class); 

        c.replay();

        InterestManager im = new InterestManager();
        // sref has no scope -> nothing should happen
        assertEquals(0, im.getInterests().size());

        im.bindEndpointEventListener(sref, epListener1);
        assertEquals(1, im.getInterests().size());

        im.bindEndpointEventListener(sref, epListener1);
        assertEquals(1, im.getInterests().size());

        im.bindEndpointEventListener(sref2, epListener2);
        assertEquals(2, im.getInterests().size());

        im.unbindEndpointEventListener(sref);
        assertEquals(1, im.getInterests().size());

        im.unbindEndpointEventListener(sref);
        assertEquals(1, im.getInterests().size());

        im.unbindEndpointEventListener(sref2);
        assertEquals(0, im.getInterests().size());

        c.verify();
    }

    @SuppressWarnings("unchecked")
    private ServiceReference<EndpointEventListener> createService(IMocksControl c, String scope) {
        ServiceReference<EndpointEventListener> sref = c.createMock(ServiceReference.class);
        expect(sref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE)).andReturn(scope).atLeastOnce();
        expect(sref.getProperty(ClientManager.DISCOVERY_ZOOKEEPER_ID)).andReturn(null).atLeastOnce();
        return sref;
    }

}
