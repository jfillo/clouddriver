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
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest.KubernetesDeployManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.config.KubernetesIntegrationTestConfiguration;
import com.netflix.spinnaker.moniker.Moniker;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {KubernetesIntegrationTestConfiguration.class})
@ActiveProfiles("v2-op-manifest")
public class KubernetesDeployManifestOperationIT {

  @Autowired
  private KubernetesDeployManifestConverter converter;

  @MockBean
  private Task task;

  // we'll mock the JobExecutor because we don't actually expect to run real kubectl commands during the tests
  // but tests will verify the commands are formatted as expected
  @MockBean
  private JobExecutor jobExecutor;

  @Autowired
  private ObjectMapper objectMapper;

  private Yaml yaml = new Yaml(new SafeConstructor());
  private ArgumentCaptor<JobRequest> argument = ArgumentCaptor.forClass(JobRequest.class);
  private List<String> expectedCommand = Arrays.asList("kubectl", "--kubeconfig=test-config", "--context=test-context", "apply", "-f", "-");

  @Before
  public void setup() {
    TaskRepository.threadLocalTask.set(task);
    when(jobExecutor.runJob(any(JobRequest.class))).thenReturn(JobResult.<String>builder().result(JobResult.Result.SUCCESS).build());
  }

  @Test
  public void deploy_deployment() throws Exception {
    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/deployment.yaml");
    String expectedManifest = readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/deployment-applied.json").trim();
    // ^ .editorconfig forces a newline, so we need to trim it ^

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("moniker", Moniker.builder().app("test-app").build());
    map.put("manifests", Collections.singletonList(sourceManifest));
    converter.convertOperation(map).operate(Collections.emptyList());

    verify(jobExecutor, atLeastOnce()).runJob(argument.capture());
    Optional<JobRequest> jr = argument.getAllValues().stream()
      .filter(j -> j.getTokenizedCommand().equals(expectedCommand))
      .findFirst();
    assertTrue("kubectl apply not called as expected", jr.isPresent());
    assertEquals("kubectl applied manifest does not match expected", expectedManifest, StreamUtils.copyToString(jr.get().getInputStream(), Charset.defaultCharset()));
  }

  @Test
  public void deploy_registered_crd() throws Exception {
    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/service-monitor.yaml");
    String expectedManifest = readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/service-monitor-applied.json").trim();
    // ^ .editorconfig forces a newline, so we need to trim it ^

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("moniker", Moniker.builder().app("test-app").build());
    map.put("manifests", Collections.singletonList(sourceManifest));
    converter.convertOperation(map).operate(Collections.emptyList());

    verify(jobExecutor, atLeastOnce()).runJob(argument.capture());
    Optional<JobRequest> jr = argument.getAllValues().stream()
      .filter(j -> j.getTokenizedCommand().equals(expectedCommand))
      .findFirst();
    assertTrue("kubectl apply not called as expected", jr.isPresent());
    assertEquals("kubectl applied manifest does not match expected", expectedManifest, StreamUtils.copyToString(jr.get().getInputStream(), Charset.defaultCharset()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void deploy_unregistered_crd() throws Exception {
    KubernetesManifest deployManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/prometheus-rule.yaml");

    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("moniker", Moniker.builder().app("test-app").build());
    map.put("manifests", Collections.singletonList(deployManifest));
    converter.convertOperation(map).operate(Collections.emptyList());
  }

  private String readFileStringFromClasspath(String file) throws IOException {
    return StreamUtils.copyToString(new ClassPathResource(file).getInputStream(), Charset.defaultCharset());
  }

  private KubernetesManifest readManifestYamlFromClasspath(String file) throws IOException {
    return objectMapper.convertValue(yaml.load(readFileStringFromClasspath(file)), KubernetesManifest.class);
  }
}
