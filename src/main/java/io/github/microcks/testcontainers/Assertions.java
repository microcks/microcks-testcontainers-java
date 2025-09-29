package io.github.microcks.testcontainers;

import io.github.microcks.testcontainers.model.TestResult;

import org.junit.jupiter.api.AssertionFailureBuilder;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Assertion helpers for easily checking TestResult instances.
 * @author laurent
 */
public class Assertions {

   /**
    * Assert that the given test result is a success. If not, throw an AssertionFailedError with all the
    * underlying failures from all the test steps in test cases.
    * @param testResult The test result to check
    */
   public static void assertSuccess(TestResult testResult) {
      List<Throwable> failures = new ArrayList<>();
      if (!testResult.isSuccess()) {
         testResult.getTestCaseResults().stream()
               .filter(testCaseResult -> !testCaseResult.isSuccess())
               .forEach(testCaseResult -> testCaseResult.getTestStepResults().stream()
                     .filter(testStepResult -> !testResult.isSuccess())
                     .forEach(testStepResult -> {
                        AssertionFailedError failureError = AssertionFailureBuilder.assertionFailure()
                              .message("Message '" + testStepResult.getRequestName() + "' of operation '"
                                    + testCaseResult.getOperationName() + "' failed")
                              .reason(String.join(" && ", testStepResult.getMessage().split("\n")))
                              .build();

                        failures.add(failureError);
                     }));

         if (!failures.isEmpty()) {
            throw new MultipleFailuresError("Test '" + testResult.getId() + "' has failed", failures);
         }
         throw new AssertionFailedError("Test '" + testResult.getId() + "' is not a success, but has no failure details");
      }
   }

   /**
    * Assert that the given test result has succeeded for the given operation name. If not, throw an
    * AssertionFailedError with all the underlying failures from all the test steps in the test case
    * @param testResult The test result to check
    * @param operationName The operation name to check (ie. the test case name)
    */
   public static void assertSuccess(TestResult testResult, String operationName) {
      AtomicBoolean foundTestCase = new AtomicBoolean(false);
      testResult.getTestCaseResults().stream()
            .filter(testCase -> testCase.getOperationName().equalsIgnoreCase(operationName))
            .forEach(testCase -> {
               foundTestCase.set(true);
               if (!testCase.isSuccess()) {
                  List<Throwable> failures = new ArrayList<>();
                  testCase.getTestStepResults().stream()
                        .filter(testStep -> !testStep.isSuccess())
                        .forEach(testStep -> {
                           AssertionFailedError failureError = AssertionFailureBuilder.assertionFailure()
                                 .message("Message '" + testStep.getRequestName() + "' of operation '"
                                       + testCase.getOperationName() + "' failed")
                                 .reason(String.join(" && ", testStep.getMessage().split("\n")))
                                 .build();

                           failures.add(failureError);
                        });
                  if (!failures.isEmpty()) {
                     throw new MultipleFailuresError("Test '" + testResult.getId() + "' for operation '"
                           + operationName + "' has failed", failures);
                  }
               }
            });
      if (!foundTestCase.get()) {
         throw new AssertionFailedError("Test '" + testResult.getId() + "' has no test case for operation '"
               + operationName + "'");
      }
   }

   /**
    * Assert that the given test result has succeeded for the given operation name and message name. If not, throw an
    * AssertionFailedError.
    * @param testResult The test result to check
    * @param operationName The operation name to check (ie. the test case name)
    * @param messageName The message name to check (ie. the test step name)
    */
   public static void assertSuccess(TestResult testResult, String operationName, String messageName) {
      AtomicBoolean foundTestCase = new AtomicBoolean(false);
      AtomicBoolean foundTestStep = new AtomicBoolean(false);
      testResult.getTestCaseResults().stream()
            .filter(testCase -> testCase.getOperationName().equalsIgnoreCase(operationName))
            .forEach(testCase -> {
               foundTestCase.set(true);
               testCase.getTestStepResults().stream()
                     .filter(testStep -> testStep.getRequestName().equalsIgnoreCase(messageName))
                     .forEach(testStep -> {
                        foundTestStep.set(true);
                        if (!testStep.isSuccess()) {
                           throw new AssertionFailedError("Test '" + testResult.getId() + "' for operation '"
                                 + operationName + "' and message '" + messageName + "' has failed: "
                                 + testStep.getMessage());
                        }
                     });
               if (!foundTestStep.get()) {
                  throw new AssertionFailedError("Test '" + testResult.getId() + "' has no test step for operation '"
                        + operationName + "' and message '" + messageName + "'");
               }
            });
      if (!foundTestCase.get()) {
         throw new AssertionFailedError("Test '" + testResult.getId() + "' has no test case for operation '"
               + operationName + "'");
      }
   }

   /**
    * Assert that the given test result has failed for the given operation name. If not, throw an
    * AssertionFailedError.
    * @param testResult The test result to check
    * @param operationName The operation name to check (ie. the test case name)
    */
   public static void assertFailure(TestResult testResult, String operationName) {
      AtomicBoolean foundTestCase = new AtomicBoolean(false);
      testResult.getTestCaseResults().stream()
            .filter(testCase -> testCase.getOperationName().equalsIgnoreCase(operationName))
            .forEach(testCase -> {
               foundTestCase.set(true);
               if (testCase.isSuccess()) {
                  throw new AssertionFailedError("Test '" + testResult.getId() + "' for operation '"
                        + operationName + "' should have failed");
               }
            });
      if (!foundTestCase.get()) {
         throw new AssertionFailedError("Test '" + testResult.getId() + "' has no test case for operation '"
               + operationName + "'");
      }
   }

   /**
    * Assert that the given test result has failed for the given operation name and message name. If not, throw an
    * AssertionFailedError.
    * @param testResult The test result to check
    * @param operationName The operation name to check (ie. the test case name)
    * @param messageName The message name to check (ie. the test step name)
    */
   public static void assertFailure(TestResult testResult, String operationName, String messageName) {
      AtomicBoolean foundTestCase = new AtomicBoolean(false);
      AtomicBoolean foundTestStep = new AtomicBoolean(false);
      testResult.getTestCaseResults().stream()
            .filter(testCase -> testCase.getOperationName().equalsIgnoreCase(operationName))
            .forEach(testCase -> {
               foundTestCase.set(true);
               testCase.getTestStepResults().stream()
                     .filter(testStep -> testStep.getRequestName().equalsIgnoreCase(messageName))
                     .forEach(testStep -> {
                        foundTestStep.set(true);
                        if (testStep.isSuccess()) {
                           throw new AssertionFailedError("Test '" + testResult.getId() + "' for operation '"
                                 + operationName + "' and message '" + messageName + "' should have failed");
                        }
                        return;
                     });
               if (!foundTestStep.get()) {
                  throw new AssertionFailedError("Test '" + testResult.getId() + "' has no test step for operation '"
                        + operationName + "' and message '" + messageName + "'");
               }
            });
      if (!foundTestCase.get()) {
         throw new AssertionFailedError("Test '" + testResult.getId() + "' has no test case for operation '"
               + operationName + "'");
      }
   }
}
