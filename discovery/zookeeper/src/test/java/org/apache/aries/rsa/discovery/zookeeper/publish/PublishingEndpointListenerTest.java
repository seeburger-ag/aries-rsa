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

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import junit.framework.TestCase;

public class PublishingEndpointListenerTest extends TestCase {

    private static final String ENDPOINT_PATH = "/osgi/service_registry/myClass/google.de#80##test#sub";

    public void testEndpointRemovalAdding() throws KeeperException, InterruptedException {
        IMocksControl c = EasyMock.createNiceControl();

        BundleContext ctx = c.createMock(BundleContext.class);
        ZooKeeper zk = c.createMock(ZooKeeper.class);

        String path = ENDPOINT_PATH;
        expectCreated(zk, path);
        expectDeleted(zk, path);

        c.replay();

        PublishingEndpointListener eli = new PublishingEndpointListener(zk, ctx);
        EndpointDescription endpoint = createEndpoint();
        eli.endpointAdded(endpoint, null);
        eli.endpointAdded(endpoint, null); // should do nothing
        eli.endpointRemoved(endpoint, null);
        eli.endpointRemoved(endpoint, null); // should do nothing

        c.verify();
    }

    public void testDiscoveryPlugin() throws Exception {
        BundleContext ctx = EasyMock.createMock(BundleContext.class);
        stubCreateFilter(ctx);
        ctx.addServiceListener(EasyMock.isA(ServiceListener.class),
                EasyMock.eq("(objectClass=" + DiscoveryPlugin.class.getName() + ")"));

        ServiceReference<DiscoveryPlugin> sr1 = createAppendPlugin(ctx);
        ServiceReference<DiscoveryPlugin> sr2 = createPropertyPlugin(ctx);

        EasyMock.expect(ctx.getServiceReferences(DiscoveryPlugin.class.getName(), null))
                .andReturn(new ServiceReference[]{sr1, sr2}).anyTimes();
        EasyMock.replay(ctx);

        EndpointDescription endpoint = createEndpoint();

        Map<String, Object> expectedProps = new HashMap<String, Object>(endpoint.getProperties());
        expectedProps.put("endpoint.id", "http://google.de:80/test/sub/appended");
        expectedProps.put("foo", "bar");
        expectedProps.put("service.imported", "true");

        final ZooKeeper zk = EasyMock.createNiceMock(ZooKeeper.class);
        String expectedFullPath = "/osgi/service_registry/org/foo/myClass/some.machine#9876##test";
        
        EndpointDescription epd = new EndpointDescription(expectedProps);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new EndpointDescriptionParser().writeEndpoint(epd, bos);
        byte[] data = bos.toByteArray();
        expectCreated(zk, expectedFullPath, EasyMock.aryEq(data));
        EasyMock.replay(zk);

        PublishingEndpointListener eli = new PublishingEndpointListener(zk, ctx);

        List<EndpointDescription> endpoints = getEndpoints(eli);
        assertEquals("Precondition", 0, endpoints.size());
        eli.endpointAdded(endpoint, null);
        assertEquals(1, endpoints.size());

        //TODO enable
        //EasyMock.verify(zk);
    }



    public void testClose() throws KeeperException, InterruptedException {
        IMocksControl c = EasyMock.createNiceControl();
        BundleContext ctx = c.createMock(BundleContext.class);
        ZooKeeper zk = c.createMock(ZooKeeper.class);
        expectCreated(zk, ENDPOINT_PATH);
        expectDeleted(zk, ENDPOINT_PATH);

        c.replay();

        PublishingEndpointListener eli = new PublishingEndpointListener(zk, ctx);
        EndpointDescription endpoint = createEndpoint();
        eli.endpointAdded(endpoint, null);
        eli.close(); // should result in zk.delete(...)

        c.verify();
    }

    @SuppressWarnings("unchecked")
    private ServiceReference<DiscoveryPlugin> createAppendPlugin(BundleContext ctx) {
        DiscoveryPlugin plugin1 = new DiscoveryPlugin() {
            public String process(Map<String, Object> mutableProperties, String endpointKey) {
                String eid = (String) mutableProperties.get("endpoint.id");
                mutableProperties.put("endpoint.id", eid + "/appended");
                return endpointKey;
            }
        };
        ServiceReference<DiscoveryPlugin> sr1 = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(ctx.getService(sr1)).andReturn(plugin1).anyTimes();
        return sr1;
    }

    @SuppressWarnings("unchecked")
    private ServiceReference<DiscoveryPlugin> createPropertyPlugin(BundleContext ctx) {
        DiscoveryPlugin plugin2 = new DiscoveryPlugin() {
            public String process(Map<String, Object> mutableProperties, String endpointKey) {
                mutableProperties.put("foo", "bar");
                return endpointKey.replaceAll("localhost", "some.machine");
            }
        };
        ServiceReference<DiscoveryPlugin> sr2 = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(ctx.getService(sr2)).andReturn(plugin2).anyTimes();
        return sr2;
    }

    @SuppressWarnings("unchecked")
    private List<EndpointDescription> getEndpoints(PublishingEndpointListener eli) throws Exception {
        Field field = eli.getClass().getDeclaredField("endpoints");
        field.setAccessible(true);
        return (List<EndpointDescription>) field.get(eli);
    }

    private void stubCreateFilter(BundleContext ctx) throws InvalidSyntaxException {
        EasyMock.expect(ctx.createFilter(EasyMock.isA(String.class))).andAnswer(new IAnswer<Filter>() {
            public Filter answer() throws Throwable {
                return FrameworkUtil.createFilter((String) EasyMock.getCurrentArguments()[0]);
            }
        }).anyTimes();
    }

    private void expectCreated(ZooKeeper zk, String path, byte[] dataMatcher) throws KeeperException, InterruptedException {
        expect(zk.create(EasyMock.eq(path), 
                         dataMatcher, 
                         EasyMock.eq(Ids.OPEN_ACL_UNSAFE),
                         EasyMock.eq(CreateMode.EPHEMERAL)))
            .andReturn("");
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
