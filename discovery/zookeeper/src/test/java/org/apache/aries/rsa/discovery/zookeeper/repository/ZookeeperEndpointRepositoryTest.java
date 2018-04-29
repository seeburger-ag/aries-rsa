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
package org.apache.aries.rsa.discovery.zookeeper.repository;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertEquals;
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

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class ZookeeperEndpointRepositoryTest {
    
    private ZooKeeperServer server;
    private ZooKeeper zk;
    private ServerCnxnFactory factory;
    private List<EndpointEvent> events = new ArrayList<>();

    @Before
    public void before() throws IOException, InterruptedException, KeeperException {
        File target = new File("target");
        File zookeeperDir = new File(target, "zookeeper");
        server = new ZooKeeperServer(zookeeperDir, zookeeperDir, 2000);
        factory = new NIOServerCnxnFactory();
        int clientPort = getClientPort();
        factory.configure(new InetSocketAddress(clientPort), 10);
        factory.startup(server);
        Watcher watcher = new Watcher() {

            @Override
            public void process(WatchedEvent event) {
                System.out.println(event);
            }
            
        };
        zk = new ZooKeeper("localhost:" + server.getClientPort(), 1000, watcher);
        printNodes("/");
    }
    
    private int getClientPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    @After
    public void after() throws InterruptedException {
        //zk.close(); // Seems to cause SessionTimeout error 
        factory.shutdown();
    }

    @Test
    public void test() throws IOException, URISyntaxException, KeeperException, InterruptedException {
        final Semaphore sem = new Semaphore(0);
        EndpointEventListener listener = new EndpointEventListener() {
            
            @Override
            public void endpointChanged(EndpointEvent event, String filter) {
                events.add(event);
                sem.release();
            }
        };
        ZookeeperEndpointRepository repository = new ZookeeperEndpointRepository(zk, listener);
        
        EndpointDescription endpoint = createEndpoint();
        repository.add(endpoint);
        
        assertThat(sem.tryAcquire(1000, TimeUnit.SECONDS), equalTo(true));

        String path = "/osgi/service_registry/http:##test.de#service1";
        EndpointDescription ep2 = repository.read(path);
        assertNotNull(ep2);

        repository.remove(endpoint);

        assertThat(sem.tryAcquire(1000, TimeUnit.SECONDS), equalTo(true));
        assertThat(events.get(0), samePropertyValuesAs(new EndpointEvent(EndpointEvent.ADDED, endpoint)));
        assertThat(events.get(1), samePropertyValuesAs(new EndpointEvent(EndpointEvent.REMOVED, endpoint)));
        
        repository.close();
    }
    
    @Test
    public void testGetZooKeeperPath() {
        assertEquals(ZookeeperEndpointRepository.PATH_PREFIX + '/' + "http:##org.example.Test",
            ZookeeperEndpointRepository.getZooKeeperPath("http://org.example.Test"));

        assertEquals(ZookeeperEndpointRepository.PATH_PREFIX, ZookeeperEndpointRepository.getZooKeeperPath(""));
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] {Runnable.class.getName()});
        props.put(RemoteConstants.ENDPOINT_ID, "http://test.de/service1");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my");

        EndpointDescription endpoint = new EndpointDescription(props);
        return endpoint;
    }

    public void printNodes(String path) throws KeeperException, InterruptedException {
        List<String> children = zk.getChildren(path, false);
        for (String child : children) {
            String newPath = path.endsWith("/") ? path : path + "/";
            String fullPath = newPath + child;
            System.out.println(fullPath);
            printNodes(fullPath);
        }
    }
}
