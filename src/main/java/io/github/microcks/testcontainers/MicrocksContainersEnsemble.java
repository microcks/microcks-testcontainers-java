/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.testcontainers;

import io.github.microcks.testcontainers.connection.AmazonServiceConnection;
import io.github.microcks.testcontainers.connection.KafkaConnection;
import io.github.microcks.testcontainers.model.Secret;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

/**
 * An abstraction other a set of containers needed for advanced Microcks services (such as Postman runtime support
 * or Asynchronous features). Use this when a single {@code MicrocksContainer} is not enough.
 * @author laurent
 */
public class MicrocksContainersEnsemble implements Startable {

   private final Network network;

   private GenericContainer<?> postman;
   private MicrocksAsyncMinionContainer asyncMinion;
   private final MicrocksContainer microcks;

   /**
    * Build a new MicrocksContainersEnsemble with its base container image name as string. This image must
    * be compatible with quay.io/microcks/microcks-uber image.
    * @param image The name (with tag/version) of Microcks Uber distribution to use.
    */
   public MicrocksContainersEnsemble(String image) {
      this(Network.newNetwork(), image);
   }

   /**
    * Build a new MicrocksContainersEnsemble with a pre-existing network and with its base container full image name.
    * @param network The network to attach ensemble containers to.
    * @param image This image must be compatible with quay.io/microcks/microcks-uber image.
    */
   public MicrocksContainersEnsemble(Network network, String image) {
      this(network, DockerImageName.parse(image));
   }

   /**
    * Build a new MicrocksContainersEnsemble with a pre-existing network and with its base container full image name.
    * @param network The network to attach ensemble containers to.
    * @param image This image must be compatible with quay.io/microcks/microcks-uber image.
    */
   public MicrocksContainersEnsemble(Network network, DockerImageName image) {
      this.network = network;
      this.microcks = new MicrocksContainer(image)
            .withNetwork(network)
            .withNetworkAliases("microcks")
            .withEnv("POSTMAN_RUNNER_URL", "http://postman:3000")
            .withEnv("TEST_CALLBACK_URL", "http://microcks:" + MicrocksContainer.MICROCKS_HTTP_PORT)
            .withEnv("ASYNC_MINION_URL", "http://microcks-async-minion:" + MicrocksAsyncMinionContainer.MICROCKS_ASYNC_MINION_HTTP_PORT);
   }

   /**
    * Enable the Postman runtime container with default container image.
    * @return self
    */
   public MicrocksContainersEnsemble withPostman() {
      return withPostman("quay.io/microcks/microcks-postman-runtime:latest");
   }

   /**
    * Enable the Postman runtime container with provided container image.
    * @param image The name (with tag/version) of Microcks Postman runtime to use.
    * @return self
    */
   public MicrocksContainersEnsemble withPostman(String image) {
      this.postman = new GenericContainer<>(DockerImageName.parse(image))
            .withNetwork(network)
            .withNetworkAliases("postman")
            .waitingFor(Wait.forLogMessage(".*postman-runtime wrapper listening on port.*", 1));
      return this;
   }

   /**
    * Enable the Async Feature container with default container image (deduced from Microcks main one).
    * @return self
    */
   public MicrocksContainersEnsemble withAsyncFeature() {
      String image = microcks.getDockerImageName().replace("microcks-uber", "microcks-uber-async-minion");
      if (image.endsWith("-native")) {
         image = image.substring(0, image.length() - "-native".length());
      }
      return withAsyncFeature(image);
   }

   /**
    * Enable the Async Feature container with provided container image.
    * @param image The name (with tag/version) of Microcks Async Minion Uber distribution to use.
    * @return self
    */
   public MicrocksContainersEnsemble withAsyncFeature(String image) {
      this.asyncMinion = new MicrocksAsyncMinionContainer(network, image, microcks);
      return this;
   }

   /**
    * Once the Async Feature is enabled, connects to a Kafka broker.
    * @param connection Connection details to a Kafka broker.
    * @return self
    */
   public MicrocksContainersEnsemble withKafkaConnection(KafkaConnection connection) {
      ensureAsyncFeatureIsEnabled();
      this.asyncMinion.withKafkaConnection(connection);
      return this;
   }

