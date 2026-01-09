package org.jctools.queues.varhandle.unpadded;

import static org.jctools.queues.util.GeneratorUtils.cleanupPaddingComments;
import static org.jctools.queues.util.GeneratorUtils.removePaddingFields;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.jctools.queues.varhandle.JavaParsingVarHandleLinkedQueueGenerator;

public class JavaParsingVarHandleUnpaddedLinkedQueueGenerator
    extends JavaParsingVarHandleLinkedQueueGenerator {
  public static void main(String[] args) throws Exception {
    runJCToolsGenerator(JavaParsingVarHandleUnpaddedLinkedQueueGenerator.class, args);
  }

  public JavaParsingVarHandleUnpaddedLinkedQueueGenerator(String sourceFileName) {
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
    // Import the node class
    cu.addImport(
        new ImportDeclaration(
            "org.jctools.queues.varhandle.LinkedQueueVarHandleNode", false, false));
  }

  @Override
  public void visit(ClassOrInterfaceDeclaration node, Void arg) {
    super.visit(node, arg);
    removePaddingFields(node);
  }

  @Override
  protected String outputPackage() {
    return "org.jctools.queues.varhandle.unpadded";
  }

  @Override
  protected String queueClassNamePrefix() {
    return "VarHandleUnpadded";
  }
}
