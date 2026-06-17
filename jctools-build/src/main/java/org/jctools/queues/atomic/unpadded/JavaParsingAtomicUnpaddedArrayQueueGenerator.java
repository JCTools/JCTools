package org.jctools.queues.atomic.unpadded;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.jctools.queues.atomic.JavaParsingAtomicArrayQueueGenerator;

import static org.jctools.queues.util.GeneratorUtils.cleanupPaddingComments;
import static org.jctools.queues.util.GeneratorUtils.removePaddingFields;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

/**
 * Combined atomic + unpadded variant of an Unsafe array queue. Inherits the Unsafe→
 * {@link java.util.concurrent.atomic.AtomicLongFieldUpdater}/
 * {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater} rewrites from
 * {@link JavaParsingAtomicArrayQueueGenerator}, retargets the output to
 * {@code org.jctools.queues.atomic.unpadded} with the {@code AtomicUnpadded} name infix, and
 * additionally drops byte-padding fields and their orphan comments after the parent visitor has
 * run.
 */
public class JavaParsingAtomicUnpaddedArrayQueueGenerator extends JavaParsingAtomicArrayQueueGenerator {
    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingAtomicUnpaddedArrayQueueGenerator.class, args);
    }

    public JavaParsingAtomicUnpaddedArrayQueueGenerator(String sourceFileName) {
        super(sourceFileName);
    }

    @Override
    public void cleanupComments(CompilationUnit cu) {
        super.cleanupComments(cu);
        cleanupPaddingComments(cu);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        super.visit(node, arg);
        removePaddingFields(node);
    }

    @Override
    protected String outputPackage() {
        return "org.jctools.queues.atomic.unpadded";
    }

    @Override
    protected String queueClassNamePrefix() {
        return "AtomicUnpadded";
    }
}
