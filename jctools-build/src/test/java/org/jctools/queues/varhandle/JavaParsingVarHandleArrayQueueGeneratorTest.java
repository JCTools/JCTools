package org.jctools.queues.varhandle;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link JavaParsingVarHandleArrayQueueGenerator}.
 */
public class JavaParsingVarHandleArrayQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingVarHandleArrayQueueGenerator("Synthetic.java"), cu);
    }

    /**
     * Bug 10: the previous implementation passed the literal 1 to {@code VarHandle.getAndAdd}
     * by constructing a {@link com.github.javaparser.ast.expr.NameExpr} with content {@code "1L"}.
     * The printer rendered that as the text {@code 1L}, so the generated source compiled, but the
     * AST was wrong: a parser round-trip of the generated file would produce a
     * {@link LongLiteralExpr}, making the generator non-idempotent at the AST level. After the
     * fix, re-parsing the generator output should yield a {@code LongLiteralExpr} for the delta.
     */
    @Test
    public void getAndIncrementUsesLongLiteralExprNotNameExpr() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "abstract class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  private long producerIndex;\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  public final long getAndIncrementProducerIndex() { return 0; }\n" +
                "}";

        String out = generate(src);

        // Re-parse the generator output and inspect the AST shape of the getAndAdd call.
        CompilationUnit reparsed = new JavaParser().parse(out).getResult().orElseThrow(
                () -> new AssertionError("regenerated source did not parse: " + out));
        List<MethodCallExpr> getAndAddCalls = reparsed.findAll(MethodCallExpr.class).stream()
                .filter(m -> "getAndAdd".equals(m.getNameAsString()))
                .collect(Collectors.toList());
        assertEquals("expected one getAndAdd call: " + out, 1, getAndAddCalls.size());

        MethodCallExpr getAndAdd = getAndAddCalls.get(0);
        assertEquals("expected (this, 1L) arguments: " + getAndAdd, 2, getAndAdd.getArguments().size());
        assertTrue(
                "second argument must be a LongLiteralExpr (was " + getAndAdd.getArgument(1).getClass().getSimpleName() + "): " + out,
                getAndAdd.getArgument(1) instanceof LongLiteralExpr);
        assertEquals("1L", ((LongLiteralExpr) getAndAdd.getArgument(1)).getValue());

        assertFalse("output should not have NameExpr-style 1L: " + out,
                out.contains("getAndAdd(this, 1)") && !out.contains("getAndAdd(this, 1L)"));
    }

    /**
     * Bug 12: with multiple VarHandle fields, the previous implementation prepended each field
     * via {@code add(0, ...)} and inserted the static initializer block at index 1, which placed
     * the init block between the most-recently-added field and the rest of the field declarations.
     * After the fix, all VH field declarations come first (in original order) and the static
     * initializer follows them as a contiguous prefix.
     */
    @Test
    public void varHandleStaticInitializerComesAfterAllVarHandleFieldDeclarations() {
        String src =
                "package org.jctools.queues;\n" +
                "// $gen:ordered-fields\n" +
                "abstract class FooArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {\n" +
                "  private long producerIndex;\n" +
                "  private long consumerIndex;\n" +
                "  private long producerLimit;\n" +
                "  FooArrayQueue(int c) { super(c); }\n" +
                "  public final long lvProducerIndex() { return 0; }\n" +
                "  final void soProducerIndex(final long newValue) {}\n" +
                "  public final long lvConsumerIndex() { return 0; }\n" +
                "  final void soConsumerIndex(final long newValue) {}\n" +
                "  public final long lvProducerLimit() { return 0; }\n" +
                "  final void soProducerLimit(final long newValue) {}\n" +
                "}";

        String out = generate(src);

        // Find positions of each VH field declaration and the static block opener.
        int idxProducer = out.indexOf("private static final VarHandle VH_PRODUCER_INDEX");
        int idxConsumer = out.indexOf("private static final VarHandle VH_CONSUMER_INDEX");
        int idxLimit = out.indexOf("private static final VarHandle VH_PRODUCER_LIMIT");
        int idxStaticBlock = out.indexOf("static {");
        assertTrue("VH_PRODUCER_INDEX field present: " + out, idxProducer >= 0);
        assertTrue("VH_CONSUMER_INDEX field present: " + out, idxConsumer >= 0);
        assertTrue("VH_PRODUCER_LIMIT field present: " + out, idxLimit >= 0);
        assertTrue("static block present: " + out, idxStaticBlock >= 0);

        assertTrue("static initializer must come after all VH field declarations (producer): " + out,
                idxStaticBlock > idxProducer);
        assertTrue("static initializer must come after all VH field declarations (consumer): " + out,
                idxStaticBlock > idxConsumer);
        assertTrue("static initializer must come after all VH field declarations (limit): " + out,
                idxStaticBlock > idxLimit);
    }
}
