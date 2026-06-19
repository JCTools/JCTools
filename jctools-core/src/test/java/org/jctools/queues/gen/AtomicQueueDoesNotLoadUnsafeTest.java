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
 * The atomic queue family is the alternative for environments without {@code sun.misc.Unsafe} —
 * AtomicReferenceFieldUpdater / AtomicLongFieldUpdater instead. Inherited tests verify it remains
 * Unsafe-free at both load-time and source-import level.
 */
public class AtomicQueueDoesNotLoadUnsafeTest extends AbstractUnsafeFreeQueueTest {

    public AtomicQueueDoesNotLoadUnsafeTest() {
        super(
                Arrays.asList(
                        "org.jctools.queues.atomic",
                        "org.jctools.queues.atomic.unpadded"),
                Arrays.asList(
                        "src/main/java/org/jctools/queues/atomic",
                        "src/main/java/org/jctools/queues/atomic/unpadded"),
                "atomic",
                10);
    }
}
