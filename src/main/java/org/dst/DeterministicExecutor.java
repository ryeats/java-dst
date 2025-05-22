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
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeterministicExecutor implements Executor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final RandomGenerator random;
  private static final ArrayList<Runnable> workQueue = new ArrayList<>();

  public DeterministicExecutor(RandomGenerator random) {
    this.random = random;
  }

  @Override
  public void execute(Runnable runnableToWrap) {
    workQueue.add(runnableToWrap);
  }

  public void tick() {
    LOGGER.debug("Executing {} tasks randomly in the work queue", workQueue.size());
    while (!workQueue.isEmpty()) {
      Collections.shuffle(workQueue, random); // New tasks can get added so we have to shuffle again
      Runnable task = workQueue.removeFirst();
      //      Runnable task = workQueue.remove(random.nextInt(1,workQueue.size()) - 1);
      task.run();
    }
  }

  public void runInCurrentQueueOrder() {
    LOGGER.debug("Executing {} tasks in the work queue", workQueue.size());
    while (!workQueue.isEmpty()) {
      Runnable task = workQueue.removeFirst();
      task.run();
    }
  }

  public int queueSize() {
    return workQueue.size();
  }
}
