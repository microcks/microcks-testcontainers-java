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

import io.github.microcks.testcontainers.model.DailyInvocationStatistic;
import io.github.microcks.testcontainers.model.RequestResponsePair;
import io.github.microcks.testcontainers.model.Secret;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.UnidirectionalEvent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Testcontainers implementation for main Microcks container.
 * @author laurent
 */
public class MicrocksContainer extends GenericContainer<MicrocksContainer> {

   /** Get a SL4J logger. */
   private static final Logger log = LoggerFactory.getLogger(MicrocksContainer.class);

   private static final String MICROCKS_FULL_IMAGE_NAME = "quay.io/microcks/microcks-uber";
   private static final DockerImageName MICROCKS_IMAGE = DockerImageName.parse(MICROCKS_FULL_IMAGE_NAME);
   private static final SimpleDateFormat METRICS_API_DAY_FORMATTER = new SimpleDateFormat("yyyyMMdd");
   private static final String HTTP_UPLOAD_LINE_FEED = "\r\n";
   private static final String HTTP_CONTENT_TYPE = "Content-Type";
   private static final String HTTP_ACCEPT = "Accept";
   private static final String APPLICATION_JSON = "application/json";

   public static final int MICROCKS_HTTP_PORT = 8080;
   public static final int MICROCKS_GRPC_PORT = 9090;

   private static ObjectMapper mapper;

   private Set<String> snapshotsToImport;
   private Set<String> mainArtifactsToImport;
   private Set<String> secondaryArtifactsToImport;
   private Set<RemoteArtifact> mainRemoteArtifactsToImport;
   private Set<RemoteArtifact> secondaryRemoteArtifactsToImport;
   private Set<Secret> secrets;

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
    * Provide paths to artifacts that will be imported as primary or main ones within the Microcks container
    * once it will be started and healthy.
    * @param artifacts A set of paths to artifacts that will be loaded as classpath resources
    * @return self
    */
   public MicrocksContainer withMainArtifacts(String... artifacts) {
      if (mainArtifactsToImport == null) {
         mainArtifactsToImport = new HashSet<>();
      }
      mainArtifactsToImport.addAll(Arrays.stream(artifacts).collect(Collectors.toList()));
      return self();
   }

   /**
    * Provide paths to artifacts that will be imported as secondary ones within the Microcks container
    * once it will be started and healthy.
    * @param artifacts A set of paths to artifacts that will be loaded as classpath resources
    * @return self
    */
   public MicrocksContainer withSecondaryArtifacts(String... artifacts) {
      if (secondaryArtifactsToImport == null) {
         secondaryArtifactsToImport = new HashSet<>();
      }
      secondaryArtifactsToImport.addAll(Arrays.stream(artifacts).collect(Collectors.toList()));
      return self();
   }

   /**
    * Provide urls to artifacts that will be imported as primary or main ones within the Microcks container
    * once it will be started and healthy.
    * @param remoteArtifactUrls A set of urls to artifacts that will be loaded as remote one
    * @return self
    */
   public MicrocksContainer withMainRemoteArtifacts(String... remoteArtifactUrls) {
      if (mainRemoteArtifactsToImport == null) {
         mainRemoteArtifactsToImport = new HashSet<>();
      }
      mainRemoteArtifactsToImport.addAll(Arrays.stream(remoteArtifactUrls)
            .map(url -> RemoteArtifact.of(url, null)).collect(Collectors.toSet()));
      return self();
   }

   /**
    * Provide remote artifacts (with secret name) that will be imported as primary or main ones within the Microcks
    * container once it will be started and healthy.
    * @param remoteArtifacts A set of remote artifacts that will be loaded
    * @return self
    */
   public MicrocksContainer withMainRemoteArtifacts(RemoteArtifact... remoteArtifacts) {
      if (mainRemoteArtifactsToImport == null) {
         mainRemoteArtifactsToImport = new HashSet<>();
      }
      mainRemoteArtifactsToImport.addAll(Arrays.stream(remoteArtifacts).collect(Collectors.toSet()));
      return self();
   }

   /**
    * Provide urls to artifacts that will be imported as secondary ones within the Microcks container
    * once it will be started and healthy.
    * @param remoteArtifactUrls A set of urls to artifacts that will be loaded as remote one
    * @return self
    */
   public MicrocksContainer withSecondaryRemoteArtifacts(String... remoteArtifactUrls) {
      if (secondaryRemoteArtifactsToImport == null) {
         secondaryRemoteArtifactsToImport = new HashSet<>();
      }
      secondaryRemoteArtifactsToImport.addAll(Arrays.stream(remoteArtifactUrls)
            .map(url -> RemoteArtifact.of(url, null)).collect(Collectors.toSet()));
      return self();
   }

