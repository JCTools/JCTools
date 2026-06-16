package org.jctools.queues.unpadded;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end behavioural tests for {@link JavaParsingUnpaddedQueueGenerator}. The unpadded variant
 * does not patch Unsafe — it only renames classes and strips the byte-padding fields/comments.
 */
public class JavaParsingUnpaddedQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingUnpaddedQueueGenerator("Synthetic.java"), cu);
    }

    @Test
    public void renamesArrayQueueAndRewritesPackage() {
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  SpscArrayQueue(int c) { super(c); }\n" +
                "}";

        String out = generate(src);

        assertTrue(out.contains("package org.jctools.queues.unpadded"));
        assertTrue(out.contains("class SpscUnpaddedArrayQueue<E>"));
        assertTrue(out.contains("extends ConcurrentCircularUnpaddedArrayQueue<E>"));
        assertTrue("ctor renamed: " + out, out.contains("SpscUnpaddedArrayQueue(int c)"));
    }

    @Test
    public void removesBytePaddingFieldsAndTheirComments() {
        // The unpadded variant must drop both the padding declarations and the inline comments
        // that document them. cleanupPaddingComments handles any orphaned `// 8b` bands.
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  byte b170,b171,b172,b173,b174,b175,b176,b177;//128b\n" +
                "  SpscArrayQueueL1Pad(int c) { super(c); }\n" +
                "}";

        String out = generate(src);

        assertFalse("padding field b000 removed: " + out, out.contains("b000"));
        assertFalse("padding field b170 removed: " + out, out.contains("b170"));
        assertFalse("padding inline comment removed: " + out, out.contains("// 8b"));
        assertFalse("padding inline comment removed: " + out, out.contains("//128b"));
        assertTrue("ctor preserved: " + out, out.contains("SpscUnpaddedArrayQueueL1Pad(int c)"));
    }

    @Test
    public void preservesNonPaddingComments() {
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  // important explanation\n" +
                "  long real;\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  SpscArrayQueueL1Pad(int c) { super(c); }\n" +
                "}";

        String out = generate(src);

        assertTrue("non-padding comment preserved: " + out, out.contains("// important explanation"));
        assertFalse("padding comment removed: " + out, out.contains("// 8b"));
    }

    @Test
    public void prependsGenerationNoteJavadocPreservingOriginal() {
        String src =
                "package org.jctools.queues;\n" +
                "/** Original docs. */\n" +
                "class SpscArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  SpscArrayQueue(int c) { super(c); }\n" +
                "}";

        String out = generate(src);

        assertTrue("NOTE prepended: " + out, out.contains("NOTE: This class was automatically generated"));
        assertTrue("original javadoc kept: " + out, out.contains("Original docs."));
    }

    @Test
    public void translatesQueueNames() {
        JavaParsingUnpaddedQueueGenerator g = new JavaParsingUnpaddedQueueGenerator("x.java");
        assertEquals("SpscUnpaddedArrayQueue", g.translateQueueName("SpscArrayQueue"));
        assertEquals("MpUnboundedXaddUnpaddedChunk", g.translateQueueName("MpUnboundedXaddChunk"));
        assertEquals("MpscLinkedUnpaddedQueue", g.translateQueueName("MpscLinkedQueue"));
    }
}
