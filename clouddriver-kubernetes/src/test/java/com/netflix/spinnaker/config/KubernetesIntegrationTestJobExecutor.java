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

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.local.ReaderConsumer;

public class KubernetesIntegrationTestJobExecutor implements JobExecutor {
  private KubernetesIntegrationTestJobRequestRepository jobRequestRepository;

  public KubernetesIntegrationTestJobExecutor(KubernetesIntegrationTestJobRequestRepository jobRequestRepository) {
    this.jobRequestRepository = jobRequestRepository;
  }

  @Override
  public JobResult<String> runJob(JobRequest jobRequest) {
    return jobRequestRepository.getJobResult(jobRequest);
  }

  @Override
  public <T> JobResult<T> runJob(JobRequest jobRequest, ReaderConsumer<T> readerConsumer) {
    return jobRequestRepository.getJobResult(jobRequest);
  }
}
