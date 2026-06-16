package org.jctools.queues.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link GeneratorUtils} helpers, particularly the workarounds for
 * {@link LexicalPreservingPrinter} bugs around {@code VariableDeclarator}-hosted types.
 */
public class GeneratorUtilsTest {

    private static CompilationUnit parseAndSetup(String source) {
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow(
                () -> new AssertionError("Failed to parse: " + source));
        LexicalPreservingPrinter.setup(cu);
        return cu;
    }

    @Test
    public void renameType_renamesInAllPositionsIncludingVariableDeclarator() {
        // Regression: ClassOrInterfaceType.setName(...) does not propagate through LPP for types
        // hosted by a VariableDeclarator (field or local). renameType uses replace() to bypass
        // this, so the rename should land in field types, return types, parameter types, and
        // local-variable types.
        CompilationUnit cu = parseAndSetup(
                "class C<E> {\n" +
                "  Foo<E> field;\n" +
                "  Foo<E> bar(Foo<E> p) {\n" +
                "    Foo<E> x = null;\n" +
                "    return x;\n" +
                "  }\n" +
                "}");

        for (ClassOrInterfaceType t : cu.findAll(ClassOrInterfaceType.class)) {
            if ("Foo".equals(t.getNameAsString())) {
                GeneratorUtils.renameType(t, "Renamed");
            }
        }

        String out = LexicalPreservingPrinter.print(cu);
        assertFalse("Foo should be fully renamed but found leftover: " + out, out.contains("Foo<"));
        // Spot-check each position
        assertTrue(out.contains("Renamed<E> field"));
        assertTrue(out.contains("Renamed<E> bar"));
        assertTrue(out.contains("Renamed<E> p"));
        assertTrue(out.contains("Renamed<E> x"));
    }

    @Test
    public void renameType_preservesScopeAndAnnotations() {
        CompilationUnit cu = parseAndSetup(
                "class C { @Deprecated outer.Foo field; }");

        ClassOrInterfaceType t = cu.findAll(ClassOrInterfaceType.class).stream()
                .filter(c -> "Foo".equals(c.getNameAsString()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        GeneratorUtils.renameType(t, "Renamed");

        String out = LexicalPreservingPrinter.print(cu);
        // Scope `outer.` and the @Deprecated annotation must survive
        assertTrue("output should keep scope qualifier: " + out, out.contains("outer.Renamed"));
        assertTrue("output should keep annotation: " + out, out.contains("@Deprecated"));
    }

    @Test
    public void replaceType_dropsArrayBracketsOnFieldDeclarator() {
        // Regression: setType(ArrayType -> non-ArrayType) on a VariableDeclarator leaves the
        // original `[]` brackets in the output. replaceType rebuilds the FieldDeclaration with
        // a fresh VariableDeclarator so brackets are gone.
        CompilationUnit cu = parseAndSetup(
                "class C<E> { protected final E[] buffer; }");

        VariableDeclarator vd = cu.findAll(VariableDeclarator.class).get(0);
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "AtomicReferenceArray");
        newType.setTypeArguments(((ArrayType) vd.getType()).getComponentType().clone());
        GeneratorUtils.replaceType(vd, newType);

        String out = LexicalPreservingPrinter.print(cu);
        assertTrue(out.contains("AtomicReferenceArray<E> buffer"));
        assertFalse("brackets should be dropped: " + out, out.contains("AtomicReferenceArray<E>[]"));
    }

    @Test
    public void replaceType_dropsArrayBracketsOnLocalVariableDeclarator() {
        CompilationUnit cu = parseAndSetup(
                "class C<E> { void m() { final E[] buffer = null; } }");

        VariableDeclarator vd = cu.findAll(VariableDeclarator.class).get(0);
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "AtomicReferenceArray");
        newType.setTypeArguments(((ArrayType) vd.getType()).getComponentType().clone());
        GeneratorUtils.replaceType(vd, newType);

        String out = LexicalPreservingPrinter.print(cu);
        assertTrue(out.contains("AtomicReferenceArray<E> buffer"));
        assertFalse("brackets should be dropped: " + out, out.contains("AtomicReferenceArray<E>[]"));
    }

