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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.netty.channel.local.LocalAddress;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.dst.net.SimTransportFactory;
import org.dst.net.TransportFactory;
import org.dst.net.cluster.StaticMesh;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SimulationTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final SocketAddress[] CLUSTER = {
    new LocalAddress("sim-zero"),
    new LocalAddress("sim-one"),
    new LocalAddress("sim-two"),
    new LocalAddress("sim-three")
  };

  @Test
  void runNetworkSimulation() {
    Simulation sim = new Simulation();
    TransportFactory transportFactory = new SimTransportFactory(sim.threadFactory());
    List<AtomicLong> msgCounters = new ArrayList<>();
    msgCounters.add(new AtomicLong());
    msgCounters.add(new AtomicLong());
    msgCounters.add(new AtomicLong());
    msgCounters.add(new AtomicLong());
    StaticMesh node0 =
        new StaticMesh(transportFactory, getMessageHandler(0, msgCounters.get(0)), 0, CLUSTER);
    StaticMesh node1 =
        new StaticMesh(transportFactory, getMessageHandler(1, msgCounters.get(1)), 1, CLUSTER);
    StaticMesh node2 =
        new StaticMesh(transportFactory, getMessageHandler(2, msgCounters.get(2)), 2, CLUSTER);
    StaticMesh node3 =
        new StaticMesh(transportFactory, getMessageHandler(3, msgCounters.get(3)), 3, CLUSTER);
    node0.start();
    node1.start();
    node2.start();
    node3.start();

    await()
        .atMost(5, SECONDS)
        .until(
            () -> {
              // this is a workaround because netty deadlocks if we execute in random order.
              sim.runCurrentTasksInOrder();
              node0.retryFailedConnections();
              node1.retryFailedConnections();
              node2.retryFailedConnections();
              node3.retryFailedConnections();
              return node0.checkClusterStatus().cardinality() == 3;
            });
    sim.scheduledExecutor().scheduleAtFixedRate(() -> node1.broadcast("1"), 1, 2, SECONDS);
    sim.scheduledExecutor().scheduleAtFixedRate(() -> node3.send(0, "3"), 2, 3, SECONDS);
    sim.run(
        () -> {
          node0.broadcast("0");
          return msgCounters.get(0).get() < 1000;
        },
        Duration.of(5, ChronoUnit.SECONDS));

    LOGGER.info("sim-zero msg count: {}", msgCounters.get(0));
    LOGGER.info("sim-one msg count: {}", msgCounters.get(1));
    LOGGER.info("sim-two msg count: {}", msgCounters.get(2));
    LOGGER.info("sim-three msg count: {}", msgCounters.get(3));
  }

  private static Function<Serializable, List<? extends Serializable>> getMessageHandler(
      int i, AtomicLong msgCounter) {
    return (serializable -> {
      LOGGER.debug("Node {} received message: {}", i, serializable.toString());
      msgCounter.incrementAndGet();
      return Collections.emptyList();
    });
  }

  @Test
  public void runSimulationHangTest() {
    assertThatThrownBy(
            () -> {
              Simulation sim = new Simulation();
              sim.scheduledExecutor()
                  .schedule(
                      () -> {
                        while (true)
                          ;
                      },
                      10,
                      SECONDS);
              AtomicLong counter = new AtomicLong();
              sim.run(
                  () -> {
                    counter.incrementAndGet();
                    return true;
                  },
                  15L);
            })
        .isInstanceOf(SimulationException.class);
  }
}
