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
 * A simple bean representing a generic authenticated connection to a remote server.
 * @author laurent
 */
public class GenericConnection {

   private final String server;
   private final String username;
   private final String password;

   /**
    * Create a GenericConnection.
    * @param server The bootstrap server url for remote target
    * @param username The username for connecting remote server
    * @param password The password for connecting remote server
    */
   public GenericConnection(String server, String username, String password) {
      this.server = server;
      this.username = username;
      this.password = password;
   }

   public String getServer() {
      return server;
   }

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }
}
