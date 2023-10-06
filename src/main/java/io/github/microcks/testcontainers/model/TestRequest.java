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
package io.github.microcks.testcontainers.model;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer object for grouping base information to launch a test (and thus create a TestResult).
 * Such requests have 3 mandatory parameters: `serviceId`, `testEndpoint` and `runnerType` (string representation
 * of a {@link io.github.microcks.testcontainers.model.TestRunnerType}).
 * @author laurent
 */
public class TestRequest {

   private String serviceId;
   private String testEndpoint;
   private String runnerType;
   private String secretName;
   private Long timeout = 5000L;
   private List<String> filteredOperations;
   private Map<String, List<Header>> operationsHeaders;

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public String getTestEndpoint() {
      return testEndpoint;
   }

   public void setTestEndpoint(String testEndpoint) {
      this.testEndpoint = testEndpoint;
   }

   public String getRunnerType() {
      return runnerType;
   }

   public void setRunnerType(String runnerType) {
      this.runnerType = runnerType;
   }

   public String getSecretName() {
      return secretName;
   }

   public void setSecretName(String secretName) {
      this.secretName = secretName;
   }

   public Long getTimeout() {
      return timeout;
   }

   public void setTimeout(Long timeout) {
      this.timeout = timeout;
   }

   public List<String> getFilteredOperations() {
      return filteredOperations;
   }

   public void setFilteredOperations(List<String> filteredOperations) {
      this.filteredOperations = filteredOperations;
   }

   public Map<String, List<Header>> getOperationsHeaders() {
      return operationsHeaders;
   }

   public void setOperationsHeaders(Map<String, List<Header>> operationsHeaders) {
      this.operationsHeaders = operationsHeaders;
   }


   /**
    * Builder/Fluent API for creating TestRequestDTO instances.
    */
   public static class Builder {
      private String serviceId;
      private String testEndpoint;
      private String runnerType;
      private String secretName;
      private Long timeout;
      private List<String> filteredOperations;
      private Map<String, List<Header>> operationsHeaders;

      public Builder serviceId(String serviceId) {
         this.serviceId = serviceId;
         return this;
      }

      public Builder testEndpoint(String testEndpoint) {
         this.testEndpoint = testEndpoint;
         return this;
      }

      public Builder runnerType(String runnerType) {
         this.runnerType = runnerType;
         return this;
      }

      public Builder secretName(String secretName) {
         this.secretName = secretName;
         return this;
      }

      public Builder timeout(Long timeout) {
         this.timeout = timeout;
         return this;
      }

      public Builder filteredOperations(List<String> filteredOperations) {
         this.filteredOperations = filteredOperations;
         return this;
      }

      public Builder operationsHeaders(Map<String, List<Header>> operationsHeaders) {
         this.operationsHeaders = operationsHeaders;
         return this;
      }

      /**
       * Build a new TestRequestDTO instance after having initialized the different properties.
       * @return A new TestRequestDTO instance
       */
      public TestRequest build() {
         // Build a request considering mandatory props are there.
         TestRequest request = new TestRequest();
         request.setServiceId(serviceId);
         request.setRunnerType(runnerType);
         request.setTestEndpoint(testEndpoint);
         // Now deal with optional properties.
         if (this.secretName != null) {
            request.setSecretName(this.secretName);
         }
         if (this.timeout != null) {
            request.setTimeout(this.timeout);
         }
         if (this.filteredOperations != null) {
            request.setFilteredOperations(this.filteredOperations);
         }
         if (this.operationsHeaders != null) {
            request.setOperationsHeaders(this.operationsHeaders);
         }
         return request;
      }
   }
}
