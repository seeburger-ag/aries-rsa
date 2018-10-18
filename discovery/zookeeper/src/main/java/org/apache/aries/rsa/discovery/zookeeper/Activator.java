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

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.discovery.zookeeper.server.ZookeeperStarter;
import org.apache.zookeeper.server.ZooTrace;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;

public class Activator implements BundleActivator {

    private static final String PID_DISCOVERY_ZOOKEEPER = "org.apache.aries.rsa.discovery.zookeeper";
    private static final String PID_ZOOKEEPER_SERVER    = "org.apache.aries.rsa.discovery.zookeeper.server";
    private ZooKeeperDiscovery zkd;
    private ZookeeperStarter zkStarter;

    public synchronized void start(BundleContext bc) throws Exception {
        zkd = new ZooKeeperDiscovery(bc);
        bc.registerService(ManagedService.class, zkd, configProperties(PID_DISCOVERY_ZOOKEEPER));
        
        zkStarter = new ZookeeperStarter(bc);
        bc.registerService(ManagedService.class, zkStarter, configProperties(PID_ZOOKEEPER_SERVER));
    }

    public synchronized void stop(BundleContext bc) throws Exception {
    	// Load ZooTrace class early to avoid ClassNotFoundException on shutdown
    	ZooTrace.getTextTraceLevel();
    	
        zkd.stop(true);
        
        if (zkStarter != null) {
            zkStarter.shutdown();
        }
    }
    
    private Dictionary<String, String> configProperties(String pid) {
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put(Constants.SERVICE_PID, pid);
        return props;
    }
}
