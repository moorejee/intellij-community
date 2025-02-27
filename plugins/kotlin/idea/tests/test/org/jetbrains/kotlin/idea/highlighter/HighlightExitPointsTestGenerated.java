// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("idea/tests")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/exitPoints")
public class HighlightExitPointsTestGenerated extends AbstractHighlightExitPointsTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("anonymousFunction.kt")
    public void testAnonymousFunction() throws Exception {
        runTest("testData/exitPoints/anonymousFunction.kt");
    }

    @TestMetadata("anonymousFunction2.kt")
    public void testAnonymousFunction2() throws Exception {
        runTest("testData/exitPoints/anonymousFunction2.kt");
    }

    @TestMetadata("getter.kt")
    public void testGetter() throws Exception {
        runTest("testData/exitPoints/getter.kt");
    }

    @TestMetadata("inline1.kt")
    public void testInline1() throws Exception {
        runTest("testData/exitPoints/inline1.kt");
    }

    @TestMetadata("inline2.kt")
    public void testInline2() throws Exception {
        runTest("testData/exitPoints/inline2.kt");
    }

    @TestMetadata("inline3.kt")
    public void testInline3() throws Exception {
        runTest("testData/exitPoints/inline3.kt");
    }

    @TestMetadata("inlineLocalReturn1.kt")
    public void testInlineLocalReturn1() throws Exception {
        runTest("testData/exitPoints/inlineLocalReturn1.kt");
    }

    @TestMetadata("inlineLocalReturn2.kt")
    public void testInlineLocalReturn2() throws Exception {
        runTest("testData/exitPoints/inlineLocalReturn2.kt");
    }

    @TestMetadata("inlineLocalReturn3.kt")
    public void testInlineLocalReturn3() throws Exception {
        runTest("testData/exitPoints/inlineLocalReturn3.kt");
    }

    @TestMetadata("inlineWithNoInlineParam.kt")
    public void testInlineWithNoInlineParam() throws Exception {
        runTest("testData/exitPoints/inlineWithNoInlineParam.kt");
    }

    @TestMetadata("invalidReturn.kt")
    public void testInvalidReturn() throws Exception {
        runTest("testData/exitPoints/invalidReturn.kt");
    }

    @TestMetadata("invalidThrow.kt")
    public void testInvalidThrow() throws Exception {
        runTest("testData/exitPoints/invalidThrow.kt");
    }

    @TestMetadata("localFunction1.kt")
    public void testLocalFunction1() throws Exception {
        runTest("testData/exitPoints/localFunction1.kt");
    }

    @TestMetadata("localFunction2.kt")
    public void testLocalFunction2() throws Exception {
        runTest("testData/exitPoints/localFunction2.kt");
    }

    @TestMetadata("localFunctionThrow.kt")
    public void testLocalFunctionThrow() throws Exception {
        runTest("testData/exitPoints/localFunctionThrow.kt");
    }

    @TestMetadata("notInline1.kt")
    public void testNotInline1() throws Exception {
        runTest("testData/exitPoints/notInline1.kt");
    }

    @TestMetadata("notInline2.kt")
    public void testNotInline2() throws Exception {
        runTest("testData/exitPoints/notInline2.kt");
    }

    @TestMetadata("notInline3.kt")
    public void testNotInline3() throws Exception {
        runTest("testData/exitPoints/notInline3.kt");
    }

    @TestMetadata("notReturnedLabeledExpression.kt")
    public void testNotReturnedLabeledExpression() throws Exception {
        runTest("testData/exitPoints/notReturnedLabeledExpression.kt");
    }

    @TestMetadata("simple.kt")
    public void testSimple() throws Exception {
        runTest("testData/exitPoints/simple.kt");
    }

    @TestMetadata("throw1.kt")
    public void testThrow1() throws Exception {
        runTest("testData/exitPoints/throw1.kt");
    }

    @TestMetadata("throw2.kt")
    public void testThrow2() throws Exception {
        runTest("testData/exitPoints/throw2.kt");
    }
}
