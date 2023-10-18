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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for MicrocksContainersEnsemble class.
 * @author laurent
 */
public class MicrocksContainersEnsembleTest {

   private static final String IMAGE = "quay.io/microcks/microcks-uber:1.8.0";

   private static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");
   private static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:03");

   @Test
   public void testPostmanContractTestingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(IMAGE);

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

   private void testMicrocksConfigRetrieval(String endpointUrl) {
      Response keycloakConfig = RestAssured.given().when()
            .get(endpointUrl + "/api/keycloak/config")
            .thenReturn();

      assertEquals(200, keycloakConfig.getStatusCode());
   }

   private void testMicrocksContractTestingFunctionality(MicrocksContainer microcks, GenericContainer badImpl, GenericContainer goodImpl) throws Exception {
      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("API Pastries:0.0.1");
      testRequest.setRunnerType(TestRunnerType.POSTMAN.name());
      testRequest.setTestEndpoint("http://bad-impl:3002");
      testRequest.setTimeout(2000l);

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
      assertTrue(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid size in response pastries")
            || testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid name in response pastry")
            || testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid price in response pastry"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.POSTMAN.name())
            .testEndpoint("http://good-impl:3003")
            .timeout(3000L)
            .build();

      testResult = microcks.testEndpoint(otherTestRequestDTO);

      System.err.println(microcks.getLogs());
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));

      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3003", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }
}
