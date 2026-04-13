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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.aries.rsa.provider.fastbin.api.AsyncCallback;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;


@SuppressWarnings("rawtypes")
public class AsyncFutureInvocationStrategy extends AbstractInvocationStrategy {

    private static final boolean REPLY_ASYNC = Boolean.getBoolean("org.apache.aries.rsa.provider.fastbin.tcp.async.reply");

    private final FutureCompleter completer = new FutureCompleter();

    private static final ExecutorService executorReplyAsync;

    static {
        if (REPLY_ASYNC) {
            executorReplyAsync = Executors.newFixedThreadPool(
                            Integer.getInteger("org.apache.aries.rsa.provider.fastbin.tcp.async.reply.executors", 24),
                            new ThreadFactory()
                            {
                                private final AtomicInteger poolNumber = new AtomicInteger(1);

                                @Override
                                public Thread newThread(Runnable r)
                                {
                                    Thread t = new Thread(r);
                                    t.setName("aries-rsa-fastbin-async-reply-executor-" + poolNumber.getAndIncrement());
                                    return t;
                                }
                            });
        }
        else {
            executorReplyAsync = null;
        }
    }

    @SuppressWarnings("unchecked")
    protected void doService(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, final DataByteArrayOutputStream responseStream, final Runnable onComplete) {

        final AsyncServiceResponse helper = new AsyncServiceResponse(loader, method, responseStream, onComplete, serializationStrategy);
        try {
            Class<?>[] types = method.getParameterTypes();
            final Object[] args = new Object[types.length];
            serializationStrategy.decodeRequest(loader, types, requestStream, args);
            Future<Object> future = (Future<Object>)method.invoke(target, args);
            CompletableFuture<Object> completable;
            if(future instanceof CompletableFuture) {
                completable = (CompletableFuture<Object>)future;
            }
            else {
                completable = completer.complete(future);
            }

            if (REPLY_ASYNC) {
                assert executorReplyAsync != null;
                // let reply be sent by own executor thread,
                // not blocking the caller of either #whenComplete (in case Future complete, or later  #complete from #FutureCompleter
                completable.whenCompleteAsync(new BiConsumer<Object, Throwable>() {
                    public void accept(Object returnValue, Throwable exception) {
                        helper.send(exception, returnValue);
                    }
                }, executorReplyAsync);
            }
            else
            {
                assert executorReplyAsync == null : executorReplyAsync;
                completable.whenComplete(new BiConsumer<Object, Throwable>() {
                    public void accept(Object returnValue, Throwable exception) {
                        helper.send(exception, returnValue);
                    }
                });
            }

        } catch (Throwable t) {
            helper.send(t, null);
        }
    }

    @Override
    protected ResponseFuture createResponse(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args) throws Exception {
        return new AsyncResponseFuture(loader, method, serializationStrategy, Dispatch.getCurrentQueue());
    }

    protected Class getResultType(Method method) {
        try {
            Type type = method.getGenericReturnType();
            ParameterizedType t = (ParameterizedType) type;
            return (Class) t.getActualTypeArguments()[0];
        }
        catch (Exception e) {
            return super.getResultType(method);
        }
    }

    private class AsyncResponseFuture implements ResponseFuture, AsyncCallback {

        private final ClassLoader loader;
        private final Method method;
        private final SerializationStrategy serializationStrategy;
        private final DispatchQueue queue;
        private final CompletableFuture<Object> future;

        public AsyncResponseFuture(ClassLoader loader, Method method, SerializationStrategy serializationStrategy, DispatchQueue queue) {
            this.loader = loader;
            this.method = method;
            this.serializationStrategy = serializationStrategy;
            this.queue = queue;
            this.future = new CompletableFuture<>();
        }

        public void set(final DataByteArrayInputStream source) {
            if( queue != null ) {
                queue.execute(new Runnable() {
                    public void run() {
                        decodeIt(source);
                    }
                });
            } else {
                decodeIt(source);
            }
        }

        private void decodeIt(DataByteArrayInputStream source) {
            try {
                serializationStrategy.decodeResponse(loader, getResultType(method), source, this);
            } catch (Throwable e) {
                onFailure(e);
            }
        }

        @Override
        public void fail(Throwable throwable) {

            onFailure(throwable);
        }

        @Override
        public void onSuccess(Object result) {
            future.complete(result);
        }

        @Override
        public void onFailure(Throwable failure) {
            future.completeExceptionally(failure);
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws Exception {
            return future;
        }
    }

    /**
     * Helper class that polls available futures in a background thread for readiness
     * and reports them to a completable future
     */
    private static class FutureCompleter extends Thread {

        private static final int SLEEP_ON_EMPTY = Integer.getInteger("org.apache.aries.rsa.provider.fastbin.tcp.async.future.completer.sleep.on.empty", 20);
        private final ConcurrentMap<Future<Object>, CompletableFuture<Object>> futures = new ConcurrentHashMap<>();
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final Semaphore available = new Semaphore(0);

        public FutureCompleter() {
            setName("Fastbin-Future-Completer");
            setDaemon(true);
        }

        @Override
        public void run() {
            while(true)
            {
                try
                {
                    // block until at least one future is registered
                    available.acquire();
                    available.drainPermits();

                    // stream over current ConcurrentMap and complete all currently available futures.
                    // We use stream to avoid concurrent modification exceptions, but we still need to remove the completed futures from the map after processing them.
                    try (Stream<Entry<Future<Object>, CompletableFuture<Object>>> entryStream = futures.entrySet().stream()
                            .filter(entry -> entry.getKey().isDone()))
                    {
                        entryStream.forEach(entry ->
                                            {
                                                Future<Object> future = entry.getKey();
                                                try
                                                {
                                                    Object object = future.get(0, TimeUnit.MILLISECONDS);
                                                    entry.getValue().complete(object);
                                                }
                                                catch (ExecutionException e)
                                                {
                                                    entry.getValue().completeExceptionally(e.getCause());
                                                }
                                                catch (Exception e) // includes TimeoutException
                                                {
                                                    entry.getValue().completeExceptionally(e);
                                                }
                                                futures.remove(future);
                                            });
                    }

                    if (futures.isEmpty()) {
                        // sleep a little to wait for additional futures to complete
                        try {
                            Thread.sleep(SLEEP_ON_EMPTY);
                        }
                        catch (InterruptedException e) {
                            // ignored
                        }
                    }
                }
                catch (Exception ex) {
                    // we catch all exceptions to avoid killing the thread,
                    // but log them to be able to investigate if something goes wrong.
                    LOGGER.warn("Unexpected exception while completing futures! Will continue.", ex);
                }
            }
        }

        public CompletableFuture<Object> complete(Future<Object> future) {
            if(started.compareAndSet(false, true)) {
                start();
            }
            CompletableFuture<Object> completable = new CompletableFuture<>();
            futures.put(future, completable);
            available.release();
            return completable;
        }
    }
}
