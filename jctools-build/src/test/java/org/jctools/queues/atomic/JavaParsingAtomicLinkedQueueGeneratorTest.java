package org.jctools.queues.atomic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the linked-queue atomic generator. Each test fixes a specific bug uncovered
 * during the post-LPP review.
 */
public class JavaParsingAtomicLinkedQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingAtomicLinkedQueueGenerator("Synthetic.java"), cu);
    }

    /**
     * Bug 2: the {@code usesFieldUpdater} flag was scoped per-FieldDeclaration instead of
     * per-VariableDeclarator. With {@code long a, b;} where only {@code a} has accessors, the flag
     * stayed {@code true} when iterating to {@code b}, causing a stray updater for {@code b}.
     * Today no jctools source declares two variables in one field, but the regression is real.
     */
    @Test
    public void multiVariableFieldDoesNotEmitStrayUpdaterForUnaccessedVariable() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "class FooLinkedQueue<E> extends BaseLinkedQueue<E> {\n" +
                "  private long producerIndex, unrelated;\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        assertTrue("updater for accessed field: " + out, out.contains("P_INDEX_UPDATER"));
        assertFalse("no stray updater for unaccessed sibling: " + out, out.contains("UNRELATED_UPDATER"));
    }

    /**
     * Bug 5: the linked atomic patcher had no {@code final}-field guard, unlike the array patcher.
     * A {@code final} non-static field whose name matched a method suffix would otherwise get a
     * stray updater and a {@code volatile} modifier (which doesn't compile on a {@code final}).
     */
    @Test
    public void finalFieldsAreSkippedByPatcher() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "class FooLinkedQueue<E> extends BaseLinkedQueue<E> {\n" +
                "  protected final boolean pooled = false;\n" +
                "  public final boolean isPooled() { return pooled; }\n" +
                "}";

        String out = generate(src);

        assertFalse("no updater for final field: " + out, out.contains("POOLED_UPDATER"));
        assertFalse("no volatile injected on final field: " + out, out.contains("volatile"));
        assertTrue("isPooled() body untouched: " + out, out.contains("return pooled"));
    }
}