   /**
    * Provide remote artifacts (with secret name)  that will be imported as secondary ones within the Microcks
    * container once it will be started and healthy.
    * @param remoteArtifacts A set of remote artifacts that will be loaded
    * @return self
    */
   public MicrocksContainer withSecondaryRemoteArtifacts(RemoteArtifact... remoteArtifacts) {
      if (secondaryRemoteArtifactsToImport == null) {
         secondaryRemoteArtifactsToImport = new HashSet<>();
      }
      secondaryRemoteArtifactsToImport.addAll(Arrays.stream(remoteArtifacts).collect(Collectors.toSet()));
      return self();
   }

   /**
    * Provide paths to local repository snapshots that will be imported within the Microcks container
    * once it will be started and healthy.
    * @param snapshots A set of repository snapshots that will be loaded as classpath resources
    * @return self
    */
   public MicrocksContainer withSnapshots(String... snapshots) {
      if (snapshotsToImport == null) {
         snapshotsToImport = new HashSet<>();
      }
      snapshotsToImport.addAll(Arrays.stream(snapshots).collect(Collectors.toList()));
      return self();
   }

   /**
    * Provide Secret that should be imported in Microcks after startup.
    * @param secret The description of a secret to access remote Git repotisory, test endpoint or broker.
    * @return self
    */
   public MicrocksContainer withSecret(Secret secret) {
      if (secrets == null) {
         secrets = new HashSet<>();
      }
      secrets.add(secret);
      return self();
   }

   @Override
   protected void containerIsStarted(InspectContainerResponse containerInfo) {
      // Load snapshots before anything else.
      if (snapshotsToImport != null && !snapshotsToImport.isEmpty()) {
         snapshotsToImport.forEach(this::importSnapshot);
      }
      // Load secrets before remote artifacts as they may be needed for authentication.
      if (secrets != null && !secrets.isEmpty()) {
         secrets.forEach(this::createSecret);
      }
      // Load remote artifacts before local ones.
      if (mainRemoteArtifactsToImport != null && !mainRemoteArtifactsToImport.isEmpty()) {
         mainRemoteArtifactsToImport.forEach(remoteArtifact -> downloadArtifact(getHttpEndpoint(), remoteArtifact, true));
      }
      if (secondaryRemoteArtifactsToImport != null && !secondaryRemoteArtifactsToImport.isEmpty()) {
         secondaryRemoteArtifactsToImport.forEach(remoteArtifact -> downloadArtifact(getHttpEndpoint(), remoteArtifact, false));
      }
      // Load local ones that may override remote ones.
      if (mainArtifactsToImport != null && !mainArtifactsToImport.isEmpty()) {
         mainArtifactsToImport.forEach((String artifactPath) -> this.importArtifact(artifactPath, true));
      }
      if (secondaryArtifactsToImport != null && !secondaryArtifactsToImport.isEmpty()) {
         secondaryArtifactsToImport.forEach((String artifactPath) -> this.importArtifact(artifactPath, false));
      }
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
      return String.format("%s/soap/%s/%s", getHttpEndpoint(), service, version);
   }

   /**
    * Get the exposed mock endpoint path for a SOAP Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A path endpoint to interact with Microcks mocks - starts with '/'.
    */
   public String getSoapMockEndpointPath(String service, String version) {
      return String.format("/soap/%s/%s", service, version);
   }

   /**
    * Get the exposed mock endpoint - with request validation enabled - for a SOAP Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getValidatingSoapMockEndpoint(String service, String version) {
      return String.format("%s/soap/%s/%s?validate=true", getHttpEndpoint(), service, version);
   }

   /**
    * Get the exposed mock endpoint path - with request validation enabled - for a SOAP Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A path endpoint to interact with Microcks mocks - starts with '/'.
    */
   public String getValidatingSoapMockEndpointPath(String service, String version) {
      return String.format("/soap/%s/%s?validate=true", service, version);
   }

   /**
    * Get the exposed mock endpoint for a REST API.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getRestMockEndpoint(String service, String version) {
      return String.format("%s/rest/%s/%s", getHttpEndpoint(), service, version);
   }

   /**
    * Get the exposed mock endpoint path for a REST API.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A path endpoint to interact with Microcks mocks - starts with '/'.
    */
   public String getRestMockEndpointPath(String service, String version) {
      return String.format("/rest/%s/%s", service, version);
   }

