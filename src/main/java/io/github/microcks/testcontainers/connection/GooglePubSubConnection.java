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
 * A simple bean representing a Google Pub/Sub connection settings.
 * @author laurent
 */
public class GooglePubSubConnection {

   private final String projectId;
   private final String emulatorHost;

   /**
    * Create a GooglePubSubConnection.
    * @param projectId The GCP Project ID
    * @param emulatorHost The Pub/Sub emulator host (including port)
    */
   public GooglePubSubConnection(String projectId, String emulatorHost) {
      this.projectId = projectId;
      this.emulatorHost = emulatorHost;
   }

   public String getProjectId() {
      return projectId;
   }

   public String getEmulatorHost() {
      return emulatorHost;
   }
}
