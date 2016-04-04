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
package org.apache.aries.rsa.discovery.zookeeper.server;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;

import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperStarter implements org.osgi.service.cm.ManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperStarter.class);

    protected ZookeeperServer main;
    private final BundleContext bundleContext;
    private Thread zkMainThread;
    private Map<String, ?> curConfiguration;

    public ZookeeperStarter(BundleContext ctx) {
        bundleContext = ctx;
    }

    public synchronized void shutdown() {
        if (main != null) {
            LOG.info("Shutting down ZooKeeper server");
            try {
                main.shutdown();
                if (zkMainThread != null) {
                    zkMainThread.join();
                }
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
            main = null;
            zkMainThread = null;
        }
    }

    private void setDefaults(Dictionary<String, String> dict) throws IOException {
        Utils.removeEmptyValues(dict); // to avoid NumberFormatExceptions
        Utils.setDefault(dict, "tickTime", "2000");
        Utils.setDefault(dict, "initLimit", "10");
        Utils.setDefault(dict, "syncLimit", "5");
        Utils.setDefault(dict, "clientPort", "2181");
        Utils.setDefault(dict, "dataDir", new File(bundleContext.getDataFile(""), "zkdata").getCanonicalPath());
    }

    @SuppressWarnings("unchecked")
    public synchronized void updated(Dictionary dict) throws ConfigurationException {
        LOG.debug("Received configuration update for Zookeeper Server: " + dict);
        try {
            if (dict != null) {
                setDefaults((Dictionary<String, String>)dict);
            }
            Map<String, ?> configMap = Utils.toMap(dict);
            if (!configMap.equals(curConfiguration)) { // only if something actually changed
                shutdown();
                curConfiguration = configMap;
                // config is null if it doesn't exist, is being deleted or has not yet been loaded
                // in which case we just stop running
                if (dict != null) {
                    startFromConfig(parseConfig(dict));
                    LOG.info("Applied configuration update: " + dict);
                }
            }
        } catch (Exception th) {
            LOG.error("Problem applying configuration update: " + dict, th);
        }
    }

    private QuorumPeerConfig parseConfig(Dictionary<String, ?> dict) throws IOException, ConfigException {
        QuorumPeerConfig config = new QuorumPeerConfig();
        config.parseProperties(Utils.toProperties(dict));
        return config;
    }

    protected void startFromConfig(final QuorumPeerConfig config) {
        int numServers = config.getServers().size();
        main = numServers > 1 ? new MyQuorumPeerMain(config) : new MyZooKeeperServerMain(config);
        zkMainThread = new Thread(new Runnable() {
            public void run() {
                try {
                    main.startup();
                } catch (Throwable e) {
                    LOG.error("Problem running ZooKeeper server.", e);
                }
            }
        });
        zkMainThread.start();
    }

    interface ZookeeperServer {
        void startup() throws IOException;
        void shutdown();
    }

    static class MyQuorumPeerMain extends QuorumPeerMain implements ZookeeperServer {

        private QuorumPeerConfig config;

        MyQuorumPeerMain(QuorumPeerConfig config) {
            this.config = config;
        }

        public void startup() throws IOException {
            runFromConfig(config);
        }

        public void shutdown() {
            if (null != quorumPeer) {
                quorumPeer.shutdown();
            }
        }
    }

    static class MyZooKeeperServerMain extends ZooKeeperServerMain implements ZookeeperServer {

        private QuorumPeerConfig config;

        MyZooKeeperServerMain(QuorumPeerConfig config) {
            this.config = config;
        }

        public void startup() throws IOException {
            ServerConfig serverConfig = new ServerConfig();
            serverConfig.readFrom(config);
            runFromConfig(serverConfig);
        }

        public void shutdown() {
            try {
                super.shutdown();
            } catch (Exception e) {
                LOG.error("Error shutting down ZooKeeper", e);
            }
        }
    }
}
