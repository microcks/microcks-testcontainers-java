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

import org.testcontainers.utility.DockerImageName;

public class TestConstants {

   private static final String LATEST = "1.13.1";
   private static final String LATEST_NATIVE = LATEST + "-native";

   public static final String LATEST_IMAGE = "quay.io/microcks/microcks-uber:" + LATEST;
   public static final String LATEST_NATIVE_IMAGE = "quay.io/microcks/microcks-uber:" + LATEST_NATIVE;
   public static final String NIGHTLY_IMAGE = "quay.io/microcks/microcks-uber:nightly";
   public static final String NIGHTLY_NATIVE_IMAGE = "quay.io/microcks/microcks-uber:nightly-native";

   public static final String ASYNC_LATEST_IMAGE = "quay.io/microcks/microcks-uber-async-minion:" + LATEST;
   public static final String ASYNC_LATEST_NATIVE_IMAGE = "quay.io/microcks/microcks-uber-async-minion:" + LATEST_NATIVE;
   public static final String ASYNC_NIGHTLY_IMAGE = "quay.io/microcks/microcks-uber-async-minion:nightly";
   public static final String ASYNC_NIGHTLY_NATIVE_IMAGE = "quay.io/microcks/microcks-uber-async-minion:nightly-native";

   public static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");
   public static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:03");

   public static final DockerImageName BAD_PASTRY_ASYNC_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo-async:01");
   public static final DockerImageName GOOD_PASTRY_ASYNC_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo-async:02");
}
