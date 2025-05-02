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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.io.Serializable;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;
import org.dst.SchedulableVirtualThreadFactory;

public class SimTransportClient implements TransportClient {
  private final Bootstrap client = new Bootstrap();
  EventLoopGroup group;

  public SimTransportClient(
      int id,
      Function<Serializable, List<? extends Serializable>> handleMessage,
      Consumer<Connection> connectionHandler,
      Executor scheduler) {
    ThreadFactory tf = new SchedulableVirtualThreadFactory(scheduler);
    group = new MultiThreadIoEventLoopGroup(tf, LocalIoHandler.newFactory());
    client
        .group(group)
        .channel(LocalChannel.class)
        .handler(
            new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                ch.pipeline()
                    .addLast(
                        new ObjectEncoder(),
                        new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                        new NettyHandler(id, handleMessage, connectionHandler));
              }
            });
  }

  @Override
  public void connect(SocketAddress... address) {
    Arrays.stream(address).forEach(client::connect);
  }

  @Override
  public void close() {
    group.shutdownGracefully();
  }
}
