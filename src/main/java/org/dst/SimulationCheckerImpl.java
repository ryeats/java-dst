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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class SimulationCheckerImpl implements SimulationStateChecker {
  protected List<Future<?>> runningTestTasks;
  protected Clock clock;
  protected Instant end;

  public SimulationCheckerImpl(Clock clock, Duration duration, List<Future<?>> runningTestTasks) {
    this.clock = clock;
    this.end = clock.instant().plus(duration);
    this.runningTestTasks = runningTestTasks;
  }

  @Override
  public boolean advance() {
    return incompleteTestTasks() && clock.instant().isBefore(end);
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
}
