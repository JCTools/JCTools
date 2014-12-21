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
import javax.tools.DiagnosticListener;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;


public class CompilationResult implements DiagnosticListener<StringWrappingJavaFile> {

    private final List<Diagnostic<? extends StringWrappingJavaFile>> diagnostics;

    private ClassLoader classLoader;
    private boolean successful;

    public CompilationResult() {
        diagnostics = new ArrayList<Diagnostic<? extends StringWrappingJavaFile>>();
    }

    @Override
    public void report(Diagnostic<? extends StringWrappingJavaFile> diagnostic) {
        diagnostics.add(diagnostic);
    }

    public List<Diagnostic<? extends StringWrappingJavaFile>> getDiagnostics() {
        return unmodifiableList(diagnostics);
    }

    public boolean isSuccessful() {
        return successful;
    }

    void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
