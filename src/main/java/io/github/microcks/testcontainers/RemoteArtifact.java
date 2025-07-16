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
 * Immutable bean representing a remote artifact with its URL and associated secret name.
 * Because we cannot use record with our Java 8 baseline, we use a simple POJO.
 * @author laurent
 */
public class RemoteArtifact {
   private final String url;
   private final String secretName;

   /**
    * Constructor for RemoteArtifact.
    * @param url The URL of the remote artifact.
    * @param secretName The name of the secret to use for accessing the artifact.
    */
   public RemoteArtifact(String url, String secretName) {
      this.url = url;
      this.secretName = secretName;
   }

   /**
    * Build a RemoteArtifact
    * @param url The URL of the remote artifact.
    * @param secretName The name of the secret to use for accessing the artifact.
    * @return A new instance of RemoteArtifact.
    */
   public static RemoteArtifact of(String url, String secretName) {
      return new RemoteArtifact(url, secretName);
   }

   public String getUrl() {
      return url;
   }
   public String getSecretName() {
      return secretName;
   }
}
