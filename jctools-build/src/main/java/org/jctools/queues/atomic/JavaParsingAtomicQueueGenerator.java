package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.jctools.queues.util.JavaParsingQueueGeneratorBase;

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
public abstract class JavaParsingAtomicQueueGenerator extends JavaParsingQueueGeneratorBase {

    /** The unpadded SPSC pool queue type used by xadd-family chunk pools in the atomic variant. */
    protected final String unpaddedPoolQueueName = "SpscAtomicUnpaddedArrayQueue";
    protected final String unpaddedPoolQueueImport = "org.jctools.queues.atomic.unpadded.SpscAtomicUnpaddedArrayQueue";

    protected JavaParsingAtomicQueueGenerator(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
        super(sourceFileName, outputPackage, queueClassNamePrefix);
    }

    abstract void processSpecialNodeTypes(NodeWithType<?, Type> node, String name);
    abstract String fieldUpdaterFieldName(String fieldName);

    @Override
    public final void visit(Parameter n, Void arg) {
        super.visit(n, arg);
        // Process parameters to methods and ctors
        processSpecialNodeTypes(n, n.getNameAsString());
    }

    @Override
    public final void visit(VariableDeclarator n, Void arg) {
        super.visit(n, arg);
        // Replace declared variables with altered types
        processSpecialNodeTypes(n, n.getNameAsString());
    }

    /**
     * Outcome of patching one accessor method against one field. {@link #NONE} means the
     * method's name didn't match the field's suffix and the body was left untouched.
     * {@link #VOLATILE_ONLY} means the body was rewritten as a plain field read/write — the
     * field needs to be {@code volatile} for those reads/writes to carry the right JMM semantics,
     * but no field updater is needed. {@link #NEEDS_UPDATER} means the body delegates to a
     * field updater and the field also needs to be {@code volatile}.
     */
    enum FieldPatchResult {
        NONE,
        VOLATILE_ONLY,
        NEEDS_UPDATER;

        boolean atLeast(FieldPatchResult other) {
            return ordinal() >= other.ordinal();
        }

        static FieldPatchResult max(FieldPatchResult a, FieldPatchResult b) {
            return a.atLeast(b) ? a : b;
        }
    }

    final FieldPatchResult patchAtomicFieldUpdaterAccessorMethod(String variableName, MethodDeclaration method, String methodNameSuffix)
    {
        String methodName = method.getNameAsString();
        if (!methodName.endsWith(methodNameSuffix)) {
            // Leave it untouched
            return FieldPatchResult.NONE;
        }

        if (methodName.startsWith("so") || methodName.startsWith("sp"))
        {
            // Use lazySet as the weakest ordering allowed by field updaters.
            // Read actual param name from the method — xadd sources use different names than the
            // original array queue sources (e.g. "value" vs "newValue").
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String valueName = method.getParameters().isEmpty() ? "newValue" :
                method.getParameters().get(method.getParameters().size() - 1).getNameAsString();
            method.setBody(fieldUpdaterLazySet(fieldUpdaterFieldName, valueName));
            return FieldPatchResult.NEEDS_UPDATER;
        }
        else if (methodName.startsWith("cas"))
        {
            // Read actual param names — xadd sources use "expected"/"value" not "expect"/"newValue"
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String expectedValueName = method.getParameters().size() >= 1 ?
                method.getParameters().get(0).getNameAsString() : "expect";
            String newValueName = method.getParameters().size() >= 2 ?
                method.getParameters().get(1).getNameAsString() : "newValue";
            method.setBody(
                fieldUpdaterCompareAndSet(fieldUpdaterFieldName, expectedValueName, newValueName));
            return FieldPatchResult.NEEDS_UPDATER;
        }
        else if (methodName.startsWith("getAndAdd"))
        {
            // Xadd queues use getAndAddProducerIndex(long delta) — maps to fieldUpdater.getAndAdd()
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            String deltaName = method.getParameters().isEmpty() ? "delta" :
                method.getParameters().get(0).getNameAsString();
            method.setBody(fieldUpdaterGetAndAdd(fieldUpdaterFieldName, deltaName));
            return FieldPatchResult.NEEDS_UPDATER;
        }
        else if (methodName.startsWith("getAndIncrement"))
        {
            // Xadd queues use getAndIncrementProducerIndex() — maps to fieldUpdater.getAndIncrement()
            String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);
            method.setBody(fieldUpdaterGetAndIncrement(fieldUpdaterFieldName));
            return FieldPatchResult.NEEDS_UPDATER;
        }
        else if (methodName.startsWith("sv"))
        {
            // Plain field assignment — the caller adds volatile to the field, so this carries
            // volatile-store semantics without needing the field updater.
            String valueName = method.getParameters().isEmpty() ? "newValue" :
                method.getParameters().get(method.getParameters().size() - 1).getNameAsString();
            method.setBody(fieldAssignment(variableName, valueName));
            return FieldPatchResult.VOLATILE_ONLY;
        }
        else if (methodName.startsWith("lv") || methodName.startsWith("lp"))
        {
            // Plain field read — the caller adds volatile to the field so this carries
            // volatile-load semantics without needing the field updater.
            method.setBody(returnField(variableName));
            return FieldPatchResult.VOLATILE_ONLY;
        }
        else
        {
            throw new IllegalStateException("Unhandled method: " + methodName);
        }
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

