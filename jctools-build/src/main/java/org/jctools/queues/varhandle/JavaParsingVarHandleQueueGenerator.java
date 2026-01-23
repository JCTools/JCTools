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

  protected void removeStaticFieldsAndInitialisers(ClassOrInterfaceDeclaration node) {
    // Remove all the static initialisers
    for (InitializerDeclaration child : node.getChildNodesByType(InitializerDeclaration.class)) {
      child.remove();
    }

    // Remove all static fields
    for (FieldDeclaration field : node.getFields()) {
      if (field.getModifiers().contains(Modifier.staticModifier())) {
        field.remove();
      }
    }
  }

  @Override
  public String translateQueueName(String qName) {
    if (qName.contains("LinkedQueue") || qName.contains("LinkedArrayQueue")) {
      return qName.replace("Linked", "Linked" + queueClassNamePrefix());
    }

    if (qName.contains("ArrayQueue")) {
      return qName.replace("ArrayQueue", queueClassNamePrefix() + "ArrayQueue");
    }

    throw new IllegalArgumentException("Unexpected queue name: " + qName);
  }

  boolean patchVarHandleAccessorMethod(
      String variableName, MethodDeclaration method, String methodNameSuffix) {
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
    } else if (methodName.startsWith("sv")) {
      method.setBody(fieldAssignment(variableName, newValueName));
    } else if (methodName.startsWith("la")) {
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);
      method.setBody(varHandleGetAcquire(varHandleFieldName, method.getType()));
    } else if (methodName.startsWith("lv")) {
      usesVarHandle = true;
      String varHandleFieldName = varHandleFieldName(variableName);
      method.setBody(varHandleGetVolatile(varHandleFieldName, method.getType()));
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
          parent.setName("VarHandleReferenceArrayQueue");
          break;
        case "ConcurrentSequencedCircularArrayQueue":
          parent.setName("SequencedVarHandleReferenceArrayQueue");
          break;
        default:
          // Padded super classes are to be renamed and thus so does the
          // class we must extend.
          parent.setName(translateQueueName(parentNameAsString));
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

      importDecls.add(importDeclaration);
    }
    cu.getImports().clear();
    for (ImportDeclaration importDecl : importDecls) {
      cu.addImport(importDecl);
    }

    cu.addImport(new ImportDeclaration("java.lang.invoke.MethodHandles", false, false));
    cu.addImport(new ImportDeclaration("java.lang.invoke.VarHandle", false, false));

    cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
    cu.addImport(staticImportDeclaration("org.jctools.queues.varhandle.VarHandleQueueUtil"));
  }

  protected String capitalise(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
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

  /** Generates something like <code>return (long) VH_PRODUCER_INDEX.getAcquire(this)</code> */
  protected BlockStmt varHandleGetAcquire(String varHandleFieldName, Type returnType) {
    BlockStmt body = new BlockStmt();
    CastExpr castExpr =
        new CastExpr(returnType, methodCallExpr(varHandleFieldName, "getAcquire", new ThisExpr()));
    body.addStatement(new ReturnStmt(castExpr));
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