    @Test
    public void replaceType_preservesFieldModifiersAndCommentOnRebuild() {
        CompilationUnit cu = parseAndSetup(
                "class C<E> {\n" +
                "  /** doc */\n" +
                "  protected final E[] buffer;\n" +
                "}");

        VariableDeclarator vd = cu.findAll(VariableDeclarator.class).get(0);
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "AtomicReferenceArray");
        newType.setTypeArguments(((ArrayType) vd.getType()).getComponentType().clone());
        GeneratorUtils.replaceType(vd, newType);

        String out = LexicalPreservingPrinter.print(cu);
        assertTrue("javadoc should be preserved: " + out, out.contains("/** doc */"));
        assertTrue("modifiers should be preserved: " + out, out.contains("protected final AtomicReferenceArray<E> buffer"));
    }

    @Test
    public void replaceType_onParameterUsesPlainSetType() {
        // Parameter setType is not affected by the LPP bug; verify replaceType still works there.
        CompilationUnit cu = parseAndSetup(
                "class C<E> { void m(E[] buffer) {} }");

        Parameter p = cu.findAll(Parameter.class).get(0);
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "AtomicReferenceArray");
        newType.setTypeArguments(((ArrayType) p.getType()).getComponentType().clone());
        GeneratorUtils.replaceType(p, newType);

        String out = LexicalPreservingPrinter.print(cu);
        assertTrue(out.contains("AtomicReferenceArray<E> buffer"));
        assertFalse(out.contains("[]"));
    }

    @Test
    public void cleanupPaddingComments_removesByteAndDropCommentsOnly() {
        // The cleanup matchers fire on comments that contain "byte b", "drop 8b" or "drop 16b" —
        // the patterns used in JCTools queue sources for orphaned commented-out padding lines and
        // pad-drop bookkeeping.
        CompilationUnit cu = parseAndSetup(
                "class C {\n" +
                "  // byte b170,b171,b172,b173,b174,b175,b176,b177;//128b\n" +
                "  // drop 8b\n" +
                "  // drop 16b\n" +
                "  // keep this comment\n" +
                "  long producerIndex;\n" +
                "}");

        GeneratorUtils.cleanupPaddingComments(cu);
        String out = LexicalPreservingPrinter.print(cu);

        assertFalse("commented-out padding line removed: " + out, out.contains("byte b170"));
        assertFalse("'drop 8b' comment removed: " + out, out.contains("drop 8b"));
        assertFalse("'drop 16b' comment removed: " + out, out.contains("drop 16b"));
        assertTrue("non-padding comment kept: " + out, out.contains("// keep this comment"));
    }

    @Test
    public void removePaddingFields_dropsByteFieldsOnly() {
        CompilationUnit cu = parseAndSetup(
                "class C {\n" +
                "  byte b000;\n" +
                "  byte b170;\n" +
                "  long producerIndex;\n" +
                "}");

        GeneratorUtils.removePaddingFields(cu.getClassByName("C").orElseThrow(AssertionError::new));
        String out = LexicalPreservingPrinter.print(cu);

        assertFalse(out.contains("b000"));
        assertFalse(out.contains("b170"));
        assertTrue("non-padding fields must survive: " + out, out.contains("long producerIndex"));
    }

    @Test
    public void formatMultilineJavadoc_indentsConsistently() {
        String formatted = GeneratorUtils.formatMultilineJavadoc(0, "first", "second");
        assertEquals("\n * first\n * second\n ", formatted);
    }

    @Test
    public void formatMultilineJavadoc_appliesIndent() {
        String formatted = GeneratorUtils.formatMultilineJavadoc(1, "line");
        assertEquals("\n     * line\n     ", formatted);
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Bug 3: a parse failure used to surface as a bare {@link java.util.NoSuchElementException}
     * with no diagnostic about which file or what was wrong. After the fix, the file path and the
     * JavaParser problem list make it into the exception message.
     */
    @Test
    public void runJCToolsGenerator_surfacesParseFailures() throws Exception {
        File outputDir = tmp.newFolder("out");
        File brokenSource = tmp.newFile("Broken.java");
        Files.write(brokenSource.toPath(), "this is { not java".getBytes(StandardCharsets.UTF_8));

        try {
            GeneratorUtils.runJCToolsGenerator(
                    org.jctools.queues.unpadded.JavaParsingUnpaddedQueueGenerator.class,
                    new String[] { outputDir.getAbsolutePath(), brokenSource.getAbsolutePath() });
            fail("expected an IllegalStateException for the unparseable source");
        } catch (IllegalStateException expected) {
            String msg = expected.getMessage();
            assertNotNull(msg);
            assertTrue("message names the file: " + msg, msg.contains("Broken.java"));
            assertTrue("message includes parse problems: " + msg, msg.toLowerCase().contains("problem")
                    || msg.contains("(line")  // JavaParser problem messages typically include "(line N,col M)"
                    || msg.contains("Parse error"));
        }
    }

    /**
     * Bug 6: replaceType used to silently fall back to {@code holder.setType(...)} (the broken
     * path it exists to avoid) when called on a {@link VariableDeclarator} whose parent is neither
     * a {@link FieldDeclaration} nor a {@link com.github.javaparser.ast.expr.VariableDeclarationExpr}.
     * After the fix it throws so callers find out at codegen time, not in the generated output.
     */
    @Test
    public void replaceType_throwsOnUnsupportedVariableDeclaratorParent() {
        // A LambdaExpr can host a VariableDeclarator-style parameter, but we synthesise a
        // detached VariableDeclarator (no parent at all) to stress the unknown-parent branch.
        VariableDeclarator orphan = new VariableDeclarator(
                new ArrayType(new ClassOrInterfaceType(null, "E")), "buffer");
        // orphan has no parent, so replaceType cannot rebuild a containing declaration
        try {
            GeneratorUtils.replaceType(orphan, new ClassOrInterfaceType(null, "AtomicReferenceArray"));
            fail("expected IllegalStateException for unsupported parent");
        } catch (IllegalStateException expected) {
            assertTrue("message describes the problem: " + expected.getMessage(),
                    expected.getMessage().contains("unsupported parent"));
        }
    }
    /**
     * Bug 8: when prepending the generator NOTE to a node that already had a multi-line Javadoc,
     * the merged comment used to render the bridge between the two sections as a stray space-only
     * line (no leading {@code *}). The fix inserts an explicit {@code *} before the existing
     * content so the merged Javadoc renders as a continuous block.
     */
    @Test
    public void prependGeneratedNoteJavadoc_bridgesExistingJavadocWithAsterisk() {
        CompilationUnit cu = parseAndSetup(
                "/**\n" +
                " * Original docs.\n" +
                " * Second line.\n" +
                " */\n" +
                "class C {}");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration node =
                cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).get(0);

        GeneratorUtils.prependGeneratedNoteJavadoc(node, GeneratorUtilsTest.class, "Original.java");

        String out = LexicalPreservingPrinter.print(cu);
        // Each interior line of the merged Javadoc must start with " *" — no stray space-only
        // lines that break the Javadoc block visually.
        for (String line : out.split("\n")) {
            if (line.isEmpty()) continue;
            // Skip the opening /**, the closing */, and the source line outside the Javadoc.
            if (line.startsWith("/**") || line.contains("*/") || line.startsWith("class")) continue;
            assertTrue("javadoc line missing leading ' *': [" + line + "] in:\n" + out,
                    line.startsWith(" *"));
        }
        assertTrue("note line present: " + out, out.contains(" * NOTE: This class was automatically generated"));
        assertTrue("source-file line present: " + out, out.contains("Original.java"));
        assertTrue("original first line preserved: " + out, out.contains(" * Original docs."));
        assertTrue("original second line preserved: " + out, out.contains(" * Second line."));
        assertFalse("no stray space-only bridge line: " + out, out.contains("\n \n"));
    }

    @Test
    public void prependGeneratedNoteJavadoc_handlesMissingExistingJavadoc() {
        CompilationUnit cu = parseAndSetup("class C {}");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration node =
                cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).get(0);

        GeneratorUtils.prependGeneratedNoteJavadoc(node, GeneratorUtilsTest.class, "Original.java");

        String out = LexicalPreservingPrinter.print(cu);
        assertTrue("note prepended: " + out, out.contains("NOTE: This class was automatically generated"));
        assertFalse("no stray space-only line in pure-note Javadoc: " + out, out.contains("\n \n"));
    }
}
