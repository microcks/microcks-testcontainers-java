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
package io.github.microcks.testcontainers.connection;

/**
 * A simple bean representing an Amazon service connection settings.
 * @author laurent
 */
public class AmazonServiceConnection {

   private String region;
   private String endpointOverride;
   private String accessKey;
   private String secretKey;

   /**
    * Create a AmazonServiceConnection.
    * @param region The AWS region to connect to
    * @param accessKey The AWS AccessKey identifier
    * @param secretKey The AWS SecretKey identifier
    */
   public AmazonServiceConnection(String region, String accessKey, String secretKey) {
      this.region = region;
      this.accessKey = accessKey;
      this.secretKey = secretKey;
   }

   /**
    * Create a AmazonServiceConnection with endpoint override.
    * @param region The AWS region to connect to
    * @param accessKey The AWS AccessKey identifier
    * @param secretKey The AWS SecretKey identifier
    * @param endpointOverride The endpoint override URI
    */
   public AmazonServiceConnection(String region, String accessKey, String secretKey, String endpointOverride) {
      this.region = region;
      this.accessKey = accessKey;
      this.secretKey = secretKey;
      this.endpointOverride = endpointOverride;
   }

   public String getRegion() {
      return region;
   }

   public String getAccessKey() {
      return accessKey;
   }

   public String getSecretKey() {
      return secretKey;
   }

   public String getEndpointOverride() {
      return endpointOverride;
   }
}
