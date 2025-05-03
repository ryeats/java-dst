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
package org.dst.net.cluster;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.dst.net.NettyTransportFactory;
import org.dst.net.TransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterMain {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final SocketAddress[] CLUSTER = {
    new InetSocketAddress("localhost", 2001),
    new InetSocketAddress("localhost", 2002),
    new InetSocketAddress("localhost", 2003),
    new InetSocketAddress("localhost", 2004)
  };

  public static void main(String... args) throws InterruptedException {
    TransportFactory transportFactory = new NettyTransportFactory();
    StaticMesh node0 = new StaticMesh(transportFactory, getMessageHandler(0), 0, CLUSTER);
    StaticMesh node1 = new StaticMesh(transportFactory, getMessageHandler(1), 1, CLUSTER);
    StaticMesh node2 = new StaticMesh(transportFactory, getMessageHandler(2), 2, CLUSTER);
    StaticMesh node3 = new StaticMesh(transportFactory, getMessageHandler(3), 3, CLUSTER);
    node0.start();
    node1.start();
    node2.start();
    node3.start();
    Thread.sleep(1000);
    node0.retryFailedConnections();
    node1.retryFailedConnections();
    node2.retryFailedConnections();
    node3.retryFailedConnections();
    node0.broadcast("Hi");
    node1.broadcast("Hello");
    node2.broadcast("Howdy");
    node3.broadcast("World");
  }

  private static Function<Serializable, List<? extends Serializable>> getMessageHandler(int i) {
    return (serializable -> {
      LOGGER.info("Node {} received message: {}", i, serializable.toString());
      return Collections.emptyList();
    });
  }
}
