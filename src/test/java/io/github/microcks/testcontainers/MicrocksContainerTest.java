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

import io.github.microcks.testcontainers.model.Header;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for MicrocksContainer class.
 * @author laurent
 */
public class MicrocksContainerTest {

   private static final String IMAGE = "quay.io/microcks/microcks-uber:nightly";
   private static final DockerImageName MICROCKS_IMAGE = DockerImageName.parse(IMAGE);

   private static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:01");
   private static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");

   @Test
   public void testMockingFunctionality() throws Exception {
      try (
            MicrocksContainer microcks = new MicrocksContainer(IMAGE);
      ) {
         microcks.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());

         microcks.importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
         microcks.importAsSecondaryArtifact(new File("target/test-classes/apipastries-postman-collection.json"));
         testMockEndpoints(microcks);
         testMicrocksMockingFunctionality(microcks);
      }
   }

   @Test
   public void testContractTestingFunctionality() throws Exception {
      try (
            Network network = Network.newNetwork();
            MicrocksContainer microcks = new MicrocksContainer(MICROCKS_IMAGE)
                  .withNetwork(network);
            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_IMAGE)
                  .withNetwork(network)
                  .withNetworkAliases("bad-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3001.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_IMAGE)
                  .withNetwork(network)
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3002.*", 1));

      ) {
         microcks.start();
         badImpl.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());

         microcks.importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
         testMicrocksContractTestingFunctionality(microcks, badImpl, goodImpl);
      }
   }

   private void testMicrocksConfigRetrieval(String endpointUrl) {
      Response keycloakConfig = RestAssured.given().when()
            .get(endpointUrl + "/api/keycloak/config")
            .thenReturn();

      assertEquals(200, keycloakConfig.getStatusCode());
   }

   private void testMockEndpoints(MicrocksContainer microcks) {
      String baseWsUrl = microcks.getSoapMockEndpoint("Pastries Service", "1.0");
      assertEquals(microcks.getHttpEndpoint() + "/soap/Pastries Service/1.0", baseWsUrl);

      String baseApiUrl = microcks.getRestMockEndpoint("API Pastries", "0.0.1");
      assertEquals(microcks.getHttpEndpoint() + "/rest/API Pastries/0.0.1", baseApiUrl);

      String baseGraphUrl = microcks.getGraphQLMockEndpoint("Pastries Graph", "1");
      assertEquals(microcks.getHttpEndpoint() + "/graphql/Pastries Graph/1", baseGraphUrl);

      String baseGrpcUrl = microcks.getGrpcMockEndpoint();
      assertEquals("grpc://" + microcks.getHost() + ":" + microcks.getMappedPort(MicrocksContainer.MICROCKS_GRPC_PORT), baseGrpcUrl);
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
      testRequest.setRunnerType(TestRunnerType.OPEN_API_SCHEMA.name());
      testRequest.setTestEndpoint("http://bad-impl:3001");
      testRequest.setTimeout(2000l);

      // First test should fail with validation failure messages.
      TestResult testResult = microcks.testEndpoint(testRequest);

      /*
      System.err.println(microcks.getLogs());
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertFalse(testResult.isSuccess());
      assertEquals("http://bad-impl:3001", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertTrue(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("object has missing required properties"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
            .testEndpoint("http://good-impl:3002")
            .timeout(2000L)
            .build();

      testResult = microcks.testEndpoint(otherTestRequestDTO);
      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3002", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertEquals("", testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());

      // Test request with operations headers
      List<Header> headers = new ArrayList<>();
      Header requestHeader = new Header();
      requestHeader.setName("X-Custom-Header-1");
      requestHeader.setValues("value1,value2,value3");
      headers.add(requestHeader);
      Map<String, List<Header>> operationsHeaders = new HashMap<>();
      operationsHeaders.put("GET /pastries", headers);
      TestRequest testRequestWithHeadersDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
            .testEndpoint("http://good-impl:3002")
            .operationsHeaders(operationsHeaders)
            .timeout(2000L)
            .build();

      testResult = microcks.testEndpoint(testRequestWithHeadersDTO);
      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3002", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertEquals("", testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
      assertEquals(1, testResult.getOperationsHeaders().size());
      assertTrue(testResult.getOperationsHeaders().containsKey("GET /pastries"));
      assertEquals(1, testResult.getOperationsHeaders().get("GET /pastries").size());
      Header resultHeader = testResult.getOperationsHeaders().get("GET /pastries").iterator().next();
      assertEquals("X-Custom-Header-1", resultHeader.getName());
      List<String> expectedValues = Arrays.asList("value1", "value2", "value3");
      assertTrue(
            Arrays.asList(resultHeader.getValues().split(",")).containsAll(expectedValues),
            resultHeader.getValues() + " should contain in any order values : " + String.join(",", expectedValues)
      );
   }
}
