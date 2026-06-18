package org.jctools.queues.atomic.unpadded;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for the combined atomic+unpadded array generator. Verifies the parent atomic rewrites
 * still happen, the package and class infix track {@code AtomicUnpadded}, byte-padding is dropped,
 * and the output is well-formed Java.
 */
public class JavaParsingAtomicUnpaddedArrayQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingAtomicUnpaddedArrayQueueGenerator("Synthetic.java"), cu);
    }

    @Test
    public void rewritesPackageAndClassInfixAndStripsPadding() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "class SpscArrayQueueProducerIndexFields<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  byte b170,b171,b172,b173,b174,b175,b176,b177;//128b\n" +
                "  private final static long P_INDEX_OFFSET = 0;\n" +
                "  private long producerIndex;\n" +
                "  SpscArrayQueueProducerIndexFields(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        assertTrue("package retargeted: " + out, out.contains("package org.jctools.queues.atomic.unpadded"));
        assertTrue("class infix is AtomicUnpadded: " + out,
                out.contains("class SpscAtomicUnpaddedArrayQueueProducerIndexFields"));
        assertTrue("ctor renamed to match: " + out,
                out.contains("SpscAtomicUnpaddedArrayQueueProducerIndexFields(int c)"));
        assertFalse("padding fields removed: " + out, out.contains("b000"));
        assertFalse("padding markers removed: " + out, out.contains("// 8b"));
        assertTrue("atomic field updater wired in: " + out, out.contains("AtomicLongFieldUpdater"));
    }

    @Test
    public void outputReParsesAsValidJava() {
        // The generator's job is to produce well-formed Java. Re-parsing the output catches LPP
        // print regressions that would compile-fail downstream consumers.
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  SpscArrayQueue(int c) { super(c); }\n" +
                "}";

        String out = generate(src);
        ParseResult<CompilationUnit> result = new JavaParser().parse(out);
        assertTrue("output must re-parse without problems: " + result.getProblems() + "\n" + out,
                result.getProblems().isEmpty() && result.getResult().isPresent());
    }
}
