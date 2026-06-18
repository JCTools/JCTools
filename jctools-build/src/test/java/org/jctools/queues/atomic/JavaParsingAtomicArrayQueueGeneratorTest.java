package org.jctools.queues.atomic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end behavioural tests for {@link JavaParsingAtomicArrayQueueGenerator}. Each test feeds
 * a minimal, hand-built source through the generator and asserts on a single intended outcome —
 * directive handling, naming, comment preservation, and the LPP bug regressions.
 */
public class JavaParsingAtomicArrayQueueGeneratorTest {

    /**
     * Run the array-queue generator with a synthetic source name (the file name only feeds the
     * "NOTE" javadoc, so any sensible value works).
     */
    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingAtomicArrayQueueGenerator("Synthetic.java"), cu);
    }

    @Test
    public void renamesClassAndConstructorAndExtendsClause() {
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueueColdField<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  SpscArrayQueueColdField(int capacity) { super(capacity); }\n" +
                "}";

        String out = generate(src);

        assertTrue("class renamed: " + out, out.contains("class SpscAtomicArrayQueueColdField<E>"));
        assertTrue("ctor renamed: " + out, out.contains("SpscAtomicArrayQueueColdField(int capacity)"));
        assertTrue("parent renamed: " + out, out.contains("extends ConcurrentCircularAtomicArrayQueue<E>"));
        assertTrue("package rewritten: " + out, out.contains("package org.jctools.queues.atomic"));
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
        assertTrue("source file recorded: " + out, out.contains("The original source file is Synthetic.java"));
        assertTrue("original javadoc kept: " + out, out.contains("Original docs."));
    }

    @Test
    public void preservesPaddingFieldCommentsVerbatim() {
        // The padding line is the highest-pain comment shape: an inline `// 8b` after a field that
        // declares 8 byte variables. Earlier the generator detached this and reconstructed it via
        // regex post-processing; LPP now keeps it intact.
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b\n" +
                "  byte b140,b141,b142,b143,b144,b145,b146,b147;//104b\n" +
                "  SpscArrayQueueL1Pad(int c) { super(c); }\n" +
                "}";

        String out = generate(src);

        assertTrue("first padding line preserved: " + out,
                out.contains("byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b"));
        assertTrue("dense padding line preserved: " + out,
                out.contains("byte b140,b141,b142,b143,b144,b145,b146,b147;//104b"));
    }

    @Test
    public void consumesGenOrderedFieldsDirectiveAndPatchesAccessors() {
        // $gen:ordered-fields tells the atomic generator to swap UNSAFE accessors for
        // AtomicLongFieldUpdater calls. The directive comment itself must be removed from output.
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "class SpscArrayQueueProducerIndexFields<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  private final static long P_INDEX_OFFSET = 0;\n" +
                "  private long producerIndex;\n" +
                "  SpscArrayQueueProducerIndexFields(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        assertFalse("$gen:ordered-fields directive removed: " + out, out.contains("$gen:ordered-fields"));
        assertTrue("field-updater declared: " + out, out.contains("AtomicLongFieldUpdater"));
        assertTrue("producerIndex marked volatile: " + out, out.contains("volatile long producerIndex"));
        assertTrue("offset field removed: " + out, !out.contains("P_INDEX_OFFSET"));
        assertTrue("soProducerIndex now uses updater: " + out, out.contains("P_INDEX_UPDATER.lazySet(this, newValue)"));
        assertTrue("lvProducerIndex returns the field: " + out,
                out.replaceAll("\\s+", " ").contains("public final long lvProducerIndex() { return producerIndex; }"));
    }

    @Test
    public void replacesEArrayBufferWithAtomicReferenceArray() {
        // Regression for the LPP VariableDeclarator bracket-leak bug. Pre-fix, output was
        // `AtomicReferenceArray<E>[] buffer` (uncompilable). With replaceType the brackets vanish.
        String src =
                "package org.jctools.queues;\n" +
                "class SpscArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  protected final E[] buffer;\n" +
                "  SpscArrayQueue(int c) { super(c); buffer = null; }\n" +
                "  void use() { final E[] buffer = this.buffer; }\n" +
                "}";

        String out = generate(src);

        assertTrue("field type rewritten: " + out, out.contains("AtomicReferenceArray<E> buffer"));
        assertFalse("no leftover brackets on field: " + out, out.contains("AtomicReferenceArray<E>[]"));
    }

    @Test
    public void translatesQueueNamesViaTranslateQueueName() {
        JavaParsingAtomicArrayQueueGenerator g = new JavaParsingAtomicArrayQueueGenerator("x.java");
        // ArrayQueue takes precedence over Chunk
        org.junit.Assert.assertEquals("MpscAtomicArrayQueue", g.translateQueueName("MpscArrayQueue"));
        // Standalone Chunk
        org.junit.Assert.assertEquals("MpUnboundedXaddAtomicChunk", g.translateQueueName("MpUnboundedXaddChunk"));
        // Linked queue
        org.junit.Assert.assertEquals("MpscLinkedAtomicQueue", g.translateQueueName("MpscLinkedQueue"));
    }

    /**
     * Bug 2: with {@code long a, b;} where only {@code a} has so/cas accessors, the prior
     * implementation kept the {@code usesFieldUpdater} flag set across siblings, so {@code b}
     * also got an updater. After the per-variable scope fix, only accessed variables get one.
     */
    @Test
    public void multiVariableFieldDoesNotEmitStrayUpdaterForUnaccessedVariable() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  private long producerIndex, unrelated;\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        assertTrue("updater for accessed field: " + out, out.contains("P_INDEX_UPDATER"));
        assertFalse("no stray updater for unaccessed sibling: " + out, out.contains("UNRELATED_UPDATER"));
    }
    /**
     * Bug 7: removeStaticFieldsAndInitialisers used to drop ALL static initializer blocks. Only
     * blocks that reference Unsafe / *_OFFSET infrastructure should be removed.
     */
    @Test
    public void unrelatedStaticInitializerSurvives() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  static int sentinel;\n" +
                "  static { sentinel = 42; }\n" +
                "  private long producerIndex;\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        assertTrue("non-Unsafe initializer preserved: " + out, out.contains("sentinel = 42"));
    }

    /**
     * processSpecialNodeTypes used a switch on {@code name} with no default arm. A long-typed local
     * whose name is in the narrow-to-int allow-list (e.g. {@code mask}, {@code offset}) becomes int;
     * any other long stays long. Pin both halves so a regression that flips the rule is caught.
     */
    @Test
    public void longNamedMaskBecomesIntButOtherLongsStayLong() {
        String src =
                "package org.jctools.queues;\n" +
                "class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  long mask = 0L;\n" +
                "  long unrelated = 0L;\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "}";

        String out = generate(src);

        // Allow-list match → narrowed to int.
        assertTrue("mask narrowed to int: " + out, out.contains("int mask"));
        // Non-listed long must stay long.
        assertTrue("unrelated stays long: " + out, out.contains("long unrelated"));
    }
}
