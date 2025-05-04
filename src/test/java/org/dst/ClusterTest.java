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
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.netty.channel.local.LocalAddress;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.SocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.awaitility.core.ConditionFactory;
import org.dst.net.SimTransportFactory;
import org.dst.net.TransportFactory;
import org.dst.net.cluster.StaticMesh;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterTest {
  //    private static final SocketAddress[] CLUSTER = {
  //      new InetSocketAddress("localhost", 2001),
  //      new InetSocketAddress("localhost", 2002),
  //      new InetSocketAddress("localhost", 2003),
  //      new InetSocketAddress("localhost", 2004)
  //    };
  private static final SocketAddress[] CLUSTER = {
    new LocalAddress("cluster-one"),
    new LocalAddress("cluster-two"),
    new LocalAddress("cluster-three"),
    new LocalAddress("cluster-our")
  };

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final List<Serializable> messages = new ArrayList<>();

  @Test
  void testStaticMesh() throws TimeoutException {
    long seed = new SecureRandom().nextLong();
    DeterministicExecutor deterministicExecutor = new DeterministicExecutor(new Random(seed));
    //        TransportFactory transportFactory = new NettyTransportFactory();
    TransportFactory transportFactory =
        new SimTransportFactory(new SchedulableVirtualThreadFactory(deterministicExecutor));
    StaticMesh node0 = new StaticMesh(transportFactory, getMessageHandler(0), 0, CLUSTER);
    StaticMesh node1 = new StaticMesh(transportFactory, getMessageHandler(1), 1, CLUSTER);
    StaticMesh node2 = new StaticMesh(transportFactory, getMessageHandler(2), 2, CLUSTER);
    StaticMesh node3 = new StaticMesh(transportFactory, getMessageHandler(3), 3, CLUSTER);
    node0.start();
    node1.start();
    node2.start();
    node3.start();
    await()
        .atMost(10, SECONDS)
        .until(
            () -> {
              deterministicExecutor.tick();
              node0.retryFailedConnections();
              node1.retryFailedConnections();
              node2.retryFailedConnections();
              node3.retryFailedConnections();
              return node0.checkClusterStatus().cardinality() == 3;
            });
    deterministicExecutor.tick();
    ConditionFactory timeout = await().atMost(1, SECONDS);
    deterministicExecutor.tick();
    node0.broadcast("blah");
    deterministicExecutor.tick();
    timeout.untilAsserted(() -> assertThat(messages).hasSize(3));
    deterministicExecutor.tick();
    node1.broadcast("blah2");
    deterministicExecutor.tick();
    timeout.untilAsserted(() -> assertThat(messages).hasSize(6));
    deterministicExecutor.tick();
    node2.broadcast("more blah");
    deterministicExecutor.tick();
    timeout.untilAsserted(() -> assertThat(messages).hasSize(9));
    deterministicExecutor.tick();
    node3.broadcast("too many blah");
    deterministicExecutor.tick();
    timeout.untilAsserted(() -> assertThat(messages).hasSize(12));
  }

  private static Function<Serializable, List<? extends Serializable>> getMessageHandler(int i) {
    return (serializable -> {
      LOGGER.info("Node {} received message: {}", i, serializable.toString());
      messages.add(serializable);
      return Collections.emptyList();
    });
  }
}
