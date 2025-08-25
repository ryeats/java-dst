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
package org.dst.junit;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;

public class SimulationClassTestDescriptor extends AbstractTestDescriptor {

  private final Class<?> testClass;

  public SimulationClassTestDescriptor(Class<?> testClass, TestDescriptor parent) {
    super(
        parent.getUniqueId().append("class", testClass.getName()),
        testClass.getSimpleName(),
        ClassSource.from(testClass));
    this.testClass = testClass;
    setParent(parent);
  }

  @Override
  public Type getType() {
    return Type.CONTAINER_AND_TEST;
  }

  public Class<?> getTestClass() {
    return testClass;
  }
}
