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

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Negative-control / sanity checks for {@link UnsafeFreeAssertions}. These don't need to run per
 * queue family — they verify the assertion infrastructure itself, so once-per-build is enough.
 *
 * <p>If either of these stops detecting Unsafe, the per-family positive tests could pass for the
 * wrong reason (broken tracker / scanner, not a clean family).
 */
public class UnsafeFreeAssertionsSelfTest {

    /**
     * Tracker negative control. Loading a non-atomic, Unsafe-using queue must trip the tracker.
     * Probe queue is {@code MpscArrayQueue} (in jctools-core).
     */
    @Test
    public void trackerCatchesUnsafeLoadInNonAtomicQueue() throws Exception {
        String nonAtomicProbe = "org.jctools.queues.MpscArrayQueue";
        Set<String> hits = UnsafeFreeAssertions.unsafeHoldersLoadedBy(nonAtomicProbe);
        assertFalse(
                "Sanity check failed: loading " + nonAtomicProbe + " did not pull in any "
                        + "Unsafe-holder class. Either the tracker is broken or the probe queue "
                        + "no longer uses Unsafe — in which case the per-family positive tests "
                        + "are no longer meaningful and need a new probe.",
                hits.isEmpty());
    }

    /**
     * Import-scan negative control. The predicate must flag obvious Unsafe imports and ignore
     * non-Unsafe imports / comments containing the word.
     */
    @Test
    public void importScannerDetectsUnsafeImports() {
        assertNotNull("scanner failed to flag a literal sun.misc.Unsafe import",
                UnsafeFreeAssertions.findUnsafeImport("import sun.misc.Unsafe;"));
        assertNotNull("scanner failed to flag a static UnsafeAccess import",
                UnsafeFreeAssertions.findUnsafeImport("import static org.jctools.util.UnsafeAccess.UNSAFE;"));
        assertNull("scanner mistakenly flagged a non-Unsafe import",
                UnsafeFreeAssertions.findUnsafeImport("import java.util.concurrent.atomic.AtomicLongFieldUpdater;"));
        assertNull("scanner mistakenly flagged a comment containing Unsafe",
                UnsafeFreeAssertions.findUnsafeImport("// historically used Unsafe but no more"));
    }
}
