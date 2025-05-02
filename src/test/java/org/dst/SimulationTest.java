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
import static org.awaitility.Awaitility.await;

import io.netty.channel.local.LocalAddress;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.SocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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

  private static final SocketAddress[] CLUSTER = {
    new LocalAddress("sim-one"),
    new LocalAddress("sim-two"),
    new LocalAddress("sim-three"),
    new LocalAddress("sim-four")
  };
  DeterministicExecutor deterministicExecutor = new DeterministicExecutor(new Random(1234L));

  @Disabled("Need to figure out the weird issues with the sim")
  @Test
  void run() {
    Simulation sim = new Simulation();

    // TODO the scheduler has to be constructed in the class that is running the sim??????
    TransportFactory transportFactory = new SimTransportFactory(deterministicExecutor);
    //        TransportFactory transportFactory = new SimTransportFactory(sim.executorService());
    StaticMesh node0 = new StaticMesh(transportFactory, getMessageHandler(0), 0, CLUSTER);
    StaticMesh node1 = new StaticMesh(transportFactory, getMessageHandler(1), 1, CLUSTER);
    StaticMesh node2 = new StaticMesh(transportFactory, getMessageHandler(2), 2, CLUSTER);
    StaticMesh node3 = new StaticMesh(transportFactory, getMessageHandler(3), 3, CLUSTER);
    node0.start();
    node1.start();
    node2.start();
    node3.start();

    await()
        .atMost(5, SECONDS)
        .until(
            () -> {
              sim.tick();
              node0.retryFailedConnections();
              node1.retryFailedConnections();
              node2.retryFailedConnections();
              node3.retryFailedConnections();
              return node0.checkClusterStatus().cardinality() == 3;
            });
    Instant start = Instant.now();
    sim.run(
        () -> {
          node0.broadcast("0");
          //            node1.broadcast("1");
          node2.broadcast("2");
          //            node3.broadcast("3");
          return start.plus(5, ChronoUnit.SECONDS).isAfter(Instant.now());
        });
  }

  private static Function<Serializable, List<? extends Serializable>> getMessageHandler(int i) {
    return (serializable -> {
      LOGGER.info("Node {} received message: {}", i, serializable.toString());
      return Collections.emptyList();
    });
  }
}
