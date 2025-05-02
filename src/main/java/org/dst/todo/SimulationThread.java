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
package org.dst.todo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/*
 Probably a better way using priority blocking queue so that only one sleeping thread is every checked
*/
public class SimulationThread {
  private static Clock clock = Clock.systemUTC();

  public static void setClock(Clock clock) {
    SimulationThread.clock = clock;
  }

  public static void sleep(Instant instant) {
    while (Instant.now(clock).isBefore(instant)) {
      Thread.yield();
    }
  }

  public static void sleep(Duration duration) {
    Instant instant = Instant.now(clock).plus(duration);
    sleep(instant);
  }

  public static void sleep(long ms) {
    sleep(Duration.of(ms, ChronoUnit.MILLIS));
  }
}
