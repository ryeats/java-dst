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

import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface TransportFactory {
  TransportClient getTransportClient(
      int id,
      Function<Serializable, List<? extends Serializable>> handleMessage,
      Consumer<Connection> connectionHandler);

  TransportServer getTransportServer(
      int id,
      Function<Serializable, List<? extends Serializable>> handleMessage,
      Consumer<Connection> connectionHandler);
}
