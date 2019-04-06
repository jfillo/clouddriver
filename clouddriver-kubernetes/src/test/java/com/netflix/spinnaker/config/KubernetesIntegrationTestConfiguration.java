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

import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Import({SecurityConfig.class})
@ComponentScan({"com.netflix.spinnaker.config", "com.netflix.spinnaker.clouddriver.config"})
@EnableAutoConfiguration(exclude = {
  BatchAutoConfiguration.class,
  GroovyTemplateAutoConfiguration.class
})
@EnableScheduling
public class KubernetesIntegrationTestConfiguration {
  // configuration in this file is meant to mirror com.netflix.spinnaker.clouddriver.Main
  // except the only difference being the exclusion of com.netflix.spinnaker.clouddriver.WebConfig
  // We can't just import com.netflix.spinnaker.clouddriver.Main
  // because clouddrivee-web module is not a dependency of clouddriver-kubernetes
  // and com.netflix.spinnaker.clouddriver.Main is package protected
}
