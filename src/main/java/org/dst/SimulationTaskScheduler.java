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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

public class SimulationTaskScheduler implements SimulationStateChecker {
  protected List<Stoppable> chaosAgents = new ArrayList<>();
  protected List<Supplier<RunnableFuture<?>>> testTaskSuppliers = new ArrayList<>();
  protected List<Future<?>> runningTestTasks = new ArrayList<>();

  public void schedule(
      RandomGenerator rand, ScheduledExecutorService scheduledExecutor, Duration duration) {
    scheduleTestTasks(rand, scheduledExecutor, duration);
    scheduleChaos(rand, scheduledExecutor, duration);
  }

  protected void scheduleTestTasks(
      RandomGenerator rand, ScheduledExecutorService scheduledExecutor, Duration duration) {
    for (int driverCount = rand.nextInt(testTaskSuppliers.size()); driverCount > 0; driverCount--) {
      RunnableFuture<?> driver =
          testTaskSuppliers.get(rand.nextInt(testTaskSuppliers.size())).get();
      // Uniform random distribution of test events over the duration
      scheduledExecutor.schedule(driver, rand.nextLong(duration.toNanos()), TimeUnit.NANOSECONDS);
      runningTestTasks.add(driver);
    }
  }

  protected void scheduleChaos(
      RandomGenerator rand, ScheduledExecutorService scheduledExecutor, Duration duration) {
    ArrayList<Stoppable> copy = new ArrayList<>(chaosAgents);
    for (int chaosCount = rand.nextInt(copy.size()); chaosCount > 0; chaosCount--) {
      Stoppable chaos = copy.remove(rand.nextInt(copy.size()));
      Duration chaosDuration = duration.minusNanos(rand.nextLong(duration.toNanos()));
      long delay = rand.nextLong(duration.minus(chaosDuration).toNanos());
      scheduledExecutor.schedule(chaos.start, delay, TimeUnit.NANOSECONDS);
      scheduledExecutor.schedule(
          chaos.stop, chaosDuration.plus(delay, ChronoUnit.NANOS).toNanos(), TimeUnit.NANOSECONDS);
    }
  }

  public void addChaosAgent(Runnable start, Runnable stop) {
    this.chaosAgents.add(new Stoppable(start, stop));
  }

  public void addSimulationTestTaskSupplier(Supplier<RunnableFuture<?>> testTaskSupplier) {
    this.testTaskSuppliers.add(testTaskSupplier);
  }

  @Override
  public boolean advance() {
    return incompleteTestTasks();
  }

  public boolean incompleteTestTasks()
  {
    runningTestTasks = runningTestTasks.stream().filter(Predicate.not(Future::isDone)).toList();
    return !runningTestTasks.isEmpty();
  }

  protected record Stoppable(Runnable start, Runnable stop) {}
}