   /**
    * Once the Async Feature is enabled, connects to an Amazon SQS service.
    * @param connection Connection details to an Amazon SQS service.
    * @return self
    */
   public MicrocksContainersEnsemble withAmazonSQSConnection(AmazonServiceConnection connection) {
      ensureAsyncFeatureIsEnabled();
      this.asyncMinion.withAmazonSQSConnection(connection);
      return this;
   }

   /**
    * Once the Async Feature is enabled, connects to an Amazon SNS service.
    * @param connection Connection details to an Amazon SNS service.
    * @return self
    */
   public MicrocksContainersEnsemble withAmazonSNSConnection(AmazonServiceConnection connection) {
      ensureAsyncFeatureIsEnabled();
      this.asyncMinion.withAmazonSNSConnection(connection);
      return this;
   }

   /**
    * Set host accessibility on ensemble containers.
    * @param hostAccessible Host accessibility flag
    * @return The ensemble being built
    */
   public MicrocksContainersEnsemble withAccessToHost(boolean hostAccessible) {
      microcks.withAccessToHost(hostAccessible);
      if (postman != null) {
         postman.withAccessToHost(hostAccessible);
      }
      if (asyncMinion != null) {
         asyncMinion.withAccessToHost(hostAccessible);
      }
      return this;
   }

   /**
    * Provide paths to artifacts that will be imported as primary or main ones within the Microcks container
    * once it will be started and healthy.
    * @param artifacts A set of paths to artifacts that will be loaded as classpath resources
    * @return self
    */
   public MicrocksContainersEnsemble withMainArtifacts(String... artifacts) {
      microcks.withMainArtifacts(artifacts);
      return this;
   }

   /**
    * Provide paths to artifacts that will be imported as secondary ones within the Microcks container
    * once it will be started and healthy.
    * @param artifacts A set of paths to artifacts that will be loaded as classpath resources
    * @return self
    */
   public MicrocksContainersEnsemble withSecondaryArtifacts(String... artifacts) {
      microcks.withSecondaryArtifacts(artifacts);
      return this;
   }

   /**
    * Provide Secret that should be imported in Microcks after startup.
    * @param secret The description of a secret to access remote Git repository, test endpoint or broker.
    * @return self
    */
   public MicrocksContainersEnsemble withSecret(Secret secret) {
      microcks.withSecret(secret);
      return this;
   }

   /**
    * Delays the Async Minion container's creation and start until provided {@link Startable}s start first.
    * Note that the circular dependencies are not supported.
    * @param startables A list of {@link Startable} to depend on
    * @return self
    */
   public MicrocksContainersEnsemble withAsyncDependsOn(Startable... startables) {
      ensureAsyncFeatureIsEnabled();
      this.asyncMinion.dependsOn(startables);
      return this;
   }


   /**
    * Get the Docker network used by this ensemble of Microcks containers.
    * @return The network ensemble containers are attached to.
    */
   public Network getNetwork() {
      return network;
   }

   /**
    * Get the main MicrocksContainer to access the endpoints, artifact management and test methods.
    * @return The main wrapped MicrocksContainer
    */
   public MicrocksContainer getMicrocksContainer() {
      return microcks;
   }

   /**
    * Get the wrapped Postman runtime container to access management methods.
    * @return The wrapped Postman runtime container
    */
   public GenericContainer<?> getPostmanContainer() {
      return postman;
   }

   /**
    * Get the wrapped Async Minion container to access management methods.
    * @return The wrapped Async minion container
    */
   public MicrocksAsyncMinionContainer getAsyncMinionContainer() {
      return asyncMinion;
   }

   @Override
   public void start() {
      // Sequential start to avoid resource contention on CI systems with weaker hardware.
      microcks.start();
      if (postman != null) {
         postman.start();
      }
      if (asyncMinion != null) {
         asyncMinion.start();
      }
   }

   @Override
   public void stop() {
      allContainers().parallel().forEach(GenericContainer::stop);
   }

   private void ensureAsyncFeatureIsEnabled() {
      if (this.asyncMinion == null) {
         throw new IllegalStateException("Async feature must have been enabled first");
      }
   }

   private Stream<GenericContainer<?>> allContainers() {
      Stream<GenericContainer<?>> stream = Stream.of(microcks);
      if (postman != null) {
         stream = Stream.concat(stream, Stream.of(postman));
      }
      if (asyncMinion != null) {
         stream = Stream.concat(stream, Stream.of(asyncMinion));
      }
      return stream;
   }
}
