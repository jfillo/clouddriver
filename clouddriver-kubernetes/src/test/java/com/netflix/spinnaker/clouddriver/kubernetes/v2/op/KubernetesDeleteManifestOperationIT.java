/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op;

import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest.KubernetesDeleteManifestConverter;
import com.netflix.spinnaker.config.KubernetesIntegrationTestConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {KubernetesIntegrationTestConfiguration.class})
@ActiveProfiles("v2-op-manifest")
public class KubernetesDeleteManifestOperationIT {

  @Autowired
  private KubernetesDeleteManifestConverter converter;

  // we'll mock the JobExecutor because we don't actually expect to run real kubectl commands during the tests
  // but tests will verify the commands are formatted as expected
  @MockBean
  private JobExecutor jobExecutor;

  @Autowired
  private TaskRepository taskRepository;

  private ArgumentCaptor<JobRequest> argumentCaptor = ArgumentCaptor.forClass(JobRequest.class);

  @Before
  public void setup() {
    TaskRepository.threadLocalTask.set(taskRepository.create("integration-test", "it-status"));
    when(jobExecutor.runJob(any(JobRequest.class))).thenReturn(JobResult.<String>builder().result(JobResult.Result.SUCCESS).build());
  }

  @Test
  public void delete_deployment() {
    List<String> expectedCommand = Arrays.asList("kubectl",
      "--kubeconfig=test-config",
      "--context=test-context",
      "--namespace=default",
      "delete",
      "deployment/my-app",
      "--ignore-not-found=true");

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "Deployment.apps my-app");
    converter.convertOperation(map).operate(Collections.emptyList());

    verify(jobExecutor, atLeastOnce()).runJob(argumentCaptor.capture());
    Optional<JobRequest> jr = argumentCaptor.getAllValues().stream()
      .filter(j -> j.getTokenizedCommand().equals(expectedCommand))
      .findFirst();
    assertTrue("kubectl not called as expected: " + expectedCommand, jr.isPresent());
  }

  @Test
  public void delete_registered_crd() {
    List<String> expectedCommand = Arrays.asList("kubectl",
      "--kubeconfig=test-config",
      "--context=test-context",
      "--namespace=default",
      "delete",
      "ServiceMonitor.monitoring.coreos.com/my-app",
      "--ignore-not-found=true");

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "ServiceMonitor.monitoring.coreos.com my-app");
    converter.convertOperation(map).operate(Collections.emptyList());

    verify(jobExecutor, atLeastOnce()).runJob(argumentCaptor.capture());
    Optional<JobRequest> jr = argumentCaptor.getAllValues().stream()
      .filter(j -> j.getTokenizedCommand().equals(expectedCommand))
      .findFirst();
    assertTrue("kubectl not called as expected: " + expectedCommand, jr.isPresent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void delete_unregistered_crd() {
    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "PrometheusRule.monitoring.coreos.com my-app");
    converter.convertOperation(map).operate(Collections.emptyList());
  }
}
