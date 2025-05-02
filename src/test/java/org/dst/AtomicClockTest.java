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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AtomicClockTest {

  private AtomicClock clock;
  private AtomicLong step;
  private Instant startTime;

  @BeforeEach
  void setUp() {
    startTime = Instant.now();
    step = new AtomicLong();
    clock = new AtomicClock(startTime, Duration.ofSeconds(1), step);
  }

  @Test
  void instant() {
    assertThat(Instant.now(clock)).isEqualTo(startTime);
    long delta = step.incrementAndGet();
    assertThat(Instant.now(clock)).isEqualTo(startTime.plus(Duration.ofSeconds(delta)));
  }
}
