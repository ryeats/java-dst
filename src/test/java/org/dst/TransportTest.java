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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

import io.netty.channel.local.LocalAddress;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.core.ConditionFactory;
import org.dst.net.Connection;
import org.dst.net.NettyTransportFactory;
import org.dst.net.SimTransportFactory;
import org.dst.net.TransportClient;
import org.dst.net.TransportFactory;
import org.dst.net.TransportServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransportTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static List<Connection> connectionList = new CopyOnWriteArrayList<>();
  private static List<Serializable> messageList = new CopyOnWriteArrayList<>();

  private static void handleConnection(Connection connection) {
    connectionList.add(connection);
    LOGGER.info("Currently connection count: {}", connectionList.size());
  }

  private static List<? extends Serializable> handleMessage(Serializable serializable) {
    messageList.add(serializable);
    LOGGER.info(
        "Received message of type: {} contents: {}",
        serializable.getClass().getName(),
        serializable);
    if (serializable instanceof String) {
      if (((String) serializable).length() < 20) {
        return List.of("Hello " + serializable);
      }
    }
    return Collections.emptyList();
  }

  @BeforeEach
  public void init() {
    connectionList = new CopyOnWriteArrayList<>();
    messageList = new CopyOnWriteArrayList<>();
  }

  @Test
  void transportTest() throws InterruptedException {
    long seed = new SecureRandom().nextLong();
    TransportFactory transportFactory = new NettyTransportFactory();
    TransportServer server0 =
        transportFactory.getTransportServer(
            0, TransportTest::handleMessage, TransportTest::handleConnection);
    TransportServer server1 =
        transportFactory.getTransportServer(
            1, TransportTest::handleMessage, TransportTest::handleConnection);
    TransportServer server2 =
        transportFactory.getTransportServer(
            2, TransportTest::handleMessage, TransportTest::handleConnection);
    TransportServer server3 =
        transportFactory.getTransportServer(
            3, TransportTest::handleMessage, TransportTest::handleConnection);
    server0.listen(new InetSocketAddress(1000));
    server1.listen(new InetSocketAddress(1001));
    server2.listen(new InetSocketAddress(1002));
    TransportClient client0 =
        transportFactory.getTransportClient(
            0, TransportTest::handleMessage, TransportTest::handleConnection);
    client0.connect(new InetSocketAddress(1000));
    ConditionFactory timeout = await().atMost(4, SECONDS);
    timeout.untilAsserted(() -> assertThat(connectionList.size()).isEqualTo(2));
    connectionList.get(0).send("test1");
    timeout.untilAsserted(() -> assertThat(messageList.size()).isEqualTo(4));
  }

  @Disabled("Disabling sim transport test since i need to look into why it is flaky ")
  @Test
  void transportSimTest() throws InterruptedException {
    long seed = new SecureRandom().nextLong();
    DeterministicExecutor deterministicExecutor = new DeterministicExecutor(new Random(seed));
    TransportFactory transportFactory = new SimTransportFactory(deterministicExecutor);
    TransportServer server0 =
        transportFactory.getTransportServer(
            0, TransportTest::handleMessage, TransportTest::handleConnection);
    TransportServer server1 =
        transportFactory.getTransportServer(
            1, TransportTest::handleMessage, TransportTest::handleConnection);
    TransportServer server2 =
        transportFactory.getTransportServer(
            2, TransportTest::handleMessage, TransportTest::handleConnection);
    TransportServer server3 =
        transportFactory.getTransportServer(
            3, TransportTest::handleMessage, TransportTest::handleConnection);
    server0.listen(new LocalAddress("transport-zero"));
    server1.listen(new LocalAddress("transport-one"));
    server2.listen(new LocalAddress("transport-two"));
    TransportClient client0 =
        transportFactory.getTransportClient(
            0, TransportTest::handleMessage, TransportTest::handleConnection);
    client0.connect(new LocalAddress("transport-zero"));
    deterministicExecutor.runInCurrentQueueOrder();
    ConditionFactory timeout = await().atMost(4, SECONDS);
    timeout.untilAsserted(
        () -> {
          deterministicExecutor.tick();
          assertThat(connectionList.size()).isEqualTo(2);
        });
    connectionList.get(0).send("test1");

    timeout.untilAsserted(
        () -> {
          deterministicExecutor.tick();
          assertThat(messageList.size()).isEqualTo(4);
        });
  }
}
