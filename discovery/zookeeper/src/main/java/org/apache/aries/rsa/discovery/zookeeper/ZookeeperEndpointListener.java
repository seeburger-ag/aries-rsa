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

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class ZookeeperEndpointListener {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperEndpointListener.class);
    
    @Reference
    private ZooKeeper zk;
    
    @Reference
    private EndpointDescriptionParser parser;
    
    @Reference
    private InterestManager listener;
    
    @Reference
    private ZookeeperEndpointPublisher publisher;
    
    public ZookeeperEndpointListener() {
    }
    
    public ZookeeperEndpointListener(ZooKeeper zk, EndpointDescriptionParser parser, InterestManager listener) {
        this.zk = zk;
        this.parser = parser;
        this.listener = listener;
        activate();
    }
    
    @Activate
    public void activate() {
        watchRecursive(ZookeeperEndpointPublisher.PATH_PREFIX);
    }
    
    private void process(WatchedEvent event) {
        String path = event.getPath();
        LOG.info("Received event {}", event);
        switch (event.getType()) {
        case NodeCreated:
        case NodeDataChanged:
        case NodeChildrenChanged:
            watchRecursive(path);
            break;
        case NodeDeleted:
            listener.handleRemoved(path);
            break;
        default:
            break;
        }
    }

    private void watchRecursive(String path) {
        LOG.info("Watching {}", path);
        try {
            EndpointDescription endpoint = read(path);
            if (endpoint != null) {
                listener.handleChanged(path, endpoint);
            }
            List<String> children = zk.getChildren(path, this::process);
            if (children == null) {
                return;
            }
            for (String child : children) {
                String childPath = (path.endsWith("/") ? path : path + "/") + child;
                watchRecursive(childPath);
            }
        } catch (NoNodeException | SessionExpiredException | ConnectionLossException e) {
            // NoNodeException happens when a node was removed
            LOG.debug(e.getMessage(), e);
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
    }

    EndpointDescription read(String path) throws KeeperException, InterruptedException {
        Stat stat = new Stat();
        byte[] data = zk.getData(path, this::process, stat);
        if (data == null || data.length == 0) {
            return null;
        } else {
            return parser.readEndpoint(new ByteArrayInputStream(data));
        }
    }

}
