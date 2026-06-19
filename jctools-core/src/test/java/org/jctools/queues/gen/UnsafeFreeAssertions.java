/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues.gen;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Shared assertions verifying that a queue family stays free of {@code sun.misc.Unsafe} —
 * neither at runtime (no transitive load of JCTools' Unsafe wrapper through static-init) nor at
 * source level (no leftover {@code import} of an {@code Unsafe*} class). Used by both the atomic
 * and VarHandle families to enforce the same property; family-specific test classes parameterise
 * with package names and source directories.
 *
 * <p>{@code sun.misc.Unsafe} itself sits on the bootstrap loader and is always present —
 * irrelevant here. What matters is whether the family triggers JCTools' Unsafe wrapper
 * ({@link org.jctools.util.UnsafeAccess} and siblings) to initialise.
 */
public final class UnsafeFreeAssertions {

    public static final Set<String> UNSAFE_HOLDER_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.jctools.util.UnsafeAccess",
            "org.jctools.util.UnsafeRefArrayAccess",
            "org.jctools.util.UnsafeLongArrayAccess",
            "org.jctools.util.UnsafeJvmInfo"
    )));

    private UnsafeFreeAssertions() {}

    /**
     * Assert that loading any concrete {@link Queue}-implementing class discovered in the given
     * packages does not transitively pull in any of {@link #UNSAFE_HOLDER_CLASSES}.
     * @param familyName short label used in failure messages (e.g. "atomic", "VarHandle")
     */
    public static void assertConcreteQueuesDoNotLoadUnsafe(
            List<String> packages, int minExpected, String familyName) throws Exception {
        List<String> queues = discoverConcreteQueues(packages);
        assertTrue("expected to discover concrete " + familyName + " queues, found " + queues.size(),
                queues.size() >= minExpected);

        for (String fqn : queues) {
            Set<String> leaked = unsafeHoldersLoadedBy(fqn);
            assertTrue(
                    "Loading " + fqn + " transitively loaded Unsafe-holder classes: "
                            + leaked + ". " + familyName + " family must remain Unsafe-free.",
                    leaked.isEmpty());
        }
    }

    /**
     * Run {@code fqn} through a fresh tracking classloader (parent = platform loader, so every
     * {@code org.jctools.*} load is observed locally) and return the subset of
     * {@link #UNSAFE_HOLDER_CLASSES} that ended up loaded.
     */
    public static Set<String> unsafeHoldersLoadedBy(String fqn) throws Exception {
        TrackingClassLoader loader = new TrackingClassLoader(currentClasspath());
        // initialize=true forces <clinit> to run on this class and the chain its static init
        // touches. That's the operative load — a static field of type UnsafeAccess would resolve
        // here.
        Class.forName(fqn, true, loader);
        // Sanity: we actually loaded something through our tracker (didn't hit a parent cache).
        assertFalse("classloader saw no jctools loads for " + fqn, loader.loaded.isEmpty());

        Set<String> hits = new HashSet<>(loader.loaded);
        hits.retainAll(UNSAFE_HOLDER_CLASSES);
        return hits;
    }

    /**
     * Assert no {@code .java} file under the given relative source directories has an import line
     * mentioning {@code Unsafe}.
     * @param familyName short label used in failure messages
     */
    public static void assertNoUnsafeImportsIn(
            List<String> relativeSourceDirs, int minExpected, String familyName) throws Exception {
        int filesScanned = 0;
        for (String relDir : relativeSourceDirs) {
            Path dir = Paths.get(relDir);
            assertTrue("source dir not found from cwd " + Paths.get("").toAbsolutePath() + ": " + dir,
                    Files.isDirectory(dir));
            try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
                for (Path file : entries.sorted().toArray(Path[]::new)) {
                    if (!file.toString().endsWith(".java")) {
                        continue;
                    }
                    String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    String offender = findUnsafeImport(src);
                    if (offender != null) {
                        throw new AssertionError(file + " — generated " + familyName + " source has"
                                + " an Unsafe import that the patcher should have stripped: "
                                + offender);
                    }
                    filesScanned++;
                }
            }
        }
        assertTrue("expected to scan some " + familyName + " source files (got " + filesScanned + ")",
                filesScanned >= minExpected);
    }

    /**
     * @return the first import line in {@code src} that mentions {@code Unsafe} (trimmed), or
     * {@code null} if there is none.
     */
    public static String findUnsafeImport(String src) {
        for (String line : src.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import") && trimmed.contains("Unsafe")) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Enumerate concrete (non-abstract) {@link Queue}-implementing classes in the given packages.
     * Loading happens through the regular test classloader and is independent of the tracking
     * classloader used by {@link #unsafeHoldersLoadedBy(String)}.
     */
    private static List<String> discoverConcreteQueues(List<String> packageNames) throws Exception {
        List<String> queues = new ArrayList<>();
        for (String packageName : packageNames) {
            String resourcePath = packageName.replace('.', '/');
            URL packageUrl = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            assertNotNull("package not on classpath: " + packageName, packageUrl);
            File dir = new File(packageUrl.toURI());
            File[] classFiles = dir.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
            assertNotNull("no class files under " + dir, classFiles);

            for (File f : classFiles) {
                String simpleName = f.getName().substring(0, f.getName().length() - ".class".length());
                Class<?> cls = Class.forName(packageName + "." + simpleName);
                if (Modifier.isAbstract(cls.getModifiers())) continue;
                if (!Queue.class.isAssignableFrom(cls)) continue;
                queues.add(packageName + "." + simpleName);
            }
        }
        return queues;
    }

    private static URL[] currentClasspath() throws Exception {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = new File(entries[i]).toURI().toURL();
        }
        return urls;
    }

    private static ClassLoader platformOrBootstrap() {
        // JDK 9+: Use the platform loader so JDK classes resolve there, not through our tracker.
        // JDK 8: getPlatformClassLoader doesn't exist; ClassLoader.getSystemClassLoader().getParent()
        // is the extension loader and works the same way for our purposes.
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (NoSuchMethodException e) {
            return ClassLoader.getSystemClassLoader().getParent();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class TrackingClassLoader extends URLClassLoader {
        final Set<String> loaded = new HashSet<>();

        TrackingClassLoader(URL[] urls) {
            super(urls, platformOrBootstrap());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> c = super.findClass(name);
            loaded.add(name);
            return c;
        }
    }
}
