/*
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
package org.jctools.queues.gen;

import java.util.Arrays;

/**
 * The VarHandle queue family targets JDK 9+ and uses {@link java.lang.invoke.VarHandle} for memory
 * ordering instead of {@code sun.misc.Unsafe}. Inherited tests verify it remains Unsafe-free at
 * both load-time and source-import level.
 */
public class VarHandleQueueDoesNotLoadUnsafeTest extends AbstractUnsafeFreeQueueTest {

    public VarHandleQueueDoesNotLoadUnsafeTest() {
        super(
                Arrays.asList(
                        "org.jctools.queues.varhandle",
                        "org.jctools.queues.varhandle.unpadded"),
                Arrays.asList(
                        "src/main/java/org/jctools/queues/varhandle",
                        "src/main/java/org/jctools/queues/varhandle/unpadded"),
                "VarHandle",
                10);
    }
}
