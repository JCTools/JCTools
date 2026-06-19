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

import org.junit.Test;

import java.util.List;

/**
 * Verifies that a queue family stays free of {@code sun.misc.Unsafe} usage. Subclasses supply the
 * family-specific {@link #packages} (for the runtime class-load probe) and {@link #sourceDirs}
 * (for the static import scan); the inherited {@link Test} methods enforce the property.
 *
 * <p>Negative-control tests for the underlying helpers live in {@link UnsafeFreeAssertionsSelfTest}
 * — they don't need to run per family.
 */
public abstract class AbstractUnsafeFreeQueueTest {

    private final List<String> packages;
    private final List<String> sourceDirs;
    private final String familyName;
    private final int minExpected;

    protected AbstractUnsafeFreeQueueTest(
            List<String> packages, List<String> sourceDirs, String familyName, int minExpected) {
        this.packages = packages;
        this.sourceDirs = sourceDirs;
        this.familyName = familyName;
        this.minExpected = minExpected;
    }

    @Test
    public final void loadingAnyQueueDoesNotPullInUnsafe() throws Exception {
        UnsafeFreeAssertions.assertConcreteQueuesDoNotLoadUnsafe(packages, minExpected, familyName);
    }

    @Test
    public final void generatedSourcesHaveNoUnsafeImports() throws Exception {
        UnsafeFreeAssertions.assertNoUnsafeImportsIn(sourceDirs, minExpected, familyName);
    }
}
