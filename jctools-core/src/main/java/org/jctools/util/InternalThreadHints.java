/*
 * Copyright 2016 Gil Tene
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class InternalThreadHints
{

    private static final MethodHandle onSpinWaitMH;

    static
    {
        MethodHandle mh;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try
        {
            mh = lookup.findStatic(java.lang.Thread.class, "onSpinWait", MethodType.methodType(void.class));
        }
        catch (Exception e)
        {
            mh = null;
        }
        onSpinWaitMH = mh;
    }

    // prevent construction...
    private InternalThreadHints()
    {
    }

    public static void onSpinWait()
    {
        // Call java.lang.Runtime.onSpinWait() on Java SE versions that support it. Do nothing otherwise.
        // This should optimize away to either nothing or to an inlining of java.lang.Runtime.onSpinWait()
        if (onSpinWaitMH != null)
        {
            try
            {
                onSpinWaitMH.invokeExact();
            }
            catch (Throwable throwable)
            {
                // Nothing to do here...
            }
        }
    }
}