package org.jctools.queues.atomic;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Base class of the atomic queue generators. These generators work by parsing a Java source file using
 * {@link JavaParser}, and replacing idioms that use {@link sun.misc.Unsafe} to instead use atomic field updates,
 * e.g.{@link java.util.concurrent.atomic.AtomicLongFieldUpdater}. They are coupled directly to the structure of the
 * expected input Java source file and are used as a utility to maintain unsafe non-portable optimized code along side
 * safe portable code for uses such as on Android, etc
 * <p>
 * These generators are coupled with the structure and naming of fields, variables and methods and are not suitable for
 * general purpose use.
 */
abstract class JavaParsingAtomicQueueGenerator extends VoidVisitorAdapter<Void> {

    /**
     * When set on a class using a single line comment, the class has fields that have unsafe 'ordered' reads and
     * writes. These fields are candidates to be patched by the generator. Other classes the fields remain unadjusted.
     */
    protected static final String GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS = "$gen:ordered-fields";
    
    /**
     * When set on a method using a single line comment, the method is not patched by the generator.
     */
    protected static final String GEN_DIRECTIVE_METHOD_IGNORE = "$gen:ignore";
    
    protected static final String INDENT_LEVEL = "    ";
    protected final String sourceFileName;

    static void main(Class<? extends JavaParsingAtomicQueueGenerator> generatorClass, String[] args) throws Exception {

        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: outputDirectory inputSourceFiles");
        }

        File outputDirectory = new File(args[0]);

