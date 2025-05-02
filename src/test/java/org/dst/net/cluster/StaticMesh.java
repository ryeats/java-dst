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

import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.dst.net.Connection;
import org.dst.net.TransportClient;
import org.dst.net.TransportFactory;
import org.dst.net.TransportServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticMesh implements Cluster {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final TransportFactory transportFactory;
  private final Function<Serializable, List<? extends Serializable>> handleMessage;

  public int getNodeIndex() {
    return nodeIndex;
  }

  private final int nodeIndex;
  private final List<SocketAddress> custerAddresses;
  private final Map<Integer, Connection> connectionMap = new ConcurrentHashMap<>();
  private TransportClient transportClient;
  private TransportServer transportServer;

  public StaticMesh(
      TransportFactory transportFactory,
      Function<Serializable, List<? extends Serializable>> handleMessage,
      int nodeIndex,
      SocketAddress... cluster) {
    this.nodeIndex = nodeIndex;
    this.custerAddresses = Arrays.asList(cluster);
    this.transportFactory = transportFactory;
    this.handleMessage = handleMessage;
  }

  @Override
  public void start() {
    this.transportClient =
        transportFactory.getTransportClient(nodeIndex, handleMessage, this::handleConnection);
    this.transportServer =
        transportFactory.getTransportServer(nodeIndex, handleMessage, this::handleConnection);
    transportServer.listen(custerAddresses.get(nodeIndex)); // should this be 0.0.0.0:port?
    for (int i = 0; i < custerAddresses.size(); i++) {
      if (i == nodeIndex) {
        continue;
      }
      try {
        if (null == connectionMap.get(i) || !connectionMap.get(i).isActive()) {
          transportClient.connect(custerAddresses.get(i));
        }
      } catch (Exception e) {
        LOGGER.error("Error connecting to node {}", i, e);
      }
    }
  }

  @Override
  public void stop() {
    connectionMap.clear();
    try {
      transportServer.close();
    } catch (IOException e) {
      LOGGER.error("Error shutting down server", e);
    }
    try {
      transportClient.close();
    } catch (IOException e) {
      LOGGER.error("Error shutting down client connections", e);
    }
    for (Connection connection : connectionMap.values()) {
      try {
        connection.close();
      } catch (IOException e) {
        LOGGER.error("Error shutting down client connections", e);
      }
    }
  }

  @Override
  public BitSet checkClusterStatus() {
    BitSet clusterStatus = new BitSet(custerAddresses.size());
    clusterStatus.set(nodeIndex);
    for (int i = custerAddresses.size() - 1; i >= 0; i--) {
      if (connectionMap.size() > i
          && null != connectionMap.get(i)
          && connectionMap.get(i).isActive()) {
        clusterStatus.set(i);
      }
    }
    return clusterStatus;
  }

  public void retryFailedConnections() {
    BitSet clusterStatus = checkClusterStatus();
    for (int i = custerAddresses.size() - 1; i >= 0; i--) {
      if (clusterStatus.get(i) || i == nodeIndex) {
        continue;
      }
      try {
        transportClient.connect(custerAddresses.get(i));
      } catch (Exception e) {
        LOGGER.error("Error connecting to node {}", i, e);
      }
    }
  }

  @Override
  public void broadcast(Serializable message) {
    connectionMap
        .values()
        .forEach(
            c -> {
              LOGGER.trace(
                  "Node {} broadcasting {} message to {} connection isActive={}",
                  nodeIndex,
                  message.getClass().getSimpleName(),
                  c.getId(),
                  c != null && c.isActive());
              if (c.isActive()) {
                c.send(message);
              }
            });
  }

  @Override
  public void send(int id, Serializable message) {
    Connection connection = connectionMap.get(id);
    LOGGER.trace(
        "Node {} sending {} message to {} connection isActive={}",
        nodeIndex,
        message.getClass().getSimpleName(),
        id,
        connection != null && connection.isActive());
    if (connection != null && connection.isActive()) {
      connection.send(message);
    }
  }

  private void handleConnection(Connection connection) {
    LOGGER.trace("Node {} recieved incoming connection from {}", nodeIndex, connection.getId());
    this.connectionMap.put(connection.getId(), connection);
  }
}
