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

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.local.ReaderConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Main.class})
@ActiveProfiles("v2-op-manifest")
@AutoConfigureMockMvc
public class RetrieveManifestControllerIT {

  @Autowired
  private MockMvc mvc;

  // we'll mock the JobExecutor because we don't actually expect to run real kubectl commands during the tests
  // but tests will verify the commands are formatted as expected
  @MockBean
  private JobExecutor jobExecutor;

  @Test
  public void retrieve_deployment() throws Exception {
    when(jobExecutor.runJob(any(JobRequest.class)))
      .thenReturn(JobResult.<String>builder().result(JobResult.Result.SUCCESS).output(readFileStringFromClasspath("com/netflix/spinnaker/clouddriver/controllers/retrieve/deployment.json")).build());
    when(jobExecutor.runJob(any(JobRequest.class), any(ReaderConsumer.class))).thenReturn(JobResult.<String>builder().result(JobResult.Result.SUCCESS).error("No resources found").build());

    mvc.perform(get("/manifests/test-account/default/deployment nginx-deployment"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.account", is("test-account")))
      .andExpect(jsonPath("$.name", is("deployment nginx-deployment")))
      .andExpect(jsonPath("$.location", is("default")))
      .andExpect(jsonPath("$.manifest.metadata.name", is("nginx-deployment")));
  }

  private String readFileStringFromClasspath(String file) throws IOException {
    return StreamUtils.copyToString(new ClassPathResource(file).getInputStream(), Charset.defaultCharset());
  }
}
