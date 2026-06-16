package org.jctools.queues.util;

import org.jctools.queues.atomic.JavaParsingAtomicArrayQueueGenerator;
import org.jctools.queues.atomic.JavaParsingAtomicLinkedQueueGenerator;
import org.jctools.queues.unpadded.JavaParsingUnpaddedQueueGenerator;
import org.jctools.queues.varhandle.JavaParsingVarHandleArrayQueueGenerator;
import org.jctools.queues.varhandle.JavaParsingVarHandleLinkedQueueGenerator;
import org.junit.Test;

/**
 * Golden-file integration tests. Each test feeds a hand-written synthetic queue source through one
 * generator and asserts the output matches a checked-in expected.java byte-for-byte. The synthetic
 * inputs are intentionally crafted to exercise every documented behaviour of their generator —
 * package rewrite, class/ctor rename, $gen directives, padding handling, Unsafe → atomic/VarHandle
 * rewrites, NOTE-javadoc prepending, etc.
 * <p>
 * To regenerate after an intentional change to a generator, run:
 * {@code mvn -pl jctools-build test -Dgolden.regenerate=true}.
 */
public class GoldenIntegrationTest {

    @Test
    public void atomicArrayGenerator() throws Exception {
        GoldenTestSupport.assertGolden(
                new JavaParsingAtomicArrayQueueGenerator("GoldenSampleArrayQueue.java"),
                "golden/atomic_array");
    }

    @Test
    public void atomicLinkedGenerator() throws Exception {
        GoldenTestSupport.assertGolden(
                new JavaParsingAtomicLinkedQueueGenerator("MpscLinkedQueue.java"),
                "golden/atomic_linked");
    }

    @Test
    public void unpaddedGenerator() throws Exception {
        GoldenTestSupport.assertGolden(
                new JavaParsingUnpaddedQueueGenerator("GoldenSampleArrayQueue.java"),
                "golden/unpadded");
    }

    @Test
    public void varHandleArrayGenerator() throws Exception {
        GoldenTestSupport.assertGolden(
                new JavaParsingVarHandleArrayQueueGenerator("GoldenSampleArrayQueue.java"),
                "golden/varhandle_array");
    }

    @Test
    public void varHandleLinkedGenerator() throws Exception {
        GoldenTestSupport.assertGolden(
                new JavaParsingVarHandleLinkedQueueGenerator("MpscLinkedQueue.java"),
                "golden/varhandle_linked");
    }
}
