package org.jctools.queues.varhandle.unpadded;

import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

import org.jctools.queues.varhandle.JavaParsingVarHandleArrayQueueGenerator;

/**
 * Combined VarHandle + unpadded variant of an Unsafe array queue. Inherits the Unsafe→
 * {@link java.lang.invoke.VarHandle} rewrites from {@link JavaParsingVarHandleArrayQueueGenerator},
 * retargets output to {@code org.jctools.queues.varhandle.unpadded} with the
 * {@code VarHandleUnpadded} name infix, and drops byte-padding fields plus orphan comments via the
 * base {@code stripsPadding()} hook.
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
  protected boolean stripsPadding() {
    return true;
  }
}
