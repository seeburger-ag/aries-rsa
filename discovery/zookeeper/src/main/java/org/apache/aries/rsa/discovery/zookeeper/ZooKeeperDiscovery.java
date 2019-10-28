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

import org.apache.aries.rsa.discovery.zookeeper.publish.PublishingEndpointListener;
import org.apache.aries.rsa.discovery.zookeeper.repository.ZookeeperEndpointRepository;
import org.apache.aries.rsa.discovery.zookeeper.subscribe.EndpointListenerTracker;
import org.apache.aries.rsa.discovery.zookeeper.subscribe.InterestManager;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ZooKeeperDiscovery {
    public static final String DISCOVERY_ZOOKEEPER_ID = "org.apache.cxf.dosgi.discovery.zookeeper";

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperDiscovery.class);

    @Reference
    private ZooKeeper zkClient;
    
    private PublishingEndpointListener endpointListener;
    private ServiceTracker<?, ?> endpointListenerTracker;
    private InterestManager imManager;
    private ZookeeperEndpointRepository repository;

    @Activate
    public void activate(BundleContext context) {
        LOG.debug("Starting ZookeeperDiscovery");
        repository = new ZookeeperEndpointRepository(zkClient);
        endpointListener = new PublishingEndpointListener(repository);
        endpointListener.start(context);
        imManager = new InterestManager(repository);
        repository.addListener(imManager);
        endpointListenerTracker = new EndpointListenerTracker(context, imManager);
        endpointListenerTracker.open();
    }

    @Deactivate
    public void deactivate() {
        LOG.debug("Stopping ZookeeperDiscovery");
        endpointListener.stop();
        endpointListenerTracker.close();
        imManager.close();
        repository.close();
    }

}
