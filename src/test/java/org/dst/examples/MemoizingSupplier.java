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
package org.dst.examples;

/*
 * (c) Copyright 2022 James Baker. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.dst.Simulation;
import org.dst.SimulationCounter;

class MemoizingSupplier<T> implements Supplier<T> {
  private final Lock lock;
  private final Supplier<T> delegate;
  private volatile boolean initialized = false;
  private T value;

  MemoizingSupplier(Supplier<T> delegate) {
    this(new ReentrantLock(), delegate);
  }

  MemoizingSupplier(Lock lock, Supplier<T> delegate) {
    this.lock = lock;
    this.delegate = delegate;
  }

  @Override
  public T get() {
    if (!initialized) {
      lock.lock();
      try {
        if (!initialized) {
          T result = delegate.get();
          value = result;
          initialized = true;
          return result;
        }
      } finally {
        lock.unlock();
      }
    }
    return value;
  }

  public static void main(String... args) {

    Simulation sim = new Simulation();
    sim.run(
        (s) -> {
          //              Duration simDuration = Duration.of(100, ChronoUnit.SECONDS);
          //              SimulationScheduler tasker = new SimulationScheduler();
          SimulationCounter tasker = new SimulationCounter(s.random(), s.executor(), 1);
          AtomicInteger countCalls = new AtomicInteger();
          MemoizingSupplier<Integer> supplier =
              new MemoizingSupplier<>(new YieldingLock(), countCalls::incrementAndGet);
          Runnable test =
              () -> {
                supplier.get();
                System.out.println(supplier.get());
              };
          tasker.addSimulationTestTaskSupplier(test);
          tasker.addSimulationTestTaskSupplier(test);
          return tasker;
        });

    //    sim.startSimulationThread();
  }

  private static final class YieldingLock extends ReentrantLock {
    @Override
    public void lock() {
      Thread.yield();
      super.lock();
    }
  }
}
