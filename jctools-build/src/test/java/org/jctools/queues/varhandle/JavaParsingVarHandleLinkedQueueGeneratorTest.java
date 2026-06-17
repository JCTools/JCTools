package org.jctools.queues.varhandle;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.jctools.queues.util.GeneratorUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link JavaParsingVarHandleLinkedQueueGenerator}.
 */
public class JavaParsingVarHandleLinkedQueueGeneratorTest {

    private static String generate(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("parse failed"));
        return GeneratorUtils.applyGenerator(
                new JavaParsingVarHandleLinkedQueueGenerator("Synthetic.java"), cu);
    }

    /**
     * Bug 9: VarHandle linked queues use {@code E[]} arrays directly (no wrapper). The previous
     * implementation routed {@code E[]} through a no-op {@code varHandleRefArrayType} helper that
     * returned its argument unchanged, plus an unused {@code visit(CastExpr)} override. After
     * cleanup, an {@code E[]} parameter / cast / variable type must still survive intact.
     */
    @Test
    public void preservesEArrayTypeUnchanged() {
        String src =
                "package org.jctools.queues;\n" +
                "import java.util.AbstractQueue;\n" +
                "abstract class FooLinkedQueue<E> extends AbstractQueue<E> {\n" +
                "  void use(E[] buffer) {\n" +
                "    final E[] copy = (E[]) buffer;\n" +
                "  }\n" +
                "}";

        String out = generate(src);

        assertTrue("parameter type preserved as E[]: " + out, out.contains("void use(E[] buffer)"));
        assertTrue("local var type preserved as E[]: " + out, out.contains("final E[] copy"));
        assertTrue("cast type preserved as (E[]): " + out, out.contains("(E[]) buffer"));
        assertFalse("no AtomicReferenceArray wrapping in VarHandle output: " + out,
                out.contains("AtomicReferenceArray"));
    }
}
