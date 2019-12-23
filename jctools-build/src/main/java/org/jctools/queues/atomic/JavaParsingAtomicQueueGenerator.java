package org.jctools.queues.atomic;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.AssignExpr.Operator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
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

abstract class JavaParsingAtomicQueueGenerator extends VoidVisitorAdapter<Void> {
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

    abstract void organiseImports(CompilationUnit cu);
    abstract String translateQueueName(String fileName);
    abstract void processSpecialNodeTypes(NodeWithType<?, Type> node, String name);
    abstract String fieldUpdaterFieldName(String fieldName);

    @Override
    public void visit(FieldAccessExpr n, Void arg) {
        super.visit(n, arg);
        if (n.getScope() instanceof NameExpr) {
            NameExpr name = (NameExpr) n.getScope();
            name.setName(translateQueueName(name.getNameAsString()));
        }
    }

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
                continue;
            }
        }
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
     *
     * @param fieldUpdaterFieldName
     * @param newValueName
     * @return
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
     *
     * @param fieldUpdaterFieldName
     * @param expectedValueName
     * @param newValueName
     * @return
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
     *
     * @param fieldName
     * @param valueName
     * @return
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
     *
     * @param type
     * @param name
     * @param initializer
     * @param modifiers
     * @return
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
     *
     * @param className
     * @param variableName
     * @return
     */
    protected FieldDeclaration declareLongFieldUpdater(String className, String variableName) {
        MethodCallExpr initializer = newAtomicLongFieldUpdater(className, variableName);

        ClassOrInterfaceType type = simpleParametricType("AtomicLongFieldUpdater", className);
        FieldDeclaration newField = fieldDeclarationWithInitialiser(type, fieldUpdaterFieldName(variableName),
                initializer, Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
        return newField;
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

    protected ImportDeclaration importDeclaration(String name) {
        return new ImportDeclaration(new Name(name), false, false);
    }

    /**
     * Generates something like <code>return field</code>
     *
     * @param fieldName
     * @return
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
        return new ImportDeclaration(new Name(name), true, false);
    }

}
