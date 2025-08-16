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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulationScheduler implements SimulationStateChecker {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected List<Stoppable> chaosAgents = new ArrayList<>();
  protected List<Supplier<RunnableFuture<?>>> testTaskSuppliers = new ArrayList<>();
  protected List<Future<?>> runningTestTasks = new ArrayList<>();
  protected Duration startDelay = null;
  protected Duration quiescencePeriod = null;

  public void schedule(
      RandomGenerator rand, ScheduledExecutorService scheduledExecutor, Duration duration) {
    if (startDelay == null) {
      startDelay = duration.dividedBy(10);
    }
    if (quiescencePeriod == null) {
      quiescencePeriod = duration.dividedBy(10);
    }
    Duration scheuleDuration = duration.minus(quiescencePeriod).minus(startDelay);
    scheduleTestTasks(rand, scheduledExecutor, scheuleDuration);
    scheduleChaos(rand, scheduledExecutor, duration.minus(scheuleDuration));
  }

  protected void scheduleTestTasks(
      RandomGenerator rand, ScheduledExecutorService scheduledExecutor, Duration duration) {
    //    for (int driverCount = testTaskSuppliers.size(); driverCount > 0; driverCount--) {
    for (int driverCount = rand.nextInt(testTaskSuppliers.size()); driverCount > 0; driverCount--) {
      Supplier<RunnableFuture<?>> driverSupplier =
          testTaskSuppliers.get(rand.nextInt(testTaskSuppliers.size()));
      // TODO support rate and distribution type parameters
      for (long i = rand.nextLong(duration.toMillis()); i > 0; i--) {
        RunnableFuture<?> driver = driverSupplier.get();
        scheduledExecutor.schedule(
            driver, startDelay.toNanos() + rand.nextLong(duration.toNanos()), TimeUnit.NANOSECONDS);
        runningTestTasks.add(driver);
      }
    }
    LOGGER.info("Scheduled {} tasks to driver the simulation", runningTestTasks.size());
  }

  protected void scheduleChaos(
      RandomGenerator rand, ScheduledExecutorService scheduledExecutor, Duration duration) {
    ArrayList<Stoppable> copy = new ArrayList<>(chaosAgents);
    for (int chaosCount = rand.nextInt(copy.size()); chaosCount > 0; chaosCount--) {
      Stoppable chaos = copy.remove(rand.nextInt(copy.size()));
      Duration chaosDuration = duration.minusNanos(rand.nextLong(duration.toNanos()));
      long delay = rand.nextLong(duration.minus(chaosDuration).toNanos());
      scheduledExecutor.schedule(chaos.start, startDelay.toNanos() + delay, TimeUnit.NANOSECONDS);
      scheduledExecutor.schedule(
          chaos.stop,
          chaosDuration.plus(delay, ChronoUnit.NANOS).plus(startDelay).toNanos(),
          TimeUnit.NANOSECONDS);
    }
  }

  public void addChaosAgent(Runnable start, Runnable stop) {
    this.chaosAgents.add(new Stoppable(start, stop));
  }

  public void addChaosAgent(Runnable toggle) {
    this.chaosAgents.add(new Stoppable(toggle, toggle));
  }

  public void addSimulationTestTaskSupplier(Supplier<RunnableFuture<?>> testTaskSupplier) {
    this.testTaskSuppliers.add(testTaskSupplier);
  }

  public void addSimulationTestTaskSupplier(Callable<?> testTaskSupplier) {
    this.testTaskSuppliers.add(() -> new FutureTask<>(testTaskSupplier));
  }

  public void addSimulationTestTaskSupplier(Runnable testTaskSupplier) {
    this.testTaskSuppliers.add(() -> new FutureTask<>(testTaskSupplier, true));
  }

  @Override
  public boolean advance() {
    return incompleteTestTasks();
  }

  public boolean incompleteTestTasks() {
    runningTestTasks = runningTestTasks.stream().filter(Predicate.not(Future::isDone)).toList();
    return !runningTestTasks.isEmpty();
  }

  protected record Stoppable(Runnable start, Runnable stop) {}

  public void setStartDelay(Duration startDelay) {
    this.startDelay = startDelay;
  }

  public void setQuiescencePeriod(Duration quiescencePeriod) {
    this.quiescencePeriod = quiescencePeriod;
  }
}
