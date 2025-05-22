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
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class SimulationClock extends Clock {

  private final Supplier<Instant> supplier;

  private final ZoneId zone;

  private AtomicLong step;

  public SimulationClock() {
    this(Instant.now(), Duration.ofSeconds(1), new AtomicLong());
  }

  public SimulationClock(final AtomicLong step) {
    this(Instant.now(), Duration.ofSeconds(1), step);
  }

  public SimulationClock(Duration duration, final AtomicLong step) {
    this(Instant.now(), duration, step);
  }

  public SimulationClock(Instant startTime, Duration duration, final AtomicLong step) {
    this(() -> startTime.plus(duration.multipliedBy(step.get())));
    this.step = step;
  }

  public SimulationClock(Supplier<Instant> supplier) {
    this(supplier, ZoneId.of("Z"));
    this.step = new AtomicLong();
  }

  public SimulationClock(Supplier<Instant> supplier, ZoneId zone) {
    this.supplier = supplier;
    this.zone = zone;
    this.step = new AtomicLong();
  }

  public long tick() {
    return step.incrementAndGet();
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId newZone) {
    return new SimulationClock(supplier, newZone);
  }

  @Override
  public Instant instant() {
    return supplier.get();
  }
}
