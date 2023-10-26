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

   private final GenericContainer<?> postman;
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
    * This image must be compatible with quay.io/microcks/microcks-uber image.
    * @param network The network to attach ensemble containers to.
    * @param image The name (with tag/version) of Microcks Uber distribution to use.
    */
   public MicrocksContainersEnsemble(Network network, String image) {
      this.network = network;
      this.microcks = new MicrocksContainer(image)
            .withNetwork(network)
            .withNetworkAliases("microcks")
            .withEnv("POSTMAN_RUNNER_URL", "http://postman:3000")
            .withEnv("TEST_CALLBACK_URL", "http://microcks:8080");

      this.postman = new GenericContainer<>(DockerImageName.parse("quay.io/microcks/microcks-postman-runtime:latest"))
            .withNetwork(network)
            .withNetworkAliases("postman")
            .waitingFor(Wait.forLogMessage(".*postman-runtime wrapper listening on port.*", 1));
   }

   /**
    * Set host accessibility on ensemble containers.
    * @param hostAccessible Host accessibility flag
    * @return The ensemble being built
    */
   public MicrocksContainersEnsemble withAccessToHost(boolean hostAccessible) {
      microcks.withAccessToHost(hostAccessible);
      postman.withAccessToHost(hostAccessible);
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

   @Override
   public void start() {
      // Sequential start to avoid resource contention on CI systems with weaker hardware.
      microcks.start();
      postman.start();
   }

   @Override
   public void stop() {
      allContainers().parallel().forEach(GenericContainer::stop);
   }

   private Stream<GenericContainer<?>> allContainers() {
      return Stream.of(microcks, postman);
   }
}
