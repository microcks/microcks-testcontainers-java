/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.testcontainers;

import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Testcontainers implementation for main Microcks container.
 * @author laurent
 */
public class MicrocksContainer extends GenericContainer<MicrocksContainer> {

   /** Get a SL4J logger. */
   private static final Logger log = LoggerFactory.getLogger(MicrocksContainer.class);

   private static final String MICROCKS_FULL_IMAGE_NAME = "quay.io/microcks/microcks-uber";
   private static final DockerImageName MICROCKS_IMAGE = DockerImageName.parse(MICROCKS_FULL_IMAGE_NAME);

   private static final int MICROCKS_HTTP_PORT = 8080;
   private static final int MICROCKS_GRPC_PORT = 9090;

   private ObjectMapper mapper;

   /**
    * Build a new MicrocksContainer with its container image name as string. This image must
    * be compatible with quay.io/microcks/microcks-uber image.
    * @param image The name (with tag/version) of Microcks Uber distribution to use.
    */
   public MicrocksContainer(String image) {
      this(DockerImageName.parse(image));
   }

   /**
    * Build a new MicrocksContainer with its full container image name. This image must
    * be compatible with quay.io/microcks/microcks-uber image.
    * @param imageName The name (with tag/version) of Microcks Uber distribution to use.
    */
   public MicrocksContainer(DockerImageName imageName) {
      super(imageName);
      imageName.assertCompatibleWith(MICROCKS_IMAGE);

      withExposedPorts(MICROCKS_HTTP_PORT, MICROCKS_GRPC_PORT);

      waitingFor(Wait.forLogMessage(".*Started MicrocksApplication.*", 1));
   }

   /**
    * Get the Http endpoint where Microcks can be accessed (you'd have to append '/api' to access APIs)
    * @return The Http endpoint for talking to container.
    */
   public String getHttpEndpoint() {
      return String.format("http://%s:%s", getHost(), getMappedPort(MICROCKS_HTTP_PORT));
   }

   /**
    * Get the exposed mock endpoint for a SOAP Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getSoapMockEndpoint(String service, String version) {
      return String.format("%s/soap/%s/%s", getHttpEndpoint(),  service, version);
   }

   /**
    * Get the exposed mock endpoint for a REST API.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getRestMockEndpoint(String service, String version) {
      return String.format("%s/rest/%s/%s", getHttpEndpoint(),  service, version);
   }

   /**
    * Get the exposed mock endpoint for a GRPC Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getGraphQLMockEndpoint(String service, String version) {
      return String.format("%s/graphql/%s/%s", getHttpEndpoint(), service, version);
   }

   /**
    * Get the exposed mock endpoint for a GRPC Service.
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getGrpcMockEndpoint() {
      return String.format("grpc://%s:%s", getHost(), getMappedPort(MICROCKS_GRPC_PORT));
   }

   /**
    * Import an artifact as a primary or main one within the Microcks container.
    * @param artifact The file representing artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws IOException If file cannot be read of transmission exception occurs.
    * @throws InterruptedException If connection to the docker container is interrupted.
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public void importAsMainArtifact(File artifact) throws IOException, InterruptedException, MicrocksException {
      importArtifact(artifact, true);
   }

   /**
    * Import an artifact as a secondary one within the Microcks container.
    * @param artifact The file representing artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws IOException If file cannot be read of transmission exception occurs.
    * @throws InterruptedException If connection to the docker container is interrupted.
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public void importAsSecondaryArtifact(File artifact) throws IOException, InterruptedException, MicrocksException {
      importArtifact(artifact, false);
   }

   /**
    * Launch a conformance test on an endpoint.
    * @param testRequest The test specifications (API under test, endpoint, runner, ...)
    * @return The final TestResult containing information on success/failure as well as details on test cases.
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws InterruptedException If connection to Microcks container is interrupted
    * @throws MicrocksException If Microcks fails creating a new test giving your request.
    */
   public TestResult testEndpoint(TestRequest testRequest) throws IOException, InterruptedException, MicrocksException {
      String requestBody = getMapper().writeValueAsString(testRequest);

      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getHttpEndpoint() + "/api/tests"))
            .header("Content-Type", ContentType.APPLICATION_JSON.getMimeType())
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

      // Send the request and parse status code.
      log.debug("Sending a test request to Microcks container: {}", requestBody);
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 201) {
         TestResult testResult = getMapper().readValue(response.body(), TestResult.class);
         log.debug("Got Test Result: {}, now polling for progression", testResult.getId());

         final String testResultId = testResult.getId();
         try {
            Awaitility.await()
                  .atMost(testRequest.getTimeout(), TimeUnit.MILLISECONDS)
                  .pollDelay(100, TimeUnit.MILLISECONDS)
                  .pollInterval(200, TimeUnit.MILLISECONDS)
                  .until(() -> !refreshTestResult(testResultId).isInProgress());
         } catch (ConditionTimeoutException timeoutException) {
            log.info("Caught a ConditionTimeoutException for test on {}", testRequest.getTestEndpoint());
         }

         // Return the final result.
         return refreshTestResult(testResultId);
      }
      if (log.isErrorEnabled()) {
         log.error("Couldn't launch on new test on Microcks with status {} ", response.statusCode());
         log.error("Error response body is {}", response.body());
      }
      throw new MicrocksException("Couldn't launch on new test on Microcks. Please check Microcks container logs");
   }

   private void importArtifact(File artifact, boolean mainArtifact) throws IOException, InterruptedException, MicrocksException {
      if (!artifact.exists()) {
         throw new IOException("Artifact " + artifact.getPath() + " does not exist or can't be read.");
      }

      HttpEntity httpEntity = MultipartEntityBuilder.create()
            .addBinaryBody("file", artifact, ContentType.APPLICATION_OCTET_STREAM, artifact.getName())
            .build();

      // Use pipeline streams to write the encoded data directly to the network instead of
      // caching it in memory. Because Multipart request bodies contain files, they can cause
      // memory overflows if cached in memory.
      Pipe pipe = Pipe.open();

      // Pipeline streams must be used in a multi-threaded environment. Using one
      // thread for simultaneous reads and writes can lead to deadlocks.
      new Thread(() -> {
         try (OutputStream outputStream = Channels.newOutputStream(pipe.sink())) {
            // Write the encoded data to the pipeline.
            httpEntity.writeTo(outputStream);
         } catch (IOException e) {
            log.error("Exception while transferring artifact content", e);
         }

      }).start();

      // Build a new Http client.
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getHttpEndpoint() + "/api/artifact/upload" + (mainArtifact ? "" : "?mainArtifact=false")))
            .header("Content-Type", httpEntity.getContentType().getValue())
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> Channels.newInputStream(pipe.source())))
            .build();

      // Send the request and parse status code.
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 201) {
         log.error("Artifact has not been correctly been imported: {}", response.body());
         throw new MicrocksException("Artifact has not been correctly been imported: " + response.body());
      }
   }

   private ObjectMapper getMapper() {
      if (mapper == null) {
         mapper = new ObjectMapper();
         // Do not include null values in both serialization and deserialization.
         mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
         mapper.setPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
      }
      return mapper;
   }

   private TestResult refreshTestResult(String testResultId) throws IOException, InterruptedException {
      // Build a new client on correct API endpoint.
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(getHttpEndpoint() + "/api/tests/" + testResultId))
            .header("Accept", ContentType.APPLICATION_JSON.getMimeType())
            .GET()
            .build();

      // Send the request and parse status code.
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      return getMapper().readValue(response.body(), TestResult.class);
   }
}
