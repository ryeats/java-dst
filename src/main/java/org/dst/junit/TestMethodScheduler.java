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
package org.dst.junit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.dst.Simulation;

public class TestMethodScheduler implements SimulationTestScheduler {

  private final Duration duration;
  private final RandomGenerator rand;
  private final ScheduledExecutorService scheduledExecutor;
  private final List<Future<?>> testTasks = new ArrayList<>();

  public TestMethodScheduler(Simulation sim, Duration duration) {
    this.scheduledExecutor = sim.scheduledExecutor();
    this.rand = sim.random();
    this.duration = duration;
  }

  @Override
  public List<Future<?>> schedule(
      Simulation sim,
      List<Supplier<RunnableFuture<?>>> testGenerators,
      List<Runnable> chaosAgents) {
    scheduleTestTasks(testGenerators, sim.getStepDuration());
    scheduleChaos(chaosAgents);
    return testTasks;
  }

  protected void scheduleTestTasks(
      List<Supplier<RunnableFuture<?>>> testTaskSuppliers, Duration stepDuration) {
    if (testTaskSuppliers.isEmpty()) {
      return;
    }
    for (int i = 0; i < duration.toMillis(); i += stepDuration.toMillis()) {
      for (int driverCount = rand.nextInt(1, testTaskSuppliers.size() + 1);
          driverCount > 0;
          driverCount--) {
        Supplier<RunnableFuture<?>> driverSupplier =
            testTaskSuppliers.get(rand.nextInt(testTaskSuppliers.size()));
        RunnableFuture<?> driver = driverSupplier.get();
        scheduledExecutor.schedule(driver, i, TimeUnit.MILLISECONDS);
        testTasks.add(driver);
      }
    }
  }

  protected void scheduleChaos(List<Runnable> chaosAgents) {}
}
