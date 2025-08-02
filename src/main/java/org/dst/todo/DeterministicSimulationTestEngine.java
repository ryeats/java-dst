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
package org.dst.todo;

import java.util.Optional;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;

public class DeterministicSimulationTestEngine<C extends EngineExecutionContext>
    implements TestEngine {

  @Override
  public String getId() {
    return "deterministic-simulation-test";
  }

  @Override
  public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
    return null;
  }

  @Override
  public void execute(ExecutionRequest request) {}

  @Override
  public Optional<String> getGroupId() {
    return TestEngine.super.getGroupId();
  }

  @Override
  public Optional<String> getArtifactId() {
    return TestEngine.super.getArtifactId();
  }

  @Override
  public Optional<String> getVersion() {
    return TestEngine.super.getVersion();
  }
}
