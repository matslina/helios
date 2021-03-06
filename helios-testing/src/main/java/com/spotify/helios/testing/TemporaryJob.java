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

package com.spotify.helios.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Futures;

import com.spotify.helios.client.HeliosClient;
import com.spotify.helios.common.descriptors.Deployment;
import com.spotify.helios.common.descriptors.Goal;
import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.JobStatus;
import com.spotify.helios.common.descriptors.PortMapping;
import com.spotify.helios.common.descriptors.TaskStatus;
import com.spotify.helios.common.descriptors.ThrottleState;
import com.spotify.helios.common.protocol.CreateJobResponse;
import com.spotify.helios.common.protocol.JobDeployResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.spotify.helios.testing.Jobs.TIMEOUT_MILLIS;
import static com.spotify.helios.testing.Jobs.get;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;

public class TemporaryJob {

  private static final Logger log = LoggerFactory.getLogger(TemporaryJob.class);

  private final Map<String, TaskStatus> statuses = Maps.newHashMap();
  private final HeliosClient client;
  private final Prober prober;
  private final Job job;
  private final List<String> hosts;
  private final Set<String> waitPorts;

  TemporaryJob(final HeliosClient client, final Prober prober, final Job job,
               final List<String> hosts, final Set<String> waitPorts) {
    this.client = checkNotNull(client, "client");
    this.prober = checkNotNull(prober, "prober");
    this.job = checkNotNull(job, "job");
    this.hosts = ImmutableList.copyOf(checkNotNull(hosts, "hosts"));
    this.waitPorts = ImmutableSet.copyOf(checkNotNull(waitPorts, "waitPorts"));
  }

  public Job job() {
    return job;
  }

  /**
   * Returns the port that a job can be reached at given the host and name of registered port.
   * This is useful to discover the value of a dynamically allocated port.
   * @param host the host where the job is deployed
   * @param port the name of the registered port
   * @return the port where the job can be reached, or null if the host or port name is not found
   */
  public Integer port(final String host, final String port) {
    checkArgument(hosts.contains(host), "host %s not found", host);
    checkArgument(job.getPorts().containsKey(port), "port %s not found", port);
    final TaskStatus status = statuses.get(host);
    if (status == null) {
      return null;
    }
    final PortMapping portMapping = status.getPorts().get(port);
    if (portMapping == null) {
      return null;
    }
    return portMapping.getExternalPort();
  }

  /**
   * Returns a {@link com.google.common.net.HostAndPort} for a registered port. This is useful
   * for discovering the value of dynamically allocated ports. This method should only be called
   * when the job has been deployed to a single host. If the job has been deployed to multiple
   * hosts an AssertionError will be thrown indicating that the {@link #addresses(String)} method
   * should must  called instead.
   * @param port the name of the registered port
   * @return a HostAndPort describing where the registered port can be reached. Null if
   * no ports have been registered.
   * @throws java.lang.AssertionError if the job has been deployed to more than one host
   */
  public HostAndPort address(final String port) {
    final List<HostAndPort> addresses = addresses(port);

    if (addresses.size() > 1) {
      throw new AssertionError(
          "Job has been deployed to multiple hosts, use addresses method instead");
    }

    return addresses.get(0);
  }

  /**
   * Returns a {@link com.google.common.net.HostAndPort} object for a registered port, for each
   * host the job has been deployed to. This is useful for discovering the value of dynamically
   * allocated ports.
   * @param port the name of the registered port
   * @return a HostAndPort describing where the registered port can be reached. Null if
   * no ports have been registered.
   */
  public List<HostAndPort> addresses(final String port) {
    checkArgument(job.getPorts().containsKey(port), "port %s not found", port);
    final List<HostAndPort> addresses = Lists.newArrayList();
    for (Map.Entry<String, TaskStatus> entry : statuses.entrySet()) {
      final Integer externalPort = entry.getValue().getPorts().get(port).getExternalPort();
      assert externalPort != null;
      addresses.add(HostAndPort.fromParts(entry.getKey(), externalPort));
    }
    return addresses;
  }

