package org.jctools.queues.atomic.unpadded;

import org.jctools.queues.atomic.JavaParsingAtomicArrayQueueGenerator;

import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

/**
 * Combined atomic + unpadded variant of an Unsafe array queue. Inherits the Unsafe→
 * {@link java.util.concurrent.atomic.AtomicLongFieldUpdater}/
 * {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater} rewrites from
 * {@link JavaParsingAtomicArrayQueueGenerator}, retargets the output to
 * {@code org.jctools.queues.atomic.unpadded} with the {@code AtomicUnpadded} name infix, and
 * additionally drops byte-padding fields and their orphan comments via the base
 * {@code stripsPadding()} hook.
 */
public class JavaParsingAtomicUnpaddedArrayQueueGenerator extends JavaParsingAtomicArrayQueueGenerator {
    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingAtomicUnpaddedArrayQueueGenerator.class, args);
    }

    public JavaParsingAtomicUnpaddedArrayQueueGenerator(String sourceFileName) {
        super(sourceFileName, "org.jctools.queues.atomic.unpadded", "AtomicUnpadded");
    }

    @Override
    protected boolean stripsPadding() {
        return true;
    }
}
