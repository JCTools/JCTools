package org.jctools.queues.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.AssumptionViolatedException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test helper for golden-file comparisons. Each generator under test ships an {@code input.java}
 * and an {@code expected.java} under {@code src/test/resources/golden/<name>/}; this helper runs
 * the generator on the input, compares to the expected, and (when {@code -Dgolden.regenerate=true})
 * rewrites the expected file in place.
 * <p>
 * Regenerate workflow: {@code mvn -pl jctools-build test -Dgolden.regenerate=true}.
 */
public final class GoldenTestSupport {

    private GoldenTestSupport() {}

    private static final String REGENERATE_PROPERTY = "golden.regenerate";

    public static void assertGolden(JCToolsGenerator generator, String resourceDir) throws Exception {
        String input = readResource(resourceDir + "/input.java");
        CompilationUnit cu = new JavaParser().parse(input).getResult().orElseThrow(
                () -> new AssertionError("input.java for " + resourceDir + " did not parse"));
        String actual = GeneratorUtils.applyGenerator(generator, cu);

        // Free idempotency check: re-parse the generator output and fail if JavaParser reports any
        // problems. Catches the "compiles by accident, AST is wrong" class of bugs that a textual
        // golden compare would miss when the regeneration workflow is rubber-stamped.
        ParseResult<CompilationUnit> reparsed = new JavaParser().parse(actual);
        assertTrue(
                "Generator output for " + resourceDir + " did not re-parse cleanly: "
                        + reparsed.getProblems() + "\n--- output ---\n" + actual,
                reparsed.getProblems().isEmpty() && reparsed.getResult().isPresent());

        if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
            Path expectedPath = locateResourceFile(resourceDir + "/expected.java");
            Files.write(expectedPath, actual.getBytes(StandardCharsets.UTF_8));
            throw new AssumptionViolatedException(
                    "Regenerated " + expectedPath + "; rerun without -D" + REGENERATE_PROPERTY);
        }

        String expected = readResource(resourceDir + "/expected.java");
        assertEquals(
                "Generator output for " + resourceDir + " drifted from the golden file. " +
                "If the change is intentional, regenerate with: " +
                "mvn -pl jctools-build test -D" + REGENERATE_PROPERTY + "=true",
                expected, actual);
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = GoldenTestSupport.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new AssertionError("missing test resource: " + name);
            }
            byte[] data = readAllBytes(in);
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * Find the on-disk source path for a test resource so {@code -Dgolden.regenerate=true} can
     * write back to it (rather than the copy in {@code target/test-classes/}).
     */
    private static Path locateResourceFile(String resourceName) throws URISyntaxException {
        URL url = GoldenTestSupport.class.getClassLoader().getResource(resourceName);
        if (url == null) {
            throw new AssertionError("missing test resource: " + resourceName);
        }
        Path classpathPath = Paths.get(url.toURI());
        // url points at target/test-classes/golden/.../expected.java; rewrite it back to
        // src/test/resources/golden/.../expected.java so subsequent runs see the change.
        String classpathStr = classpathPath.toString();
        String classesMarker = java.io.File.separator + "test-classes" + java.io.File.separator;
        int idx = classpathStr.indexOf(classesMarker);
        if (idx < 0) {
            return classpathPath;
        }
        String moduleRoot = classpathStr.substring(0, classpathStr.indexOf(java.io.File.separator + "target"));
        String tail = classpathStr.substring(idx + classesMarker.length());
        return Paths.get(moduleRoot, "src", "test", "resources", tail);
    }
}
