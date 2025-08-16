/*
 * (c) Copyright 2025 Ryan Yeats. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dst;

import java.lang.invoke.MethodHandles;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simulation {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final AtomicLong timeStep = SimulationTime.TIME;
  private final Clock clock;
  private final long seed;
  private final String base64ExecFingerprint;
  private final long stepDuration;
  private final SimulationInitializer initializer;
  private final List<SimulationScheduledExecutor> scheduledExecutors = new ArrayList<>();
  private RandomGenerator random;
  private SimulationScheduledExecutor scheduledExecutor;
  private ExecutorService executorService;
  private DeterministicExecutor deterministicExecutor;
  private ThreadFactory threadFactory;

  public Simulation(
      long seed,
      String base64ExecFingerprint,
      Duration stepDuration,
      SimulationInitializer initializer) {
    this.seed = seed;
    this.stepDuration = stepDuration.toMillis();
    this.base64ExecFingerprint = base64ExecFingerprint;
    this.initializer = initializer;
    this.clock = new SimulationClock(SimulationTime::onInstantNow);
  }

  public Simulation(long seed, String base64ExecFingerprint, SimulationInitializer initializer) {
    this(seed, base64ExecFingerprint, Duration.of(1, ChronoUnit.SECONDS), initializer);
  }

  public Simulation(long seed, SimulationInitializer initializer) {
    this(seed, null, initializer);
  }

  public Simulation(SimulationInitializer initializer) {
    this(new SecureRandom().nextLong(), null, Duration.of(1, ChronoUnit.SECONDS), initializer);
  }

  public ScheduledExecutorService scheduledExecutor() {
    return this.scheduledExecutor;
  }

  public ScheduledExecutorService newOffsetScheduledExecutor(Duration offset) {
    SimulationScheduledExecutor executor =
        new SimulationScheduledExecutor(Clock.offset(clock, offset), executorService);
    this.scheduledExecutors.add(executor);
    return executor;
  }

  public ExecutorService executorService() {
    return this.executorService;
  }

  public Executor executor() {
    return this.executorService;
  }

  // TODO is this useful it won't be wrapped in a virtual thread
  public DeterministicExecutor deterministicExecutor() {
    return this.deterministicExecutor;
  }

  public RandomGenerator random() {
    return random;
  }

  public RandomGenerator newRandom() {
    return new Random(seed);
  }

  public ThreadFactory threadFactory() {
    return this.threadFactory;
  }

  public Clock clock() {
    return clock;
  }

  public long getTimeStep() {
    return timeStep.get();
  }

  public void tick() throws TimeoutException {
    timeStep.addAndGet(stepDuration);
    scheduledExecutors.forEach(SimulationScheduledExecutor::tick);
    scheduledExecutor.tick();
    deterministicExecutor.tick();
  }

  // workaround execution order issues during startup of netty...
  public void runCurrentTasksInOrder() {
    deterministicExecutor.runInCurrentQueueOrder();
  }

  // TODO debatable if duration is useful parameter and shouldn't just be handled inside the
  // simStateChecker
  public void run() {
    LOGGER.info("Running simulation for seed: {}", seed);
    this.random = new Random(seed);
    this.deterministicExecutor = new DeterministicExecutor(random, base64ExecFingerprint);
    this.threadFactory = new SchedulableVirtualThreadFactory(deterministicExecutor);
    this.executorService = Executors.newThreadPerTaskExecutor(threadFactory);
    this.scheduledExecutor = new SimulationScheduledExecutor(clock, executorService);
    Instant wallTime = Instant.now();
    Future<SimulationStateChecker> init = executorService.submit(() -> initializer.init(this));
    try {
      while (!init.isDone()) {
        this.tick();
      }
      SimulationStateChecker simStateChecker = init.get();
      while (simStateChecker.advance()) {
        this.tick();
      }
    } catch (Exception e) {
      throw new SimulationException(seed, e);
    }
    Duration runDuration = wallTime.until(Instant.now());
    LOGGER.info(
        "Simulation seed {} ran for {}s simulating {} ticks",
        seed,
        runDuration.getSeconds(),
        timeStep);
  }

  // TODO do we still need this for the timeout?
  public Thread startSimulationThread() {
    return Thread.ofPlatform().name("simulation").start(this::run);
  }

  public long getSeed() {
    return seed;
  }

  public String getExecutionFingerprint() {
    return deterministicExecutor.getExecutionFingerprint();
  }
}