        for (int i = 1; i < args.length; i++) {
            File file = new File(args[i]);
            System.out.println("Processing " + file);
            CompilationUnit cu = new JavaParser().parse(file).getResult().get();
            JavaParsingAtomicQueueGenerator generator = buildGenerator(generatorClass, file.getName());
            generator.visit(cu, null);

            generator.organiseImports(cu);

            String outputFileName = generator.translateQueueName(file.getName().replace(".java", "")) + ".java";

            try (FileWriter writer = new FileWriter(new File(outputDirectory, outputFileName))) {
                writer.write(cu.toString());
            }

            System.out.println("Saved to " + outputFileName);
        }
    }

    JavaParsingAtomicQueueGenerator(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    abstract void processSpecialNodeTypes(NodeWithType<?, Type> node, String name);
    abstract String fieldUpdaterFieldName(String fieldName);

    @Override
    public void visit(PackageDeclaration n, Void arg) {
        super.visit(n, arg);
        // Change the package of the output
        n.setName("org.jctools.queues.atomic");
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
            if (wanted.equals(content)) {
                return true;
            }
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

    protected String translateQueueName(String qName) {
        if (qName.contains("LinkedQueue") || qName.contains("LinkedArrayQueue")) {
            return qName.replace("Linked", "LinkedAtomic");
        }

        if (qName.contains("ArrayQueue")) {
            return qName.replace("ArrayQueue", "AtomicArrayQueue");
        }

        throw new IllegalArgumentException("Unexpected queue name: " + qName);

    }

    boolean patchAtomicFieldUpdaterAccessorMethod(String variableName, MethodDeclaration method, String methodNameSuffix)
    {
        boolean usesFieldUpdater = false;
        String methodName = method.getNameAsString();
        if (!methodName.endsWith(methodNameSuffix)) {
            // Leave it untouched
            return false;
        }

        String newValueName = "newValue";
        if (methodName.startsWith("so") || methodName.startsWith("sp"))
        {
            /*
             * In the case of 'sp' use lazySet as the weakest
             * ordering allowed by field updaters
             */
            usesFieldUpdater = true;
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);

            method.setBody(fieldUpdaterLazySet(fieldUpdaterFieldName, newValueName));
        }
        else if (methodName.startsWith("cas"))
        {
            usesFieldUpdater = true;
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String expectedValueName = "expect";
            method.setBody(
                fieldUpdaterCompareAndSet(fieldUpdaterFieldName, expectedValueName, newValueName));
        }
        else if (methodName.startsWith("sv"))
        {
            method.setBody(fieldAssignment(variableName, newValueName));
        }
        else if (methodName.startsWith("lv") || methodName.startsWith("lp"))
        {
            method.setBody(returnField(variableName));
        }
        else
        {
            throw new IllegalStateException("Unhandled method: " + methodName);
        }
        return usesFieldUpdater;
    }

    /**
     * Searches all extended or implemented super classes or interfaces for
     * special classes that differ with the atomics version and replaces them
     * with the appropriate class.
     */
     protected void replaceParentClassesForAtomics(ClassOrInterfaceDeclaration n) {
        for (ClassOrInterfaceType parent : n.getExtendedTypes()) {
            String parentNameAsString = parent.getNameAsString();
            switch (parentNameAsString) {
                case "AbstractQueue":
                    // ignore the JDK parent
                    break;
                case "BaseLinkedQueue":
                    parent.setName("BaseLinkedAtomicQueue");
                    break;
                case "ConcurrentCircularArrayQueue":
                    parent.setName("AtomicReferenceArrayQueue");
                    break;
                case "ConcurrentSequencedCircularArrayQueue":
                    parent.setName("SequencedAtomicReferenceArrayQueue");
                    break;
                default:
                    // Padded super classes are to be renamed and thus so does the
                    // class we must extend.
                    parent.setName(translateQueueName(parentNameAsString));
            }
        }
    }
    protected void organiseImports(CompilationUnit cu) {
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

        cu.addImport(new ImportDeclaration("java.util.concurrent.atomic", false, true));

        cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
        cu.addImport(staticImportDeclaration("org.jctools.queues.atomic.AtomicQueueUtil"));
    }

    protected String capitalise(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    protected String formatMultilineJavadoc(int indent, String... lines) {
        String indentation = "";
        for (int i = 0; i < indent; i++) {
            indentation += INDENT_LEVEL;
        }

        String out = "\n";
        for (String line : lines) {
            out += indentation + " * " + line + "\n";
        }
        out += indentation + " ";
        return out;
    }

    /**
     * Generates something like
     * <code>P_INDEX_UPDATER.lazySet(this, newValue)</code>
     */
    protected BlockStmt fieldUpdaterLazySet(String fieldUpdaterFieldName, String newValueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ExpressionStmt(
                methodCallExpr(fieldUpdaterFieldName, "lazySet", new ThisExpr(), new NameExpr(newValueName))));
        return body;
    }

    /**
     * Generates something like
     * <code>return P_INDEX_UPDATER.compareAndSet(this, expectedValue, newValue)</code>
     */
    protected BlockStmt fieldUpdaterCompareAndSet(String fieldUpdaterFieldName, String expectedValueName,
            String newValueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(methodCallExpr(fieldUpdaterFieldName, "compareAndSet", new ThisExpr(),
                new NameExpr(expectedValueName), new NameExpr(newValueName))));
        return body;
    }

    protected MethodCallExpr methodCallExpr(String owner, String method, Expression... args) {
        MethodCallExpr methodCallExpr = new MethodCallExpr(new NameExpr(owner), method);
        for (Expression expr : args) {
            methodCallExpr.addArgument(expr);
        }
        return methodCallExpr;
    }

    /**
     * Generates something like <code>field = newValue</code>
     */
    protected BlockStmt fieldAssignment(String fieldName, String valueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                new ExpressionStmt(new AssignExpr(new NameExpr(fieldName), new NameExpr(valueName), Operator.ASSIGN)));
        return body;
    }

    /**
     * Generates something like
     * <code>private static final AtomicLongFieldUpdater<MpmcAtomicArrayQueueProducerIndexField> P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(MpmcAtomicArrayQueueProducerIndexField.class, "producerIndex");</code>
     */
    protected FieldDeclaration fieldDeclarationWithInitialiser(Type type, String name, Expression initializer,
            Keyword... modifiers) {
        FieldDeclaration fieldDeclaration = new FieldDeclaration();
        VariableDeclarator variable = new VariableDeclarator(type, name, initializer);
        fieldDeclaration.getVariables().add(variable);
        fieldDeclaration.setModifiers(modifiers);
        return fieldDeclaration;
    }

    /**
     * Generates something like
     * <code>private static final AtomicLongFieldUpdater<MpmcAtomicArrayQueueProducerIndexField> P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(MpmcAtomicArrayQueueProducerIndexField.class, "producerIndex");</code>
     */
    protected FieldDeclaration declareLongFieldUpdater(String className, String variableName) {
        MethodCallExpr initializer = newAtomicLongFieldUpdater(className, variableName);

        ClassOrInterfaceType type = simpleParametricType("AtomicLongFieldUpdater", className);
        return fieldDeclarationWithInitialiser(type, fieldUpdaterFieldName(variableName),
                initializer, Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
    }

    protected MethodCallExpr newAtomicLongFieldUpdater(String className, String variableName) {
        return methodCallExpr("AtomicLongFieldUpdater", "newUpdater", new ClassExpr(classType(className)),
                new StringLiteralExpr(variableName));
    }

    protected ClassOrInterfaceType simpleParametricType(String className, String... typeArgs) {
        NodeList<Type> typeArguments = new NodeList<Type>();
        for (String typeArg : typeArgs) {
            typeArguments.add(classType(typeArg));
        }
        return new ClassOrInterfaceType(null, new SimpleName(className), typeArguments);
    }

    protected ClassOrInterfaceType classType(String className) {
        return new ClassOrInterfaceType(null, className);
    }

    /**
     * Generates something like <code>return field</code>
     *
     */
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

    private static <T> T buildGenerator(Class<? extends T> generatorClass, String fileName) throws Exception {
        return generatorClass.getDeclaredConstructor(String.class).newInstance(fileName);
    }

    ImportDeclaration staticImportDeclaration(String name) {
        return new ImportDeclaration(name, true, true);
    }
}
