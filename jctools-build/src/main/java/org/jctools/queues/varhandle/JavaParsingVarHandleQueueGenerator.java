package org.jctools.queues.varhandle;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import org.jctools.queues.util.JavaParsingQueueGeneratorBase;

/**
 * Base class of the VarHandle queue generators. These generators work by parsing a Java source file
 * using {@link JavaParser}, and replacing idioms that use {@link sun.misc.Unsafe} to instead use
 * VarHandle, e.g.{@link java.lang.invoke.VarHandle}. They are coupled directly to the structure of
 * the expected input Java source file and are used as a utility to maintain unsafe non-portable
 * optimized code along side safe portable code for uses on JDK 9+.
 *
 * <p>These generators are coupled with the structure and naming of fields, variables and methods
 * and are not suitable for general purpose use.
 */
public abstract class JavaParsingVarHandleQueueGenerator extends JavaParsingQueueGeneratorBase {

  // Track whether the current file has VarHandle field declarations
  protected boolean hasVarHandleFields = false;
  protected boolean usesPoolQueue = false;

  JavaParsingVarHandleQueueGenerator(String sourceFileName) {
    this(sourceFileName, "org.jctools.queues.varhandle", "VarHandle");
  }

  /**
   * Constructor for unpadded-variant subclasses (varhandle + unpadded combined). Other subclasses
   * delegate to the single-arg constructor with the default varhandle infix and package.
   */
  protected JavaParsingVarHandleQueueGenerator(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
    super(sourceFileName, outputPackage, queueClassNamePrefix);
  }

  abstract void processSpecialNodeTypes(NodeWithType<?, Type> node, String name);

  abstract String varHandleFieldName(String fieldName);

  @Override
  public void visit(Parameter n, Void arg) {
    super.visit(n, arg);
    // Process parameters to methods and ctors
    processSpecialNodeTypes(n, n.getNameAsString());
  }

  @Override
  public void visit(VariableDeclarator n, Void arg) {
    super.visit(n, arg);
    // Replace declared variables with altered types
    processSpecialNodeTypes(n, n.getNameAsString());
  }

