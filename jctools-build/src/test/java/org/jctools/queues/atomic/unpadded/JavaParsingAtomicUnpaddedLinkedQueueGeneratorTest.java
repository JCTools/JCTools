package org.jctools.queues.atomic.unpadded;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for the combined atomic+unpadded linked generator. Adds the
 * {@code LinkedQueueAtomicNode} import unconditionally, so verify it shows up exactly once and the
 * other rewrites still run.
 */
public class JavaParsingAtomicUnpaddedLinkedQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingAtomicUnpaddedLinkedQueueGenerator("Synthetic.java"), cu);
    }

    @Test
    public void rewritesPackageInfixAndAddsLinkedNodeImport() {
        String src =
                "package org.jctools.queues;\n" +
                "class FooLinkedQueue<E> extends BaseLinkedQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  FooLinkedQueue() {}\n" +
                "}";

        String out = generate(src);

        assertTrue("package retargeted: " + out, out.contains("package org.jctools.queues.atomic.unpadded"));
        assertTrue("class infix is AtomicUnpadded: " + out, out.contains("class FooLinkedAtomicUnpaddedQueue"));
        assertTrue("LinkedQueueAtomicNode import added: " + out,
                out.contains("import org.jctools.queues.atomic.LinkedQueueAtomicNode;"));
        // Ensure exactly one occurrence of the import line.
        int firstImport = out.indexOf("import org.jctools.queues.atomic.LinkedQueueAtomicNode;");
        int lastImport = out.lastIndexOf("import org.jctools.queues.atomic.LinkedQueueAtomicNode;");
        assertTrue("import added exactly once: " + out, firstImport >= 0 && firstImport == lastImport);
        assertFalse("padding fields removed: " + out, out.contains("b000"));
    }

    @Test
    public void outputReParsesAsValidJava() {
        String src =
                "package org.jctools.queues;\n" +
                "class FooLinkedQueue<E> extends BaseLinkedQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  FooLinkedQueue() {}\n" +
                "}";

        String out = generate(src);
        ParseResult<CompilationUnit> result = new JavaParser().parse(out);
        assertTrue("output must re-parse without problems: " + result.getProblems() + "\n" + out,
                result.getProblems().isEmpty() && result.getResult().isPresent());
    }
}
