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
 * Verifies that a family's {@code *Pad} classes are empty pass-through abstracts (no fields, no
 * body comments). Subclasses supply the family-specific {@link #packages} (for the reflection
 * field check) and {@link #sourceDirs} (for the source-file body scan); the inherited {@link Test}
 * methods enforce the property.
 */
public abstract class AbstractUnpaddedPadClassesTest {

    private final List<String> packages;
    private final List<String> sourceDirs;
    private final int minExpected;

    protected AbstractUnpaddedPadClassesTest(
            List<String> packages, List<String> sourceDirs, int minExpected) {
        this.packages = packages;
        this.sourceDirs = sourceDirs;
        this.minExpected = minExpected;
    }

    @Test
    public final void padClassesDeclareNoFields() throws Exception {
        PadClassAssertions.assertPadClassesDeclareNoFields(packages, minExpected);
    }

    @Test
    public final void padClassBodiesHaveNoComments() throws Exception {
        PadClassAssertions.assertPadClassBodiesHaveNoComments(sourceDirs, minExpected);
    }
}
