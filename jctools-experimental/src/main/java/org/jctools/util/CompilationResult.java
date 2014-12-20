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
