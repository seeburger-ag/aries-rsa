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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.aries.rsa.provider.fastbin.FastBinProvider;
import org.apache.aries.rsa.provider.fastbin.util.ClassLoaderObjectInputStream;
import org.apache.aries.rsa.provider.fastbin.util.FilteredClassLoaderObjectInputStream;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.osgi.framework.ServiceException;

public class ObjectSerializationStrategy implements SerializationStrategy {
    public static final ObjectSerializationStrategy INSTANCE = new ObjectSerializationStrategy();
    private static final ObjectSerializationStrategy V1 = INSTANCE;
    // private int protocolVersion = FastBinProvider.PROTOCOL_VERSION

    private static final Set<String> DENIED_CLASSES;
    private static final Set<String> ALLOWED_CLASSES;
    private static final FilteredClassLoaderObjectInputStream.AllowlistPackagesPredicate ALLOWED_PACKAGES;
    private static final String ADDITIONAL_DENIED_CLASSES = System.getProperty( "org.apache.aries.rsa.provider.fastbin.api.DESERIALIZATION_CLASS_DENY_LIST", "");
    private static final String ADDITIONAL_ALLOWED_PACKAGE = System.getProperty( "org.apache.aries.rsa.provider.fastbin.api.DESERIALIZATION_PACKAGE_ALLOW_LIST", "");
    private static final String ADDITIONAL_ALLOWED_CLASSES = System.getProperty( "org.apache.aries.rsa.provider.fastbin.api.DESERIALIZATION_CLASS_ALLOW_LIST", "");

    static
    {
        // denied classes
        Set<String> deniedClasses = new HashSet<>(Arrays.asList("java.net.URL", "[Ljava.net.URL"));
        final String[] customDeniedClasses = ADDITIONAL_DENIED_CLASSES.split(",");
        if (customDeniedClasses.length > 0)
        {
            deniedClasses.addAll(Arrays.asList(customDeniedClasses));
        }
        DENIED_CLASSES = deniedClasses;

        // allowed classes
        Set<String> allowedClasses = new HashSet<>(Arrays.asList(
                        "B",  // byte
                        "C",  // char
                        "D",  // double
                        "F",  // float
                        "I",  // int
                        "J",  // long
                        "S",  // short
                        "Z",  // boolean
                        "L"   // Object type (LClassName;)
        ));
        final String[] customAllowedClasses = ADDITIONAL_ALLOWED_CLASSES.split(",");
        if (customAllowedClasses.length > 0)
        {
            allowedClasses.addAll(Arrays.asList(customAllowedClasses));
        }
        ALLOWED_CLASSES = allowedClasses;

        // allowed packages
        List<String> packages = new ArrayList<>(Arrays.asList(
                        "java",
                        "javax",
                        "Ljava",
                        "org.apache.aries.rsa",
                        "org.osgi.framework",
                        "com.seeburger"));

        final String[] customPackages = ADDITIONAL_ALLOWED_PACKAGE.split(",");
        if (customPackages.length > 0)
        {
            packages.addAll(Arrays.asList(customPackages));
        }
        ALLOWED_PACKAGES = new FilteredClassLoaderObjectInputStream.AllowlistPackagesPredicate(packages);
    }



    public String name() {
        return "object";
    }

    public void encodeRequest(ClassLoader loader, Class<?>[] types, Object[] args, DataByteArrayOutputStream target) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(target);
        oos.writeObject(args);
        oos.flush();
    }

    public void decodeResponse(ClassLoader loader, Class<?> type, DataByteArrayInputStream source, AsyncCallback result) throws IOException, ClassNotFoundException {
        ClassLoaderObjectInputStream ois = new FilteredClassLoaderObjectInputStream(source, DENIED_CLASSES, ALLOWED_CLASSES, ALLOWED_PACKAGES);
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
        ClassLoaderObjectInputStream ois = new FilteredClassLoaderObjectInputStream(source, DENIED_CLASSES, ALLOWED_CLASSES, ALLOWED_PACKAGES);
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
