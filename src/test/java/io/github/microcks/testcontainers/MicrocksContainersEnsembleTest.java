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

import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.github.microcks.testcontainers.model.TestStepResult;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is a test case for MicrocksContainersEnsemble class.
 * @author laurent
 */
public class MicrocksContainersEnsembleTest {

   private static final String IMAGE = "quay.io/microcks/microcks-uber:1.8.0";

   private static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");
   private static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:03");

   private static final DockerImageName BAD_PASTRY_ASYNC_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo-async:01");
   private static final DockerImageName GOOD_PASTRY_ASYNC_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo-async:02");

   @Test
   public void testMockingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(IMAGE)
                  .withMainArtifacts("apipastries-openapi.yaml")
                  .withSecondaryArtifacts("apipastries-postman-collection.json")
                  .withAccessToHost(true);
      ) {
         ensemble.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksMockingFunctionality(ensemble.getMicrocksContainer());
      }
   }

   @Test
   public void testPostmanContractTestingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(IMAGE).withPostman();

            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("bad-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3002.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3003.*", 1));
      ) {
         ensemble.start();
         badImpl.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         ensemble.getMicrocksContainer().importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
         ensemble.getMicrocksContainer().importAsSecondaryArtifact(new File("target/test-classes/apipastries-postman-collection.json"));
         testMicrocksContractTestingFunctionality(ensemble.getMicrocksContainer(), badImpl, goodImpl);
      }
   }

   @Test
   public void testAsyncFeatureMockingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble("quay.io/microcks/microcks-uber:nightly")
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature();
      ) {
         ensemble.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncMockingFunctionality(ensemble);
      }
   }

   @Test
   public void testAsyncFeatureTestingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble("quay.io/microcks/microcks-uber:nightly")
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature();

            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_ASYNC_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("bad-impl")
                  .waitingFor(Wait.forLogMessage(".*Starting WebSocket server on ws://localhost:4001/websocket.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_ASYNC_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Starting WebSocket server on ws://localhost:4002/websocket.*", 1));
      ) {
         ensemble.start();
         badImpl.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncContractTestingFunctionality(ensemble);
      }
   }

   private void testMicrocksConfigRetrieval(String endpointUrl) {
      Response keycloakConfig = RestAssured.given().when()
            .get(endpointUrl + "/api/keycloak/config")
            .thenReturn();

      assertEquals(200, keycloakConfig.getStatusCode());
   }

   private void testMicrocksMockingFunctionality(MicrocksContainer microcks) {
      String baseApiUrl = microcks.getRestMockEndpoint("API Pastries", "0.0.1");

      // Check that mock from main/primary artifact has been loaded.
      Response millefeuille = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Millefeuille")
            .thenReturn();

      assertEquals(200, millefeuille.getStatusCode());
      assertEquals("Millefeuille", millefeuille.jsonPath().get("name"));
      //millefeuille.getBody().prettyPrint();

      // Check that mock from secondary artifact has been loaded.
      Response eclairChocolat = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Eclair Chocolat")
            .thenReturn();

      assertEquals(200, eclairChocolat.getStatusCode());
      assertEquals("Eclair Chocolat", eclairChocolat.jsonPath().get("name"));
      //eclairChocolat.getBody().prettyPrint();
   }

   private void testMicrocksContractTestingFunctionality(MicrocksContainer microcks, GenericContainer badImpl, GenericContainer goodImpl) throws Exception {
      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("API Pastries:0.0.1");
      testRequest.setRunnerType(TestRunnerType.POSTMAN.name());
      testRequest.setTestEndpoint("http://bad-impl:3002");
      testRequest.setTimeout(5000l);

      // First test should fail with validation failure messages.
      TestResult testResult = microcks.testEndpoint(testRequest);

      /*
      System.err.println(microcks.getLogs());
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
       */

      assertFalse(testResult.isSuccess());
      assertEquals("http://bad-impl:3002", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      // Postman runner stop at first failure so there's just 1 testStepResult per testCaseResult
      assertEquals(1, testResult.getTestCaseResults().get(0).getTestStepResults().size());
      // Order is not deterministic so it could be a matter of invalid size, invalid name or invalid price.
      assertTrue(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid size in response pastries")
            || testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid name in response pastry")
            || testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid price in response pastry"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.POSTMAN.name())
            .testEndpoint("http://good-impl:3003")
            .timeout(5000L)
            .build();

      testResult = microcks.testEndpoint(otherTestRequestDTO);

      /*
      mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
       */

      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3003", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }

   private void testMicrocksAsyncMockingFunctionality(MicrocksContainersEnsemble ensemble) {
      String wsEndpoint = ensemble.getAsyncMinionContainer().getWSMockEndpoint("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      List<String> messages = new ArrayList<>();
      try {
         // Open a WebSocket client.
         WebSocketClient wsClient = new WebSocketClient(new URI(wsEndpoint), new Draft_6455()) {
            @Override
            public void onMessage(String message) {
               messages.add(message);
            }
            @Override
            public void onOpen(ServerHandshake handshake) {
            }
            @Override
            public void onClose(int code, String reason, boolean remote) {
            }
            @Override
            public void onError(Exception e) {
            }
         };
         wsClient.connect();

         // Wait 7 seconds for messages from Async Minion WebSocket to get at least 2 messages.
         await()
               .pollDelay(7, TimeUnit.SECONDS)
               .untilAsserted(() -> assertTrue(true));
      } catch (URISyntaxException ex) {
         fail("URISyntaxException exception: " + ex.getMessage());
      }

      assertFalse(messages.isEmpty());
      for (String message : messages) {
         assertEquals(expectedMessage, message);
      }
   }

   private void testMicrocksAsyncContractTestingFunctionality(MicrocksContainersEnsemble ensemble) throws Exception {

      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("ws://bad-impl:4001/websocket");
      testRequest.setTimeout(7000l);

      // First test should fail with validation failure messages.
      // We're using a CompletableFuture here because in real test, you may want
      // to execute your application and send a bunch of messages.
      CompletableFuture<TestResult> testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);
      long start = System.currentTimeMillis();

      TestResult testResult = null;
      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      // Be sure the completion mechanism actually works and that you get the result only
      // after the timeout.
      long duration = System.currentTimeMillis() - start;
      assertTrue(duration > 7000);

      /*
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertFalse(testResult.isSuccess());
      assertEquals("ws://bad-impl:4001/websocket", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      TestStepResult testStepResult = testResult.getTestCaseResults().get(0).getTestStepResults().get(0);
      assertTrue(testStepResult.getMessage().contains("object has missing required properties ([\"status\"]"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA.name())
            .testEndpoint("ws://good-impl:4002/websocket")
            .timeout(7000L)
            .build();

      try {
         testResult = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequestDTO).get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      /*
      mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertTrue(testResult.isSuccess());
      assertEquals("ws://good-impl:4002/websocket", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }
}