   /**
    * Get the exposed mock endpoint - with request validation enabled - for a REST API.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getValidatingRestMockEndpoint(String service, String version) {
      return String.format("%s/rest-valid/%s/%s", getHttpEndpoint(), service, version);
   }

   /**
    * Get the exposed mock endpoint path - with request validation enabled - for a REST API.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A path endpoint to interact with Microcks mocks - starts with '/'.
    */
   public String getValidatingRestMockEndpointPath(String service, String version) {
      return String.format("/rest-valid/%s/%s", service, version);
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
    * Get the exposed mock endpoint path for a GRPC Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @return A path endpoint to interact with Microcks mocks - starts with '/'.
    */
   public String getGraphQLMockEndpointPath(String service, String version) {
      return String.format("/graphql/%s/%s", service, version);
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
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public void importAsMainArtifact(File artifact) throws IOException, MicrocksException {
      MicrocksContainer.importArtifact(getHttpEndpoint(), artifact, true);
   }

   /**
    * Import an artifact as a secondary one within the Microcks container.
    * @param artifact The file representing artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws IOException If file cannot be read of transmission exception occurs.
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public void importAsSecondaryArtifact(File artifact) throws IOException, MicrocksException {
      MicrocksContainer.importArtifact(getHttpEndpoint(), artifact, false);
   }

   /**
    * Import a repository snapshot within the Microcks container.
    * @param snapshot The file representing the Microcks repository snapshot
    * @throws IOException If file cannot be read of transmission exception occurs.
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public void importSnapshot(File snapshot) throws IOException, MicrocksException {
      MicrocksContainer.importSnapshot(getHttpEndpoint(), snapshot);
   }

   /**
    * Import an artifact as a primary or secondary one within the Microcks container. This may be a fallback to
    * non-static {@code importArtifactAs...(File artifact)} method if you don't have direct access to the MicrocksContainer
    * instance you want to import this artifact into.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param artifact The file representing artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @param mainArtifact Whether this artifact should be considered as main or secondary.
    * @throws IOException If file cannot be read of transmission exception occurs.
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public static void importArtifact(String microcksContainerHttpEndpoint, File artifact, boolean mainArtifact) throws IOException, MicrocksException {
      if (!artifact.exists()) {
         throw new IOException("Artifact " + artifact.getPath() + " does not exist or can't be read.");
      }

      URL url = new URL(microcksContainerHttpEndpoint + "/api/artifact/upload" + (mainArtifact ? "" : "?mainArtifact=false"));
      HttpURLConnection httpConn = uploadFileToMicrocks(url, artifact, "application/octet-stream");

      if (httpConn.getResponseCode() != 201) {
         // Read response content for diagnostic purpose and disconnect.
         StringBuilder responseContent = getResponseContent(httpConn);
         httpConn.disconnect();

         log.error("Artifact has not been correctly imported: {}", responseContent);
         throw new MicrocksException("Artifact has not been correctly imported: " + responseContent);
      }
      // Disconnect Http connection.
      httpConn.disconnect();
   }

   /**
    * Import a repository snapshot within the Microcks container. This may be a fallback to
    * non-static {@code importSnapshot(File artifact)} method if you don't have direct access to the MicrocksContainer
    * instance you want to import this snapshot into.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param snapshot The file representing the Microcks repository snapshot
    * @throws IOException If file cannot be read of transmission exception occurs.
    * @throws MicrocksException If artifact cannot be correctly imported in container (probably malformed)
    */
   public static void importSnapshot(String microcksContainerHttpEndpoint, File snapshot) throws IOException, MicrocksException {
      if (!snapshot.exists()) {
         throw new IOException("Snapshot " + snapshot.getPath() + " does not exist or can't be read.");
      }

      URL url = new URL(microcksContainerHttpEndpoint + "/api/import");
      HttpURLConnection httpConn = uploadFileToMicrocks(url, snapshot, APPLICATION_JSON);

      if (httpConn.getResponseCode() != 201) {
         // Read response content for diagnostic purpose and disconnect.
         StringBuilder responseContent = getResponseContent(httpConn);
         httpConn.disconnect();

         log.error("Snapshot has not been correctly imported: {}", responseContent);
         throw new MicrocksException("Snapshot has not been correctly imported: " + responseContent);
      }
      // Disconnect Http connection.
      httpConn.disconnect();
   }

   /**
    * Launch a conformance test on an endpoint.
    * @param testRequest The test specifications (API under test, endpoint, runner, ...)
    * @return The final TestResult containing information on success/failure as well as details on test cases.
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws MicrocksException If Microcks fails creating a new test giving your request.
    */
   public TestResult testEndpoint(TestRequest testRequest) throws IOException, MicrocksException {
      return testEndpoint(getHttpEndpoint(), testRequest);
   }

   /**
    * Launch a conformance test on an endpoint asynchronously.
    * @param testRequest The test specifications (API under test, endpoint, runner, ...)
    * @return A completable future that will allow to retrieve a TestResult once test is finished.
    */
   public CompletableFuture<TestResult> testEndpointAsync(TestRequest testRequest) {
      return testEndpointAsync(getHttpEndpoint(), testRequest);
   }

   /**
    * Launch a conformance test on an endpoint asynchronously.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param testRequest The test specifications (API under test, endpoint, runner, ...)
    * @return A completable future that will allow to retrieve a TestResult once test is finished.
    */
   public static CompletableFuture<TestResult> testEndpointAsync(String microcksContainerHttpEndpoint, TestRequest testRequest) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            return MicrocksContainer.testEndpoint(microcksContainerHttpEndpoint, testRequest);
         } catch (Exception e) {
            throw new CompletionException(e);
         }
      });
   }

   /**
    * Launch a conformance test on an endpoint. This may be a fallback to non-static {@code testEndpoint(TestRequest testRequest)}
    * method if you don't have direct access to the MicrocksContainer instance you want to run this test on.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param testRequest The test specifications (API under test, endpoint, runner, ...)
    * @return The final TestResult containing information on success/failure as well as details on test cases.
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws MicrocksException If Microcks fails creating a new test giving your request.
    */
   public static TestResult testEndpoint(String microcksContainerHttpEndpoint, TestRequest testRequest) throws IOException, MicrocksException {
      String requestBody = getMapper().writeValueAsString(testRequest);

      // Build a new client on correct API endpoint.
      URL url = new URL(microcksContainerHttpEndpoint + "/api/tests");
      HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
      httpConn.setRequestMethod("POST");
      httpConn.setRequestProperty(HTTP_CONTENT_TYPE, APPLICATION_JSON);
      httpConn.setDoOutput(true);

      try (OutputStream os = httpConn.getOutputStream()) {
         byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
         os.write(input, 0, input.length);
      }

      // Read response content.
      StringBuilder responseContent = getResponseContent(httpConn);

      if (httpConn.getResponseCode() == 201) {
         httpConn.disconnect();

         TestResult testResult = getMapper().readValue(responseContent.toString(), TestResult.class);
         log.debug("Got Test Result: {}, now polling for progression", testResult.getId());

         final String testResultId = testResult.getId();
         try {
            Awaitility.await()
                  .atMost(testRequest.getTimeout() + 1000, TimeUnit.MILLISECONDS)
                  .pollDelay(100, TimeUnit.MILLISECONDS)
                  .pollInterval(200, TimeUnit.MILLISECONDS)
                  .until(() -> !refreshTestResult(microcksContainerHttpEndpoint, testResultId).isInProgress());
         } catch (ConditionTimeoutException timeoutException) {
            log.info("Caught a ConditionTimeoutException for test on {}", testRequest.getTestEndpoint());
         }

         // Return the final result.
         return refreshTestResult(microcksContainerHttpEndpoint, testResultId);
      }
      if (log.isErrorEnabled()) {
         log.error("Couldn't launch on new test on Microcks with status {} ", httpConn.getResponseCode());
         log.error("Error response body is {}", responseContent);
      }
      httpConn.disconnect();
      throw new MicrocksException("Couldn't launch on new test on Microcks. Please check Microcks container logs");
   }


   /**
    * Retrieve messages exchanged during a test on an endpoint (for further investigation or checks).
    * @param testResult The TestResult to get messages for
    * @param operationName The name of the operation to get messages corresponding to test case
    * @return The list of RequestResponsePair representing messages exchanged during test case
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws MicrocksException If Microcks fails retrieving the messages
    */
   public List<RequestResponsePair> getMessagesForTestCase(TestResult testResult, String operationName) throws IOException, MicrocksException {
      return getMessagesForTestCase(getHttpEndpoint(), testResult, operationName);
   }

   /**
    * Retrieve messages exchanged during a test on an endpoint. This may be a fallback to non-static {@code getMessagesForTestCase(TestResult testResult, String operationName)}
    * method if you don't have direct access to the MicrocksContainer instance you want to get messages from.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param testResult The TestResult to get messages for
    * @param operationName The name of the operation to get messages corresponding to test case
    * @return The list of RequestResponsePair representing messages exchanged during test case
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws MicrocksException If Microcks fails retrieving the messages
    */
   public static List<RequestResponsePair> getMessagesForTestCase(String microcksContainerHttpEndpoint, TestResult testResult, String operationName)
         throws IOException, MicrocksException {

      // Build the testCase unique identifier.
      String operation = operationName.replace('/', '!');
      String testCaseId = testResult.getId() + "-" + testResult.getTestNumber() + "-" + URLEncoder.encode(operation);

      URL url = new URL(microcksContainerHttpEndpoint + "/api/tests/" + testResult.getId() + "/messages/" + testCaseId);
      HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
      httpConn.setRequestMethod("GET");
      httpConn.setRequestProperty(HTTP_ACCEPT, APPLICATION_JSON);
      httpConn.setDoOutput(false);

      if (httpConn.getResponseCode() == 200) {
         // Read response content and disconnect Http connection.
         StringBuilder responseContent = getResponseContent(httpConn);
         httpConn.disconnect();

         // Unmarshal and return result.
         return getMapper().readValue(responseContent.toString(),
               mapper.getTypeFactory().constructCollectionType(List.class, RequestResponsePair.class));
      }
      if (log.isErrorEnabled()) {
         log.error("Couldn't retrieve messages status {} ", httpConn.getResponseCode());
      }
      httpConn.disconnect();
      throw new MicrocksException("Couldn't retrieve messages on test on Microcks. Please check Microcks container logs");
   }

   /**
    * Retrieve event messages received during a test on an endpoint (for further investigation or checks).
    * @param testResult The TestResult to get event messages for
    * @param operationName The name of the operation to get event messages corresponding to test case
    * @return The list of UnidirectionalEvent representing events received during test case
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws MicrocksException If Microcks fails retrieving the messages
    */
   public List<UnidirectionalEvent> getEventMessagesForTestCase(TestResult testResult, String operationName) throws IOException, MicrocksException {
      return getEventMessagesForTestCase(getHttpEndpoint(), testResult, operationName);
   }

   /**
    * Retrieve event messages received during a test on an endpoint. This may be a fallback to non-static {@code getEventMessagesForTestCase(TestResult testResult, String operationName)}
    * method if you don't have direct access to the MicrocksContainer instance you want to get messages from.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param testResult The TestResult to get event messages for
    * @param operationName The name of the operation to get event messages corresponding to test case
    * @return The list of UnidirectionalEvent representing events received during test case
    * @throws IOException If connection to Microcks container failed (no route to host, low-level network stuffs)
    * @throws MicrocksException If Microcks fails retrieving the event messages
    */
   public static List<UnidirectionalEvent> getEventMessagesForTestCase(String microcksContainerHttpEndpoint, TestResult testResult, String operationName)
         throws IOException, MicrocksException {

      // Build the testCase unique identifier.
      String operation = operationName.replace('/', '!');
      String testCaseId = testResult.getId() + "-" + testResult.getTestNumber() + "-" + URLEncoder.encode(operation);

      URL url = new URL(microcksContainerHttpEndpoint + "/api/tests/" + testResult.getId() + "/events/" + testCaseId);
      HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
      httpConn.setRequestMethod("GET");
      httpConn.setRequestProperty(HTTP_ACCEPT, APPLICATION_JSON);
      httpConn.setDoOutput(false);

      if (httpConn.getResponseCode() == 200) {
         // Read response content and disconnect Http connection.
         StringBuilder responseContent = getResponseContent(httpConn);
         httpConn.disconnect();

         // Unmarshal and return result.
         return getMapper().readValue(responseContent.toString(),
               mapper.getTypeFactory().constructCollectionType(List.class, UnidirectionalEvent.class));
      }
      if (log.isErrorEnabled()) {
         log.error("Couldn't retrieve messages status {} ", httpConn.getResponseCode());
      }
      httpConn.disconnect();
      throw new MicrocksException("Couldn't retrieve messages on test on Microcks. Please check Microcks container logs");
   }

   /**
    * Download a remote artifact as a primary or main one within the Microcks container.
    * @param remoteArtifactUrl The URL to remote artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws ArtifactLoadException If artifact cannot be correctly downloaded in container (probably not found)
    */
   public void downloadAsMainRemoteArtifact(String remoteArtifactUrl) throws ArtifactLoadException {
      downloadArtifact(getHttpEndpoint(), new RemoteArtifact(remoteArtifactUrl, null), true);
   }

   /**
    * Download a remote artifact as a secondary one within the Microcks container.
    * @param remoteArtifactUrl The URL to remote artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws ArtifactLoadException If artifact cannot be correctly downloaded in container (probably not found)
    */
   public void downloadAsSecondaryRemoteArtifact(String remoteArtifactUrl) throws ArtifactLoadException {
      downloadArtifact(getHttpEndpoint(), new RemoteArtifact(remoteArtifactUrl, null), false);
   }

   /**
    * Download a remote artifact as a primary or main one within the Microcks container.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param remoteArtifactUrl The URL to remote artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws ArtifactLoadException If artifact cannot be correctly downloaded in container (probably not found)
    */
   public static void downloadAsMainRemoteArtifact(String microcksContainerHttpEndpoint, String remoteArtifactUrl) throws ArtifactLoadException {
      downloadArtifact(microcksContainerHttpEndpoint, new RemoteArtifact(remoteArtifactUrl, null), true);
   }

   /**
    * Download a remote artifact as a secondary one within the Microcks container.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param remoteArtifactUrl The URL to remote artifact (OpenAPI, Postman collection, Protobuf, GraphQL schema, ...)
    * @throws ArtifactLoadException If artifact cannot be correctly downloaded in container (probably not found)
    */
   public static void downloadAsSecondaryRemoteArtifact(String microcksContainerHttpEndpoint, String remoteArtifactUrl) throws ArtifactLoadException {
      downloadArtifact(microcksContainerHttpEndpoint, new RemoteArtifact(remoteArtifactUrl, null), false);
   }

   /**
    * Verifies that given Service has been invoked at least one time, for the current invocations' date.
    * @param serviceName    The name of the service to verify invocation for
    * @param serviceVersion The version of the service to verify invocation for
    * @return false if the given service was not found, or if the daily invocation's count is zero. Else, returns true if the daily invocations' count for the given service is at least one.
    */
   public boolean verify(String serviceName, String serviceVersion) {
      return doVerify(getHttpEndpoint(), serviceName, serviceVersion, null);
   }

   /**
    * Verifies that given Service has been invoked at least one time, for the current invocations' date.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param serviceName                   The name of the service to verify invocation for
    * @param serviceVersion                The version of the service to verify invocation for
    * @return false if the given service was not found, or if the daily invocation's count is zero. Else, returns true if the daily invocations' count for the given service is at least one.
    */
   public static boolean verify(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion) {
      return doVerify(microcksContainerHttpEndpoint, serviceName, serviceVersion, null);
   }

   /**
    * Verifies that given Service has been invoked at least one time, for the given invocations' date.
    * In unit testing context, it should be useless to pass the invocations date, prefer calling {@link #verify(String, String)}`.
    * @param serviceName     The name of the service to verify invocation for
    * @param serviceVersion  The version of the service to verify invocation for
    * @param invocationsDate The date to verify invocation for (nullable)
    * @return false if the given service was not found, or if the daily invocation's count is zero. Else, returns true if the daily invocations' count for the given service is at least one.
    */
   public boolean verify(String serviceName, String serviceVersion, Date invocationsDate) {
      return doVerify(getHttpEndpoint(), serviceName, serviceVersion, invocationsDate);
   }

   /**
    * Verifies that given Service has been invoked at least one time, for the given invocations' date.
    * In unit testing context, it should be useless to pass the invocations date, prefer calling {@link #verify(String, String)}`.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param serviceName                   The name of the service to verify invocation for
    * @param serviceVersion                The version of the service to verify invocation for
    * @param invocationsDate               The date to verify invocation for (nullable)
    * @return false if the given service was not found, or if the daily invocation's count is zero. Else, returns true if the daily invocations' count for the given service is at least one.
    */
   public static boolean verify(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion, Date invocationsDate) {
      return doVerify(microcksContainerHttpEndpoint, serviceName, serviceVersion, invocationsDate);
   }

   private static boolean doVerify(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion, Date invocationsDate) {
      DailyInvocationStatistic dailyInvocationStatistic = getServiceInvocations(microcksContainerHttpEndpoint, serviceName, serviceVersion, invocationsDate);

      if (dailyInvocationStatistic == null) {
         return false;
      }

      BigDecimal count = dailyInvocationStatistic.getDailyCount();
      return count != null && count.intValue() != 0;
   }

   /**
    * Get the invocations' count for a given service, identified by its name and version, for the current invocations' date.
    * @param serviceName    The name of the service to get invocations count for
    * @param serviceVersion The version of the service to get invocations count for
    * @return zero if the given service was not found, or has never been invoked. Else, returns the daily count of invocations for the given service.
    */
   public Long getServiceInvocationsCount(String serviceName, String serviceVersion) {
      return doGetServiceInvocationsCount(getHttpEndpoint(), serviceName, serviceVersion, null);
   }

   /**
    * Get the invocations' count for a given service, identified by its name and version, for the current invocations' date.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainers
    * @param serviceName                   The name of the service to get invocations count for
    * @param serviceVersion                The version of the service to get invocations count for
    * @return zero if the given service was not found, or has never been invoked. Else, returns the daily count of invocations for the given service.
    */
   public static Long getServiceInvocationsCount(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion) {
      return doGetServiceInvocationsCount(microcksContainerHttpEndpoint, serviceName, serviceVersion, null);
   }

   /**
    * Get the invocations' count for a given service, identified by its name and version, for the given invocations' date.
    * In unit testing context, it should be useless to pass the invocations date, prefer calling {@link #getServiceInvocationsCount(String, String)}`.
    * @param serviceName     The name of the service to get invocations count for
    * @param serviceVersion  The version of the service to get invocations count for
    * @param invocationsDate nullable
    * @return zero if the given service was not found, or has never been invoked. Else, returns the daily count of invocations for the given service.
    */
   public Long getServiceInvocationsCount(String serviceName, String serviceVersion, Date invocationsDate) {
      return doGetServiceInvocationsCount(getHttpEndpoint(), serviceName, serviceVersion, invocationsDate);
   }

   /**
    * Get the invocations' count for a given service, identified by its name and version, for the given invocations' date.
    * In unit testing context, it should be useless to pass the invocations date, prefer calling {@link #getServiceInvocationsCount(String, String)}`.
    * @param microcksContainerHttpEndpoint The Http endpoint where to reach running MicrocksContainer
    * @param serviceName                   The name of the service to get invocations count for
    * @param serviceVersion                The version of the service to get invocations count for
    * @param invocationsDate               The date to get invocations count for (nullable)
    * @return zero if the given service was not found, or has never been invoked. Else, returns the daily count of invocations for the given service.
    */
   public static Long getServiceInvocationsCount(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion, Date invocationsDate) {
      return doGetServiceInvocationsCount(microcksContainerHttpEndpoint, serviceName, serviceVersion, invocationsDate);
   }

   private static Long doGetServiceInvocationsCount(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion, Date invocationsDate) {
      DailyInvocationStatistic dailyInvocationStatistic = getServiceInvocations(microcksContainerHttpEndpoint, serviceName, serviceVersion, invocationsDate);

      if (dailyInvocationStatistic == null) {
         return 0L;
      }
      BigDecimal count = dailyInvocationStatistic.getDailyCount();
      return count.longValue();
   }


   /**
    * Returns all data from Microcks Metrics REST API about the invocations of a given service.
    */
   private static DailyInvocationStatistic getServiceInvocations(String microcksContainerHttpEndpoint, String serviceName, String serviceVersion, Date invocationsDate) {
      // Encode service name and version and take care of replacing '+' by '%20' as metrics API
      // does not handle '+' in URL path.
      String encodedServiceName = URLEncoder.encode(serviceName).replace("+", "%20");
      String encodedServiceVersion = URLEncoder.encode(serviceVersion).replace("+", "%20");

      String restApiURL = String.format("%s/api/metrics/invocations/%s/%s", microcksContainerHttpEndpoint,
            encodedServiceName, encodedServiceVersion);

      if (invocationsDate != null) {
         restApiURL += "?day=" + METRICS_API_DAY_FORMATTER.format(invocationsDate);
      }

      try {
         Thread.sleep(100); // to avoid race condition issue when requesting Microcks Metrics REST API
      } catch (InterruptedException e) {
         log.warn("Failed to sleep before calling Microcks API", e);
         Thread.currentThread().interrupt();
      }

      try {
         StringBuilder content = getFromRestApi(restApiURL);
         return content.length() == 0 ? null : getMapper().readValue(content.toString(), DailyInvocationStatistic.class);
      } catch (IOException e) {
         log.warn("Failed to get service's invocations at address " + restApiURL, e);
      }

      return null;
   }

   private void importArtifact(String artifactPath, boolean mainArtifact) {
      URL resource = Thread.currentThread().getContextClassLoader().getResource(artifactPath);
      if (resource == null) {
         resource = MicrocksContainer.class.getClassLoader().getResource(artifactPath);
         if (resource == null) {
            log.warn("Could not load classpath artifact: {}", artifactPath);
            throw new ArtifactLoadException("Error while importing artifact: " + artifactPath);
         }
      }
      try {
         // Decode resource file path that may contain spaces converted into %20.
         File resourceFile = new File(URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8.name()));
         MicrocksContainer.importArtifact(getHttpEndpoint(), resourceFile, mainArtifact);
      } catch (Exception e) {
         log.error("Could not load classpath artifact: {}", artifactPath);
         throw new ArtifactLoadException("Error while importing artifact: " + artifactPath, e);
      }
   }

   private static void downloadArtifact(String microcksContainerHttpEndpoint, RemoteArtifact remoteArtifact, boolean mainArtifact) throws ArtifactLoadException {
      try {
         // Use the artifact/download endpoint to download the artifact.
         URL url = new URL(microcksContainerHttpEndpoint + "/api/import");
         HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
         httpConn.setUseCaches(false);
         httpConn.setRequestMethod("POST");
         httpConn.setDoOutput(true);

         String requestBody = "mainArtifact=" + mainArtifact + "&url=" + remoteArtifact.getUrl();

         if (remoteArtifact.getSecretName() != null) {
            requestBody += "&secretName=" + remoteArtifact.getSecretName();
         }

         // Write the request body to the output stream of the connection
         try (OutputStream os = httpConn.getOutputStream();
              BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {
            writer.write(requestBody);
            writer.flush();
         }

         if (httpConn.getResponseCode() != 201) {
            // Read response content for diagnostic purpose and disconnect.
            StringBuilder responseContent = getResponseContent(httpConn);
            httpConn.disconnect();

            log.error("Artifact has not been correctly downloaded: {}", responseContent);
            throw new MicrocksException("Artifact has not been correctly downloaded: " + responseContent);
         }
         // Disconnect Http connection.
         httpConn.disconnect();
      } catch (Exception e) {
         log.error("Could not load remote artifact: {}", remoteArtifact.getUrl());
         throw new ArtifactLoadException("Error while importing remote artifact: " + remoteArtifact.getUrl(), e);
      }
   }

   private void importSnapshot(String snapshotPath) {
      URL resource = Thread.currentThread().getContextClassLoader().getResource(snapshotPath);
      if (resource == null) {
         resource = MicrocksContainer.class.getClassLoader().getResource(snapshotPath);
         if (resource == null) {
            log.warn("Could not load classpath snapshot: {}", snapshotPath);
            throw new ArtifactLoadException("Error while importing snapshot: " + snapshotPath);
         }
      }
      try {
         MicrocksContainer.importSnapshot(getHttpEndpoint(), new File(resource.getFile()));
      } catch (Exception e) {
         log.error("Could not load classpath snapshot: {}", snapshotPath);
         throw new ArtifactLoadException("Error while importing snapshot: " + snapshotPath, e);
      }
   }

   private void createSecret(Secret secret) {
      try {
         URL url = new URL(getHttpEndpoint() + "/api/secrets");
         HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
         httpConn.setRequestMethod("POST");
         httpConn.setRequestProperty(HTTP_CONTENT_TYPE, APPLICATION_JSON);
         httpConn.setRequestProperty(HTTP_ACCEPT, APPLICATION_JSON);
         httpConn.setDoOutput(true);

         String requestBody = getMapper().writeValueAsString(secret);

         try (OutputStream os = httpConn.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
            os.flush();
         }

         if (httpConn.getResponseCode() != 201) {
            // Read response content for diagnostic purpose and disconnect.
            StringBuilder responseContent = getResponseContent(httpConn);
            httpConn.disconnect();

            log.error("Secret has not been correctly created: {}", responseContent);
            throw new MicrocksException("Secret has not been correctly created: " + responseContent);
         }
         // Disconnect Http connection.
         httpConn.disconnect();
      } catch (Exception e) {
         log.warn("Error while creating Secret: {}", secret.getName());
         throw new SecretCreationException("Error while creating Secret", e);
      }
   }

   private static ObjectMapper getMapper() {
      if (mapper == null) {
         mapper = new ObjectMapper();
         // Do not include null values in both serialization and deserialization.
         mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
         mapper.setPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
      }
      return mapper;
   }

   private static StringBuilder getResponseContent(HttpURLConnection httpConn) throws IOException {
      StringBuilder responseContent = new StringBuilder();
      try (BufferedReader br = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), StandardCharsets.UTF_8))) {
         String responseLine;
         while ((responseLine = br.readLine()) != null) {
            responseContent.append(responseLine.trim());
         }
      }
      return responseContent;
   }

   private static HttpURLConnection uploadFileToMicrocks(URL microcksApiURL, File file, String contentType) throws IOException {
      // Creates a unique boundary based on time stamp
      String boundary = "===" + System.currentTimeMillis() + "===";

      HttpURLConnection httpConn = (HttpURLConnection) microcksApiURL.openConnection();
      httpConn.setUseCaches(false);
      httpConn.setDoOutput(true);
      httpConn.setDoInput(true);
      httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

      try (OutputStream os = httpConn.getOutputStream();
           PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
           FileInputStream is = new FileInputStream(file)) {

         writer.append("--" + boundary)
               .append(HTTP_UPLOAD_LINE_FEED)
               .append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"")
               .append(HTTP_UPLOAD_LINE_FEED)
               .append("Content-Type: " + contentType)
               .append(HTTP_UPLOAD_LINE_FEED)
               .append("Content-Transfer-Encoding: binary")
               .append(HTTP_UPLOAD_LINE_FEED)
               .append(HTTP_UPLOAD_LINE_FEED);
         writer.flush();

         byte[] buffer = new byte[4096];
         int bytesRead = -1;
         while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
         }
         os.flush();

         // Finalize writer with a boundary before flushing.
         writer.append(HTTP_UPLOAD_LINE_FEED)
               .append("--" + boundary + "--")
               .append(HTTP_UPLOAD_LINE_FEED).flush();
      }

      return httpConn;
   }

   private static TestResult refreshTestResult(String microcksContainerHttpEndpoint, String testResultId) throws IOException {
      StringBuilder content = getFromRestApi(microcksContainerHttpEndpoint + "/api/tests/" + testResultId);

      return getMapper().readValue(content.toString(), TestResult.class);
   }

   /**
    * Does a GET HTTP call to Microcks REST API and expecting to obtain a 200 response with a `application/json` body:
    * returns it as a StringBuilder.
    */
   private static StringBuilder getFromRestApi(String restApiURL) throws IOException {
      // Build a new client on correct API endpoint.
      URL url = new URL(restApiURL);
      HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
      httpConn.setRequestMethod("GET");
      httpConn.setRequestProperty(HTTP_ACCEPT, APPLICATION_JSON);
      httpConn.setDoOutput(false);

      // Send the request and parse response body.
      StringBuilder content = getResponseContent(httpConn);

      // Disconnect Http connection.
      httpConn.disconnect();
      return content;
   }

   public static class ArtifactLoadException extends RuntimeException {

      public ArtifactLoadException(String message) {
         super(message);
      }
      public ArtifactLoadException(String message, Throwable cause) {
         super(message, cause);
      }
   }

   public static class SecretCreationException extends RuntimeException {

      public SecretCreationException(String message) {
         super(message);
      }
      public SecretCreationException(String message, Throwable cause) {
         super(message, cause);
      }
   }
}
