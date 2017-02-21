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
package org.apache.aries.rsa.topologymanager.exporter;

import static org.easymock.EasyMock.expectLastCall;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.aries.rsa.spi.ExportPolicy;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TopologyManagerExportTest {

    /**
     * This tests if the topology manager handles a service marked to be exported correctly by exporting it to
     * an available RemoteServiceAdmin and notifying an EndpointListener afterwards.
     *
     * @throws Exception
     */
    @Test
    public void testServiceExportUnexport() throws Exception {
        IMocksControl c = EasyMock.createControl();
        RemoteServiceAdmin rsa = c.createMock(RemoteServiceAdmin.class);
        final EndpointListener notifier = c.createMock(EndpointListener.class);
        final ServiceReference sref = createUserService(c);
        EndpointDescription epd = createEndpoint();
        expectServiceExported(c, rsa, notifier, sref, epd);

        c.replay();
        EndpointRepository endpointRepo = new EndpointRepository();
        endpointRepo.setNotifier(notifier);
        Executor executor = syncExecutor();
        ExportPolicy policy = new DefaultExportPolicy();
        TopologyManagerExport exportManager = new TopologyManagerExport(endpointRepo, executor, policy);
        exportManager.add(rsa);
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        c.verify();

        c.reset();
        notifier.endpointRemoved(epd, null);
        expectLastCall().once();
        c.replay();
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, sref));
        c.verify();

        c.reset();
        c.replay();
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED, sref));
        c.verify();

        c.reset();
        c.replay();
        exportManager.remove(rsa);
        c.verify();
    }

    @Test
    public void testExportExisting() throws Exception {
        IMocksControl c = EasyMock.createControl();
        RemoteServiceAdmin rsa = c.createMock(RemoteServiceAdmin.class);
        final EndpointListenerNotifier mockEpListenerNotifier = c.createMock(EndpointListenerNotifier.class);
        final ServiceReference sref = createUserService(c);
        expectServiceExported(c, rsa, mockEpListenerNotifier, sref, createEndpoint());
        c.replay();

        EndpointRepository endpointRepo = new EndpointRepository();
        endpointRepo.setNotifier(mockEpListenerNotifier);
        ExportPolicy policy = new DefaultExportPolicy();
        TopologyManagerExport exportManager = new TopologyManagerExport(endpointRepo, syncExecutor(), policy);
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        exportManager.add(rsa);
        c.verify();
    }

    @Test
    public void testExportExistingMultipleInterfaces() throws Exception {
        IMocksControl c = EasyMock.createControl();
        RemoteServiceAdmin rsa = c.createMock(RemoteServiceAdmin.class);
        final EndpointListenerNotifier mockEpListenerNotifier = c.createMock(EndpointListenerNotifier.class);
        List<String> exportedInterfaces = Arrays.asList("a.b.C","foo.Bar");
        final ServiceReference sref = createUserService(c, exportedInterfaces);
        expectServiceExported(c, rsa, mockEpListenerNotifier, sref, createEndpoint());
        c.replay();

        EndpointRepository endpointRepo = new EndpointRepository();
        endpointRepo.setNotifier(mockEpListenerNotifier);
        ExportPolicy policy = new DefaultExportPolicy();
        TopologyManagerExport exportManager = new TopologyManagerExport(endpointRepo, syncExecutor(), policy);
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        exportManager.add(rsa);
        c.verify();
    }

    @Test
    public void testExportExistingNoExportedInterfaces() throws Exception {
        IMocksControl c = EasyMock.createControl();
        RemoteServiceAdmin rsa = c.createMock(RemoteServiceAdmin.class);
        final EndpointListenerNotifier mockEpListenerNotifier = c.createMock(EndpointListenerNotifier.class);
        String exportedInterfaces = "";
        final ServiceReference sref = createUserService(c, exportedInterfaces);
        c.replay();

        EndpointRepository endpointRepo = new EndpointRepository();
        endpointRepo.setNotifier(mockEpListenerNotifier);
        ExportPolicy policy = new DefaultExportPolicy();
        TopologyManagerExport exportManager = new TopologyManagerExport(endpointRepo, syncExecutor(), policy);
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        exportManager.add(rsa);
        c.verify();
    }

    private void expectServiceExported(IMocksControl c, RemoteServiceAdmin rsa,
                                       final EndpointListener listener,
                                       final ServiceReference sref, EndpointDescription epd) {
        ExportRegistration exportRegistration = createExportRegistration(c, epd);
        EasyMock.expect(rsa.exportService(EasyMock.same(sref), (Map<String, Object>)EasyMock.anyObject()))
            .andReturn(Collections.singletonList(exportRegistration)).once();
        listener.endpointAdded(epd, null);
        EasyMock.expectLastCall().once();
    }

    private Executor syncExecutor() {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private ExportRegistration createExportRegistration(IMocksControl c, EndpointDescription endpoint) {
        ExportRegistration exportRegistration = c.createMock(ExportRegistration.class);
        ExportReference exportReference = c.createMock(ExportReference.class);
        EasyMock.expect(exportRegistration.getExportReference()).andReturn(exportReference).anyTimes();
        EasyMock.expect(exportRegistration.getException()).andReturn(null).anyTimes();
        EasyMock.expect(exportReference.getExportedEndpoint()).andReturn(endpoint).anyTimes();
        return exportRegistration;
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(RemoteConstants.ENDPOINT_ID, "1");
        props.put(Constants.OBJECTCLASS, new String[] {"abc"});
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "cxf");
        return new EndpointDescription(props);
    }

    private ServiceReference createUserService(IMocksControl c) {
        return createUserService(c, "*");
    }

    private ServiceReference createUserService(IMocksControl c, Object exportedInterfaces) {
        final ServiceReference sref = c.createMock(ServiceReference.class);
        EasyMock.expect(sref.getProperty(EasyMock.same(RemoteConstants.SERVICE_EXPORTED_INTERFACES)))
            .andReturn(exportedInterfaces).anyTimes();
        Bundle srefBundle = c.createMock(Bundle.class);
        if(!"".equals(exportedInterfaces)) {
            EasyMock.expect(sref.getBundle()).andReturn(srefBundle).atLeastOnce();
            EasyMock.expect(srefBundle.getSymbolicName()).andReturn("serviceBundleName").atLeastOnce();
        }
        EasyMock.expect(sref.getProperty("objectClass")).andReturn("org.My").anyTimes();
        return sref;
    }
}
