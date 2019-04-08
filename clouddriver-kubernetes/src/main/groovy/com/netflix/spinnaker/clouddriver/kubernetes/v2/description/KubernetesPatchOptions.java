/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import lombok.Data;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions.MergeStrategy.json;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions.MergeStrategy.merge;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions.MergeStrategy.strategic;

@Data
public class KubernetesPatchOptions {
  private MergeStrategy mergeStrategy;
  private boolean record;

  public static KubernetesPatchOptions json() {
    return new KubernetesPatchOptions().setMergeStrategy(json);
  }

  public static KubernetesPatchOptions merge() {
    return new KubernetesPatchOptions().setMergeStrategy(merge);
  }

  public static KubernetesPatchOptions strategic() {
    return new KubernetesPatchOptions().setMergeStrategy(strategic);
  }

  public enum MergeStrategy {
    strategic,
    json,
    merge
  }
}
