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
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest.KubernetesDeployManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.config.KubernetesIntegrationTestConfiguration;
import com.netflix.spinnaker.config.KubernetesIntegrationTestJobRequestRepository;
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

import java.io.ByteArrayInputStream;
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

  @Autowired
  private KubernetesIntegrationTestJobRequestRepository jobRequestRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TaskRepository taskRepository;

  private Yaml yaml = new Yaml(new SafeConstructor());
  private List<String> expectedCommand = Arrays.asList("kubectl", "--kubeconfig=test-config", "--context=test-context", "apply", "-f", "-");

  @Before
  public void setup() {
    TaskRepository.threadLocalTask.set(taskRepository.create("integration-test", "it-status"));
  }

  @Test
  public void deploy_deployment() throws Exception {
    String expectedManifest = readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/deployment-applied.json").trim();
    // ^ .editorconfig forces a newline, so we need to trim it ^
    jobRequestRepository.registerJob(new JobRequest(expectedCommand, new ByteArrayInputStream(expectedManifest.getBytes())),
      JobResult.builder()
        .result(JobResult.Result.SUCCESS)
        .output("deployment.apps \"nginx-deployment\" created")
        .error(""
        ).build());

    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/deployment.yaml");
    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("moniker", Moniker.builder().app("test-app").build());
    map.put("manifests", Collections.singletonList(sourceManifest));
    converter.convertOperation(map).operate(Collections.emptyList());
  }

  @Test
  public void deploy_registered_crd() throws Exception {
    String expectedManifest = readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/service-monitor-applied.json").trim();
    // ^ .editorconfig forces a newline, so we need to trim it ^
    jobRequestRepository.registerJob(new JobRequest(expectedCommand, new ByteArrayInputStream(expectedManifest.getBytes())),
      JobResult.builder()
        .result(JobResult.Result.SUCCESS)
        .output("ServiceMonitor.monitoring.coreos.com \"example-app\" created")
        .error(""
        ).build());

    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/service-monitor.yaml");
    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("moniker", Moniker.builder().app("test-app").build());
    map.put("manifests", Collections.singletonList(sourceManifest));
    converter.convertOperation(map).operate(Collections.emptyList());
  }

  @Test
  public void deploy_unregistered_crd() throws Exception {
    String expectedManifest = readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/prometheus-rule-applied.json").trim();
    // ^ .editorconfig forces a newline, so we need to trim it ^
    jobRequestRepository.registerJob(new JobRequest(expectedCommand, new ByteArrayInputStream(expectedManifest.getBytes())),
      JobResult.builder()
        .result(JobResult.Result.SUCCESS)
        .output("ServiceMonitor.monitoring.coreos.com \"prometheus-example-rules\" created")
        .error(""
        ).build());

    KubernetesManifest sourceManifest = readManifestYamlFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/prometheus-rule.yaml");
    Map<String,Object> map = new HashMap<>();
    map.put("account","test-account");
    map.put("moniker", Moniker.builder().app("test-app").build());
    map.put("manifests", Collections.singletonList(sourceManifest));
    converter.convertOperation(map).operate(Collections.emptyList());
  }

  private String readFileStringFromClasspath(String file) throws IOException {
    return StreamUtils.copyToString(new ClassPathResource(file).getInputStream(), Charset.defaultCharset());
  }

  private KubernetesManifest readManifestYamlFromClasspath(String file) throws IOException {
    return objectMapper.convertValue(yaml.load(readFileStringFromClasspath(file)), KubernetesManifest.class);
  }
}
