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
package org.apache.aries.rsa.provider.tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

public class TcpInvocationHandler implements InvocationHandler {
    private String host;
    private int port;
    private ClassLoader cl;
    private int timeoutMillis;

    public TcpInvocationHandler(ClassLoader cl, String host, int port, int timeoutMillis)
        throws UnknownHostException, IOException {
        this.cl = cl;
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Future.class.isAssignableFrom(method.getReturnType()) ||
            CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            return createFutureResult(method, args);
        } else if (Promise.class.isAssignableFrom(method.getReturnType())) {
            return createPromiseResult(method, args);
        } else {
            return handleSyncCall(method, args);
        }
    }

    private Object createFutureResult(final Method method, final Object[] args) {
        return CompletableFuture.supplyAsync(new Supplier<Object>() {
            public Object get() {
                try {
                    return handleSyncCall(method, args);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Object createPromiseResult(final Method method, final Object[] args) {
        final Deferred<Object> deferred = new Deferred<Object>();
        new Thread(new Runnable() {
            
            @Override
            public void run() {
                try {
                    deferred.resolve(handleSyncCall(method, args));
                } catch (Throwable e) {
                    deferred.fail(e);
                }
            }
        }).start();
        return deferred.getPromise();
    }

    private Object handleSyncCall(Method method, Object[] args) throws Throwable {
        Object result;
        try (
                Socket socket = new Socket(this.host, this.port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
            socket.setSoTimeout(timeoutMillis);
            out.writeObject(method.getName());
            out.writeObject(args);
            out.flush();
            result = parseResult(socket);
        } catch (Throwable e) {
            throw new RuntimeException("Error calling " + host + ":" + port + " method: " + method.getName(), e);
        }
        if (result instanceof Throwable) {
            throw (Throwable)result;
        }
        return result;
    }

    private Object parseResult(Socket socket) throws Throwable {
        try (ObjectInputStream in = new LoaderObjectInputStream(socket.getInputStream(), cl)) {
            return in.readObject();
        }
    }

}
