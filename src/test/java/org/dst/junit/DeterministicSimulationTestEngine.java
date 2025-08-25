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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;

@DeterministicSimulation
public class DeterministicSimulationTestEngine {

  @BeforeAll
  public static void setup() {
    System.out.println("setup");
  }

  @SimulationTest
  public void test1() {
    System.out.println("Test1");
  }

  @SimulationTest
  public void test2() {
    System.out.println("Test2");
  }

  @SimulationTest
  @Disabled
  public void test3() {
    System.out.println("Test3");
  }

  @SimulationTest
  public void test4() {
    System.out.println("Test4");
  }

  @AfterAll
  public static void tearDown() {
    System.out.println("tearDown");
  }
}
