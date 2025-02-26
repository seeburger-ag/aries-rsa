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
package org.apache.aries.rsa.core;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;

import static org.junit.Assert.*;

public class ImportRegistrationImplTest {

    @Test
    public void testException() {
        IMocksControl c = EasyMock.createNiceControl();
        Exception e = c.createMock(Exception.class);
        c.replay();

        ImportRegistrationImpl i = new ImportRegistrationImpl(e);

        assertEquals(e, i.getException());
        assertNull(i.getImportedEndpointDescription());
        assertNull(i.getImportedService());
        assertEquals(i, i.getParent());
    }

    @Test
    public void testDefaultCtor() {
        IMocksControl c = EasyMock.createNiceControl();
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        CloseHandler closeHandler = c.createMock(CloseHandler.class);

        c.replay();

        ImportRegistrationImpl i = new ImportRegistrationImpl(endpoint, closeHandler, null);

        assertNull(i.getException());
        assertEquals(i, i.getParent());
        assertEquals(endpoint, i.getImportedEndpointDescription());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testCloneAndClose() {
        IMocksControl c = EasyMock.createControl();
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        CloseHandler closeHandler = c.createMock(CloseHandler.class);

        ServiceRegistration sr = c.createMock(ServiceRegistration.class);
        ServiceReference sref = c.createMock(ServiceReference.class);
        EasyMock.expect(sr.getReference()).andReturn(sref).anyTimes();

        c.replay();

        ImportRegistrationImpl i1 = new ImportRegistrationImpl(endpoint, closeHandler, null);

        ImportRegistrationImpl i2 = new ImportRegistrationImpl(i1);

        ImportRegistrationImpl i3 = new ImportRegistrationImpl(i2);

        try {
            i2.setImportedServiceRegistration(sr);
            fail("An exception should be thrown here !");
        } catch (IllegalStateException e) {
            // must be thrown here
        }

        i1.setImportedServiceRegistration(sr);

        assertEquals(i1, i1.getParent());
        assertEquals(i1, i2.getParent());
        assertEquals(i1, i3.getParent());

        assertEquals(endpoint, i1.getImportedEndpointDescription());
        assertEquals(endpoint, i2.getImportedEndpointDescription());
        assertEquals(endpoint, i3.getImportedEndpointDescription());

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(i3));
        EasyMock.expectLastCall().once();

        c.replay();

        i3.close();
        i3.close(); // shouldn't change anything

        assertNull(i3.getImportedEndpointDescription());

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(i1));
        EasyMock.expectLastCall().once();

        c.replay();

        i1.close();

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(i2));
        EasyMock.expectLastCall().once();

        sr.unregister();
        EasyMock.expectLastCall().once();

        c.replay();

        i2.close();

        c.verify();
    }

    @Test
    public void testCloseAll() {
        IMocksControl c = EasyMock.createControl();
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        CloseHandler closeHandler = c.createMock(CloseHandler.class);

        c.replay();

        ImportRegistrationImpl i1 = new ImportRegistrationImpl(endpoint, closeHandler, null);

        ImportRegistrationImpl i2 = new ImportRegistrationImpl(i1);

        ImportRegistrationImpl i3 = new ImportRegistrationImpl(i2);

        assertEquals(i1, i1.getParent());
        assertEquals(i1, i2.getParent());
        assertEquals(i1, i3.getParent());

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(i2));
        EasyMock.expectLastCall().once();

        c.replay();

        i2.close();

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(i1));
        EasyMock.expectLastCall().once();
        closeHandler.onClose(EasyMock.eq(i3));
        EasyMock.expectLastCall().once();

        c.replay();
        i3.closeAll();
        c.verify();
    }
}
