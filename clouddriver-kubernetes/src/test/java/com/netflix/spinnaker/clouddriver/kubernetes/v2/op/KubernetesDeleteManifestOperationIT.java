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
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest.KubernetesDeleteManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.config.KubernetesIntegrationTestConfiguration;
import com.netflix.spinnaker.config.KubernetesIntegrationTestJobRequestRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {KubernetesIntegrationTestConfiguration.class})
@ActiveProfiles("v2-op-manifest")
public class KubernetesDeleteManifestOperationIT {

  @Autowired
  private KubernetesDeleteManifestConverter converter;

  @Autowired
  private TaskRepository taskRepository;

  @Autowired
  private KubernetesIntegrationTestJobRequestRepository jobRequestRepository;

  @Before
  public void setup() {
    TaskRepository.threadLocalTask.set(taskRepository.create("integration-test", "it-status"));
    jobRequestRepository.registerJob(new JobRequest(Arrays.asList("kubectl", "--kubeconfig=test-config", "--context=test-context", "--namespace=default", "delete", "none/my-app", "--ignore-not-found=true")),
      JobResult.Result.FAILURE, "", "error: the server doesn't have a resource type \"none\"", false);
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

    jobRequestRepository.registerJob(new JobRequest(expectedCommand),
      JobResult.Result.SUCCESS, "deployment \"my-app\" deleted", "", false);

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "Deployment.apps my-app");
    converter.convertOperation(map).operate(Collections.emptyList());
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

    jobRequestRepository.registerJob(new JobRequest(expectedCommand),
      JobResult.Result.SUCCESS,"ServiceMonitor.monitoring.coreos.com \"my-app\" deleted", "", false);

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "ServiceMonitor.monitoring.coreos.com my-app");
    converter.convertOperation(map).operate(Collections.emptyList());
  }

  @Test(expected = KubectlJobExecutor.KubectlException.class)
  public void delete_unregistered_crd() {
    List<String> expectedCommand = Arrays.asList("kubectl",
      "--kubeconfig=test-config",
      "--context=test-context",
      "--namespace=default",
      "delete",
      "PrometheusRule.monitoring.coreos.com/my-app",
      "--ignore-not-found=true");

    jobRequestRepository.registerJob(new JobRequest(expectedCommand),
      JobResult.Result.SUCCESS,"PrometheusRule.monitoring.coreos.com \"my-app\" deleted", "", false);

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "PrometheusRule.monitoring.coreos.com my-app");
    converter.convertOperation(map).operate(Collections.emptyList());
  }
}