            importDecls.add(translateChunkStaticImportOrSelf(importDeclaration));
        }
        cu.getImports().clear();
        for (ImportDeclaration importDecl : importDecls) {
            cu.addImport(importDecl);
        }

        cu.addImport(new ImportDeclaration("java.util.concurrent.atomic", false, true));

        cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
        cu.addImport(staticImportDeclaration("org.jctools.queues.atomic.AtomicQueueUtil"));

        if (referencesType(cu, unpaddedPoolQueueName)) {
            cu.addImport(new ImportDeclaration(unpaddedPoolQueueImport, false, false));
        }
    }

    /** Generates something like <code>P_INDEX_UPDATER.lazySet(this, newValue)</code>. */
    private static BlockStmt fieldUpdaterLazySet(String fieldUpdaterFieldName, String newValueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ExpressionStmt(
                methodCallExpr(fieldUpdaterFieldName, "lazySet", new ThisExpr(), new NameExpr(newValueName))));
        return body;
    }

    /** Generates something like <code>return P_INDEX_UPDATER.getAndAdd(this, delta)</code>. */
    private static BlockStmt fieldUpdaterGetAndAdd(String fieldUpdaterFieldName, String deltaName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(methodCallExpr(fieldUpdaterFieldName, "getAndAdd", new ThisExpr(),
                new NameExpr(deltaName))));
        return body;
    }

    /** Generates something like <code>return P_INDEX_UPDATER.getAndIncrement(this)</code>. */
    private static BlockStmt fieldUpdaterGetAndIncrement(String fieldUpdaterFieldName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(methodCallExpr(fieldUpdaterFieldName, "getAndIncrement", new ThisExpr())));
        return body;
    }

    /** Generates something like <code>return P_INDEX_UPDATER.compareAndSet(this, expectedValue, newValue)</code>. */
    private static BlockStmt fieldUpdaterCompareAndSet(String fieldUpdaterFieldName, String expectedValueName,
            String newValueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(methodCallExpr(fieldUpdaterFieldName, "compareAndSet", new ThisExpr(),
                new NameExpr(expectedValueName), new NameExpr(newValueName))));
        return body;
    }

    /**
     * Generates a field declaration {@code <type> <name> = <initializer>;} with the given modifiers.
     */
    static FieldDeclaration fieldDeclarationWithInitialiser(Type type, String name, Expression initializer,
            Keyword... modifiers) {
        FieldDeclaration fieldDeclaration = new FieldDeclaration();
        VariableDeclarator variable = new VariableDeclarator(type, name, initializer);
        fieldDeclaration.getVariables().add(variable);
        fieldDeclaration.setModifiers(modifiers);
        return fieldDeclaration;
    }

    /**
     * Generates something like
     * <code>private static final AtomicLongFieldUpdater&lt;MpmcAtomicArrayQueueProducerIndexField&gt; P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater(MpmcAtomicArrayQueueProducerIndexField.class, "producerIndex");</code>
     */
    final FieldDeclaration declareLongFieldUpdater(String className, String variableName) {
        MethodCallExpr initializer = newAtomicLongFieldUpdater(className, variableName);

        ClassOrInterfaceType type = simpleParametricType("AtomicLongFieldUpdater", className);
        return fieldDeclarationWithInitialiser(type, fieldUpdaterFieldName(variableName),
                initializer, Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
    }

    /**
     * Generates something like
     * <code>private static final AtomicReferenceFieldUpdater&lt;MpscBlockingConsumerAtomicArrayQueueConsumerFields, Thread&gt; BLOCKED = AtomicReferenceFieldUpdater.newUpdater(MpscBlockingConsumerAtomicArrayQueueConsumerFields.class, Thread.class, "blocked");</code>
     */
    final FieldDeclaration declareRefFieldUpdater(String className, String typeName, String variableName) {
        MethodCallExpr initializer = newAtomicRefFieldUpdater(className, typeName, variableName);

        ClassOrInterfaceType type = simpleParametricType("AtomicReferenceFieldUpdater", className, typeName);
        return fieldDeclarationWithInitialiser(type, fieldUpdaterFieldName(variableName),
            initializer, Keyword.PRIVATE, Keyword.STATIC, Keyword.FINAL);
    }

    private static MethodCallExpr newAtomicLongFieldUpdater(String className, String variableName) {
        return methodCallExpr("AtomicLongFieldUpdater", "newUpdater", new ClassExpr(classType(className)),
                new StringLiteralExpr(variableName));
    }

    private static MethodCallExpr newAtomicRefFieldUpdater(String className, String typeName, String variableName) {
        return methodCallExpr("AtomicReferenceFieldUpdater", "newUpdater", new ClassExpr(classType(className)), new ClassExpr(classType(typeName)),
            new StringLiteralExpr(variableName));
    }
}
