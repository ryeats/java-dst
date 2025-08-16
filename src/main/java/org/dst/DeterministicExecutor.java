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
package org.dst;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeterministicExecutor implements Executor, AutoCloseable {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final RandomGenerator random;
  private final List<Runnable> workQueue = new ArrayList<>();
  private final ExecutorService singleThread = Executors.newSingleThreadExecutor();
  private final ByteBuffer execFingerprint;
  private int maxExecutions = 256;
  private int timeout = 5;
  private final ByteArrayOutputStream execStats = new ByteArrayOutputStream();

  public DeterministicExecutor(RandomGenerator random) {
    this.random = random;
    this.execFingerprint = null;
  }

  public DeterministicExecutor(RandomGenerator random, String base64ExecFingerprint) {
    this.random = random;
    if (base64ExecFingerprint != null) {
      this.execFingerprint =
          ByteBuffer.wrap(
              Base64.getDecoder().decode(base64ExecFingerprint.getBytes(StandardCharsets.UTF_8)));
    } else {
      this.execFingerprint = null;
    }
  }

  @Override
  public void execute(Runnable runnable) {
    //    System.out.println("Calling execute from "+ Thread.currentThread());
    singleThread.submit(() -> workQueue.add(runnable));
  }

  public void tick() {
    try {
      singleThread.submit(() -> this.internalTick(true)).get(timeout, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void internalTick(boolean shuffle) {
    LOGGER.trace("Executing {} tasks in the work queue shuffle:{}", workQueue.size(), shuffle);
    int count = 0;
    while (!workQueue.isEmpty() && count < maxExecutions) {
      removeWorkTask(shuffle).run();
      count++;
    }

    // TODO this doesn't catch randomness issues or two tasks being swaped between ticks
    if (LOGGER.isDebugEnabled()) {
      execStats.write(count);
      if (execFingerprint != null && execFingerprint.hasRemaining()) {
        if (count != execFingerprint.get()) {
          // TODO how to expose enough detail to debug?
          LOGGER.debug("Non-deterministic");
        }
      }
    }
  }

  public void runInCurrentQueueOrder() {
    try {
      singleThread.submit(() -> this.internalTick(false)).get(timeout, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Runnable removeWorkTask(boolean shuffle) {
    if (shuffle) {
      Collections.shuffle(workQueue, random);
    }
    return workQueue.removeFirst();
  }

  public int queueSize() {
    return workQueue.size();
  }

  @Override
  public void close() {
    singleThread.close();
  }

  public void setMaxExecutions(int maxExecutions) {
    this.maxExecutions = maxExecutions;
  }

  public void setTimeout(int seconds) {
    this.timeout = seconds;
  }

  public String getExecutionFingerprint() {
    String base64Fingerprint = Base64.getEncoder().encodeToString(execStats.toByteArray());
    LOGGER.debug("Simulation execution fingerprint: {}", base64Fingerprint);
    return base64Fingerprint;
  }
}
