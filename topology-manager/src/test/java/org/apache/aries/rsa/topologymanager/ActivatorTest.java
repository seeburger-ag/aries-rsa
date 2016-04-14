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
package org.apache.aries.rsa.topologymanager;

import static org.easymock.EasyMock.createNiceControl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.newCapture;

import org.apache.aries.rsa.topologymanager.exporter.DefaultExportPolicy;
import org.apache.aries.rsa.topologymanager.exporter.TopologyManagerExport;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

public class ActivatorTest {

    @Test
    public void testDoStart() throws Exception {
        IMocksControl c = createNiceControl();
        BundleContext context = c.createMock(BundleContext.class);
        expect(context.getProperty("org.osgi.framework.uuid")).andReturn("myid");
        context.addServiceListener(isA(TopologyManagerExport.class));
        expectLastCall();
        final Capture<String> filter = newCapture();
        expect(context.createFilter(EasyMock.capture(filter)))
            .andAnswer(new IAnswer<Filter>() {
                public Filter answer() throws Throwable {
                    return FrameworkUtil.createFilter(filter.getValue());
                }
            }).times(2);
        ServiceReference sref = c.createMock(ServiceReference.class);
        Bundle bundle = c.createMock(Bundle.class);
        expect(sref.getBundle()).andReturn(bundle).anyTimes();
        expect(context.getServiceReferences((String)null, Activator.DOSGI_SERVICES))
            .andReturn(new ServiceReference[]{sref});

        c.replay();
        Activator activator = new Activator();
        activator.start(context);
        activator.doStart(context, new DefaultExportPolicy());
        c.verify();

        c.reset();
        c.replay();
        activator.doStop(context);
        c.verify();
    }

}
