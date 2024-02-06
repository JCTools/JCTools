package org.jctools.queues.util;

import com.github.javaparser.ast.CompilationUnit;

public interface JCToolsGenerator {
    void visit(CompilationUnit cu, Void arg);

    void cleanupComments(CompilationUnit cu);

    void organiseImports(CompilationUnit cu);

    String translateQueueName(String qName);
}
