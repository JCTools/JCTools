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

import javax.tools.Diagnostic;
import java.util.List;

import static java.util.Collections.unmodifiableList;


public final class CompilationResult {

    private final List<Diagnostic<StringWrappingJavaFile>> diagnostics;
    private final ClassLoader classLoader;

    public CompilationResult(final ClassLoader classLoader, List<Diagnostic<StringWrappingJavaFile>> diagnostics) {
        this.diagnostics = diagnostics;
        this.classLoader = classLoader;
    }

    public CompilationResult(List<Diagnostic<StringWrappingJavaFile>> diagnostics) {
        this(null, diagnostics);
    }

    public List<Diagnostic<StringWrappingJavaFile>> getDiagnostics() {
        return unmodifiableList(diagnostics);
    }

    public boolean isSuccessful() {
        return classLoader != null;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public String toString() {
        return isSuccessful() ? "Compilation was successful" : "Errors:\n" + diagnostics.toString();
    }
}
