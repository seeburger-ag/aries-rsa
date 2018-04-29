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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParser;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperEndpointRepository implements Closeable, Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperEndpointRepository.class);
    private final ZooKeeper zk;
    private final EndpointDescriptionParser parser;
    private EndpointEventListener listener;
    public static final String PATH_PREFIX = "/osgi/service_registry";
    
    private Map<String, EndpointDescription> nodes = new ConcurrentHashMap<String, EndpointDescription>();
    
    public ZookeeperEndpointRepository(ZooKeeper zk) {
        this(zk, null);
    }
    
    public ZookeeperEndpointRepository(ZooKeeper zk, EndpointEventListener listener) {
        this.zk = zk;
        this.listener = listener;
        this.parser = new EndpointDescriptionParser();
        try {
            createPath(PATH_PREFIX);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create base path");
        }
        this.registerWatcher();
    }

    public void addListener(EndpointEventListener listener) {
        this.listener = listener;
    }

    /**
     * Read current endpoint stored at a znode
     * 
     * @param path
     * @return
     */
    public EndpointDescription read(String path) {
        return nodes.get(path);
    }

    public void add(EndpointDescription endpoint) throws KeeperException, InterruptedException  {
        String path = getZooKeeperPath(endpoint.getId());
        LOG.info("Exporting endpoint to zookeeper. Endpoint: {}, Path: {}", endpoint, path);
        createBasePath();
        createEphemeralNode(path, getData(endpoint));
    }

    public void modify(EndpointDescription endpoint) throws KeeperException, InterruptedException {
        String path = getZooKeeperPath(endpoint.getId());
        LOG.info("Changing endpoint in zookeeper. Endpoint: {}, Path: {}", endpoint, path);
        createBasePath();
        zk.setData(path, getData(endpoint), -1);
    }
    
    public void remove(EndpointDescription endpoint) throws InterruptedException, KeeperException {
        String path = getZooKeeperPath(endpoint.getId());
        LOG.info("Removing endpoint in zookeeper. Endpoint: {}, Path: {}", endpoint, path);
        zk.delete(path, -1);
    }
    
    public Collection<EndpointDescription> getAll() {
        return nodes.values();
    }

    /**
     * Removes nulls and empty strings from the given string array.
     *
     * @param strings an array of strings
     * @return a new array containing the non-null and non-empty
     *         elements of the original array in the same order
     */
    public static List<String> removeEmpty(List<String> strings) {
        List<String> result = new ArrayList<String>();
        if (strings == null) {
            return result;
        }
        for (String s : strings) {
            if (s != null && !s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    public static String getZooKeeperPath(String name) {
        String escaped = name.replace('/', '#');
        return name == null || name.isEmpty() ? PATH_PREFIX : PATH_PREFIX + '/' + escaped;
    }

    @Override
    public void process(WatchedEvent event) {
        LOG.info("Received event {}", event);
        if (event.getType() == EventType.NodeDeleted) {
            handleRemoved(event.getPath());
            return;
        }
        watchRecursive(event.getPath());
    }

    @Override
    public void close() throws IOException {
        nodes.clear();
    }

    private void createBasePath() throws KeeperException, InterruptedException {
        String path = ZookeeperEndpointRepository.getZooKeeperPath(PATH_PREFIX);
        createPath(path);
    }

    private void registerWatcher() {
        try {
            watchRecursive(ZookeeperEndpointRepository.PATH_PREFIX);
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
    }

    /**
     * TODO Check if we handle connection losses correctly
     * @param path
     */
    private void watchRecursive(String path) {
        LOG.debug("Watching {}", path);
        try {
            handleZNodeChanged(path);
            List<String> children = zk.getChildren(path, this);
            if (children == null) {
                return;
            }
            for (String child : children) {
                String childPath = (path.endsWith("/") ? path : path + "/") + child;
                watchRecursive(childPath);
            }
        } catch (NoNodeException e) {
            // Happens when a node was removed
            LOG.debug(e.getMessage(), e);
        } catch (ConnectionLossException e) {
            LOG.debug(e.getMessage(), e);
        } catch (SessionExpiredException e) {
            LOG.debug(e.getMessage(), e);
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
    }

    private byte[] getData(EndpointDescription epd) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        parser.writeEndpoint(epd, bos);
        return bos.toByteArray();
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
    
    private void createPath(String path) throws KeeperException, InterruptedException {
        StringBuilder current = new StringBuilder();
        List<String> parts = ZookeeperEndpointRepository.removeEmpty(Arrays.asList(path.split("/")));
        for (String part : parts) {
            current.append('/');
            current.append(part);
            try {
                if (zk.exists(current.toString(), false) == null) {
                    zk.create(current.toString(), new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
            } catch (NodeExistsException nee) {
                // it's not the first node with this path to ever exist - that's normal
            }
        }
    }

    private void handleZNodeChanged(String path) throws KeeperException, InterruptedException {
        Stat stat = new Stat();
        byte[] data = zk.getData(path, false, stat);
        if (data == null || data.length == 0) {
            return;
        }
        EndpointDescription endpoint = parser.readEndpoint(new ByteArrayInputStream(data));
        if (endpoint != null) {
            handleChanged(path, endpoint);
        }
    }

    private void handleRemoved(String path) {
        EndpointDescription endpoint = nodes.remove(path);
        EndpointEvent event = new EndpointEvent(EndpointEvent.REMOVED, endpoint);
        if (listener != null) {
            listener.endpointChanged(event, null);
        }
    }

    private void handleChanged(String path, EndpointDescription endpoint) {
        EndpointDescription old = nodes.put(path, endpoint);
        int type = old == null ? EndpointEvent.ADDED : EndpointEvent.MODIFIED;
        EndpointEvent event = new EndpointEvent(type, endpoint);
        if (listener != null) {
            listener.endpointChanged(event, null);
        }
    }

}
