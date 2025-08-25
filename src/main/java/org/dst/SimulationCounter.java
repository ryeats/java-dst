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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulationCounter implements SimulationStateChecker {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected final RandomGenerator rand;
  protected final int maxTickCount;
  private final Executor executor;
  protected int tickCount;
  protected List<Supplier<RunnableFuture<?>>> testTaskSuppliers = new ArrayList<>();
  protected List<Future<?>> runningTestTasks = new ArrayList<>();
  protected int startDelay;
  protected int quiescencePeriod;

  public SimulationCounter(RandomGenerator rand, Executor executor, int maxTickCount) {
    this.maxTickCount = maxTickCount;
    this.startDelay = 0;
    this.quiescencePeriod = 0;
    this.rand = rand;
    this.executor = executor;
  }

  public void schedule() {
    for (int driverCount = rand.nextInt(1, testTaskSuppliers.size() + 1);
        driverCount > 0;
        driverCount--) {
      Supplier<RunnableFuture<?>> driverSupplier =
          testTaskSuppliers.get(rand.nextInt(testTaskSuppliers.size()));
      RunnableFuture<?> driver = driverSupplier.get();
      executor.execute(driver);
      runningTestTasks.add(driver);
    }
    LOGGER.info("Scheduled {} tasks to driver the simulation", runningTestTasks.size());
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
    tickCount++;
    if (tickCount < startDelay) {
      return true;
    }
    if (tickCount <= maxTickCount - quiescencePeriod) {
      schedule();
    }

    return incompleteTestTasks() || tickCount <= maxTickCount;
  }

  public boolean incompleteTestTasks() {
    List<Future<?>> failedTasks =
        runningTestTasks.stream().filter((f) -> f.state().equals(Future.State.FAILED)).toList();
    if (!failedTasks.isEmpty()) {
      return false;
    }
    List<Future<?>> remainingTasks =
        runningTestTasks.stream().filter(Predicate.not(Future::isDone)).toList();
    return !remainingTasks.isEmpty();
  }

  public void setStartDelay(int startDelay) {
    this.startDelay = Math.min(startDelay, maxTickCount);
  }

  public void setQuiescencePeriod(int quiescencePeriod) {
    this.quiescencePeriod = quiescencePeriod;
  }
}