  void deploy() {
    try {
      log.info("Deploying job {} with image {}", job.getId(), job.getImage());

      // Create job
      final CreateJobResponse createResponse = get(client.createJob(job));
      if (createResponse.getStatus() != CreateJobResponse.Status.OK) {
        fail(format("Failed to create job %s - %s", job.getId(),
                    createResponse.toString()));
      }

      // Deploy job
      final Deployment deployment = Deployment.of(job.getId(), Goal.START);
      for (final String host : hosts) {
        log.info("Waiting for Helios job deploy of {} on host {}", deployment.getJobId(), host);

        final JobDeployResponse deployResponse = get(client.deploy(deployment, host));
        if (deployResponse.getStatus() != JobDeployResponse.Status.OK) {
          fail(format("Failed to deploy job %s %s - %s",
                      job.getId(), job.toString(), deployResponse));
        }
      }

      // Wait for job to come up
      for (final String host : hosts) {
        awaitUp(host);
      }
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      fail(format("Failed to deploy job %s %s - %s",
                  job.getId(), job.toString(), e));
    }
  }

  void undeploy(final List<AssertionError> errors) {
    log.info("Undeploying job {}", job.getId());

    Jobs.undeploy(client, job.getId(), hosts, errors);
  }

  private void awaitUp(final String host) throws TimeoutException {
    log.info("Waiting for job {} to be up on host {}", job.getId(), host);

    final TaskStatus status = Polling.awaitUnchecked(
        TIMEOUT_MILLIS, MILLISECONDS, new Callable<TaskStatus>() {
          @Override
          public TaskStatus call() throws Exception {
            log.info("Getting job status for job {}", job.getId());

            final JobStatus status = Futures.getUnchecked(client.jobStatus(job.getId()));
            if (status == null) {
              return null;
            }
            final TaskStatus taskStatus = status.getTaskStatuses().get(host);
            if (taskStatus == null) {
              return null;
            }

            final TaskStatus.State state = taskStatus.getState();
            if (state == TaskStatus.State.RUNNING) {
              return taskStatus;
            } else if (state == TaskStatus.State.FAILED ||
                       state == TaskStatus.State.EXITED ||
                       state == TaskStatus.State.STOPPED) {
              // Throw exception which should stop the test dead in it's tracks
              String stateString = state.toString();
              if (taskStatus.getThrottled() != ThrottleState.NO) {
                stateString += format("(%s)", taskStatus.getThrottled());
              }
              throw new AssertionError(format(
                  "Unexpected job state %s. Check helios agent logs for details.", stateString));
            } else {
              // For things like PULLING_IMAGE, STARTING, etc., we just continue waiting.
              return null;
            }
          }
        }
    );

    statuses.put(host, status);

    for (final String port : waitPorts) {
      awaitPort(port, host);
    }
  }

  private void awaitPort(final String port, final String host) throws TimeoutException {
    log.info("Awaiting port availability on {}:{}", host, port);

    final TaskStatus taskStatus = statuses.get(host);
    assert taskStatus != null;
    final Integer externalPort = taskStatus.getPorts().get(port).getExternalPort();
    assert externalPort != null;
    Polling.awaitUnchecked(TIMEOUT_MILLIS, MILLISECONDS, new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        log.info("Probing: {} @ {}:{}", port, host, externalPort);
        final boolean up = prober.probe(host, externalPort);
        if (up) {
          log.info("Up: {} @ {}:{}", port, host, externalPort);
          return true;
        } else {
          return null;
        }
      }
    });
  }

  public static interface Deployer {

    TemporaryJob deploy(Job job, List<String> hosts, Set<String> waitPorts);

    TemporaryJob deploy(Job job, String hostFilter, Set<String> waitPorts);
  }
}
