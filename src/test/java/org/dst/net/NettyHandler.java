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
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHandler extends ChannelInboundHandlerAdapter {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final Function<Serializable, List<? extends Serializable>> messageHandler;
  private final Consumer<Connection> connectionHandler;
  private final NodeId nodeId;

  public NettyHandler(
      int id,
      Function<Serializable, List<? extends Serializable>> messageHandler,
      Consumer<Connection> connectionHandler) {
    this.messageHandler = messageHandler;
    this.connectionHandler = connectionHandler;
    this.nodeId = new NodeId(id);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof NodeId(int id)) {
      connectionHandler.accept(new NettyConnection(id, ctx));
    } else {
      LOGGER.trace("Node {} received message {}", nodeId.id(), msg.getClass().getSimpleName());
      List<? extends Serializable> resp = messageHandler.apply((Serializable) msg);
      if (LOGGER.isTraceEnabled() && !resp.isEmpty()) {
        LOGGER.trace(
            "Node {} replying with message {}",
            nodeId.id(),
            resp.getFirst().getClass().getSimpleName());
      }
      resp.forEach(ctx::writeAndFlush);
      //      if(!resp.isEmpty())
      //      {
      //        ctx.flush();
      //      }
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    ctx.writeAndFlush(nodeId); // TODO would be nice if this logic was in StaticMesh instead of here
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    LOGGER.warn("Unexpected exception from downstream.", cause);
    ctx.close();
  }
}
