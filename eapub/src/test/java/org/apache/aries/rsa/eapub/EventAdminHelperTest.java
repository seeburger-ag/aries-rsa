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
package org.apache.aries.rsa.eapub;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;

@SuppressWarnings({"rawtypes", "unchecked"})
public class EventAdminHelperTest {
    
    @Test
    public void testPublishNotification() throws Exception {
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
        final ExportReference er = EasyMock.createNiceMock(ExportReference.class);
        EasyMock.expect(er.getExportedEndpoint()).andReturn(epd).anyTimes();
        EasyMock.expect(er.getExportedService()).andReturn(sref).anyTimes();
        EasyMock.replay(er);

        final Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        EasyMock.expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put("Bundle-Version", "1.2.3.test");
        EasyMock.expect(bundle.getHeaders()).andReturn(headers).anyTimes();
        EasyMock.replay(bundle);

        EventAdmin ea = EasyMock.createNiceMock(EventAdmin.class);
        ea.postEvent((Event) EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                Event event = (Event) EasyMock.getCurrentArguments()[0];

                Assert.assertEquals("org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION", event.getTopic());
                Assert.assertSame(bundle, event.getProperty("bundle"));
                Assert.assertEquals(42L, event.getProperty("bundle.id"));
                Assert.assertEquals("test.bundle", event.getProperty("bundle.symbolicname"));
                Assert.assertEquals(new Version(1, 2, 3, "test"), event.getProperty("bundle.version"));
                Assert.assertNull(event.getProperty("cause"));
                Assert.assertEquals(epd, event.getProperty("export.registration"));

                Assert.assertEquals(Long.MAX_VALUE, event.getProperty("service.remote.id"));
                Assert.assertEquals(uuid, event.getProperty("service.remote.uuid"));
                Assert.assertEquals("foo://bar", event.getProperty("service.remote.uri"));
                Assert.assertTrue(Arrays.equals(interfaces.toArray(new String[] {}),
                                                (String[]) event.getProperty("objectClass")));

                Assert.assertNotNull(event.getProperty("timestamp"));

                RemoteServiceAdminEvent rsae = (RemoteServiceAdminEvent) event.getProperty("event");
                Assert.assertNull(rsae.getException());
                Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_REGISTRATION, rsae.getType());
                Assert.assertSame(bundle, rsae.getSource());
                ExportReference er = rsae.getExportReference();
                Assert.assertSame(epd, er.getExportedEndpoint());
                Assert.assertSame(sref, er.getExportedService());

                return null;
            }
        });
        EasyMock.replay(ea);

        ServiceReference eaSref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(eaSref);

        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
        EasyMock.expect(bc.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.expect(bc.getAllServiceReferences(EventAdmin.class.getName(), null))
            .andReturn(new ServiceReference[] {eaSref}).anyTimes();
        EasyMock.expect(bc.getService(eaSref)).andReturn(ea).anyTimes();
        EasyMock.replay(bc);

        RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(
                RemoteServiceAdminEvent.EXPORT_REGISTRATION,
                bundle,
                er,
                null
        );
        new EventAdminHelper(bc).remoteAdminEvent(event);
        EasyMock.verify(epd, sref, er, ea, eaSref, bc);
    }

    @Test
    public void testPublishErrorNotification() throws Exception {
        final EndpointDescription epd = EasyMock.createNiceMock(EndpointDescription.class);
        EasyMock.expect(epd.getInterfaces()).andReturn(Arrays.asList("org.foo.Bar")).anyTimes();
        EasyMock.replay(epd);
        final ServiceReference sref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(sref);
        final ExportReference er = EasyMock.createNiceMock(ExportReference.class);
        EasyMock.expect(er.getExportedEndpoint()).andReturn(epd).anyTimes();
        EasyMock.expect(er.getExportedService()).andReturn(sref).anyTimes();
        EasyMock.replay(er);

        final Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        EasyMock.expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        EasyMock.expect(bundle.getHeaders()).andReturn(new Hashtable<String, String>()).anyTimes();
        EasyMock.replay(bundle);

        final Exception exportException = new Exception();

        EventAdmin ea = EasyMock.createNiceMock(EventAdmin.class);
        ea.postEvent((Event) EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                Event event = (Event) EasyMock.getCurrentArguments()[0];

                Assert.assertEquals("org/osgi/service/remoteserviceadmin/EXPORT_ERROR", event.getTopic());
                Assert.assertSame(bundle, event.getProperty("bundle"));
                Assert.assertEquals(42L, event.getProperty("bundle.id"));
                Assert.assertEquals("test.bundle", event.getProperty("bundle.symbolicname"));
                Assert.assertEquals(new Version("0"), event.getProperty("bundle.version"));
                Assert.assertSame(exportException, event.getProperty("cause"));
                Assert.assertEquals(epd, event.getProperty("export.registration"));
                Assert.assertTrue(Arrays.equals(new String[] {"org.foo.Bar"},
                                                (String[]) event.getProperty("objectClass")));

                RemoteServiceAdminEvent rsae = (RemoteServiceAdminEvent) event.getProperty("event");
                Assert.assertSame(exportException, rsae.getException());
                Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_ERROR, rsae.getType());
                Assert.assertSame(bundle, rsae.getSource());
                ExportReference er = rsae.getExportReference();
                Assert.assertSame(epd, er.getExportedEndpoint());
                Assert.assertSame(sref, er.getExportedService());

                return null;
            }
        });
        EasyMock.replay(ea);

        ServiceReference eaSref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(eaSref);

        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);

        EasyMock.expect(bc.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.expect(bc.getAllServiceReferences(EventAdmin.class.getName(), null))
            .andReturn(new ServiceReference[] {eaSref}).anyTimes();
        EasyMock.expect(bc.getService(eaSref)).andReturn(ea).anyTimes();
        EasyMock.replay(bc);


        RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(
                RemoteServiceAdminEvent.EXPORT_ERROR,
                bundle,
                er,
                exportException
        );
        new EventAdminHelper(bc).remoteAdminEvent(event);
        EasyMock.verify(epd, sref, er, ea, eaSref, bc);
    }
}
