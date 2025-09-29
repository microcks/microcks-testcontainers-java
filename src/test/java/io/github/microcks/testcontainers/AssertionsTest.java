package io.github.microcks.testcontainers;

import io.github.microcks.testcontainers.model.TestCaseResult;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestStepResult;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Assertions.
 * @author laurent
 */
public class AssertionsTest {

   @Test
   void testFullSuccess() {
      TestResult result = new TestResult();
      result.setSuccess(true);

      Assertions.assertSuccess(result);
   }

   @Test
   void testPartialSuccess() {
      TestResult result = new TestResult();
      result.setId("testPartialSuccess");
      result.setSuccess(false);

      TestCaseResult case1 = new TestCaseResult();
      case1.setOperationName("op1");
      case1.setSuccess(true);
      TestStepResult step11 = new TestStepResult();
      step11.setRequestName("req11");
      step11.setSuccess(true);
      case1.setTestStepResults(List.of(step11));

      TestCaseResult case2 = new TestCaseResult();
      case2.setOperationName("op2");
      case2.setSuccess(false);
      TestStepResult step21 = new TestStepResult();
      step21.setRequestName("req21");
      step21.setSuccess(false);
      step21.setMessage("Not matching");
      case2.setTestStepResults(List.of(step21));

      result.setTestCaseResults(List.of(case1, case2));

      boolean failed = false;
      try {
         Assertions.assertSuccess(result);
      } catch (Error error) {
         failed = true;
         assertInstanceOf(MultipleFailuresError.class, error);
         MultipleFailuresError mfe = (MultipleFailuresError)error;
         assertTrue(mfe.getMessage().startsWith("Test 'testPartialSuccess' has failed (1 failure)"));

         List<Throwable> failures = mfe.getFailures();
         assertEquals(1, failures.size());
         assertTrue(failures.get(0).getMessage().equals("Message 'req21' of operation 'op2' failed ==> Not matching"));
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }

      // Check for operation-specific assertion.
      Assertions.assertSuccess(result, "op1");
      Assertions.assertSuccess(result, "op1", "req11");

      failed = false;
      try {
         Assertions.assertSuccess(result, "op2");
      } catch (Error error) {
         failed = true;
         assertInstanceOf(MultipleFailuresError.class, error);
         MultipleFailuresError mfe = (MultipleFailuresError)error;
         assertTrue(mfe.getMessage().startsWith("Test 'testPartialSuccess' for operation 'op2' has failed (1 failure)"));

         List<Throwable> failures = mfe.getFailures();
         assertEquals(1, failures.size());
         assertEquals("Message 'req21' of operation 'op2' failed ==> Not matching", failures.get(0).getMessage());
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }

      failed = false;
      try {
         Assertions.assertSuccess(result, "op1", "req12");
      } catch (Error error) {
         failed = true;
         assertInstanceOf(AssertionFailedError.class, error);
         assertEquals("Test 'testPartialSuccess' has no test step for operation 'op1' and message 'req12'", error.getMessage());
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }
   }

   @Test
   void testPartialFailure() {
      TestResult result = new TestResult();
      result.setId("testPartialFailure");
      result.setSuccess(false);

      TestCaseResult case1 = new TestCaseResult();
      case1.setOperationName("op1");
      case1.setSuccess(true);
      TestCaseResult case2 = new TestCaseResult();
      case2.setOperationName("op2");
      case2.setSuccess(false);
      TestStepResult step21 = new TestStepResult();
      step21.setRequestName("req21");
      step21.setSuccess(false);
      step21.setMessage("Not matching");
      case2.setTestStepResults(List.of(step21));
      result.setTestCaseResults(List.of(case1, case2));

      Assertions.assertFailure(result, "op2");
      Assertions.assertFailure(result, "op2", "req21");

      boolean failed = false;
      try {
         Assertions.assertFailure(result, "op1");
      } catch (Error error) {
         failed = true;
         assertInstanceOf(AssertionFailedError.class, error);
         assertEquals("Test 'testPartialFailure' for operation 'op1' should have failed", error.getMessage());
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }

      failed = false;
      try {
         Assertions.assertFailure(result, "op2", "req22");
      } catch (Error error) {
         failed = true;
         assertInstanceOf(AssertionFailedError.class, error);
         assertEquals("Test 'testPartialFailure' has no test step for operation 'op2' and message 'req22'", error.getMessage());
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }
   }

   @Test
   void testFailureWithEmptyCases() {
      TestResult result = new TestResult();
      result.setId("testFailureWithEmptyCases");
      result.setSuccess(false);
      result.setTestCaseResults(List.of());

      boolean failed = false;
      try {
         Assertions.assertSuccess(result);
      } catch (Error error) {
         failed = true;
         assertInstanceOf(AssertionFailedError.class, error);
         assertEquals("Test 'testFailureWithEmptyCases' is not a success, but has no failure details", error.getMessage());
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }

      failed = false;
      try {
         Assertions.assertSuccess(result, "op1");
      } catch (Error error) {
         failed = true;
         assertInstanceOf(AssertionFailedError.class, error);
         assertEquals("Test 'testFailureWithEmptyCases' has no test case for operation 'op1'", error.getMessage());
      }
      if (!failed) {
         fail("An AssertionFailedError should have been thrown");
      }
   }
}
