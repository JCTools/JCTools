package org.jctools.queues.varhandle;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.AssignExpr.Operator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jctools.queues.util.JCToolsGenerator;

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
public abstract class JavaParsingVarHandleQueueGenerator extends VoidVisitorAdapter<Void>
    implements JCToolsGenerator {

  /**
   * When set on a class using a single line comment, the class has fields that have unsafe
   * 'ordered' reads and writes. These fields are candidates to be patched by the generator. Other
   * classes the fields remain unadjusted.
   */
  protected static final String GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS =
      "$gen:ordered-fields";

  /**
   * When set on a method using a single line comment, the method is not patched by the generator.
   */
  protected static final String GEN_DIRECTIVE_METHOD_IGNORE = "$gen:ignore";

  protected final String sourceFileName;

  // Track whether the current file has VarHandle field declarations
  protected boolean hasVarHandleFields = false;
  protected boolean usesPoolQueue = false;

  protected String outputPackage() {
    return "org.jctools.queues.varhandle";
  }

  protected String queueClassNamePrefix() {
    return "VarHandle";
  }

  JavaParsingVarHandleQueueGenerator(String sourceFileName) {
    this.sourceFileName = sourceFileName;
  }

  abstract void processSpecialNodeTypes(NodeWithType<?, Type> node, String name);

  abstract String varHandleFieldName(String fieldName);

  @Override
  public void visit(PackageDeclaration n, Void arg) {
    super.visit(n, arg);
    // Change the package of the output
    n.setName(outputPackage());
  }

  @Override
  public void visit(Parameter n, Void arg) {
    super.visit(n, arg);
    // Process parameters to methods and ctors
    processSpecialNodeTypes(n);
  }

  @Override
  public void visit(VariableDeclarator n, Void arg) {
    super.visit(n, arg);
    // Replace declared variables with altered types
    processSpecialNodeTypes(n);
  }

  /**
   * Renames Chunk type references (e.g. {@code MpUnboundedXaddChunk} to {@code MpUnboundedXaddVarHandleChunk})
   * wherever they appear as types. The {@code contains(queueClassNamePrefix())} guard prevents
   * double-translation when the visitor processes a node already renamed by a parent visitor call.
   */
  @Override
  public void visit(ClassOrInterfaceType n, Void arg) {
    super.visit(n, arg);
    String name = n.getNameAsString();
    if (name.endsWith("Chunk") && !name.contains(queueClassNamePrefix())) {
      n.setName(translateQueueName(name));
    }
  }

  /**
   * Renames Chunk class references in name expressions, e.g. {@code MpmcUnboundedXaddChunk.NOT_USED}.
   * The {@code isUpperCase} check avoids renaming local variables like {@code cChunk}.
   */
  @Override
  public void visit(NameExpr n, Void arg) {
    super.visit(n, arg);
    String name = n.getNameAsString();
    if (name.endsWith("Chunk") && Character.isUpperCase(name.charAt(0)) && !name.contains(queueClassNamePrefix())) {
      n.setName(translateQueueName(name));
    }
  }

  private void processSpecialNodeTypes(Parameter node) {
    processSpecialNodeTypes(node, node.getNameAsString());
  }

  private void processSpecialNodeTypes(VariableDeclarator node) {
    processSpecialNodeTypes(node, node.getNameAsString());
  }

  protected boolean isCommentPresent(Node node, String wanted) {
    Optional<Comment> maybeComment = node.getComment();
    if (maybeComment.isPresent()) {
      Comment comment = maybeComment.get();
      String content = comment.getContent().trim();
      return wanted.equals(content);
    }
    return false;
  }

  /**
   * Removes Unsafe-specific static infrastructure: static initializer blocks (which compute field
   * offsets via {@code Unsafe.objectFieldOffset}) and static fields ending with {@code _OFFSET}.
   * Other static fields (e.g. {@code NOT_USED} constants in Chunk classes) are preserved.
   */
  protected void removeStaticFieldsAndInitialisers(ClassOrInterfaceDeclaration node) {
    for (InitializerDeclaration child : node.getChildNodesByType(InitializerDeclaration.class)) {
      child.remove();
    }

    for (FieldDeclaration field : node.getFields()) {
      if (field.getModifiers().contains(Modifier.staticModifier())) {
        boolean isOffsetField = field.getVariables().stream()
            .anyMatch(v -> v.getNameAsString().endsWith("_OFFSET"));
        if (isOffsetField) {
          field.remove();
        }
      }
    }
  }

  @Override
  public String translateQueueName(String qName) {
    if (qName.contains("LinkedQueue") || qName.contains("LinkedArrayQueue")) {
      return qName.replace("Linked", "Linked" + queueClassNamePrefix());
    }

    // Xadd Chunk classes (e.g. MpUnboundedXaddChunk -> MpUnboundedXaddVarHandleChunk)
    if (qName.endsWith("Chunk")) {
      return qName.replace("Chunk", queueClassNamePrefix() + "Chunk");
    }

    if (qName.contains("ArrayQueue")) {
      return qName.replace("ArrayQueue", queueClassNamePrefix() + "ArrayQueue");
    }

    throw new IllegalArgumentException("Unexpected queue name: " + qName);
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
      // Xadd queues use getAndIncrementProducerIndex() — maps to VarHandle.getAndAdd(this, 1)
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);
      method.setBody(varHandleGetAndAdd(varHandleFieldName, "1"));
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

  /**
   * Searches all extended or implemented super classes or interfaces for special classes that
   * differ with the VarHandle version and replaces them with the appropriate class.
   */
  protected void replaceParentClassesForVarHandle(ClassOrInterfaceDeclaration n) {
    for (ClassOrInterfaceType parent : n.getExtendedTypes()) {
      String parentNameAsString = parent.getNameAsString();
      switch (parentNameAsString) {
        case "AbstractQueue":
          // ignore the JDK parent
          break;
        case "BaseLinkedQueue":
          parent.setName("BaseLinked" + queueClassNamePrefix() + "Queue");
          break;
        case "ConcurrentCircularArrayQueue":
          parent.setName("ConcurrentCircular" + queueClassNamePrefix() + "ArrayQueue");
          break;
        case "ConcurrentSequencedCircularArrayQueue":
          parent.setName("ConcurrentSequencedCircular" + queueClassNamePrefix() + "ArrayQueue");
          break;
        default:
          // Guard against double-translation: visit(ClassOrInterfaceType) may have
          // already renamed this type before we get here
          if (!parentNameAsString.contains(queueClassNamePrefix())) {
            parent.setName(translateQueueName(parentNameAsString));
          }
      }
    }
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
          importDecls.add(new ImportDeclaration(outputPackage() + "." + translatedClass + "." + simpleName, true, false));
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

  protected String capitalise(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  /** Generates something like <code>return (long) VH_PRODUCER_INDEX.getAndAdd(this, delta)</code> */
  protected BlockStmt varHandleGetAndAdd(String varHandleFieldName, String deltaName) {
    BlockStmt body = new BlockStmt();
    CastExpr castExpr = new CastExpr(
        PrimitiveType.longType(),
        methodCallExpr(varHandleFieldName, "getAndAdd", new ThisExpr(), new NameExpr(deltaName)));
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

  protected MethodCallExpr methodCallExpr(String owner, String method, Expression... args) {
    MethodCallExpr methodCallExpr = new MethodCallExpr(new NameExpr(owner), method);
    for (Expression expr : args) {
      methodCallExpr.addArgument(expr);
    }
    return methodCallExpr;
  }

  /** Generates something like <code>field = newValue</code> */
  protected BlockStmt fieldAssignment(String fieldName, String valueName) {
    BlockStmt body = new BlockStmt();
    body.addStatement(
        new ExpressionStmt(
            new AssignExpr(new NameExpr(fieldName), new NameExpr(valueName), Operator.ASSIGN)));
    return body;
  }

  /**
   * Generates something like <code>private static final VarHandle VH_PRODUCER_INDEX;</code> and
   * initializer block with: <code>
   * static {
   *   try {
   *     VH_PRODUCER_INDEX = MethodHandles.lookup().findVarHandle(ClassName.class, "producerIndex", long.class);
   *   } catch (Exception e) {
   *     throw new ExceptionInInitializerError(e);
   *   }
   * }
   * </code>
   */
  protected FieldDeclaration declareVarHandle(String className, String variableName) {
    ClassOrInterfaceType type = classType("VarHandle");
    FieldDeclaration fieldDeclaration = new FieldDeclaration();
    VariableDeclarator variable = new VariableDeclarator(type, varHandleFieldName(variableName));
    fieldDeclaration.getVariables().add(variable);
    fieldDeclaration.setModifiers(Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
    return fieldDeclaration;
  }

  /**
   * Creates a static initializer block for VarHandle initialization Overloaded version that accepts
   * variable names as strings and assumes long type
   */
  protected InitializerDeclaration createVarHandleStaticInitializer(
      String className, List<String> variableNames) {
    InitializerDeclaration initializer = new InitializerDeclaration(true, new BlockStmt());
    BlockStmt initBody = initializer.getBody();

    // Create try block
    BlockStmt tryBlock = new BlockStmt();
    MethodCallExpr lookup = new MethodCallExpr(new NameExpr("MethodHandles"), "lookup");

    for (String variableName : variableNames) {
      MethodCallExpr findVarHandle = new MethodCallExpr(lookup, "findVarHandle");
      findVarHandle.addArgument(new ClassExpr(classType(className)));
      findVarHandle.addArgument(new StringLiteralExpr(variableName));
      findVarHandle.addArgument(new ClassExpr(classType("long")));

      AssignExpr assignment =
          new AssignExpr(
              new NameExpr(varHandleFieldName(variableName)), findVarHandle, Operator.ASSIGN);
      tryBlock.addStatement(new ExpressionStmt(assignment));
    }

    // Create catch clause
    Parameter catchParam = new Parameter(classType("Exception"), "e");
    BlockStmt catchBlock = new BlockStmt();
    catchBlock.addStatement(
        new ThrowStmt(
            new ObjectCreationExpr(
                null,
                classType("ExceptionInInitializerError"),
                new NodeList<>(new NameExpr("e")))));
    CatchClause catchClause = new CatchClause(catchParam, catchBlock);

    // Create try-catch statement
    TryStmt tryStmt = new TryStmt(tryBlock, new NodeList<>(catchClause), null);
    initBody.addStatement(tryStmt);

    return initializer;
  }

  /** Determines the class type for VarHandle initialization based on field type */
  protected String getFieldClassType(Type fieldType) {
    if (PrimitiveType.longType().equals(fieldType)) {
      return "long";
    } else if (isRefType(fieldType, "Thread")) {
      return "Thread";
    }
    // Default to Object for reference types
    return "Object";
  }

  /** Generates something like <code>return field</code> */
  protected BlockStmt returnField(String fieldName) {
    BlockStmt body = new BlockStmt();
    body.addStatement(new ReturnStmt(fieldName));
    return body;
  }

  protected boolean isRefArray(Type in, String refClassName) {
    if (in instanceof ArrayType) {
      ArrayType aType = (ArrayType) in;
      return isRefType(aType.getComponentType(), refClassName);
    }
    return false;
  }

  protected boolean isRefType(Type in, String className) {
    // Does not check type parameters
    if (in instanceof ClassOrInterfaceType) {
      return (className.equals(((ClassOrInterfaceType) in).getNameAsString()));
    }
    return false;
  }

  ImportDeclaration staticImportDeclaration(String name) {
    return new ImportDeclaration(name, true, true);
  }

  protected ClassOrInterfaceType classType(String className) {
    return new ClassOrInterfaceType(null, className);
  }
}
