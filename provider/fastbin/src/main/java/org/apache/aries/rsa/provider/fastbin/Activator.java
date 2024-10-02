/*
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
package org.apache.aries.rsa.provider.fastbin;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.fastbin.io.ClientInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.util.UuidGenerator;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Capability( //
        namespace = "osgi.remoteserviceadmin.distribution", //
        attribute = {"configs:List<String>=aries.fastbin"}, //
        version = "1.1.0"
)
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator extends BaseActivator {

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);
    static Activator INSTANCE;
    FastBinProvider fastBinProvider;
    ClientInvoker client;
    ServerInvoker server;

    @Override
    public void start(BundleContext context) throws Exception
    {
        INSTANCE = this;
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(RemoteConstants.REMOTE_INTENTS_SUPPORTED, new String[]{""});
        props.put(RemoteConstants.REMOTE_CONFIGS_SUPPORTED, new String[]{FastBinProvider.CONFIG_NAME});
        fastBinProvider = new FastBinProvider();
        fastBinProvider.activate(context, props);
        this.client = fastBinProvider.getClient();
        this.server = fastBinProvider.getServer();
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
        if(fastBinProvider!=null)
            fastBinProvider.deactivate();
    }

    public ClientInvoker getClient() {
        return client;
    }

    public ServerInvoker getServer() {
        return server;
    }

    public static Activator getInstance() {
        return INSTANCE;
    }

}
