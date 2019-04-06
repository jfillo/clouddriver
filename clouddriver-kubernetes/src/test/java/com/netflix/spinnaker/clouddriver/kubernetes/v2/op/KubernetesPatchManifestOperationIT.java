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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest.KubernetesPatchManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.config.KubernetesIntegrationTestConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.Charset;
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
public class KubernetesPatchManifestOperationIT {

  @Autowired
  private KubernetesPatchManifestConverter converter;

  // we'll mock the JobExecutor because we don't actually expect to run real kubectl commands during the tests
  // but tests will verify the commands are formatted as expected
  @MockBean
  private JobExecutor jobExecutor;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TaskRepository taskRepository;

  private Yaml yaml = new Yaml(new SafeConstructor());
  private Gson gson = new Gson();
  private ArgumentCaptor<JobRequest> argument = ArgumentCaptor.forClass(JobRequest.class);

  @Before
  public void setup() {
    TaskRepository.threadLocalTask.set(taskRepository.create("integration-test", "it-status"));
    when(jobExecutor.runJob(any(JobRequest.class))).thenReturn(JobResult.<String>builder().result(JobResult.Result.SUCCESS).build());
  }

  @Test
  public void patch_deployment_resource() throws Exception {
    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/deployment-patch.yaml");
    List<String> expectedCommand = Arrays.asList("kubectl",
      "--kubeconfig=test-config",
      "--context=test-context",
      "--namespace=default",
      "patch",
      "deployment",
      "my-app",
      "--type",
      "merge",
      "--patch",
      gson.toJson(sourceManifest));

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "Deployment.apps my-app");
    map.put("patchBody", sourceManifest);
    map.put("options", KubernetesPatchOptions.merge());
    converter.convertOperation(map).operate(Collections.emptyList());

    verify(jobExecutor, atLeastOnce()).runJob(argument.capture());
    Optional<JobRequest> jr = argument.getAllValues().stream()
      .filter(j -> j.getTokenizedCommand().equals(expectedCommand))
      .findFirst();
    assertTrue("kubectl apply not called as expected: " + expectedCommand, jr.isPresent());
  }

  @Test
  public void patch_registered_crd() throws Exception {
    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/service-monitor-patch.yaml");
    List<String> expectedCommand = Arrays.asList("kubectl",
      "--kubeconfig=test-config",
      "--context=test-context",
      "--namespace=default",
      "patch",
      "ServiceMonitor.monitoring.coreos.com",
      "my-app",
      "--type",
      "merge",
      "--patch",
      gson.toJson(sourceManifest));

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "ServiceMonitor.monitoring.coreos.com my-app");
    map.put("patchBody", sourceManifest);
    map.put("options", KubernetesPatchOptions.merge());
    converter.convertOperation(map).operate(Collections.emptyList());

    verify(jobExecutor, atLeastOnce()).runJob(argument.capture());
    Optional<JobRequest> jr = argument.getAllValues().stream()
      .filter(j -> j.getTokenizedCommand().equals(expectedCommand))
      .findFirst();
    assertTrue("kubectl apply not called as expected: " + expectedCommand, jr.isPresent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void patch_unregistered_crd() throws Exception {
    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/prometheus-rule-patch.yaml");

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("manifestName", "PrometheusRule..monitoring.coreos.com my-app");
    map.put("patchBody", sourceManifest);
    map.put("options", KubernetesPatchOptions.merge());
    converter.convertOperation(map).operate(Collections.emptyList());
  }

  private String readFileStringFromClasspath(String file) throws IOException {
    return StreamUtils.copyToString(new ClassPathResource(file).getInputStream(), Charset.defaultCharset());
  }

  private KubernetesManifest readManifestYamlFromClasspath(String file) throws IOException {
    return objectMapper.convertValue(yaml.load(readFileStringFromClasspath(file)), KubernetesManifest.class);
  }
}
