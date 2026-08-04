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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link ServerResponseThresholdTracker}.
 *
 * <p>
 * Uses the two-arg package-private constructor so that millisecond-level delays
 * can be used instead of the production 10s / 20s values.
 *
 * <p>
 * Threshold values chosen:
 * <ul>
 * <li>warning = 80 ms</li>
 * <li>error (client timeout) = 200 ms</li>
 * </ul>
 * Awaitility polls for at most 2 seconds so tests fail fast on regressions.
 */
public class ServerResponseThresholdTrackerTest {
	/** Warning threshold used across all tests (ms). */
	private static final long WARN_MS = 80L;
	/** Error/client-timeout threshold used across all tests (ms). */
	private static final long TIMEOUT_MS = 200L;

	private static final String CLASS_NAME = "MyService";
	private static final String METHOD_NAME = "myMethod";
	private static final long CORR_ID = 1L;

	private ServerResponseThresholdTracker tracker;

	/** Logback in-memory appender for the class under test. */
	private ListAppender<ILoggingEvent> appender;
	private Logger trackerLogger;

	@Before
	public void setUp() {
		tracker = new ServerResponseThresholdTracker(WARN_MS, TIMEOUT_MS);

		appender = new ListAppender<>();
		appender.start();
		trackerLogger = (Logger) LoggerFactory.getLogger(ServerResponseThresholdTracker.class);
		trackerLogger.addAppender(appender);
	}

	@After
	public void tearDown() {
		trackerLogger.detachAppender(appender);
		tracker.close();
	}

	private List<ILoggingEvent> logsAtLevel(Level level) {
		return appender.list.stream().filter(e -> e.getLevel() == level).collect(Collectors.toList());
	}

	/**
	 * A task that completes well before the warning threshold must not produce any
	 * log output.
	 */
	@Test
	public void completingBeforeWarningProducesNoLogs() throws Exception {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, false);
		tracker.complete(CORR_ID);

		// wait past both thresholds to be sure nothing fires
		Thread.sleep(TIMEOUT_MS + 100);

		assertThat(appender.list, is(empty()));
	}

	/**
	 * A task still running after {@code WARN_MS} must produce exactly one WARN
	 * entry.
	 */
	@Test
	public void warnIsLoggedWhenWarningThresholdExceeded() {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, false);

		await().atMost(2, TimeUnit.SECONDS).until(() -> !logsAtLevel(Level.WARN).isEmpty());

		assertThat(logsAtLevel(Level.WARN), hasSize(1));
		assertThat(logsAtLevel(Level.ERROR), is(empty()));

		tracker.complete(CORR_ID);
	}

	/**
	 * A task still running after {@code TIMEOUT_MS} must produce an ERROR entry (in
	 * addition to the earlier WARN).
	 */
	@Test
	public void errorIsLoggedWhenClientTimeoutExceeded() {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, true);

		await().atMost(2, TimeUnit.SECONDS).until(() -> !logsAtLevel(Level.ERROR).isEmpty());

		assertThat(logsAtLevel(Level.WARN), hasSize(1));
		assertThat(logsAtLevel(Level.ERROR), hasSize(1));

		tracker.complete(CORR_ID);
	}

	/**
	 * Completing a task after the WARN has fired but before the ERROR fires must
	 * cancel the pending ERROR task – so only the WARN entry appears.
	 */
	@Test
	public void completingAfterWarnButBeforeErrorCancelsErrorTask() throws Exception {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, true);

		// wait until the warning has fired
		await().atMost(2, TimeUnit.SECONDS).until(() -> !logsAtLevel(Level.WARN).isEmpty());

		tracker.complete(CORR_ID);

		// wait past the point where the error would have fired
		Thread.sleep(TIMEOUT_MS + 100);

		assertThat(logsAtLevel(Level.WARN), hasSize(1));
		assertThat(logsAtLevel(Level.ERROR), is(empty()));
	}

	/**
	 * Calling {@link ServerResponseThresholdTracker#complete} with a correlation id
	 * that was never tracked must not throw.
	 */
	@Test
	public void completeWithUnknownCorrelationIdDoesNotThrow() {
		tracker.complete(999L); // never tracked – must be a no-op
	}

	/**
	 * Calling {@link ServerResponseThresholdTracker#close} must cancel all pending
	 * timer tasks so that no log entries appear afterward.
	 */
	@Test
	public void closePreventsPendingTasksFromFiring() throws Exception {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, true);
		tracker.close();

		Thread.sleep(TIMEOUT_MS + 100);

		assertThat(appender.list, is(empty()));
	}

	/**
	 * Log messages must include the class and method names passed to
	 * {@link ServerResponseThresholdTracker#track}.
	 */
	@Test
	public void logMessagesContainClassAndMethodName() {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, true);

		await().atMost(2, TimeUnit.SECONDS).until(() -> !logsAtLevel(Level.WARN).isEmpty());

		String msg = logsAtLevel(Level.WARN).getFirst().getFormattedMessage();
		assertThat(msg, containsString(CLASS_NAME));
		assertThat(msg, containsString(METHOD_NAME));

		tracker.complete(CORR_ID);
	}

	/**
	 * Multiple concurrent tasks must be tracked independently: completing one must
	 * not cancel the timers of the others.
	 */
	@Test
	public void multipleTasksTrackedIndependently() throws Exception {
		tracker.track(1L, "ServiceA", "methodA", true);
		tracker.track(2L, "ServiceB", "methodB", true);
		tracker.track(3L, "ServiceC", "methodC", true);

		// complete task 2 immediately – its timers must be canceled
		tracker.complete(2L);

		// wait until warnings fire for tasks 1 and 3
		await().atMost(2, TimeUnit.SECONDS).until(() -> logsAtLevel(Level.WARN).size() >= 2);

		long warnForA = logsAtLevel(Level.WARN).stream().filter(e -> e.getFormattedMessage().contains("methodA"))
				.count();
		long warnForB = logsAtLevel(Level.WARN).stream().filter(e -> e.getFormattedMessage().contains("methodB"))
				.count();
		long warnForC = logsAtLevel(Level.WARN).stream().filter(e -> e.getFormattedMessage().contains("methodC"))
				.count();

		assertEquals("task A should have warned", 1L, warnForA);
		assertEquals("task B was completed early – no warn expected", 0L, warnForB);
		assertEquals("task C should have warned", 1L, warnForC);

		tracker.complete(1L);
		tracker.complete(3L);
	}

	/**
	 * Calling {@link ServerResponseThresholdTracker#complete} twice for the same
	 * correlation id must be a no-op on the second call.
	 */
	@Test
	public void completeIsIdempotent() throws Exception {
		tracker.track(CORR_ID, CLASS_NAME, METHOD_NAME, true);
		tracker.complete(CORR_ID);
		tracker.complete(CORR_ID); // second call must not throw

		Thread.sleep(TIMEOUT_MS + 100);

		assertThat(appender.list, is(empty()));
	}
}
