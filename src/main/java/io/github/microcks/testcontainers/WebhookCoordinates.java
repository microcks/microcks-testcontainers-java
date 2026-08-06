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

/**
 * Immutable bean representing the coordinates of a webhook to register.
 * Because we cannot use record with our Java 8 baseline, we use a simple POJO.
 * @author laurent
 */
public class WebhookCoordinates {

   private String serviceId;
   private String operationName;
   private String targetUrl;

   /**
    * Create a new WebhookCoordinates instance.
    * @param serviceId The ID of the service to which the operation belongs.
    * @param operationName The name of the operation to register a webhook for.
    * @param targetUrl The target URL to which the webhook should be sent.
    */
   public WebhookCoordinates(String serviceId, String operationName, String targetUrl) {
      this.serviceId = serviceId;
      this.operationName = operationName;
      this.targetUrl = targetUrl;
   }

   /**
    * Build a new WebhookCoordinates instance.
    * @param serviceId The ID of the service to which the operation belongs.
    * @param operationName The name of the operation to register a webhook for.
    * @param targetUrl The target URL to which the webhook should be sent.
    * @return A new WebhookCoordinates instance.
    */
   public static WebhookCoordinates of(String serviceId, String operationName, String targetUrl) {
      return new WebhookCoordinates(serviceId, operationName, targetUrl);
   }

   public String getServiceId() {
      return serviceId;
   }

   public String getOperationName() {
      return operationName;
   }

   public String getTargetUrl() {
      return targetUrl;
   }
}
