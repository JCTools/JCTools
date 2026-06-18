package org.jctools.queues.varhandle.unpadded;

import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.jctools.queues.varhandle.JavaParsingVarHandleLinkedQueueGenerator;

/**
 * Combined VarHandle + unpadded variant of an Unsafe linked queue. Inherits the Unsafe→
 * {@link java.lang.invoke.VarHandle} rewrites from {@link JavaParsingVarHandleLinkedQueueGenerator},
 * retargets output to {@code org.jctools.queues.varhandle.unpadded} with the
 * {@code VarHandleUnpadded} name infix, drops byte-padding fields plus orphan comments via the base
 * {@code stripsPadding()} hook, and adds the {@code LinkedQueueVarHandleNode} import the unpadded
 * linked variant needs.
 */
public class JavaParsingVarHandleUnpaddedLinkedQueueGenerator
    extends JavaParsingVarHandleLinkedQueueGenerator {
  public static void main(String[] args) throws Exception {
    runJCToolsGenerator(JavaParsingVarHandleUnpaddedLinkedQueueGenerator.class, args);
  }

  public JavaParsingVarHandleUnpaddedLinkedQueueGenerator(String sourceFileName) {
    super(sourceFileName, "org.jctools.queues.varhandle.unpadded", "VarHandleUnpadded");
  }

  @Override
  protected boolean stripsPadding() {
    return true;
  }

  @Override
  public void organiseImports(CompilationUnit cu) {
    super.organiseImports(cu);
    cu.addImport(
        new ImportDeclaration(
            "org.jctools.queues.varhandle.LinkedQueueVarHandleNode", false, false));
  }
}
