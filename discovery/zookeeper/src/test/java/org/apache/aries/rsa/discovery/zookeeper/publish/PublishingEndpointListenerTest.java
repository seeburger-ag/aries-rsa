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

import static org.easymock.EasyMock.expect;

import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.discovery.zookeeper.repository.ZookeeperEndpointRepository;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import junit.framework.TestCase;

public class PublishingEndpointListenerTest extends TestCase {

    private static final String ENDPOINT_PATH = "/osgi/service_registry/http:##google.de:80#test#sub";

    public void testEndpointRemovalAdding() throws KeeperException, InterruptedException {
        IMocksControl c = EasyMock.createNiceControl();

        ZooKeeper zk = c.createMock(ZooKeeper.class);

        String path = ENDPOINT_PATH;
        expectCreated(zk, path);
        expectDeleted(zk, path);

        c.replay();

        ZookeeperEndpointRepository repository = new ZookeeperEndpointRepository(zk);
        PublishingEndpointListener eli = new PublishingEndpointListener(repository);
        EndpointDescription endpoint = createEndpoint();
        eli.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint), null);
        eli.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint), null); // should do nothing
        eli.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, endpoint), null);
        eli.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, endpoint), null); // should do nothing

        c.verify();
    }

    private void expectCreated(ZooKeeper zk, String path) throws KeeperException, InterruptedException {
        expect(zk.create(EasyMock.eq(path), 
                         (byte[])EasyMock.anyObject(), 
                         EasyMock.eq(Ids.OPEN_ACL_UNSAFE),
                         EasyMock.eq(CreateMode.EPHEMERAL)))
            .andReturn("");
    }

    private void expectDeleted(ZooKeeper zk, String path) throws InterruptedException, KeeperException {
        zk.delete(EasyMock.eq(path), EasyMock.eq(-1));
        EasyMock.expectLastCall().once();
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(Constants.OBJECTCLASS, new String[] {"myClass"});
        props.put(RemoteConstants.ENDPOINT_ID, "http://google.de:80/test/sub");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "myConfig");
        return new EndpointDescription(props);
    }
}
