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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.dst.net.SimTransportFactory;
import org.dst.net.TransportFactory;
import org.dst.net.cluster.StaticMesh;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SimulationTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final List<AtomicLong> MSG_COUNTERS = new ArrayList<>();
  private static final List<StaticMesh> MESH_NODES = new ArrayList<>();
  private static final SocketAddress[] CLUSTER = {
    new LocalAddress("sim-zero"),
    new LocalAddress("sim-one"),
    new LocalAddress("sim-two"),
    new LocalAddress("sim-three")
  };

  @Test
  void runNetworkSimulation() {
    MSG_COUNTERS.add(new AtomicLong());
    MSG_COUNTERS.add(new AtomicLong());
    MSG_COUNTERS.add(new AtomicLong());
    MSG_COUNTERS.add(new AtomicLong());
    Simulation simulation =
        new Simulation(
            (sim) -> {
              TransportFactory transportFactory = new SimTransportFactory(sim.threadFactory());
              MESH_NODES.add(
                  new StaticMesh(
                      transportFactory, getMessageHandler(0, MSG_COUNTERS.get(0)), 0, CLUSTER));
              MESH_NODES.add(
                  new StaticMesh(
                      transportFactory, getMessageHandler(1, MSG_COUNTERS.get(1)), 1, CLUSTER));
              MESH_NODES.add(
                  new StaticMesh(
                      transportFactory, getMessageHandler(2, MSG_COUNTERS.get(2)), 2, CLUSTER));
              MESH_NODES.add(
                  new StaticMesh(
                      transportFactory, getMessageHandler(3, MSG_COUNTERS.get(3)), 3, CLUSTER));
              MESH_NODES.forEach(StaticMesh::start);

              await()
                  .atMost(5, SECONDS)
                  .until(
                      () -> {
                        MESH_NODES.forEach(StaticMesh::retryFailedConnections);
                        return MESH_NODES.get(0).checkClusterStatus().cardinality() == 3;
                      });
              sim.scheduledExecutor()
                  .scheduleAtFixedRate(() -> MESH_NODES.get(1).broadcast("1"), 1, 2, SECONDS);
              sim.scheduledExecutor()
                  .scheduleAtFixedRate(() -> MESH_NODES.get(3).send(0, "3"), 2, 3, SECONDS);
              return () -> {
                MESH_NODES.get(0).broadcast("0");
                return MSG_COUNTERS.getFirst().get() < 1000;
              };
            });
    simulation.run();

    LOGGER.info("sim-zero msg count: {}", MSG_COUNTERS.get(0));
    LOGGER.info("sim-one msg count: {}", MSG_COUNTERS.get(1));
    LOGGER.info("sim-two msg count: {}", MSG_COUNTERS.get(2));
    LOGGER.info("sim-three msg count: {}", MSG_COUNTERS.get(3));
  }

  private static Function<Serializable, List<? extends Serializable>> getMessageHandler(
      int i, AtomicLong msgCounter) {
    return (serializable -> {
      LOGGER.debug("Node {} received message: {}", i, serializable.toString());
      msgCounter.incrementAndGet();
      return Collections.emptyList();
    });
  }

  // TODO need to try some different ways of running the simulation first then revisit making sure
  // it doesn't hang
  @Disabled
  @Test
  public void runSimulationHangTest() {
    assertThatThrownBy(
            () -> {
              Simulation simulation =
                  new Simulation(
                      (sim) -> {
                        sim.scheduledExecutor()
                            .schedule(
                                () -> {
                                  while (true)
                                    ;
                                },
                                1,
                                SECONDS);
                        return () -> true;
                      });

              Thread thread = simulation.startSimulationThread();
              thread.join(10);
            })
        .isInstanceOf(InterruptedException.class);
  }
}
