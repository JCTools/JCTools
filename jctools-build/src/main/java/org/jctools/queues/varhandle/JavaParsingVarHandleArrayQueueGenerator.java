package org.jctools.queues.varhandle;

import static org.jctools.queues.util.GeneratorUtils.prependGeneratedNoteJavadoc;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * This generator takes in an JCTools 'ArrayQueue' Java source file and patches {@link
 * sun.misc.Unsafe} accesses into {@link java.lang.invoke.VarHandle}. It outputs a Java source file
 * with these patches.
 *
 * <p>An 'ArrayQueue' is one that is backed by a circular array and use a <code>producerLimit</code>
 * and a <code>consumerLimit</code> field to track the positions of each.
 */
public class JavaParsingVarHandleArrayQueueGenerator extends JavaParsingVarHandleQueueGenerator {

  /** The unpadded SPSC pool queue type used by xadd-family chunk pools in the VarHandle variant. */
  protected final String unpaddedPoolQueueName = "SpscVarHandleUnpaddedArrayQueue";
  protected final String unpaddedPoolQueueImport = "org.jctools.queues.varhandle.unpadded.SpscVarHandleUnpaddedArrayQueue";

  public static void main(String[] args) throws Exception {
    runJCToolsGenerator(JavaParsingVarHandleArrayQueueGenerator.class, args);
  }

  public JavaParsingVarHandleArrayQueueGenerator(String sourceFileName) {
    this(sourceFileName, "org.jctools.queues.varhandle", "VarHandle");
  }

  /** Constructor for unpadded subclasses to pass through different package/prefix values. */
  protected JavaParsingVarHandleArrayQueueGenerator(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
    super(sourceFileName, outputPackage, queueClassNamePrefix);
  }

  /**
   * Replaces {@code SpscArrayQueue} type references in variable/parameter declarations with the
   * VarHandle unpadded pool queue variant. Used by xadd queues which pool chunks internally.
   */
  @Override
  void processSpecialNodeTypes(NodeWithType<?, Type> node, String name)
  {
    Type type = node.getType();
    if (isRefType(type, "SpscArrayQueue")) {
      ClassOrInterfaceType newType = classType(unpaddedPoolQueueName);
      if (type instanceof ClassOrInterfaceType) {
        ((ClassOrInterfaceType) type).getTypeArguments().ifPresent(newType::setTypeArguments);
      }
      node.setType(newType);
      usesPoolQueue = true;
    }
  }

  /**
   * Replaces {@code new SpscArrayQueue<R>(...)} with the VarHandle unpadded pool queue variant.
   */
  @Override
  public void visit(ObjectCreationExpr n, Void arg) {
    super.visit(n, arg);
    if (isRefType(n.getType(), "SpscArrayQueue")) {
      ClassOrInterfaceType newType = classType(unpaddedPoolQueueName);
      n.getType().getTypeArguments().ifPresent(newType::setTypeArguments);
      n.setType(newType);
      usesPoolQueue = true;
    }
  }

  @Override
  public void visit(ConstructorDeclaration n, Void arg) {
    super.visit(n, arg);
    String nameAsString = n.getNameAsString();
    // Ignore internal class WeakIterator which we don't need to rename
    if (nameAsString.equals("WeakIterator"))
      return;
    // Update the ctor to match the class name
    n.setName(translateQueueName(n.getNameAsString()));
  }

  @Override
  public void visit(ClassOrInterfaceDeclaration node, Void arg) {
    super.visit(node, arg);

    replaceParentClasses(node);

    String nameAsString = node.getNameAsString();
    // Ignore internal class WeakIterator which we don't need to rename
    if (nameAsString.equals("WeakIterator"))
      return;

    node.setName(translateQueueName(node.getNameAsString()));

    if (isCommentPresent(node, GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS)) {
      node.setComment(null);
      removeStaticFieldsAndInitialisers(node);
      patchVarHandleAccessorMethods(node);
      // Mark that this file has VarHandle fields so we can add proper imports
      hasVarHandleFields = true;
    }

    for (MethodDeclaration method : node.getMethods()) {
      if (isCommentPresent(method, GEN_DIRECTIVE_METHOD_IGNORE)) {
        method.remove();
      }
    }

    if (!node.getMethodsByName("failFastOffer").isEmpty()) {
      MethodDeclaration deprecatedMethodRedirect = node.addMethod("weakOffer", Keyword.PUBLIC);
      patchMethodAsDeprecatedRedirector(
          deprecatedMethodRedirect,
          "failFastOffer",
          PrimitiveType.intType(),
          new Parameter(classType("E"), "e"));
    }

    prependGeneratedNoteJavadoc(node, getClass(), sourceFileName);
  }

