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

import java.util.Date;
/**
 * Data Transfer Object for grouping information about a webhook registration.
 * This will be mapped to WebhookRegistrationRequestDTO on the server side.
 * @author laurent
 */
public class WebhookRegistrationRequest {

   private Date expiresAt;
   private String operationId;
   private String targetUrl;
   private Long frequency;
   private Integer errorCountThreshold;

   public WebhookRegistrationRequest(String operationId, String targetUrl, Long frequency, Integer errorCountThreshold) {
      this.operationId = operationId;
      this.targetUrl = targetUrl;
      this.frequency = frequency;
      this.errorCountThreshold = errorCountThreshold;
      this.expiresAt = new Date(System.currentTimeMillis() + (2 * 24 * 3600 * 1000L));
   }

   public WebhookRegistrationRequest(String operationId, String targetUrl) {
      this(operationId, targetUrl, 3 * 1000L, 5);
   }

   public Date getExpiresAt() {
      return expiresAt;
   }

   public void setExpiresAt(Date expiresAt) {
      this.expiresAt = expiresAt;
   }

   public String getOperationId() {
      return operationId;
   }

   public void setOperationId(String operationId) {
      this.operationId = operationId;
   }

   public String getTargetUrl() {
      return targetUrl;
   }

   public void setTargetUrl(String targetUrl) {
      this.targetUrl = targetUrl;
   }

   public Long getFrequency() {
      return frequency;
   }

   public void setFrequency(Long frequency) {
      this.frequency = frequency;
   }

   public Integer getErrorCountThreshold() {
      return errorCountThreshold;
   }

   public void setErrorCountThreshold(Integer errorCountThreshold) {
      this.errorCountThreshold = errorCountThreshold;
   }
}
