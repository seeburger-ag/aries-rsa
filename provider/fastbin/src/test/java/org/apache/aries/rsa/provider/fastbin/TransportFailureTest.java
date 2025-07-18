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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.fastbin.api.AsyncCallback;
import org.apache.aries.rsa.provider.fastbin.api.AsyncCallbackFuture;
import org.apache.aries.rsa.provider.fastbin.api.ProtobufSerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.tcp.ClientInvokerImpl;
import org.apache.aries.rsa.provider.fastbin.tcp.ServerInvokerImpl;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("rawtypes")
public class TransportFailureTest {

    private static long SLEEP_TIME = 100;
    private static long MAX_DELAY = 1000;

    @Test
    public void testInvoke() throws Exception {

        DispatchQueue queue = Dispatch.createQueue();
        HashMap<String, SerializationStrategy> map = new HashMap<>();
        map.put("protobuf", new ProtobufSerializationStrategy());

        ServerInvokerImpl server = new ServerInvokerImpl("tcp://localhost:0", queue, map);
        server.start();

        ClientInvokerImpl client = new ClientInvokerImpl(queue, map);
        client.start();

        try {
            server.registerService("service-id", new ServerInvoker.ServiceFactory() {
                public Object get() {
                    return new HelloImpl();
                }
                public void unget() {
                }
            }, HelloImpl.class.getClassLoader());

            InvocationHandler handler = client.getProxy(server.getConnectAddress(), "service-id", HelloImpl.class.getClassLoader(),FastBinProvider.PROTOCOL_VERSION);
            Hello hello = (Hello) Proxy.newProxyInstance(HelloImpl.class.getClassLoader(), new Class[]{Hello.class}, handler);

            AsyncCallbackFuture<String> future1 = new AsyncCallbackFuture<>();
            hello.hello("Guillaume", future1);

            long t0 = System.currentTimeMillis();
            try {
                assertEquals("Hello Guillaume!", future1.get(MAX_DELAY, TimeUnit.MILLISECONDS));
                fail("Should have thrown an exception");
            } catch (Exception e) {
                // Expected
                long t1 = System.currentTimeMillis();
                assertTrue(t1 - t0 > SLEEP_TIME / 2);
                assertTrue(t1 - t0 < MAX_DELAY / 2);
            }

        }
        finally {
            server.stop();
            client.stop();
        }
    }

    public interface Hello {
        void hello(String name, AsyncCallback<String> callback) throws Exception;
    }

    public static class HelloImpl implements Hello {
        public void hello(final String name, final AsyncCallback<String> callback) throws Exception {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(SLEEP_TIME);
                        // Big introspection call to access the transport channel and close it, simulating
                        // a disconnect on the client side.
                        ((SocketChannel) get(get(get(get(get(callback, "val$helper"), "onComplete"), "this$1"), "transport"), "channel")).close();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    callback.onSuccess("Hello " + name + "!");
                }
            }).start();
        }
    }

    private static Object get(Object obj, String field) throws Exception {
        for (Class cl = obj.getClass(); cl != Object.class; cl = cl.getSuperclass()) {
            try {
                Field f = obj.getClass().getDeclaredField(field);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable t) {
                // Ignore
            }
        }
        throw new NoSuchFieldException(field);
    }

}
