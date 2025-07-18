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
package org.apache.aries.rsa.provider.fastbin.tcp;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.aries.rsa.provider.fastbin.api.Dispatched;
import org.apache.aries.rsa.provider.fastbin.api.ObjectSerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.api.Serialization;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.io.ClientInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ProtocolCodec;
import org.apache.aries.rsa.provider.fastbin.io.Transport;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.BufferEditor;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientInvokerImpl implements ClientInvoker, Dispatched {

    public static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(20);

    protected static final Logger LOGGER = LoggerFactory.getLogger(ClientInvokerImpl.class);

    @SuppressWarnings("rawtypes")
    private static final Map<Class, String> CLASS_TO_PRIMITIVE = new HashMap<>(8, 1.0F);

    static {
        CLASS_TO_PRIMITIVE.put(boolean.class, "Z");
        CLASS_TO_PRIMITIVE.put(byte.class, "B");
        CLASS_TO_PRIMITIVE.put(char.class, "C");
        CLASS_TO_PRIMITIVE.put(short.class, "S");
        CLASS_TO_PRIMITIVE.put(int.class, "I");
        CLASS_TO_PRIMITIVE.put(long.class, "J");
        CLASS_TO_PRIMITIVE.put(float.class, "F");
        CLASS_TO_PRIMITIVE.put(double.class, "D");
    }

    protected final AtomicLong correlationGenerator = new AtomicLong();
    protected final DispatchQueue queue;
    protected final Map<String, TransportPool> transports = new HashMap<>();
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final Map<Long, ResponseFuture> requests = new HashMap<>();
    protected final long timeout;
    protected final Map<String, SerializationStrategy> serializationStrategies;
    protected final boolean isTracing;

    public ClientInvokerImpl(DispatchQueue queue, Map<String, SerializationStrategy> serializationStrategies) {
        this(queue, DEFAULT_TIMEOUT, serializationStrategies);
    }

    public ClientInvokerImpl(DispatchQueue queue, long timeout, Map<String, SerializationStrategy> serializationStrategies) {
        this.queue = queue;
        this.timeout = timeout;
        this.serializationStrategies = serializationStrategies;
        this.isTracing = LOGGER.isTraceEnabled();
    }

    public DispatchQueue queue() {
        return queue;
    }

    public void start() throws Exception {
        start(null);
    }

    public void start(Runnable onComplete) throws Exception {
        running.set(true);
        if (onComplete != null) {
            onComplete.run();
        }
    }

    public void stop() {
        stop(null);
    }

    public void stop(final Runnable onComplete) {
        if (running.compareAndSet(true, false)) {
            queue().execute(new Runnable() {
                public void run() {
                    final AtomicInteger latch = new AtomicInteger(transports.size());
                    final Runnable countDown = new Runnable() {
                        public void run() {
                            if (latch.decrementAndGet() == 0) {
                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            }
                        }
                    };
                    for (TransportPool pool : transports.values()) {
                        pool.stop(countDown);
                    }
                }
            });
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public InvocationHandler getProxy(String address, String service, ClassLoader classLoader, int protocolVersion) {
        return new ProxyInvocationHandler(address, service, classLoader,protocolVersion);
    }

    protected void onCommand(TransportPool pool, Object data) {
        try {
            DataByteArrayInputStream bais = new DataByteArrayInputStream( (Buffer) data);
            bais.readInt();
            long correlation = bais.readVarLong();
            pool.onDone(correlation);
            ResponseFuture response = requests.remove(correlation);
            if( response!=null ) {
                response.set(bais);
            }
        } catch (Exception e) {
            LOGGER.info("Error while reading response", e);
        }
    }

    protected void onFailure(Object id, Throwable throwable) {
        ResponseFuture response = requests.remove(id);
        if( response!=null ) {
            response.fail(throwable);
        }
    }

    static final WeakHashMap<Method, MethodData> method_cache = new WeakHashMap<>();

    static class MethodData {
        private final SerializationStrategy serializationStrategy;
        final Buffer signature;
        final InvocationStrategy invocationStrategy;

        MethodData(InvocationStrategy invocationStrategy, SerializationStrategy serializationStrategy, Buffer signature) {
            this.invocationStrategy = invocationStrategy;
            this.serializationStrategy = serializationStrategy;
            this.signature = signature;
        }
    }

    private MethodData getMethodData(Method method) throws IOException {
        MethodData rc;
        synchronized (method_cache) {
            rc = method_cache.get(method);
        }
        if( rc==null ) {
            StringBuilder sb = new StringBuilder();
            sb.append(method.getName());
            sb.append(",");
            Class<?>[] types = method.getParameterTypes();
            for(int i = 0; i < types.length; i++) {
                if( i != 0 ) {
                    sb.append(",");
                }
                sb.append(encodeClassName(types[i]));
            }
            Buffer signature = new UTF8Buffer(sb.toString()).buffer();

            Serialization annotation = method.getAnnotation(Serialization.class);
            SerializationStrategy serializationStrategy;
            if( annotation!=null ) {
                serializationStrategy = serializationStrategies.get(annotation.value());
                if( serializationStrategy==null ) {
                    throw new RuntimeException("Could not find the serialization strategy named: "+annotation.value());
                }
            } else {
                serializationStrategy = ObjectSerializationStrategy.INSTANCE;
            }

            final InvocationStrategy strategy = InvocationType.forMethod(method);

            rc = new MethodData(strategy, serializationStrategy, signature);
            synchronized (method_cache) {
                method_cache.put(method, rc);
            }
        }
        return rc;
    }

    String encodeClassName(Class<?> type) {
        if( type.getComponentType()!=null ) {
            return "["+ encodeClassName(type.getComponentType());
        }
        if( type.isPrimitive() ) {
            return CLASS_TO_PRIMITIVE.get(type);
        } else {
            return "L"+type.getName();
        }
    }

    protected Object request(ProxyInvocationHandler handler, final String address, final UTF8Buffer service, final ClassLoader classLoader, final Method method, final Object[] args) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("DOSGi Client stopped");
        }

        final long correlation = correlationGenerator.incrementAndGet();

        // Encode the request before we try to pass it onto
        // IO layers so that #1 we can report encoding error back to the caller
        // and #2 reduce CPU load done in the execution queue since it's
        // serially executed.

        DataByteArrayOutputStream baos = new DataByteArrayOutputStream((int) (handler.lastRequestSize * 1.10));
        baos.writeInt(0); // we don't know the size yet...
        baos.writeVarLong(correlation);
        writeBuffer(baos, service);

        MethodData methodData = getMethodData(method);
        writeBuffer(baos, methodData.signature);

        final ResponseFuture future = methodData.invocationStrategy.request(methodData.serializationStrategy.forProtocolVersion(handler.protocolVersion), classLoader, method, args, baos, handler.protocolVersion);
        // toBuffer() is better than toByteArray() since it avoids an
        // array copy.
        final Buffer command = baos.toBuffer();

        // Update the field size.
        BufferEditor editor = command.buffer().bigEndianEditor();
        final int commandSize = command.length;
        editor.writeInt(commandSize);
        handler.lastRequestSize = command.length;

        queue().execute(new Runnable() {
            public void run() {
                try {
                    TransportPool pool = transports.get(address);
                    if (pool == null) {
                        pool = new InvokerTransportPool(address, queue());
                        transports.put(address, pool);
                        pool.start();
                    }
                    requests.put(correlation, future);
                    pool.offer(command, correlation);
                } catch (Exception e) {
                    LOGGER.info("Error while sending request", e);
                    future.fail(e);
                }
            }
        });

        Object result;
        try
        {
            result = future.get(timeout, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            trace(method, address, args, commandSize, future, null, e);
            throw e;
        }
        trace(method, address, args, commandSize, future, result, null);
        return result;
    }

    private void trace(Method method, String address, Object[] args, int commandSize, ResponseFuture future, Object result, Throwable ex)
    {
        if (!isTracing) return;

        String methodString = String.valueOf(method).replace("public abstract ", "");
        String message = String.format("Finished call. Address=%s, future=%s, method=%s, args=%s, size=%d, result=%s", address, future, methodString, Arrays.toString(args), commandSize, result);
        LOGGER.trace(message, ex);
    }


    private void writeBuffer(DataByteArrayOutputStream baos, Buffer value) throws IOException {
        baos.writeVarInt(value.length);
        baos.write(value);
    }

    protected class ProxyInvocationHandler implements InvocationHandler {

        int protocolVersion;
        final String address;
        final UTF8Buffer service;
        final ClassLoader classLoader;
        int lastRequestSize = 250;

        public ProxyInvocationHandler(String address, String service, ClassLoader classLoader, int protocolVersion) {
            this.address = address;
            this.service = new UTF8Buffer(service);
            this.classLoader = classLoader;
            this.protocolVersion = protocolVersion;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if(method.getDeclaringClass()==Object.class) {

                    if (args != null && args.length == 1 && "equals".equals(method.getName())) {
                        //special treatment for equals to make sure proxy.equals(proxy) -> true
                        Object arg = args[0];
                        if (arg == null) {
                            return false;
                        }
                        if (proxy == arg) {
                            return true;
                        }
                    }
                    //shortcut for hashcode, toString...
                    return method.invoke(this, args);
                }
                return request(this, address, service, classLoader, method, args);
            }
            catch (Throwable e) {
                if (e instanceof ExecutionException) {
                    ExecutionException executionException = (ExecutionException)e;
                    e = executionException.getCause();
                }
                if (e instanceof RuntimeException) {
                    throw e;
                }
                Class< ? >[] exceptionTypes = method.getExceptionTypes();
                for (Class< ? > exceptionType : exceptionTypes) {
                    if(exceptionType.isAssignableFrom(e.getClass()))
                        throw e;
                }
                throw new ServiceException(e.getMessage(), e);
            }
        }

    }

    protected class InvokerTransportPool extends TransportPool {

        public InvokerTransportPool(String uri, DispatchQueue queue) {
            /*
             * the evict time needs to be 0. Otherwise, the client will
             * evict transport objects which breaks the connection for
             * long-running async calls.
             * Since there is limit of 2 transports per uri it shouldn't be that many objects
             */
            super(uri, queue, TransportPool.DEFAULT_POOL_SIZE, 0);
        }

        @Override
        protected Transport createTransport(String uri) throws Exception {
            return new TcpTransportFactory().connect(uri);
        }

        @Override
        protected ProtocolCodec createCodec() {
            return new LengthPrefixedCodec();
        }

        @Override
        protected void onCommand(Object command) {
            ClientInvokerImpl.this.onCommand(this, command);
        }

        @Override
        protected void onFailure(Object id, Throwable throwable) {
            ClientInvokerImpl.this.onFailure(id, throwable);
        }
    }

}
