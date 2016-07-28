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
package org.apache.aries.rsa.provider.fastbin.api;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;

import org.apache.aries.rsa.provider.fastbin.FastBinProvider;
import org.apache.aries.rsa.provider.fastbin.util.ClassLoaderObjectInputStream;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.osgi.framework.ServiceException;

/**
 * <p>
 * </p>
 *
 */
public class ObjectSerializationStrategy implements SerializationStrategy {
    public static final ObjectSerializationStrategy INSTANCE = new ObjectSerializationStrategy();
    private static final ObjectSerializationStrategy V1 = INSTANCE;
    private int protocolVersion = FastBinProvider.PROTOCOL_VERSION;


    public String name() {
        return "object";
    }

    public void encodeRequest(ClassLoader loader, Class<?>[] types, Object[] args, DataByteArrayOutputStream target) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(target);
        oos.writeObject(args);
        oos.flush();
    }

    public void decodeResponse(ClassLoader loader, Class<?> type, DataByteArrayInputStream source, AsyncCallback result) throws IOException, ClassNotFoundException {
        ClassLoaderObjectInputStream ois = new ClassLoaderObjectInputStream(source);
        ois.setClassLoader(loader);
        Throwable error = (Throwable) ois.readObject();
        Object value = ois.readObject();
        if (error != null) {
            result.onFailure(error);
        } else {
            result.onSuccess(value);
        }
    }

    public void decodeRequest(ClassLoader loader, Class<?>[] types, DataByteArrayInputStream source, Object[] target) throws IOException, ClassNotFoundException {
        final ClassLoaderObjectInputStream ois = new ClassLoaderObjectInputStream(source);
        ois.setClassLoader(loader);
        final Object[] args = (Object[]) ois.readObject();
        if( args!=null ) {
            System.arraycopy(args, 0, target, 0, args.length);
        }
    }



    public void encodeResponse(ClassLoader loader, Class<?> type, Object value, Throwable error, DataByteArrayOutputStream target) throws IOException, ClassNotFoundException {
        ObjectOutputStream oos = new ObjectOutputStream(target);
        oos.writeObject(error);
        oos.writeObject(value);
        oos.flush();
    }

    @Override
    public int getProtocolVersion() {
        return FastBinProvider.PROTOCOL_VERSION;
    }

    @Override
    public SerializationStrategy forProtocolVersion(int protocolVersion)
    {
        switch (protocolVersion)
        {
            case 1:
                return V1;
            default:
                break;
        }
        throw new ServiceException(MessageFormat.format("Incorrect fastbin protocol {0} version. Only protocol versions up to {1} are supported.", protocolVersion,FastBinProvider.PROTOCOL_VERSION));
    }

}
