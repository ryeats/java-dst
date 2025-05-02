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

import io.netty.channel.ChannelHandlerContext;
import java.io.Serializable;

public class NettyConnection implements Connection {

  private final int id;
  private final ChannelHandlerContext ctx;

  public NettyConnection(int id, ChannelHandlerContext ctx) {
    this.id = id;
    this.ctx = ctx;
  }

  @Override
  public void send(Serializable message) {
    ctx.writeAndFlush(message);
  }

  @Override
  public boolean isActive() {
    return ctx.channel().isActive();
  }

  @Override
  public void close() {
    ctx.close();
  }

  @Override
  public int getId() {
    return id;
  }
}
