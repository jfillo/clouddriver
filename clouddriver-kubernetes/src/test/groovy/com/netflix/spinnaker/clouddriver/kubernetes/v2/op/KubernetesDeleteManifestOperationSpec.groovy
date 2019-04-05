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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeleteManifestDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesDeploymentHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesUnregisteredCustomResourceHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeleteManifestOperation
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import spock.lang.Specification

class KubernetesDeleteManifestOperationSpec extends Specification {
  def ACCOUNT = "account"
  def NAME = "my-name"
  def NAMESPACE = "my-namespace"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  KubernetesDeleteManifestOperation createMockDeleter(KubernetesKind kind, String resourceName, KubernetesHandler handler, boolean registerAsCrd) {
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    credentialsMock.delete(kind, _, _, _, _) >> Collections.singletonList(NAME)

    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCredentialsMock.getCredentials() >> credentialsMock
    namedCredentialsMock.getName() >> ACCOUNT

    def deleteDescription = new KubernetesDeleteManifestDescription()
      .setCredentials(namedCredentialsMock)
      .setAccount(ACCOUNT)
      .setLocation(NAMESPACE)
      .setKinds(Collections.singletonList(kind.toString()))
      .setManifestName(kind.toString() + " " + resourceName)

    def registry = new KubernetesResourcePropertyRegistry(Collections.singletonList(handler), new KubernetesSpinnakerKindMap())
    if (registerAsCrd) {
      CustomKubernetesResource crd = new CustomKubernetesResource()
      crd.kubernetesKind = kind.toString()
      KubernetesResourceProperties properties = KubernetesResourceProperties.fromCustomResource(crd)
      registry.registerAccountProperty(ACCOUNT, properties)
    }

    def deleteOp = new KubernetesDeleteManifestOperation(deleteDescription, registry)

    return deleteOp
  }

  void "deployment deleter is correctly invoked"() {
    def kind = KubernetesKind.DEPLOYMENT

    setup:
    def deleteOp = createMockDeleter( KubernetesKind.DEPLOYMENT,
      NAME,
      new KubernetesDeploymentHandler(),
      false)

    when:
    def result = deleteOp.operate([])
    then:
    result.manifestNamesByNamespace[NAMESPACE].size() == 1
    result.manifestNamesByNamespace[NAMESPACE][0] == "$kind $NAME"
  }

  void "unregistered crd deleter is correctly invoked"() {
    def kind = KubernetesKind.fromString("ServiceMonitor.coreos.monitoring.io")

    setup:
    def deleteOp = createMockDeleter(kind,
      NAME,
      new KubernetesUnregisteredCustomResourceHandler(),
      true)

    when:
    def result = deleteOp.operate([])
    then:
    result.manifestNamesByNamespace[NAMESPACE].size() == 1
    result.manifestNamesByNamespace[NAMESPACE][0] == "$kind $NAME"
  }
}
