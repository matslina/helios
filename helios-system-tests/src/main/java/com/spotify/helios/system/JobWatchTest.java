/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.system;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import com.spotify.helios.cli.CliMain;
import com.spotify.helios.common.descriptors.JobId;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.spotify.helios.common.descriptors.HostStatus.Status.UP;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertTrue;

public class JobWatchTest extends SystemTestBase {

  @Test
  public void test() throws Exception {
    startDefaultMaster();
    startDefaultAgent(testHost());
    awaitHostStatus(testHost(), UP, LONG_WAIT_MINUTES, MINUTES);

    // Create job
    final JobId jobId = createJob(testJobName, testJobVersion, "busybox", IDLE_COMMAND,
                                  ImmutableMap.of("FOO", "4711",
                                                  "BAR", "deadbeef"));

    // deploy
    deployJob(jobId, testHost());

    final String[] commands = new String[]{"watch", "-z", masterEndpoint(),
                                           "--no-log-setup", jobId.toString()};

    final AtomicBoolean success = new AtomicBoolean(false);
    final List<String> outputLines = Lists.newArrayList();

    final long deadline = System.currentTimeMillis() + MINUTES.toMillis(LONG_WAIT_MINUTES);

    final String testHost = testHost();
    final String abbreviatedTestHost;
    if (testHost.length() > 10) {
      abbreviatedTestHost = testHost.substring(0, 10);
    } else {
      abbreviatedTestHost = testHost;
    }

    final OutputStream out = new OutputStream() {
      int counter = 0;
      final byte[] lineBuffer = new byte[8192];

      @Override
      public void write(int b) throws IOException {
        if (System.currentTimeMillis() > deadline) {
          throw new IOException("timed out trying to succeed");
        }
        lineBuffer[counter] = (byte) b;
        counter++;

        if (b != 10) {
          return;
        }

        String line = Charsets.UTF_8.decode(
            ByteBuffer.wrap(lineBuffer, 0, counter)).toString();
        outputLines.add(line);
        counter = 0;

        if (line.contains(abbreviatedTestHost) && !line.contains("UNKNOWN")) {
          success.set(true);
          throw new IOException("output closed");
        }
      }
    };
    final CliMain main = new CliMain(new PrintStream(out),
                                     new PrintStream(new ByteArrayOutputStream()), commands);
    main.run();
    assertTrue("Should have stopped the stream due to success: got\n"
               + Joiner.on("").join(outputLines), success.get());
  }
}
