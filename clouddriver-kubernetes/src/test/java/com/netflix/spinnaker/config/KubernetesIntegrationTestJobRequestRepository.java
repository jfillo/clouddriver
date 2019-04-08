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

package com.netflix.spinnaker.config;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class KubernetesIntegrationTestJobRequestRepository {

  private Gson gson = new Gson();
  private ConcurrentMap<JobRequest, JobResult> repository = new ConcurrentHashMap<>();

  public void reInitializeRepository() {
    repository.clear();

    // provide some default top output so metrics caching kicks in
    repository.put(new JobRequest(Arrays.asList("kubectl", "--kubeconfig=test-config", "--context=test-context", "--namespace=default", "top", "po", "--containers")),
      JobResult.builder().result(JobResult.Result.SUCCESS).error("").output(readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/top.output")).build());

    // list some crds because clusters are likely to have a few installed
    repository.put(new JobRequest(Arrays.asList("kubectl", "--kubeconfig=test-config", "--context=test-context", "--namespace=default", "-o", "json", "get", "customResourceDefinition")),
      JobResult.builder().result(JobResult.Result.SUCCESS).error("").output(parseManifestList(readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/list-customResourceDefinition.output.json"))).build());
  }

  public void registerJob(JobRequest jobRequest, JobResult jobResult) {// JobResult.Result result, Object output, String error, boolean parseManifestList) {
    repository.put(jobRequest, jobResult);
  }

  public JobResult getJobResult(JobRequest jobRequest) {
    log.info("{}", jobRequest.getTokenizedCommand());
    return repository.getOrDefault(jobRequest,
      JobResult.builder()
        .result(JobResult.Result.SUCCESS)
        .output(parseManifestList(readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/kubernetes/v2/op/manifest/empty-list.output.json")))
        .error("")
        .build());
  }

  private String readFileStringFromClasspath(String file) {
    try {
      return StreamUtils.copyToString(new ClassPathResource(file).getInputStream(), Charset.defaultCharset());
    } catch (Exception e) {
      log.error("", e);
      return "";
    }
  }

  private List<KubernetesManifest> parseManifestList(String manifestJson) {
    try {
      JsonReader reader = new JsonReader(new StringReader(manifestJson));
      List<KubernetesManifest> manifestList = new ArrayList<>();
      reader.beginObject();
      while (reader.hasNext()) {
        if (reader.nextName().equals("items")) {
          reader.beginArray();
          while (reader.hasNext()) {
            KubernetesManifest manifest = gson.fromJson(reader, KubernetesManifest.class);
            manifestList.add(manifest);
          }
          reader.endArray();
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return manifestList;
    } catch (Exception e) {
      log.error("", e);
      return Collections.emptyList();
    }
  }
}
