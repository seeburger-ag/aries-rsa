package org.apache.aries.rsa.itests.felix;
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


import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.aries.rsa.discovery.endpoint.PropertiesMapper;
import org.apache.aries.rsa.itests.tcp.api.EchoService;
import org.apache.aries.rsa.provider.tcp.TCPProvider;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.xmlns.rsa.v1_0.EndpointDescriptionType;

@RunWith(PaxExam.class)
public class TestDiscoveryExport {

    private final class DummyWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
        }
    }

    private static final String GREETER_ZOOKEEPER_NODE = "/osgi/service_registry/org/apache/aries/rsa/itests/tcp/api/EchoService";

    @Inject
    BundleContext bundleContext;

    @Inject
    ConfigurationAdmin configAdmin;
    
    @Inject
    DistributionProvider tcpProvider;

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                CoreOptions.junitBundles(),
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.configadmin").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa").artifactId("core").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa").artifactId("spi").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa").artifactId("topology-manager").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa.provider").artifactId("tcp").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa.discovery").artifactId("local").versionAsInProject(),
                mavenBundle().groupId("org.apache.zookeeper").artifactId("zookeeper").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa.discovery").artifactId("zookeeper").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa.discovery").artifactId("zookeeper-server").versionAsInProject(),
                mavenBundle().groupId("org.apache.aries.rsa.itests").artifactId("testbundle-tcp-service").versionAsInProject(),
//              
                //CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
        };
    }
    
    public void testInstalled() throws Exception {
        for (Bundle bundle : bundleContext.getBundles()) {
            System.out.println(bundle.getBundleId() + " " + bundle.getSymbolicName() + " " + bundle.getState() + " " + bundle.getVersion());
        }
    }

    @Test
    public void testDiscoveryExport() throws Exception {
        final int zkPort = 12051;
        //getFreePort(); does not seem to work 
        System.out.println("*** Port for ZooKeeper Server: " + zkPort);
        updateZkServerConfig(zkPort, configAdmin);
        Thread.sleep(1000); // To avoid exceptions in clients
        updateZkClientConfig(zkPort, configAdmin);
        ZooKeeper zk = new ZooKeeper("localhost:" + zkPort, 1000, new DummyWatcher());
        assertNodeExists(zk, GREETER_ZOOKEEPER_NODE, 10000);
        List<String> children = zk.getChildren(GREETER_ZOOKEEPER_NODE, false);
        EndpointDescriptionParser parser = new EndpointDescriptionParser();
        String path = children.get(0);
        byte[] data = zk.getData(GREETER_ZOOKEEPER_NODE + "/" + path, false, null);
        List<EndpointDescriptionType> epdList = parser.getEndpointDescriptions(new ByteArrayInputStream(data));
        Map<String, Object> props = new PropertiesMapper().toProps(epdList.get(0).getProperty());
        EndpointDescription epd = new EndpointDescription(props);
        EchoService service = (EchoService)tcpProvider.importEndpoint(EchoService.class.getClassLoader(), bundleContext, new Class[]{EchoService.class}, epd);
        String answer = service.echo("test");
        Assert.assertEquals("test", answer);
        zk.close();
    }

    private void assertNodeExists(ZooKeeper zk, String zNode, int timeout) {
        long endTime = System.currentTimeMillis() + timeout;
        Stat stat = null;
        while (stat == null && System.currentTimeMillis() < endTime) {
            try {
                stat = zk.exists(zNode, null);
                Thread.sleep(200);
            } catch (Exception e) {
                // Ignore
            }
        }
        Assert.assertNotNull("ZooKeeper node " + zNode + " was not found", stat);
    }

    protected void updateZkClientConfig(final int zkPort, ConfigurationAdmin cadmin) throws IOException {
        Dictionary<String, Object> cliProps = new Hashtable<String, Object>();
        cliProps.put("zookeeper.host", "127.0.0.1");
        cliProps.put("zookeeper.port", "" + zkPort);
        cadmin.getConfiguration("org.apache.aries.rsa.discovery.zookeeper", null).update(cliProps);
    }

    protected void updateZkServerConfig(final int zkPort, ConfigurationAdmin cadmin) throws IOException {
        Dictionary<String, Object> svrProps = new Hashtable<String, Object>();
        svrProps.put("clientPort", zkPort);
        cadmin.getConfiguration("org.apache.aries.rsa.discovery.zookeeper.server", null).update(svrProps);
    }
    
    protected int getFreePort() throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true); // enables quickly reopening socket on same port
            socket.bind(new InetSocketAddress(0)); // zero finds a free port
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }
}
