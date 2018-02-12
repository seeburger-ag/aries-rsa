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

import java.io.IOException;
import java.net.Socket;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.discovery.zookeeper.publish.PublishingEndpointListener;
import org.apache.aries.rsa.discovery.zookeeper.repository.ZookeeperEndpointRepository;
import org.apache.aries.rsa.discovery.zookeeper.subscribe.EndpointListenerTracker;
import org.apache.aries.rsa.discovery.zookeeper.subscribe.InterestManager;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperDiscovery implements Watcher, ManagedService {

    public static final String DISCOVERY_ZOOKEEPER_ID = "org.apache.cxf.dosgi.discovery.zookeeper";

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperDiscovery.class);

    private final BundleContext bctx;

    private PublishingEndpointListener endpointListener;
    private ServiceTracker<?, ?> endpointListenerTracker;
    private InterestManager imManager;
    private ZooKeeper zkClient;
    private boolean closed;
    private boolean started;

    private Dictionary<String, ?> curConfiguration;

    private ZookeeperEndpointRepository repository;

    public ZooKeeperDiscovery(BundleContext bctx) {
        this.bctx = bctx;
    }

    public synchronized void updated(final Dictionary<String, ?> configuration) throws ConfigurationException {
        LOG.debug("Received configuration update for Zookeeper Discovery: {}", configuration);
        // make changes only if config actually changed, to prevent unnecessary ZooKeeper reconnections
        if (!toMap(configuration).equals(toMap(curConfiguration))) {
            stop(false);
            curConfiguration = configuration;
            // config is null if it doesn't exist, is being deleted or has not yet been loaded
            // in which case we just stop running
            if (closed || configuration == null) {
                return;
            }
            new Thread(new Runnable() {
                public void run() {
                    try {
                        createZookeeper(configuration);
                    } catch (IOException e) {
                        LOG.error("Error starting zookeeper client", e);
                    }
                }
            }).start();
        }
    }

    private synchronized void start() {
        if (closed) {
            return;
        }
        if (started) {
            // we must be re-entrant, i.e. can be called when already started
            LOG.debug("ZookeeperDiscovery already started");
            return;
        }
        LOG.debug("starting ZookeeperDiscovery");
        repository = new ZookeeperEndpointRepository(zkClient);
        endpointListener = new PublishingEndpointListener(repository);
        endpointListener.start(bctx);
        imManager = new InterestManager(repository);
        repository.addListener(imManager);
        endpointListenerTracker = new EndpointListenerTracker(bctx, imManager);
        endpointListenerTracker.open();
        started = true;
    }

    public synchronized void stop(boolean close) {
        if (started) {
            LOG.debug("stopping ZookeeperDiscovery");
        }
        started = false;
        closed |= close;
        if (endpointListener != null) {
            endpointListener.stop();
        }
        if (endpointListenerTracker != null) {
            endpointListenerTracker.close();
        }
        if (imManager != null) {
            imManager.close();
        }
        if (zkClient != null) {
            try {
                zkClient.close();
            } catch (InterruptedException e) {
                LOG.error("Error closing ZooKeeper", e);
            }
        }
    }

    protected ZooKeeper createZooKeeper(String host, String port, int timeout) throws IOException {
        LOG.info("ZooKeeper discovery connecting to {}:{} with timeout {}",
                new Object[]{host, port, timeout});
        return new ZooKeeper(host + ":" + port, timeout, this);
    }

    /* Callback for ZooKeeper */
    public void process(WatchedEvent event) {
        LOG.debug("got ZooKeeper event " + event);
        switch (event.getState()) {
        case SyncConnected:
            LOG.info("Connection to ZooKeeper established");
            // this event can be triggered more than once in a row (e.g. after Disconnected event),
            // so we must be re-entrant here
            start();
            break;

        case Expired:
            LOG.info("Connection to ZooKeeper expired. Trying to create a new connection");
            stop(false);
            try {
                createZookeeper(curConfiguration);
            } catch (IOException e) {
                LOG.error("Error starting zookeeper client", e);
            }
            break;

        default:
            // ignore other events
            break;
        }
    }

    private void createZookeeper(Dictionary<String, ?> config) throws IOException {
        String host = (String)getWithDefault(config, "zookeeper.host", "localhost");
        String port = (String)getWithDefault(config, "zookeeper.port", "2181");
        int timeout = Integer.parseInt((String)getWithDefault(config, "zookeeper.timeout", "3000"));
        waitPort(host, Integer.parseInt(port));
        zkClient = createZooKeeper(host, port, timeout);
    }

    private void waitPort(String host, int port) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2000) {
            try (Socket socket = new Socket(host, port)) {
                return;
            } catch (IOException e) {
                safeSleep();
            }
        }
    }

    private void safeSleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e1) {
        }
    }
    
    public Object getWithDefault(Dictionary<String, ?> config, String key, Object defaultValue) {
        Object value = config.get(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Converts the given Dictionary to a Map.
     *
     * @param dict a dictionary
     * @param <K> the key type
     * @param <V> the value type
     * @return the converted map, or an empty map if the given dictionary is null
     */
    public static <K, V> Map<K, V> toMap(Dictionary<K, V> dict) {
        Map<K, V> map = new HashMap<K, V>();
        if (dict != null) {
            Enumeration<K> keys = dict.keys();
            while (keys.hasMoreElements()) {
                K key = keys.nextElement();
                map.put(key, dict.get(key));
            }
        }
        return map;
    }
}
