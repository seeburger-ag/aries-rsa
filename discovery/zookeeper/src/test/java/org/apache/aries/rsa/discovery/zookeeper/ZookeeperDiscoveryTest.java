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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParserImpl;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

@RunWith(MockitoJUnitRunner.class)
public class ZookeeperDiscoveryTest {
    final Semaphore semConnected = new Semaphore(0);
    final Semaphore sem = new Semaphore(0);
    private ZooKeeperServer server;
    private ZooKeeper zk;
    private ServerCnxnFactory factory;
    private List<EndpointEvent> events = new ArrayList<>();
    @Mock
    private ServiceReference<EndpointEventListener> sref;

    @Before
    public void before() throws IOException, InterruptedException, KeeperException {
        startZookeeperServer();
        zk = new ZooKeeper("localhost:" + server.getClientPort(), 1000, this::process);
        printNodes("/");
    }
    
    @After
    public void after() throws InterruptedException {
        //zk.close(); // Seems to cause SessionTimeout error 
        factory.shutdown();
    }

    @Test
    public void test() throws IOException, URISyntaxException, KeeperException, InterruptedException {
        EndpointDescriptionParserImpl parser = new EndpointDescriptionParserImpl();
        ZookeeperEndpointPublisher repository = new ZookeeperEndpointPublisher(zk, parser);
        repository.activate();
        InterestManager im = new InterestManager();
        
        String scope = "("+ Constants.OBJECTCLASS +"=*)";
        Mockito.when(sref.getProperty(Mockito.eq(EndpointEventListener.ENDPOINT_LISTENER_SCOPE))).thenReturn(scope);
        im.bindEndpointEventListener(sref, this::onEndpointChanged);
        ZookeeperEndpointListener zklistener = new ZookeeperEndpointListener(zk, parser, im);
        
        assertThat(semConnected.tryAcquire(1, SECONDS), equalTo(true));
        
        EndpointDescription endpoint = createEndpoint();
        repository.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint));
        
        assertThat(sem.tryAcquire(100, SECONDS), equalTo(true));
    
        String path = "/osgi/service_registry/http:##test.de#service1";
        EndpointDescription ep2 = zklistener.read(path);
        assertNotNull(ep2);

        repository.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
    
        assertThat(sem.tryAcquire(1000, TimeUnit.SECONDS), equalTo(true));
        assertThat(events.get(0).getType(), equalTo(EndpointEvent.ADDED));
        assertThat(events.get(1).getType(), equalTo(EndpointEvent.REMOVED));
        assertThat(events.get(0).getEndpoint(), equalTo(endpoint));
        assertThat(events.get(1).getEndpoint(), equalTo(endpoint));
    }

    private void process(WatchedEvent event) {
        if (event.getState() == KeeperState.SyncConnected) {
            semConnected.release();
        }
        System.out.println(event);
    }

    private void startZookeeperServer() throws IOException, InterruptedException {
        File target = new File("target");
        File zookeeperDir = new File(target, "zookeeper");
        server = new ZooKeeperServer(zookeeperDir, zookeeperDir, 2000);
        factory = new NIOServerCnxnFactory();
        int clientPort = getClientPort();
        factory.configure(new InetSocketAddress(clientPort), 10);
        factory.startup(server);
    }
    
    private int getClientPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private void onEndpointChanged(EndpointEvent event, String filter) {
        events.add(event);
        sem.release();
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] {Runnable.class.getName()});
        props.put(RemoteConstants.ENDPOINT_ID, "http://test.de/service1");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my");

        EndpointDescription endpoint = new EndpointDescription(props);
        return endpoint;
    }

    private void printNodes(String path) throws KeeperException, InterruptedException {
        List<String> children = zk.getChildren(path, false);
        for (String child : children) {
            String newPath = path.endsWith("/") ? path : path + "/";
            String fullPath = newPath + child;
            System.out.println(fullPath);
            printNodes(fullPath);
        }
    }
}
