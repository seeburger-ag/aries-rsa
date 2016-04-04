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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.aries.rsa.discovery.endpoint.PropertiesMapper;
import org.apache.aries.rsa.discovery.zookeeper.util.Utils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.xmlns.rsa.v1_0.EndpointDescriptionType;
import org.osgi.xmlns.rsa.v1_0.PropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for local Endpoints and publishes them to ZooKeeper.
 */
public class PublishingEndpointListener implements EndpointListener {

    private static final Logger LOG = LoggerFactory.getLogger(PublishingEndpointListener.class);

    private final ZooKeeper zk;
    private final ServiceTracker discoveryPluginTracker;
    private final List<EndpointDescription> endpoints = new ArrayList<EndpointDescription>();
    private boolean closed;

    private final EndpointDescriptionParser endpointDescriptionParser;

    public PublishingEndpointListener(ZooKeeper zk, BundleContext bctx) {
        this.zk = zk;
        discoveryPluginTracker = new ServiceTracker(bctx, DiscoveryPlugin.class.getName(), null);
        discoveryPluginTracker.open();
        endpointDescriptionParser = new EndpointDescriptionParser();
    }

    public void endpointAdded(EndpointDescription endpoint, String matchedFilter) {
        synchronized (endpoints) {
            if (closed) {
                return;
            }
            if (endpoints.contains(endpoint)) {
                // TODO -> Should the published endpoint be updated here?
                return;
            }

            try {
                addEndpoint(endpoint);
                endpoints.add(endpoint);
            } catch (Exception ex) {
                LOG.error("Exception while processing the addition of an endpoint.", ex);
            }
        }
    }

    private void addEndpoint(EndpointDescription endpoint) throws URISyntaxException, KeeperException,
                                                                  InterruptedException, IOException {
        Collection<String> interfaces = endpoint.getInterfaces();
        String endpointKey = getKey(endpoint);
        Map<String, Object> props = new HashMap<String, Object>(endpoint.getProperties());

        // process plugins
        Object[] plugins = discoveryPluginTracker.getServices();
        if (plugins != null) {
            for (Object plugin : plugins) {
                if (plugin instanceof DiscoveryPlugin) {
                    endpointKey = ((DiscoveryPlugin)plugin).process(props, endpointKey);
                }
            }
        }
        LOG.info("Exporting endpoint to zookeeper: {}", endpoint);
        for (String name : interfaces) {
            String path = Utils.getZooKeeperPath(name);
            String fullPath = path + '/' + endpointKey;
            LOG.info("Creating ZooKeeper node for service with path {}", fullPath);
            createPath(path, zk);
            List<PropertyType> propsOut = new PropertiesMapper().fromProps(props);
            EndpointDescriptionType epd = new EndpointDescriptionType();
            epd.getProperty().addAll(propsOut);
            byte[] epData = endpointDescriptionParser.getData(epd);
            createEphemeralNode(fullPath, epData);
        }
    }

    private void createEphemeralNode(String fullPath, byte[] data) throws KeeperException, InterruptedException {
        try {
            zk.create(fullPath, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (NodeExistsException nee) {
            // this sometimes happens after a ZooKeeper node dies and the ephemeral node
            // that belonged to the old session was not yet deleted. We need to make our
            // session the owner of the node so it won't get deleted automatically -
            // we do this by deleting and recreating it ourselves.
            LOG.info("node for endpoint already exists, recreating: {}", fullPath);
            try {
                zk.delete(fullPath, -1);
            } catch (NoNodeException nne) {
                // it's a race condition, but as long as it got deleted - it's ok
            }
            zk.create(fullPath, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }
    }

    public void endpointRemoved(EndpointDescription endpoint, String matchedFilter) {
        LOG.info("Local EndpointDescription removed: {}", endpoint);

        synchronized (endpoints) {
            if (closed) {
                return;
            }
            if (!endpoints.contains(endpoint)) {
                return;
            }

            try {
                removeEndpoint(endpoint);
                endpoints.remove(endpoint);
            } catch (Exception ex) {
                LOG.error("Exception while processing the removal of an endpoint", ex);
            }
        }
    }

    private void removeEndpoint(EndpointDescription endpoint) throws UnknownHostException, URISyntaxException {
        Collection<String> interfaces = endpoint.getInterfaces();
        String endpointKey = getKey(endpoint);
        for (String name : interfaces) {
            String path = Utils.getZooKeeperPath(name);
            String fullPath = path + '/' + endpointKey;
            LOG.debug("Removing ZooKeeper node: {}", fullPath);
            try {
                zk.delete(fullPath, -1);
            } catch (Exception ex) {
                LOG.debug("Error while removing endpoint: {}", ex); // e.g. session expired
            }
        }
    }

    private static void createPath(String path, ZooKeeper zk) throws KeeperException, InterruptedException {
        StringBuilder current = new StringBuilder();
        List<String> parts = Utils.removeEmpty(Arrays.asList(path.split("/")));
        for (String part : parts) {
            current.append('/');
            current.append(part);
            try {
                zk.create(current.toString(), new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (NodeExistsException nee) {
                // it's not the first node with this path to ever exist - that's normal
            }
        }
    }

    private static String getKey(EndpointDescription endpoint) throws URISyntaxException {
        URI uri = new URI(endpoint.getId());
        return new StringBuilder().append(uri.getHost()).append("#").append(uri.getPort())
            .append("#").append(uri.getPath().replace('/', '#')).toString();
    }

    public void close() {
        LOG.debug("closing - removing all endpoints");
        synchronized (endpoints) {
            closed = true;
            for (EndpointDescription endpoint : endpoints) {
                try {
                    removeEndpoint(endpoint);
                } catch (Exception ex) {
                    LOG.error("Exception while removing endpoint during close", ex);
                }
            }
            endpoints.clear();
        }
        discoveryPluginTracker.close();
    }
}
