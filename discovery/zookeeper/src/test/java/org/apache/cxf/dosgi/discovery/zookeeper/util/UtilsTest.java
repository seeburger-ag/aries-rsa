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
package org.apache.cxf.dosgi.discovery.zookeeper.util;

import java.util.List;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointListener;

import junit.framework.TestCase;

public class UtilsTest extends TestCase {

    public void testGetZooKeeperPath() {
        assertEquals(Utils.PATH_PREFIX + '/' + "org/example/Test",
            Utils.getZooKeeperPath("org.example.Test"));

        // used for the recursive discovery
        assertEquals(Utils.PATH_PREFIX, Utils.getZooKeeperPath(null));
        assertEquals(Utils.PATH_PREFIX, Utils.getZooKeeperPath(""));
    }

    public void testGetScopes() {
        IMocksControl c = EasyMock.createNiceControl();

        String[] scopes = new String[]{"myScope=test", ""};

        @SuppressWarnings("unchecked")
        ServiceReference<EndpointListener> sref = c.createMock(ServiceReference.class);
        EasyMock.expect(sref.getProperty(EasyMock.eq(EndpointListener.ENDPOINT_LISTENER_SCOPE)))
            .andReturn(scopes).anyTimes();

        c.replay();

        List<String> ret = Utils.getScopes(sref);

        c.verify();
        assertEquals(1, ret.size());
        assertEquals(scopes[0], ret.get(0));
    }
}
