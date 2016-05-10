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
package org.apache.aries.rsa.core;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import org.apache.aries.rsa.spi.Endpoint;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

@SuppressWarnings({"rawtypes", "unchecked"})
public class EventProducerTest {
    
    
    @Test
    public void testPublishNotification() throws Exception {
        RemoteServiceAdminCore rsaCore = EasyMock.createNiceMock(RemoteServiceAdminCore.class);
        EasyMock.replay(rsaCore);

        final EndpointDescription epd = EasyMock.createNiceMock(EndpointDescription.class);
        EasyMock.expect(epd.getServiceId()).andReturn(Long.MAX_VALUE).anyTimes();
        final String uuid = UUID.randomUUID().toString();
        EasyMock.expect(epd.getFrameworkUUID()).andReturn(uuid).anyTimes();
        EasyMock.expect(epd.getId()).andReturn("foo://bar").anyTimes();
        final List<String> interfaces = Arrays.asList("org.foo.Bar", "org.boo.Far");
        EasyMock.expect(epd.getInterfaces()).andReturn(interfaces).anyTimes();
        EasyMock.expect(epd.getConfigurationTypes()).andReturn(Arrays.asList("org.apache.cxf.ws")).anyTimes();
        EasyMock.replay(epd);
        final ServiceReference sref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(sref);

        final Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        EasyMock.expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put("Bundle-Version", "1.2.3.test");
        EasyMock.expect(bundle.getHeaders()).andReturn(headers).anyTimes();
        EasyMock.replay(bundle);

        RemoteServiceAdminListener rsal = EasyMock.createNiceMock(RemoteServiceAdminListener.class);
        rsal.remoteAdminEvent((RemoteServiceAdminEvent) EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                RemoteServiceAdminEvent rsae = (RemoteServiceAdminEvent) EasyMock.getCurrentArguments()[0];
                Assert.assertNull(rsae.getException());
                Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_REGISTRATION, rsae.getType());
                Assert.assertSame(bundle, rsae.getSource());
                ExportReference er = rsae.getExportReference();
                Assert.assertSame(epd, er.getExportedEndpoint());
                Assert.assertSame(sref, er.getExportedService());

                return null;
            }
        });
        EasyMock.replay(rsal);

        ServiceReference rsalSref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.expect(rsalSref.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.replay(rsalSref);

        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
        EasyMock.expect(bc.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.expect(bc.getServiceReferences(RemoteServiceAdminListener.class.getName(), null))
                .andReturn(new ServiceReference[] {rsalSref}).anyTimes();
        EasyMock.expect(bc.getService(rsalSref)).andReturn(rsal).anyTimes();
        Endpoint endpoint = EasyMock.mock(Endpoint.class);
        EasyMock.expect(endpoint.description()).andReturn(epd);
        EasyMock.replay(endpoint);
        EasyMock.replay(bc);
        EventProducer eventProducer = new EventProducer(bc);

        ExportRegistrationImpl ereg = new ExportRegistrationImpl(sref, endpoint, rsaCore);
        eventProducer.publishNotification(ereg);

        EasyMock.verify(rsaCore, sref, bundle, rsal, rsalSref, bc);
    }

    @Test
    public void testPublishErrorNotification() throws Exception {
        RemoteServiceAdminCore rsaCore = EasyMock.createNiceMock(RemoteServiceAdminCore.class);
        EasyMock.replay(rsaCore);

        final EndpointDescription endpoint = EasyMock.createNiceMock(EndpointDescription.class);
        EasyMock.expect(endpoint.getInterfaces()).andReturn(Arrays.asList("org.foo.Bar")).anyTimes();
        EasyMock.replay(endpoint);
        final ServiceReference sref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(sref);

        final Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        EasyMock.expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        EasyMock.expect(bundle.getHeaders()).andReturn(new Hashtable<String, String>()).anyTimes();
        EasyMock.replay(bundle);

        final Exception exportException = new Exception();

        RemoteServiceAdminListener rsal = EasyMock.createNiceMock(RemoteServiceAdminListener.class);
        rsal.remoteAdminEvent((RemoteServiceAdminEvent) EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                RemoteServiceAdminEvent rsae = (RemoteServiceAdminEvent) EasyMock.getCurrentArguments()[0];
                Assert.assertSame(exportException, rsae.getException());
                Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_ERROR, rsae.getType());
                Assert.assertSame(bundle, rsae.getSource());
                Assert.assertNull(rsae.getImportReference());
                Assert.assertNull(rsae.getExportReference());

                return null;
            }
        });
        EasyMock.replay(rsal);

        ServiceReference rsalSref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.expect(rsalSref.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.replay(rsalSref);

        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);

        EasyMock.expect(bc.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.expect(bc.getServiceReferences(RemoteServiceAdminListener.class.getName(), null))
                .andReturn(new ServiceReference[] {rsalSref}).anyTimes();
        EasyMock.expect(bc.getService(rsalSref)).andReturn(rsal).anyTimes();
        EasyMock.replay(bc);
        EventProducer eventProducer = new EventProducer(bc);

        ExportRegistrationImpl ereg = new ExportRegistrationImpl(rsaCore, exportException);
        eventProducer.publishNotification(Arrays.<ExportRegistration>asList(ereg));

        EasyMock.verify(rsaCore, sref, bundle, rsal, rsalSref, bc);
    }
}
