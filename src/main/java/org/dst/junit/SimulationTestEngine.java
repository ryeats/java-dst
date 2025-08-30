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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.dst.Simulation;
import org.dst.SimulationCheckerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

public class SimulationTestEngine implements TestEngine {

  private static final Predicate<Class<?>> IS_SIMULATION_TEST_CONTAINER =
      classCandidate ->
          AnnotationSupport.isAnnotated(classCandidate, DeterministicSimulation.class);

  @Override
  public String getId() {
    return "simulation-test";
  }

  @Override
  public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
    TestDescriptor engineDescriptor = new EngineDescriptor(uniqueId, "Simulation Test");

    request
        .getSelectorsByType(ClasspathRootSelector.class)
        .forEach(
            selector -> {
              appendTestsInClasspathRoot(selector.getClasspathRoot(), engineDescriptor);
            });

    request
        .getSelectorsByType(PackageSelector.class)
        .forEach(
            selector -> {
              appendTestsInPackage(selector.getPackageName(), engineDescriptor);
            });

    request
        .getSelectorsByType(ClassSelector.class)
        .forEach(
            selector -> {
              appendTestsInClass(selector.getJavaClass(), engineDescriptor);
            });
    //    request
    //            .getSelectorsByType(MethodSelector.class)
    //            .forEach(
    //                    selector -> {
    //                      appendTestinMethod(selector.getJavaMethod(), engineDescriptor);
    //                    });

    return engineDescriptor;
  }

  private void appendTestsInClasspathRoot(URI uri, TestDescriptor engineDescriptor) {
    ReflectionSupport.findAllClassesInClasspathRoot(
            uri, IS_SIMULATION_TEST_CONTAINER, name -> true) //
        .stream() //
        .map(aClass -> new SimulationClassTestDescriptor(aClass, engineDescriptor)) //
        .forEach(engineDescriptor::addChild);
  }

  private void appendTestsInPackage(String packageName, TestDescriptor engineDescriptor) {
    ReflectionSupport.findAllClassesInPackage(
            packageName, IS_SIMULATION_TEST_CONTAINER, name -> true) //
        .stream() //
        .map(aClass -> new SimulationClassTestDescriptor(aClass, engineDescriptor)) //
        .forEach(engineDescriptor::addChild);
  }

  private void appendTestsInClass(Class<?> javaClass, TestDescriptor engineDescriptor) {
    if (AnnotationSupport.isAnnotated(javaClass, DeterministicSimulation.class)) {
      SimulationClassTestDescriptor classDesc =
          new SimulationClassTestDescriptor(javaClass, engineDescriptor);
      engineDescriptor.addChild(classDesc);
      // TODO preplan the whole test?
      //      ReflectionUtils.findMethods(javaClass, (m) ->
      // m.isAnnotationPresent(SimulationTest.class))
      //          .forEach(
      //              method -> {
      //                MethodTestDescriptor methodTestDescriptor =
      //                    new MethodTestDescriptor(
      //                        classDesc.getUniqueId().append("method", method.getName()),
      //                        method,
      //                        classDesc);
      //                classDesc.addChild(methodTestDescriptor);
      //              });
    }
  }

  @Override
  public void execute(ExecutionRequest request) {
    TestDescriptor root = request.getRootTestDescriptor();

    execute(request, root);
  }

  public void execute(ExecutionRequest request, TestDescriptor descriptor) {
    EngineExecutionListener listener = request.getEngineExecutionListener();
    if (descriptor instanceof EngineDescriptor) {
      listener.executionStarted(descriptor);
      descriptor.getChildren().forEach(childDescriptor -> execute(request, childDescriptor));
      listener.executionFinished(descriptor, TestExecutionResult.successful());
    }

    if (descriptor instanceof SimulationClassTestDescriptor classDesc) {
      listener.executionStarted(classDesc);

      Class<?> clazz = classDesc.getTestClass();
      try (Simulation sim = classDesc.getSimulation()) {

        Object instance = clazz.getDeclaredConstructor().newInstance();

        List<Supplier<RunnableFuture<?>>> testMethods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
          if (method.isAnnotationPresent(SimulationTest.class)) {
            if (method.isAnnotationPresent(Disabled.class)) {
              MethodTestDescriptor disabledDescriptor =
                  new MethodTestDescriptor(
                      classDesc.getUniqueId().append("method", method.getName()),
                      method,
                      classDesc);
              listener.dynamicTestRegistered(disabledDescriptor);
              listener.executionSkipped(disabledDescriptor, "Disabled");
              continue;
            }
            testMethods.add(createTestSupplier(method, instance, classDesc, listener));
          }
        }
        TestMethodScheduler testMethodScheduler =
            new TestMethodScheduler(sim, classDesc.getSimulationTimeDuration());
        sim.run(
            (s) -> {
              try {
                invokeAnnotated(clazz, BeforeAll.class, instance);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              List<Future<?>> tests =
                  testMethodScheduler.schedule(sim, testMethods, Collections.emptyList());
              return new SimulationCheckerImpl(
                  sim.clock(), classDesc.getSimulationTimeDuration(), tests);
            });

        sim.run(
            List.of(
                () -> {
                  try {
                    invokeAnnotated(clazz, AfterAll.class, instance);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));

      } catch (Throwable t) {
        listener.executionFinished(classDesc, TestExecutionResult.failed(t));
      }

      listener.executionFinished(classDesc, TestExecutionResult.successful());
    }

    for (TestDescriptor child : descriptor.getChildren()) {
      execute(request, child);
    }
  }

  private void executeTestMethod(
      Method method,
      Object instance,
      SimulationClassTestDescriptor classDesc,
      EngineExecutionListener listener,
      AtomicInteger idCounter)
      throws Exception {

    invokeAnnotated(classDesc.getTestClass(), BeforeEach.class, instance);

    MethodTestDescriptor methodDescriptor =
        new MethodTestDescriptor(
            classDesc
                .getUniqueId()
                .append("method", method.getName() + idCounter.incrementAndGet()),
            method,
            classDesc);
    // TODO should the whole simulation be a single test or will each reporting each test be
    // helpful?
    listener.dynamicTestRegistered(methodDescriptor);
    listener.executionStarted(methodDescriptor);

    try {
      method.invoke(instance);
      listener.executionFinished(methodDescriptor, TestExecutionResult.successful());
    } catch (Throwable t) {
      listener.executionFinished(methodDescriptor, TestExecutionResult.failed(t));
    }

    invokeAnnotated(classDesc.getTestClass(), AfterEach.class, instance);
  }

  private void invokeAnnotated(
      Class<?> clazz, Class<? extends Annotation> annotation, Object instance) throws Exception {
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.isAnnotationPresent(annotation)) {
        method.setAccessible(true);
        method.invoke(instance);
      }
    }
  }

  private Supplier<RunnableFuture<?>> createTestSupplier(
      Method method,
      Object instance,
      SimulationClassTestDescriptor classDesc,
      EngineExecutionListener listener) {
    final AtomicInteger testCounter = new AtomicInteger();
    return () ->
        new FutureTask<>(
            () -> {
              try {
                this.executeTestMethod(method, instance, classDesc, listener, testCounter);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            },
            true);
  }
}
