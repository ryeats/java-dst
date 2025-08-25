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

import java.lang.reflect.Method;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;

public class MethodTestDescriptor extends AbstractTestDescriptor {

  private final Method testMethod;

  public MethodTestDescriptor(UniqueId uniqueId, Method testMethod, TestDescriptor parent) {
    super(uniqueId, displayName(testMethod), MethodSource.from(testMethod));
    this.testMethod = testMethod;
    setParent(parent);
  }

  public Method getTestMethod() {
    return testMethod;
  }

  @Override
  public Type getType() {
    return Type.TEST;
  }

  private static String displayName(Method testField) {
    return testField.getName();
  }
}
