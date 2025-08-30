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

public class Simulation implements AutoCloseable {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final AtomicLong timeStep = SimulationTime.TIME;
  private final Clock clock;
  private final long seed;
  private final String base64ExecFingerprint;
  private final Duration stepDuration;
  private final List<SimulationScheduledExecutor> scheduledExecutors = new ArrayList<>();
  private final RandomGenerator random;
  private final SimulationScheduledExecutor scheduledExecutor;
  private final ExecutorService executorService;
  private final DeterministicExecutor deterministicExecutor;
  private final ThreadFactory threadFactory;

  public Simulation(Long seed, String base64ExecFingerprint, Duration stepDuration) {
    this.seed = seed == null ? new SecureRandom().nextLong() : seed;
    this.stepDuration = stepDuration;
    this.base64ExecFingerprint = base64ExecFingerprint;
    this.clock = new SimulationClock(SimulationTime::onInstantNow);
    this.random = new Random(this.seed);
    this.deterministicExecutor = new DeterministicExecutor(random, base64ExecFingerprint);
    this.threadFactory = new SchedulableVirtualThreadFactory(deterministicExecutor);
    this.executorService = Executors.newThreadPerTaskExecutor(threadFactory);
    this.scheduledExecutor = new SimulationScheduledExecutor(clock, executorService);
  }

  public Simulation(Long seed, String base64ExecFingerprint) {
    this(seed, base64ExecFingerprint, Duration.of(1, ChronoUnit.SECONDS));
  }

  public Simulation(Long seed) {
    this(seed, null);
  }

  public Simulation() {
    this(null, null, Duration.of(1, ChronoUnit.SECONDS));
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
    timeStep.addAndGet(stepDuration.toMillis());
    scheduledExecutors.forEach(SimulationScheduledExecutor::tick);
    scheduledExecutor.tick();
    deterministicExecutor.tick();
  }

  // workaround execution order issues during startup of netty...
  public void runCurrentTasksInOrder() {
    deterministicExecutor.runInCurrentQueueOrder();
  }

  public List<Future<?>> run(List<Runnable> runnableList) throws TimeoutException {
    List<Future<?>> futures = new ArrayList<>();
    for (Runnable runnable : runnableList) {
      futures.add(executorService.submit(runnable));
    }

    while (futures.stream().anyMatch(f -> !f.isDone())) {
      this.tick();
    }
    return futures;
  }

  // TODO debatable if duration is useful parameter and shouldn't just be handled inside the
  // simStateChecker
  public void run(SimulationInitializer initializer) {
    LOGGER.info("Running simulation for seed: {}", seed);
    Instant wallTime = Instant.now();
    Future<SimulationStateChecker> init = executorService.submit(() -> initializer.init(this));
    try {
      while (!init.isDone()) {
        this.tick();
      }
      SimulationStateChecker simStateChecker = init.get();
      do {
        this.tick();
      } while (simStateChecker.advance());
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
  public Thread startSimulationThread(SimulationInitializer initializer) {
    return Thread.ofPlatform()
        .name("simulation")
        .start(
            () -> {
              this.run(initializer);
            });
  }

  public long getSeed() {
    return seed;
  }

  public String getExecutionFingerprint() {
    return deterministicExecutor.getExecutionFingerprint();
  }

  public Duration getStepDuration() {
    return stepDuration;
  }

  @Override
  public void close() throws Exception {
    deterministicExecutor.close();
  }
}
