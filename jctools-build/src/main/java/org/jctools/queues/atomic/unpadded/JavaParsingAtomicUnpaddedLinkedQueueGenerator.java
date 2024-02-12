package org.jctools.queues.atomic.unpadded;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.jctools.queues.atomic.JavaParsingAtomicLinkedQueueGenerator;

import static org.jctools.queues.util.GeneratorUtils.cleanupPaddingComments;
import static org.jctools.queues.util.GeneratorUtils.removePaddingFields;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

public class JavaParsingAtomicUnpaddedLinkedQueueGenerator extends JavaParsingAtomicLinkedQueueGenerator {
    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingAtomicUnpaddedLinkedQueueGenerator.class, args);
    }

    public JavaParsingAtomicUnpaddedLinkedQueueGenerator(String sourceFileName) {
        super(sourceFileName);
    }

    @Override
    public void cleanupComments(CompilationUnit cu) {
        super.cleanupComments(cu);
        cleanupPaddingComments(cu);
    }

    @Override
    public void organiseImports(CompilationUnit cu) {
        super.organiseImports(cu);
        cu.addImport(new ImportDeclaration("org.jctools.queues.atomic.LinkedQueueAtomicNode",
                false, false));
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        super.visit(node, arg);
        removePaddingFields(node);
    }

    @Override
    protected String outputPackage() {
        return "org.jctools.queues.atomic.unpadded";
    }

    @Override
    protected String queueClassNamePrefix() {
        return "AtomicUnpadded";
    }
}
