package org.jctools.queues.atomic;

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
import org.jctools.queues.util.JCToolsGenerator;

import static org.jctools.queues.util.GeneratorUtils.renameType;

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
public abstract class JavaParsingAtomicQueueGenerator extends VoidVisitorAdapter<Void> implements JCToolsGenerator {

    /**
     * When set on a class using a single line comment, the class has fields that have unsafe 'ordered' reads and
     * writes. These fields are candidates to be patched by the generator. Other classes the fields remain unadjusted.
     */
    protected static final String GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS = "$gen:ordered-fields";
    
    /**
     * When set on a method using a single line comment, the method is not patched by the generator.
     */
    protected static final String GEN_DIRECTIVE_METHOD_IGNORE = "$gen:ignore";
    
    protected final String sourceFileName;
    protected boolean usesPoolQueue = false;

    protected String outputPackage() {
        return "org.jctools.queues.atomic";
    }

    protected String queueClassNamePrefix() {
        return "Atomic";
    }

    protected String unpaddedPoolQueueName() {
        return "SpscAtomicUnpaddedArrayQueue";
    }

    protected String unpaddedPoolQueueImport() {
        return "org.jctools.queues.atomic.unpadded.SpscAtomicUnpaddedArrayQueue";
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
     * Renames Chunk type references (e.g. {@code MpUnboundedXaddChunk} to {@code MpUnboundedXaddAtomicChunk})
     * wherever they appear as types: field declarations, method return types, generic parameters, casts, etc.
     * The {@code contains(queueClassNamePrefix())} guard prevents double-translation when the visitor processes
     * a node that was already renamed by a parent visitor call.
     */
    @Override
    public void visit(ClassOrInterfaceType n, Void arg) {
        super.visit(n, arg);
        String name = n.getNameAsString();
        if (name.endsWith("Chunk") && !name.contains(queueClassNamePrefix())) {
            renameType(n, translateQueueName(name));
        }
    }

    /**
     * Renames Chunk class references in name expressions, e.g. {@code MpmcUnboundedXaddChunk.NOT_USED}
     * where {@code MpmcUnboundedXaddChunk} is a {@link NameExpr} qualifying a static field access.
     * The {@code isUpperCase} check avoids renaming local variables like {@code cChunk} that also
     * end with "Chunk" but are not class names.
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
            if (wanted.equals(content)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes Unsafe-specific static infrastructure: static initializer blocks that compute field
     * offsets via {@code Unsafe.objectFieldOffset} (or read {@code UNSAFE}/{@code UnsafeAccess}),
     * and static fields ending with {@code _OFFSET}. Other static fields (e.g. {@code NOT_USED}
     * constants in Chunk classes) and unrelated initializer blocks are preserved.
     */
    protected void removeStaticFieldsAndInitialisers(ClassOrInterfaceDeclaration node) {
        for (InitializerDeclaration child : node.getChildNodesByType(InitializerDeclaration.class)) {
            if (referencesUnsafe(child)) {
                child.remove();
            }
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

    private static boolean referencesUnsafe(Node node) {
        for (NameExpr ref : node.findAll(NameExpr.class)) {
            String name = ref.getNameAsString();
            if ("UNSAFE".equals(name) || "UnsafeAccess".equals(name) || "UnsafeRefArrayAccess".equals(name)) {
                return true;
            }
        }
        // Also catch references to *_OFFSET fields, which only exist in Unsafe initializer blocks.
        for (NameExpr ref : node.findAll(NameExpr.class)) {
            if (ref.getNameAsString().endsWith("_OFFSET")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String translateQueueName(String qName) {
        if (qName.contains("LinkedQueue") || qName.contains("LinkedArrayQueue")) {
            return qName.replace("Linked", "Linked" + queueClassNamePrefix());
        }

        // ArrayQueue check must come before Chunk check because some inner hierarchy classes
        // contain both "ArrayQueue" and end with "Chunk" (e.g. MpUnboundedXaddArrayQueueProducerChunk)
        if (qName.contains("ArrayQueue")) {
            return qName.replace("ArrayQueue", queueClassNamePrefix() + "ArrayQueue");
        }

        // Standalone Chunk classes (e.g. MpUnboundedXaddChunk -> MpUnboundedXaddAtomicChunk)
        if (qName.endsWith("Chunk")) {
            return qName.replace("Chunk", queueClassNamePrefix() + "Chunk");
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

        if (methodName.startsWith("so") || methodName.startsWith("sp"))
        {
            // Use lazySet as the weakest ordering allowed by field updaters.
            // Read actual param name from the method — xadd sources use different names than the
            // original array queue sources (e.g. "value" vs "newValue").
            usesFieldUpdater = true;
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String valueName = method.getParameters().isEmpty() ? "newValue" :
                method.getParameters().get(method.getParameters().size() - 1).getNameAsString();
            method.setBody(fieldUpdaterLazySet(fieldUpdaterFieldName, valueName));
        }
        else if (methodName.startsWith("cas"))
        {
            // Read actual param names — xadd sources use "expected"/"value" not "expect"/"newValue"
            usesFieldUpdater = true;
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String expectedValueName = method.getParameters().size() >= 1 ?
                method.getParameters().get(0).getNameAsString() : "expect";
            String newValueName = method.getParameters().size() >= 2 ?
                method.getParameters().get(1).getNameAsString() : "newValue";
            method.setBody(
                fieldUpdaterCompareAndSet(fieldUpdaterFieldName, expectedValueName, newValueName));
        }
        else if (methodName.startsWith("getAndAdd"))
        {
            // Xadd queues use getAndAddProducerIndex(long delta) — maps to fieldUpdater.getAndAdd()
            usesFieldUpdater = true;
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String deltaName = method.getParameters().isEmpty() ? "delta" :
                method.getParameters().get(0).getNameAsString();
            method.setBody(fieldUpdaterGetAndAdd(fieldUpdaterFieldName, deltaName));
        }
        else if (methodName.startsWith("getAndIncrement"))
        {
            // Xadd queues use getAndIncrementProducerIndex() — maps to fieldUpdater.getAndIncrement()
            usesFieldUpdater = true;
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            method.setBody(fieldUpdaterGetAndIncrement(fieldUpdaterFieldName));
        }
        else if (methodName.startsWith("sv"))
        {
            String valueName = method.getParameters().isEmpty() ? "newValue" :
                method.getParameters().get(method.getParameters().size() - 1).getNameAsString();
            method.setBody(fieldAssignment(variableName, valueName));
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
            //      "import static o.j.q.atomic.MpmcUnboundedXaddAtomicChunk.NOT_USED"
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

        cu.addImport(new ImportDeclaration("java.util.concurrent.atomic", false, true));

        cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
        cu.addImport(staticImportDeclaration("org.jctools.queues.atomic.AtomicQueueUtil"));

        if (usesPoolQueue) {
            String poolImport = unpaddedPoolQueueImport();
            if (poolImport != null) {
                cu.addImport(new ImportDeclaration(poolImport, false, false));
            }
        }
    }

    protected String capitalise(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
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
     * <code>return P_INDEX_UPDATER.getAndAdd(this, delta)</code>
     */
    protected BlockStmt fieldUpdaterGetAndAdd(String fieldUpdaterFieldName, String deltaName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(methodCallExpr(fieldUpdaterFieldName, "getAndAdd", new ThisExpr(),
                new NameExpr(deltaName))));
        return body;
    }

    /**
     * Generates something like
     * <code>return P_INDEX_UPDATER.getAndIncrement(this)</code>
     */
    protected BlockStmt fieldUpdaterGetAndIncrement(String fieldUpdaterFieldName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(methodCallExpr(fieldUpdaterFieldName, "getAndIncrement", new ThisExpr())));
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

    /**
     * Generates something like
     * <code>private static final AtomicReferenceFieldUpdater&lt;MpscBlockingConsumerAtomicArrayQueueConsumerFields, Thread&gt; BLOCKED = AtomicReferenceFieldUpdater.newUpdater(MpscBlockingConsumerAtomicArrayQueueConsumerFields.class, Thread.class, "blocked");</code>
     */
    protected FieldDeclaration declareRefFieldUpdater(String className, String typeName, String variableName) {
        MethodCallExpr initializer = newAtomicRefFieldUpdater(className, typeName, variableName);

        ClassOrInterfaceType type = simpleParametricType("AtomicReferenceFieldUpdater", className, typeName);
        return fieldDeclarationWithInitialiser(type, fieldUpdaterFieldName(variableName),
            initializer, Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
    }

    protected MethodCallExpr newAtomicLongFieldUpdater(String className, String variableName) {
        return methodCallExpr("AtomicLongFieldUpdater", "newUpdater", new ClassExpr(classType(className)),
                new StringLiteralExpr(variableName));
    }

    protected MethodCallExpr newAtomicRefFieldUpdater(String className, String typeName, String variableName) {
        return methodCallExpr("AtomicReferenceFieldUpdater", "newUpdater", new ClassExpr(classType(className)), new ClassExpr(classType(typeName)),
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

    /**
     * Resolves the erased bound of a single-letter generic type parameter by looking at the
     * class declaration's type parameters. E.g. for {@code class Foo<R extends Bar<R,E>, E>},
     * resolving "R" returns "Bar". Returns "Object" if no bound is found.
     */
    protected String resolveErasedBound(ClassOrInterfaceDeclaration n, String typeParamName) {
        for (com.github.javaparser.ast.type.TypeParameter tp : n.getTypeParameters()) {
            if (tp.getNameAsString().equals(typeParamName)) {
                NodeList<ClassOrInterfaceType> bounds = tp.getTypeBound();
                if (!bounds.isEmpty()) {
                    return bounds.get(0).getNameAsString();
                }
            }
        }
        return "Object";
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
}
