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
package org.apache.aries.rsa.topologymanager.importer.local;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.topologymanager.Activator;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class ListenerHookImplTest {

    @Test
    public void testExtendFilter() throws InvalidSyntaxException {
        String filter = "(a=b)";
        BundleContext bc = createBundleContext();
        filter = new ListenerHookImpl(bc, null).extendFilter(filter);

        Filter f = FrameworkUtil.createFilter(filter);

        Dictionary<String, String> m = new Hashtable<>();
        m.put("a", "b");
        assertTrue(filter + " filter must match as uuid is missing", f.match(m));
        m.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, "MyUUID");
        assertFalse(filter + " filter must NOT match as uuid is the local one", f.match(m));
    }

    @Test
    public void testAddedRemoved() throws InvalidSyntaxException {
        IMocksControl c = EasyMock.createControl();
        String filter = "(objectClass=My)";
        BundleContext bc = createBundleContext();
        BundleContext listenerBc = createBundleContext();
        ServiceInterestListener serviceInterestListener = c.createMock(ServiceInterestListener.class);
        ListenerHookImpl listenerHook = new ListenerHookImpl(bc, serviceInterestListener);

        ListenerInfo listener = c.createMock(ListenerInfo.class);
        EasyMock.expect(listener.getBundleContext()).andReturn(listenerBc);
        EasyMock.expect(listener.getFilter()).andReturn(filter).atLeastOnce();

        // Main assertions
        serviceInterestListener.addServiceInterest(listenerHook.extendFilter(filter));
        EasyMock.expectLastCall();
        serviceInterestListener.removeServiceInterest(listenerHook.extendFilter(filter));
        EasyMock.expectLastCall();

        Collection<ListenerInfo> listeners = Collections.singletonList(listener);

        c.replay();
        listenerHook.added(listeners);
        listenerHook.removed(listeners);
        c.verify();
    }

    private BundleContext createBundleContext() {
        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
        EasyMock.expect(bc.getProperty(EasyMock.eq("org.osgi.framework.uuid"))).andReturn("MyUUID").atLeastOnce();
        EasyMock.replay(bc);
        Activator.frameworkUUID = "MyUUID";
        return bc;
    }
}
