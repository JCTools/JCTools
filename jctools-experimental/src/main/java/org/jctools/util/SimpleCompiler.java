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
package org.jctools.util;

import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static java.io.File.separator;
import static java.util.Arrays.asList;
import static javax.tools.JavaCompiler.CompilationTask;

/**
 * A simplified wrapper around the rage inducing Java compiler API.
 */
public class SimpleCompiler {

    private static final String compilationDirectory;
    private static final URL[] compilationDirectoryUrls;

    static {
        // TODO: consider a commandline property to configure this
        compilationDirectory = System.getProperty("java.io.tmpdir") + separator + "jctools" + separator;
        File dir = new File(compilationDirectory);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new Error("Unable to make compilation directory: " + compilationDirectory);
            }
        }

        try {
            compilationDirectoryUrls = new URL[]{new URL("file://" + compilationDirectory + "/")};
        } catch (MalformedURLException e) {
            throw new Error(e);
        }
    }

    private final JavaCompiler compiler;

    public SimpleCompiler() {
        compiler = ToolProvider.getSystemJavaCompiler();
    }

    public CompilationResult compile(
            final String name, final String src) {
        return compile(asList(new StringWrappingJavaFile(name, src)));
    }

    public CompilationResult compile(
            final List<StringWrappingJavaFile> javaFiles) {

        CompilationResult result = new CompilationResult();

        @SuppressWarnings("unchecked")
        DiagnosticListener<JavaFileObject> listener = (DiagnosticListener) result;

        final CompilationTask task = compiler.getTask(null, null,
                listener, asList("-d", compilationDirectory), null, javaFiles);
        result.setSuccessful(task.call());

        if (result.isSuccessful()) {
            result.setClassLoader(new URLClassLoader(compilationDirectoryUrls));
        }

        return result;
    }

}