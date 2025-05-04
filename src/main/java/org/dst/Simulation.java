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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

  private final AtomicLong timeStep = new AtomicLong();
  private final RandomGenerator random;
  private final Clock clock;
  private final SimulationScheduledExecutor scheduledExecutor;
  private final ExecutorService executorService;
  private final List<SimulationScheduledExecutor> scheduledExecutors = new ArrayList<>();
  private final DeterministicExecutor deterministicExecutor;
  private final ThreadFactory threadFactory;
  private final long seed;

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

  public Executor executor() {
    return this.deterministicExecutor;
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

  public void tick() throws TimeoutException {
    timeStep.incrementAndGet();
    scheduledExecutors.forEach(SimulationScheduledExecutor::tick);
    scheduledExecutor.tick();
    deterministicExecutor.tick();
  }

  // workaround execution order issues during startup of netty...
  public void runCurrentTasksInOrder() {
    deterministicExecutor.runInCurrentQueueOrder();
  }

  // TODO Add run with simulation duration in sim ticks or real time
  // TODO change simulation return value to be a status about the end state
  public Duration run(SimulationStateChecker simStateChecker) {
    LOGGER.info("Running simulation for seed: {}", seed);
    Instant startTime = Instant.now();
    try {
      while (simStateChecker.advance()) {
        this.tick();
      }
    } catch (Exception e) {
      throw new SimulationException(seed, e);
    }
    Duration runDuration = startTime.until(Instant.now());
    LOGGER.info(
        "Simulation seed {} ran for {}s with total simulation time of {}hrs",
        seed,
        runDuration.getSeconds(),
        String.format("%.2f", timeStep.get() / 60.0 / 60.0));
    return runDuration;
  }

  public long getTickTimeout() {
    return deterministicExecutor.getTickTimeout();
  }

  public void setTickTimeout(long tickTimeout) {
    this.deterministicExecutor.setTickTimeout(tickTimeout);
  }
}