  String varHandleFieldName(String fieldName) {
    switch (fieldName) {
      case "producerIndex":
        return "VH_PRODUCER_INDEX";
      case "consumerIndex":
        return "VH_CONSUMER_INDEX";
      case "producerLimit":
        return "VH_PRODUCER_LIMIT";
      case "producerIndexCache":
        return "VH_PRODUCER_INDEX_CACHE";
      case "blocked":
        return "VH_BLOCKED";
      // Xadd queue family fields — used by MpUnboundedXaddChunk and its subclasses
      case "producerChunk":
        return "VH_PRODUCER_CHUNK";
      case "producerChunkIndex":
        return "VH_PRODUCER_CHUNK_INDEX";
      case "consumerChunk":
        return "VH_CONSUMER_CHUNK";
      case "index":
        return "VH_INDEX";
      case "prev":
        return "VH_PREV";
      case "next":
        return "VH_NEXT";
      default:
        throw new IllegalArgumentException("Unhandled field: " + fieldName);
    }
  }

  /**
   * Patch each method whose name ends with {@code <prefix>FieldName} (capitalised) with a body
   * that delegates to a {@link java.lang.invoke.VarHandle} for the matching field. Handled
   * prefixes: {@code so}, {@code sp}, {@code cas}, {@code getAndAdd}, {@code getAndIncrement},
   * {@code sv}, {@code lv}, {@code lp}. {@code lp}/{@code sv} are plain reads/writes on the
   * field; {@code lv} is a plain read when the field is already {@code volatile} and a
   * {@code VarHandle.getVolatile} call otherwise; the rest delegate to the VarHandle.
   *
   * @param n the AST node for the containing class
   */
  private void patchVarHandleAccessorMethods(ClassOrInterfaceDeclaration n) {
    String className = n.getNameAsString();
    List<FieldInfo> varHandleFields = new ArrayList<>();

    for (FieldDeclaration field : n.getFields()) {
      if (field.getModifiers().contains(Modifier.staticModifier())) {
        // Ignore statics
        continue;
      }
      // Skip final fields — e.g. MpUnboundedXaddChunk.pooled has accessor isPooled()
      // whose name matches the suffix pattern but must not be patched
      if (field.getModifiers().contains(Modifier.finalModifier())) {
        continue;
      }

      // Check if the field is volatile in the original source
      boolean isFieldVolatile = field.getModifiers().contains(Modifier.volatileModifier());

      for (VariableDeclarator variable : field.getVariables()) {
        String variableName = variable.getNameAsString();
        String methodNameSuffix = capitalise(variableName);
        Type fieldType = variable.getType();

        boolean variableUsesVarHandle = false;
        for (MethodDeclaration method : n.getMethods()) {
          variableUsesVarHandle |= patchVarHandleAccessorMethod(variableName, method, methodNameSuffix, isFieldVolatile);
        }

        if (variableUsesVarHandle) {
          varHandleFields.add(new FieldInfo(variableName, fieldType));
        }
      }
    }

    // Prepend the VarHandle field declarations (in original order) and the static initializer
    // that wires them up. add(0, ...) per field would interleave declarations and the init block,
    // producing a visually-broken output; we insert as a contiguous prefix instead.
    if (!varHandleFields.isEmpty()) {
      for (int i = 0; i < varHandleFields.size(); i++) {
        n.getMembers().add(i, declareVarHandle(className, varHandleFields.get(i).name));
      }
      n.getMembers().add(varHandleFields.size(), createVarHandleStaticInitializerWithTypes(n, className, varHandleFields));
    }
  }

  @Override
  protected void addExtraImports(CompilationUnit cu) {
    if (usesPoolQueue && unpaddedPoolQueueImport != null) {
      cu.addImport(new ImportDeclaration(unpaddedPoolQueueImport, false, false));
    }
  }
}
