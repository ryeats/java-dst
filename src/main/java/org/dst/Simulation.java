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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simulation {
  private static final ExecutorService executor = Executors.newSingleThreadExecutor();
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final AtomicLong timeStep = new AtomicLong();
  private final RandomGenerator random;
  private final Clock clock;
  private final SimulationScheduledExecutor scheduledExecutor;
  private final ExecutorService executorService;
  private final List<SimulationScheduledExecutor> scheduledExecutors = new ArrayList<>();
  private final DeterministicExecutor deterministicExecutor;
  private final ThreadFactory threadFactory;
  private final long seed;
  private final Duration stepTimeout = Duration.ofSeconds(3);

  public Simulation(long seed, Duration stepDuration) {
    clock = new AtomicClock(stepDuration, timeStep);
    this.random = new Random(seed);
    this.deterministicExecutor = new DeterministicExecutor(random);
    this.threadFactory = new SchedulableVirtualThreadFactory(deterministicExecutor);
    this.executorService = Executors.newThreadPerTaskExecutor(threadFactory);
    this.scheduledExecutor = new SimulationScheduledExecutor(clock, executorService);
    this.seed = seed;
  }

  public Simulation(long seed) {
    this(seed, Duration.ofSeconds(1));
  }

  public Simulation() {
    this(new SecureRandom().nextLong(), Duration.ofSeconds(1));
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

  public RandomGenerator random() {
    return random;
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

  public void tick() {
    timeStep.incrementAndGet();
    scheduledExecutors.forEach(SimulationScheduledExecutor::tick);
    scheduledExecutor.tick();
    deterministicExecutor.tick();
  }

  // workaround execution order issues during startup of netty...
  public void runCurrentTasksInOrder() {
    deterministicExecutor.runInCurrentQueueOrder();
  }

  public Duration run(SimulationStateChecker simStateChecker) {
    LOGGER.info("Running simulation for seed: {}", seed);
    Instant startTime = Instant.now();
    while (simStateChecker.advance()) {
      //      this.tick();
      Future<?> future = executor.submit(this::tick);
      try {
        future.get(stepTimeout.toNanos(), TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        LOGGER.error("Task timed out. Cancelling...", e);
        future.cancel(true); // Interrupt the thread
      } catch (InterruptedException | ExecutionException e) {
        LOGGER.error("Timed out with exception", e);
      }
    }
    Duration runDuration = startTime.until(Instant.now());
    LOGGER.info(
        "Simulation ran for {}s and {}ns for {} simulation ticks",
        runDuration.getSeconds(),
        runDuration.getNano(),
        timeStep.get());
    return runDuration;
  }
}
