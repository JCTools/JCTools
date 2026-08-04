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

@InternalAPI
public final class ThreadHints
{

    /**
     * Set this system property to true to disable {@link #onSpinWait()}.
     */
    public static final String DISABLE_ON_SPIN_WAIT = "jctools.disable.onSpinWait";
    private static final boolean okToInvokeInternalThreadHintsCaller;

    static
    {
        boolean okToInvokeMHCaller = false;
        if (!Boolean.getBoolean(DISABLE_ON_SPIN_WAIT))
        {
            try
            {
                Class.forName("java.lang.invoke.MethodHandle");
                okToInvokeMHCaller = true;
            }
            catch (Exception e)
            {
                okToInvokeMHCaller = false;
            }
        }
        // okToInvokeThreadHintsMHCaller will be false for e.g. Java SE 6 and earlier:
        okToInvokeInternalThreadHintsCaller = okToInvokeMHCaller;
    }

    // prevent construction...
    private ThreadHints()
    {
    }

    /**
     * Indicates that the caller is momentarily unable to progress, until the
     * occurrence of one or more actions on the part of other activities.  By
     * invoking this method within each iteration of a spin-wait loop construct,
     * the calling thread indicates to the runtime that it is busy-waiting. The runtime
     * may take action to improve the performance of invoking spin-wait loop
     * constructions.
     */
    public static void onSpinWait()
    {
        if (okToInvokeInternalThreadHintsCaller)
        {
            InternalThreadHints.onSpinWait();
        }
    }
}