  boolean patchVarHandleAccessorMethod(
      String variableName, MethodDeclaration method, String methodNameSuffix, boolean isFieldVolatile) {
    boolean usesVarHandle = false;
    String methodName = method.getNameAsString();
    if (!methodName.endsWith(methodNameSuffix)) {
      // Leave it untouched
      return false;
    }

    // Get the actual parameter name from the method, default to "newValue"
    String newValueName = "newValue";
    if (!method.getParameters().isEmpty()) {
      newValueName = method.getParameters().get(0).getNameAsString();
    }

    if (methodName.startsWith("so") || methodName.startsWith("sp")) {
      /*
       * In the case of 'sp' use setRelease as the weakest
       * ordering allowed by VarHandle
       */
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);

      method.setBody(varHandleSetRelease(varHandleFieldName, newValueName));
    } else if (methodName.startsWith("cas")) {
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);
      // CAS methods have 2 parameters: expect and newValue
      String expectedValueName = "expect";
      if (!method.getParameters().isEmpty()) {
        expectedValueName = method.getParameters().get(0).getNameAsString();
      }
      if (method.getParameters().size() >= 2) {
        newValueName = method.getParameters().get(1).getNameAsString();
      }
      method.setBody(varHandleCompareAndSet(varHandleFieldName, expectedValueName, newValueName));
    } else if (methodName.startsWith("getAndAdd")) {
      // Xadd queues use getAndAddProducerIndex(long delta) — maps to VarHandle.getAndAdd()
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);
      String deltaName = method.getParameters().isEmpty() ? "delta" :
          method.getParameters().get(0).getNameAsString();
      method.setBody(varHandleGetAndAdd(varHandleFieldName, deltaName));
    } else if (methodName.startsWith("getAndIncrement")) {
      // Xadd queues use getAndIncrementProducerIndex() — maps to VarHandle.getAndAdd(this, 1L).
      // Must use 1L (long literal) — VarHandle polymorphic signature requires exact type match.
      // Pass it as a LongLiteralExpr so the AST is idempotent across parse-rewrite-print cycles.
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);
      method.setBody(varHandleGetAndAdd(varHandleFieldName,
          new com.github.javaparser.ast.expr.LongLiteralExpr("1L")));
    } else if (methodName.startsWith("sv")) {
      method.setBody(fieldAssignment(variableName, newValueName));
    } else if (methodName.startsWith("lv")) {
      if (isFieldVolatile) {
        // Field is already volatile, just return it directly (like lp)
        method.setBody(returnField(variableName));
      } else {
        // Field is not volatile, use VarHandle for volatile access
        usesVarHandle = true;
        String varHandleFieldName = varHandleFieldName(variableName);
        method.setBody(varHandleGetVolatile(varHandleFieldName, method.getType()));
      }
    } else if (methodName.startsWith("lp")) {
      method.setBody(returnField(variableName));
    } else {
      throw new IllegalStateException("Unhandled method: " + methodName);
    }
    return usesVarHandle;
  }

  @Override
  public void cleanupComments(CompilationUnit cu) {
    // nop
  }

  @Override
  public void organiseImports(CompilationUnit cu) {
    List<ImportDeclaration> importDecls = new ArrayList<>();

    // remove irrelevant imports
    for (ImportDeclaration importDeclaration : cu.getImports()) {
      String name = importDeclaration.getNameAsString();
      if (name.startsWith("org.jctools.util.Unsafe")) {
        continue;
      }

      if (name.startsWith("org.jctools.queues.LinkedArrayQueueUtil")) {
        continue;
      }

      // Rewrite static imports from Chunk classes to point at the translated variant,
      // e.g. "import static o.j.q.MpmcUnboundedXaddChunk.NOT_USED" ->
      //      "import static o.j.q.varhandle.MpmcUnboundedXaddVarHandleChunk.NOT_USED"
      if (importDeclaration.isStatic() && name.startsWith("org.jctools.queues.") && name.contains("Chunk.")) {
        String simpleName = name.substring(name.lastIndexOf('.') + 1);
        String className = name.substring("org.jctools.queues.".length(), name.lastIndexOf('.'));
        if (className.endsWith("Chunk")) {
          String translatedClass = translateQueueName(className);
          importDecls.add(new ImportDeclaration(outputPackage + "." + translatedClass + "." + simpleName, true, false));
          continue;
        }
      }

      importDecls.add(importDeclaration);
    }
    cu.getImports().clear();
    for (ImportDeclaration importDecl : importDecls) {
      cu.addImport(importDecl);
    }

    // Only add java.lang.invoke imports if the class has VarHandle fields
    // (set during the visit phase when we find $gen:ordered-fields comment)
    if (hasVarHandleFields) {
      cu.addImport(new ImportDeclaration("java.lang.invoke.MethodHandles", false, false));
      cu.addImport(new ImportDeclaration("java.lang.invoke.VarHandle", false, false));
    }

    cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
    cu.addImport(staticImportDeclaration("org.jctools.queues.varhandle.VarHandleQueueUtil"));

    addExtraImports(cu);
  }

  protected void addExtraImports(CompilationUnit cu) {
    // subclasses can override to add extra imports
  }

  /** Generates something like <code>return (long) VH_PRODUCER_INDEX.getAndAdd(this, delta)</code> */
  protected BlockStmt varHandleGetAndAdd(String varHandleFieldName, String deltaName) {
    return varHandleGetAndAdd(varHandleFieldName, new NameExpr(deltaName));
  }

  /**
   * Same as {@link #varHandleGetAndAdd(String, String)} but takes a literal/expression as the
   * delta argument (e.g. {@code new LongLiteralExpr("1L")}). Useful for the {@code getAndIncrement}
   * dispatch where {@code 1L} must reach the AST as a {@link com.github.javaparser.ast.expr.LongLiteralExpr}
   * rather than a {@link NameExpr}, so a parser round-trip of the generated source is idempotent.
   */
  protected BlockStmt varHandleGetAndAdd(String varHandleFieldName, com.github.javaparser.ast.expr.Expression delta) {
    BlockStmt body = new BlockStmt();
    CastExpr castExpr = new CastExpr(
        PrimitiveType.longType(),
        methodCallExpr(varHandleFieldName, "getAndAdd", new ThisExpr(), delta));
    body.addStatement(new ReturnStmt(castExpr));
    return body;
  }

  /** Generates something like <code>VH_PRODUCER_INDEX.setRelease(this, newValue)</code> */
  protected BlockStmt varHandleSetRelease(String varHandleFieldName, String newValueName) {
    BlockStmt body = new BlockStmt();
    body.addStatement(
        new ExpressionStmt(
            methodCallExpr(
                varHandleFieldName, "setRelease", new ThisExpr(), new NameExpr(newValueName))));
    return body;
  }

  /** Generates something like <code>return (long) VH_PRODUCER_INDEX.getVolatile(this)</code> */
  protected BlockStmt varHandleGetVolatile(String varHandleFieldName, Type returnType) {
    BlockStmt body = new BlockStmt();
    CastExpr castExpr =
        new CastExpr(returnType, methodCallExpr(varHandleFieldName, "getVolatile", new ThisExpr()));
    body.addStatement(new ReturnStmt(castExpr));
    return body;
  }

  /**
   * Generates something like <code>
   * return VH_PRODUCER_INDEX.compareAndSet(this, expectedValue, newValue)</code>
   */
  protected BlockStmt varHandleCompareAndSet(
      String varHandleFieldName, String expectedValueName, String newValueName) {
    BlockStmt body = new BlockStmt();
    body.addStatement(
        new ReturnStmt(
            methodCallExpr(
                varHandleFieldName,
                "compareAndSet",
                new ThisExpr(),
                new NameExpr(expectedValueName),
                new NameExpr(newValueName))));
    return body;
  }

  /**
   * Generates something like <code>private static final VarHandle VH_PRODUCER_INDEX;</code>. The
   * static initializer block that wires it up is built separately by the array/linked subclasses
   * via their own {@code createVarHandleStaticInitializerWithTypes(...)}.
   */
  protected FieldDeclaration declareVarHandle(String className, String variableName) {
    ClassOrInterfaceType type = classType("VarHandle");
    FieldDeclaration fieldDeclaration = new FieldDeclaration();
    VariableDeclarator variable = new VariableDeclarator(type, varHandleFieldName(variableName));
    fieldDeclaration.getVariables().add(variable);
    fieldDeclaration.setModifiers(Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
    return fieldDeclaration;
  }

  /** Determines the class type for VarHandle initialization based on field type */
  protected String getFieldClassType(Type fieldType) {
    return getFieldClassType(null, fieldType);
  }

  /**
   * Determines the class type for VarHandle initialization based on field type. When the field
   * type is a generic type parameter (e.g. {@code R}), resolves to the erased bound by looking at
   * the class declaration's type parameters.
   */
  protected String getFieldClassType(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration n, Type fieldType) {
    if (PrimitiveType.longType().equals(fieldType)) {
      return "long";
    } else if (isRefType(fieldType, "Thread")) {
      return "Thread";
    }
    // Resolve generic type parameters to their erased bound
    if (n != null && fieldType instanceof ClassOrInterfaceType) {
      String typeName = ((ClassOrInterfaceType) fieldType).getNameAsString();
      if (typeName.length() == 1 && Character.isUpperCase(typeName.charAt(0))) {
        for (com.github.javaparser.ast.type.TypeParameter tp : n.getTypeParameters()) {
          if (tp.getNameAsString().equals(typeName)) {
            NodeList<ClassOrInterfaceType> bounds = tp.getTypeBound();
            if (!bounds.isEmpty()) {
              return bounds.get(0).getNameAsString();
            }
          }
        }
      }
    }
    return "Object";
  }
}
