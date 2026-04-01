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

import java.io.Closeable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks in-flight {@code SendTask} executions and emits log entries when their
 * execution time exceeds configured thresholds.
 *
 * <p>
 * When a task is registered via {@link #track} two {@link TimerTask}s are
 * scheduled on a single shared daemon {@link Timer}:
 * <ol>
 * <li>fires after the configured warning threshold – logs a WARN</li>
 * <li>fires after the live client timeout returned by
 * {@code Activator.getInstance().getClient().getTimeout()} – logs an ERROR
 * indicating the server has exceeded the client timeout and the client has
 * likely already given up waiting for this response</li>
 * </ol>
 * Both tasks are cancelled immediately when {@link #complete} is called, so
 * fast calls produce no log output at all.
 *
 * <p>
 * Call {@link #close()} when the owning component stops to cancel the shared
 * timer and all pending tasks.
 */
final class ServerResponseThresholdTracker implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerResponseThresholdTracker.class);

	/**
	 * Single shared daemon timer – one thread services all threshold tasks per
	 * request.
	 */
	private final Timer timer = new Timer("rsa-threshold-monitor", true);

	/** Active tasks keyed by correlation id. */
	private final ConcurrentMap<Long, TrackedEntry> activeTasks = new ConcurrentHashMap<>();

	/**
	 * Duration after which a running server-side call issues a WARN. Configurable
	 * via system property
	 * {@code org.apache.aries.rsa.provider.fastbin.tcpthreshold.warn.ms}. Default
	 * half of the client timeout threshold.
	 */
	private final long thresholdWarningMs;

	/**
	 * Client-side request timeout passed in at construction time from
	 * {@code ServerInvokerImpl}, which receives it from {@code FastBinProvider}.
	 */
	private final long clientTimeout;

	/** Flag to disable all tracking and logging when client timeout is non-positive. */
	private boolean turnedOn = true;

	/**
	 * Production constructor – reads the warning threshold from the system
	 * property.
	 *
	 * @param clientTimeout
	 *            the client-side request timeout in milliseconds; should match
	 *            {@code ClientInvokerImpl}'s configured timeout
	 */
	ServerResponseThresholdTracker(long clientTimeout) {
		this(Long.getLong("org.apache.aries.rsa.provider.fastbin.tcpthreshold.warn.ms", clientTimeout / 2),
			 clientTimeout);

		if (clientTimeout <= 0) {
			turnedOn = false;
			LOGGER.info("Client timeout is non-positive ({} ms) – response threshold tracking disabled", clientTimeout);
		}
	}

	/**
	 * Package-private constructor for unit tests – accepts explicit threshold
	 * values so tests can use short delays without touching system properties.
	 *
	 * @param thresholdWarningMs
	 *            duration in ms before a WARN is logged
	 * @param clientTimeout
	 *            duration in ms before an ERROR is logged (mirrors client timeout)
	 */
	ServerResponseThresholdTracker(long thresholdWarningMs, long clientTimeout) {
		this.thresholdWarningMs = thresholdWarningMs;
		this.clientTimeout = clientTimeout;
	}

	private static final class TrackedEntry {
		final TimerTask warnTask;
		final TimerTask errorTask;

		TrackedEntry(TimerTask warnTask, TimerTask errorTask) {
			this.warnTask = warnTask;
			this.errorTask = errorTask;
		}
	}

	/**
	 * Registers a {@code SendTask} as in-flight and schedules all three threshold
	 * checks. Must be called in {@code ServerInvokerImpl.onCommand()} before
	 * submitting the task to the executor.
	 *
	 * @param correlationId
	 *            unique request correlation id
	 * @param className
	 *            simple name of the service class ({@code "unknown"} if
	 *            unavailable)
	 * @param methodName
	 *            name of the invoked method ({@code "unknown"} if unavailable)
	 */
	void track(final long correlationId, final String className, final String methodName) {
		if (!turnedOn) {
			return;
		}

		TimerTask warnTask = new TimerTask() {
			@Override
			public void run() {
				LOGGER.warn("Remote call still running after {}ms , method: {}.{}", thresholdWarningMs, className,
						methodName);
			}
		};

		TimerTask errorTask = new TimerTask() {
			@Override
			public void run() {
				LOGGER.error(
						"Remote call still running after {}ms - client timeout reached. "
								+ "Client may have already abandoned the request, method: {}.{}",
						clientTimeout, className, methodName);
			}
		};

		activeTasks.put(correlationId, new TrackedEntry(warnTask, errorTask));
		timer.schedule(warnTask, thresholdWarningMs);
		timer.schedule(errorTask, clientTimeout);
	}

	/**
	 * Cancels all pending threshold tasks for the given correlation id. If the call
	 * finishes before any threshold is reached no log entry is produced. Must be
	 * called inside the {@code SendTask}'s completion callback {@code finally}
	 * block.
	 *
	 * @param correlationId
	 *            unique request correlation id
	 */
	void complete(long correlationId) {
		if (!turnedOn) {
			return;
		}
		TrackedEntry entry = activeTasks.remove(correlationId);
		if (entry != null) {
			entry.warnTask.cancel();
			entry.errorTask.cancel();
		}
	}

	/**
	 * Cancels the shared timer and all pending threshold tasks. Must be called when
	 * the owning {@code ServerInvokerImpl} stops.
	 */
	@Override
	public void close() {
		timer.cancel();
		activeTasks.clear();
	}
}
