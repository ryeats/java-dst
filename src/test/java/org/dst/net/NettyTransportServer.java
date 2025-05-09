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
package org.dst.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.io.Closeable;
import java.io.Serializable;
import java.net.SocketAddress;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class NettyTransportServer implements TransportServer, Closeable {
  private final int id;
  private final Function<Serializable, List<? extends Serializable>> messageHandler;
  private final Consumer<Connection> connectionHandler;
  private ServerBootstrap server;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;

  public NettyTransportServer(
      int id,
      Function<Serializable, List<? extends Serializable>> messageHandler,
      Consumer<Connection> connectionHandler) {
    this.messageHandler = messageHandler;
    this.connectionHandler = connectionHandler;
    this.id = id;
  }

  @Override
  public void listen(SocketAddress address) {
    bossGroup = new NioEventLoopGroup();
    workerGroup = new NioEventLoopGroup();
    server =
        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(
                new ChannelInitializer<SocketChannel>() {
                  @Override
                  public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline()
                        .addLast(
                            new ObjectEncoder(),
                            new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                            new NettyHandler(id, messageHandler, connectionHandler));
                  }
                });
    server.bind(address);
  }

  @Override
  public void close() {
    server = null;
    if (bossGroup != null) {
      bossGroup.shutdownGracefully();
    }
    if (workerGroup != null) {
      workerGroup.shutdownGracefully();
    }
  }
}
