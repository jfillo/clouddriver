/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeployManifestDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesReplicaSetHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesUnregisteredCustomResourceHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeployManifestOperation
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.moniker.Moniker
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification

class KubernetesDeployManifestOperationSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())

  def ACCOUNT = "account"
  def NAME = "my-name"
  def VERSION = "version"
  def NAMESPACE = "my-namespace"
  def DEFAULT_NAMESPACE = "default"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  KubernetesDeployManifestOperation createMockDeployer(KubernetesV2Credentials credentials, String manifest, KubernetesHandler kubernetesHandler, KubernetesKind kind, boolean registerAsCrd) {
    def deployDescription = new KubernetesDeployManifestDescription()
      .setManifest(stringToManifest(manifest))
      .setMoniker(new Moniker())
      .setSource(KubernetesDeployManifestDescription.Source.text)

    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCredentialsMock.getCredentials() >> credentials
    namedCredentialsMock.getName() >> ACCOUNT
    deployDescription.setCredentials(namedCredentialsMock)

    credentials.deploy(_, _) >> null

    def registry = new KubernetesResourcePropertyRegistry(Collections.singletonList(kubernetesHandler), new KubernetesSpinnakerKindMap())

    if (registerAsCrd) {
      CustomKubernetesResource crd = new CustomKubernetesResource()
      crd.kubernetesKind = kind.toString()
      KubernetesResourceProperties properties = KubernetesResourceProperties.fromCustomResource(crd)
      registry.registerAccountProperty(ACCOUNT, properties)
    }

    NamerRegistry.lookup().withProvider(KubernetesCloudProvider.ID)
      .withAccount(ACCOUNT)
      .setNamer(KubernetesManifest.class, new KubernetesManifestNamer())

    if (kubernetesHandler.versioned()) {
      def versionedArtifactConverterMock = Mock(KubernetesVersionedArtifactConverter)
      versionedArtifactConverterMock.getDeployedName(_) >> "$NAME-$VERSION"
      versionedArtifactConverterMock.toArtifact(_, _, _) >> new Artifact()
      registry.get(ACCOUNT, kind).versionedConverter = versionedArtifactConverterMock
    }
    
    def deployOp = new KubernetesDeployManifestOperation(deployDescription, registry, null)

    return deployOp
  }

  void "replica set deployer is correctly invoked"() {
    def KIND = KubernetesKind.REPLICA_SET
    def BASIC_REPLICA_SET = """
apiVersion: $KubernetesApiVersion.EXTENSIONS_V1BETA1
kind: $KIND
metadata:
  name: $NAME
  namespace: $NAMESPACE
"""

    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, BASIC_REPLICA_SET, new KubernetesReplicaSetHandler(), KIND, false)

    when:
    def result = deployOp.operate([])
    then:
    result.manifestNamesByNamespace[NAMESPACE].size() == 1
    result.manifestNamesByNamespace[NAMESPACE][0] == "$KIND $NAME-$VERSION"
  }

  void "replica set deployer uses backup namespace"() {
    def KIND = KubernetesKind.REPLICA_SET
    def BASIC_REPLICA_SET_NO_NAMESPACE = """
apiVersion: $KubernetesApiVersion.EXTENSIONS_V1BETA1
kind: $KIND
metadata:
  name: $NAME
"""

    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> DEFAULT_NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, BASIC_REPLICA_SET_NO_NAMESPACE, new KubernetesReplicaSetHandler(), KIND, false)

    when:
    def result = deployOp.operate([])

    then:
    result.manifestNamesByNamespace[DEFAULT_NAMESPACE].size() == 1
    result.manifestNamesByNamespace[DEFAULT_NAMESPACE][0] == "$KIND $NAME-$VERSION"
  }

  void "unregistered crd deployer is correctly invoked"() {
    def kind = KubernetesKind.fromString("ServiceMonitor.coreos.monitoring.io")
    def REGISTERED_CRD = """
apiVersion: coreos.monitoring.io/v1
kind: ServiceMonitor
metadata:
  name: $NAME
"""

    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, REGISTERED_CRD, new KubernetesUnregisteredCustomResourceHandler(), kind, true)

    when:
    def result = deployOp.operate([])
    then:
    result.manifestNamesByNamespace[NAMESPACE].size() == 1
    result.manifestNamesByNamespace[NAMESPACE][0] == "$kind $NAME"
  }
}
