package org.jctools.queues.varhandle.unpadded;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for the combined VarHandle+unpadded array generator: package, class infix,
 * VarHandle wiring, padding stripping, and re-parseable output.
 */
public class JavaParsingVarHandleUnpaddedArrayQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingVarHandleUnpaddedArrayQueueGenerator("Synthetic.java"), cu);
    }

    @Test
    public void rewritesPackageAndClassInfixAndStripsPadding() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "abstract class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  byte b170,b171,b172,b173,b174,b175,b176,b177;//128b\n" +
                "  private long producerIndex;\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        assertTrue("package retargeted: " + out, out.contains("package org.jctools.queues.varhandle.unpadded"));
        assertTrue("class infix is VarHandleUnpadded: " + out,
                out.contains("class FooVarHandleUnpaddedArrayQueue"));
        assertFalse("padding fields removed: " + out, out.contains("b000"));
        assertFalse("padding markers removed: " + out, out.contains("// 8b"));
        assertTrue("VarHandle wired in: " + out, out.contains("VarHandle VH_PRODUCER_INDEX"));
    }

    @Test
    public void outputReParsesAsValidJava() {
        String src =
                "package org.jctools.queues;\n" +
                "abstract class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "}";

        String out = generate(src);
        ParseResult<CompilationUnit> result = new JavaParser().parse(out);
        assertTrue("output must re-parse without problems: " + result.getProblems() + "\n" + out,
                result.getProblems().isEmpty() && result.getResult().isPresent());
    }
}
