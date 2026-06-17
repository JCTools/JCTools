package org.jctools.queues.varhandle.unpadded;

import static org.jctools.queues.util.GeneratorUtils.cleanupPaddingComments;
import static org.jctools.queues.util.GeneratorUtils.removePaddingFields;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.jctools.queues.varhandle.JavaParsingVarHandleArrayQueueGenerator;

/**
 * Combined VarHandle + unpadded variant of an Unsafe array queue. Inherits the Unsafe→
 * {@link java.lang.invoke.VarHandle} rewrites from {@link JavaParsingVarHandleArrayQueueGenerator},
 * retargets output to {@code org.jctools.queues.varhandle.unpadded} with the
 * {@code VarHandleUnpadded} name infix, and drops byte-padding fields plus orphan comments after
 * the parent visitor has run.
 */
public class JavaParsingVarHandleUnpaddedArrayQueueGenerator
    extends JavaParsingVarHandleArrayQueueGenerator {
  public static void main(String[] args) throws Exception {
    runJCToolsGenerator(JavaParsingVarHandleUnpaddedArrayQueueGenerator.class, args);
  }

  public JavaParsingVarHandleUnpaddedArrayQueueGenerator(String sourceFileName) {
    super(sourceFileName, "org.jctools.queues.varhandle.unpadded", "VarHandleUnpadded");
  }

  @Override
  public void cleanupComments(CompilationUnit cu) {
    super.cleanupComments(cu);
    cleanupPaddingComments(cu);
  }

  @Override
  public void visit(ClassOrInterfaceDeclaration node, Void arg) {
    super.visit(node, arg);
    removePaddingFields(node);
  }
}
