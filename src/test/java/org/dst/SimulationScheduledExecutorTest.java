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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.lang.invoke.MethodHandles;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SimulationScheduledExecutorTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private AtomicLong step;
  private AtomicClock clock;
  private SimulationScheduledExecutor se;
  private StringBuffer sb;
  private ThreadFactory tf;
  private DeterministicExecutor de;

  @BeforeEach
  public void setup() {
    step = new AtomicLong();
    sb = new StringBuffer();
    clock = new AtomicClock(step);
    RandomGenerator random = new Random(654321L);
    de = new DeterministicExecutor(random);
    tf = new SchedulableVirtualThreadFactory(de);
    se = new SimulationScheduledExecutor(clock, Executors.newThreadPerTaskExecutor(tf));
  }

  public static synchronized void syncTest(StringBuffer sb) {
    sb.append("-");
  }

  @Test
  void testScheduledExecution() {
    ScheduledFuture<String> scheduledCallFuture =
        se.schedule(() -> sb.append(1).toString(), 2, TimeUnit.SECONDS);

    ScheduledFuture<?> scheduledRunFuture =
        se.schedule(
            () -> {
              sb.append(2);
            },
            2,
            TimeUnit.SECONDS);
    se.scheduleAtFixedRate(() -> sb.append(3), 1, 2, TimeUnit.SECONDS);
    se.scheduleWithFixedDelay(() -> sb.append(4), 4, 1, TimeUnit.SECONDS);
    // Runnable Lambda
    se.submit(
        () -> {
          sb.append(0);
        });
    // Callable Lambda
    Future<String> callFuture = se.submit(() -> sb.append(5).toString());

    // Runnable Future Lambda
    AtomicReference<String> ref = new AtomicReference<>();
    Future<AtomicReference<String>> runFuture =
        se.submit(
            () -> {
              ref.set(sb.append(6).toString());
            },
            ref);
    // Only 3 and 4 should repeat
    assertThat(de.queueSize()).isEqualTo(3);
    assertThat(se.getDelayedQueueSize()).isEqualTo(4);
    runSimulationStep();
    assertThat("06532313434334").startsWith(sb.toString());
    runSimulationStep();
    assertThat("06532313434334").startsWith(sb.toString());
    runSimulationStep();
    assertThat("06532313434334").startsWith(sb.toString());
    runSimulationStep();
    assertThat("06532313434334").startsWith(sb.toString());
    runSimulationStep();
    assertThat("06532313434334").startsWith(sb.toString());
    runSimulationStep();
    assertThat("06532313434334").startsWith(sb.toString());
    runSimulationStep();
    assertThat(se.getDelayedQueueSize()).isEqualTo(2);
    assertThat("06532313434334").startsWith(sb.toString());
  }

  public void runSimulationStep() {
    long time = clock.tick();
    se.tick();
    de.tick();
    LOGGER.info(
        "time:{} work:{} delay:{} out:{}",
        time,
        de.queueSize(),
        se.getDelayedQueueSize(),
        sb.toString());
  }
}
