/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.liveedit.Utils.buildClass;
import static com.android.tools.deploy.liveedit.Utils.classToType;

import org.junit.Assert;

public class TestInvokeSpecial {

    @org.junit.Test
    public void testInvokeSuperHash() throws Exception {
        byte[] byteCode = buildClass(InvokeSpecialChild.class);

        InvokeSpecialChild obj = new InvokeSpecialChild();
        MethodBodyEvaluator body = new MethodBodyEvaluator(byteCode, "callSuperGetHash()I");
        String type = classToType(InvokeSpecialChild.class);
        Object result = body.eval(obj, type, new Object[] {});

        Assert.assertEquals("invokespecial (super.hash)", obj.callSuperGetHash(), result);
    }

    @org.junit.Test
    public void testInvokeSuperArrayGetter() throws Exception {
        byte[] byteCode = buildClass(InvokeSpecialChild.class);

        InvokeSpecialChild obj = new InvokeSpecialChild();
        MethodBodyEvaluator body = new MethodBodyEvaluator(byteCode, "callgetArrayValue([II)I");
        String type = classToType(InvokeSpecialChild.class);
        int[] array = new int[] {5};
        int index = 0;
        Object result = body.eval(obj, type, new Object[] {array, index});

        Assert.assertEquals(
                "invokespecial (super.hash)", obj.callgetArrayValue(array, index), result);
    }
}
