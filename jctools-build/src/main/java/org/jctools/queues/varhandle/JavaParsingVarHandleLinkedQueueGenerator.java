package org.jctools.queues.varhandle;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayList;
import java.util.List;

import static org.jctools.queues.util.GeneratorUtils.prependGeneratedNoteJavadoc;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

/**
 * This generator takes in an JCTools 'LinkedQueue' Java source file and patches {@link sun.misc.Unsafe} accesses into
 * {@link java.lang.invoke.VarHandle}. It outputs a Java source file with these patches.
 * <p>
 * A 'LinkedQueue' is one that is backed by a linked list and uses a <code>producerNode</code> and a
 * <code>consumerNode</code> field to track the positions of each.
 */
public class JavaParsingVarHandleLinkedQueueGenerator extends JavaParsingVarHandleQueueGenerator {

    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingVarHandleLinkedQueueGenerator.class, args);
    }

    public JavaParsingVarHandleLinkedQueueGenerator(String sourceFileName) {
        super(sourceFileName);
    }

    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        super.visit(n, arg);
        // Update the ctor to match the class name
        String nameAsString = n.getNameAsString();
        // Ignore internal class WeakIterator which we don't need to rename
        if (nameAsString.equals("WeakIterator"))
            return;
        n.setName(translateQueueName(nameAsString));
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        super.visit(node, arg);

        replaceParentClasses(node);

        String nameAsString = node.getNameAsString();
        if (nameAsString.contains("Queue"))
            node.setName(translateQueueName(nameAsString));

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

        prependGeneratedNoteJavadoc(node, getClass(), sourceFileName);
    }

    @Override
    public void visit(CastExpr n, Void arg) {
        super.visit(n, arg);

        if (isRefArray(n.getType(), "E")) {
            n.setType(varHandleRefArrayType((ArrayType) n.getType()));
        }
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        super.visit(n, arg);
        // Replace the return type of a method with altered types
        processSpecialNodeTypes(n, n.getNameAsString());
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        Type type = n.getType();
        if (isRefType(type, "LinkedQueueNode")) {
            n.setType(simpleParametricType("LinkedQueueVarHandleNode", "E"));
        }
    }

    String varHandleFieldName(String fieldName) {
        switch (fieldName) {
        case "producerNode":
            return "VH_PRODUCER_NODE";
        case "consumerNode":
            return "VH_CONSUMER_NODE";
        case "consumerIndex":
            return "VH_CONSUMER_INDEX";
        case "producerIndex":
            return "VH_PRODUCER_INDEX";
        case "producerLimit":
            return "VH_PRODUCER_LIMIT";
        default:
            throw new IllegalArgumentException("Unhandled field: " + fieldName);
        }
    }

    /**
     * Given a variable declaration of some sort, check its name and type and
     * if it looks like any of the key type changes between unsafe and VarHandle
     * queues, perform the conversion to change its type.
     */
    void processSpecialNodeTypes(NodeWithType<?, Type> node, String name) {
        Type type = node.getType();
        // VarHandle uses long offsets, unlike Atomic which uses int array indices
        // So we don't convert offset types for VarHandle
        if (isRefType(type, "LinkedQueueNode")) {
            node.setType(simpleParametricType("LinkedQueueVarHandleNode", "E"));
        } else if (isRefArray(type, "E")) {
            node.setType(varHandleRefArrayType((ArrayType) type));
        }
    }

    /**
     * Patch each method whose name ends with {@code <prefix>FieldName} (capitalised) with a body
     * that delegates to a {@link java.lang.invoke.VarHandle} for the matching field. Same
     * handled prefixes as {@link JavaParsingVarHandleArrayQueueGenerator}'s patcher. Additionally
     * synthesises an {@code xchgProducerNode} method that delegates to {@code getAndSet} on the
     * producer-node VarHandle — driven by name-match on the {@code producerNode} field, not by
     * suffix dispatch.
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
            // Skip final fields — see JavaParsingVarHandleArrayQueueGenerator for the same guard.
            // Final fields can't have so/cas/sv accessors, and a final field whose name happens
            // to match a method suffix would otherwise get a stray VarHandle declaration.
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

                if ("producerNode".equals(variableName)) {
                    variableUsesVarHandle = true;
                    String varHandleFieldName = varHandleFieldName(variableName);

                    MethodDeclaration method = n.addMethod("xchgProducerNode", Keyword.PROTECTED, Keyword.FINAL);
                    method.setType(simpleParametricType("LinkedQueueVarHandleNode", "E"));
                    method.addParameter(simpleParametricType("LinkedQueueVarHandleNode", "E"), "newValue");
                    method.setBody(varHandleGetAndSet(varHandleFieldName, "newValue", method.getType()));
                }

                if (variableUsesVarHandle) {
                    varHandleFields.add(new FieldInfo(variableName, fieldType));
                    n.getMembers().add(0, declareVarHandle(className, variableName));
                }
            }

            // Don't add volatile modifier - keep fields as they are in the original source
        }

        // Add static initializer for all VarHandles
        if (!varHandleFields.isEmpty()) {
            n.getMembers().add(1, createVarHandleStaticInitializerWithTypes(className, varHandleFields));
        }
    }

    /**
     * Helper class to track field information
     */
    private static class FieldInfo {
        final String name;
        final Type type;

        FieldInfo(String name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    /**
     * Creates a static initializer block for VarHandle initialization with proper field types
     */
    private InitializerDeclaration createVarHandleStaticInitializerWithTypes(String className, List<FieldInfo> fieldInfos) {
        InitializerDeclaration initializer = new InitializerDeclaration(true, new BlockStmt());
        BlockStmt initBody = initializer.getBody();

        // Create try block
        BlockStmt tryBlock = new BlockStmt();
        MethodCallExpr lookup = new MethodCallExpr(new NameExpr("MethodHandles"), "lookup");

        for (FieldInfo fieldInfo : fieldInfos) {
            MethodCallExpr findVarHandle = new MethodCallExpr(lookup, "findVarHandle");
            findVarHandle.addArgument(new ClassExpr(classType(className)));
            findVarHandle.addArgument(new StringLiteralExpr(fieldInfo.name));

            // Determine the field class type
            String fieldClassType;
            if (isRefType(fieldInfo.type, "LinkedQueueNode") || isRefType(fieldInfo.type, "LinkedQueueVarHandleNode")) {
                fieldClassType = "LinkedQueueVarHandleNode";
            } else {
                fieldClassType = getFieldClassType(fieldInfo.type);
            }
            findVarHandle.addArgument(new ClassExpr(classType(fieldClassType)));

            AssignExpr assignment = new AssignExpr(
                new NameExpr(varHandleFieldName(fieldInfo.name)),
                findVarHandle,
                AssignExpr.Operator.ASSIGN
            );
            tryBlock.addStatement(new ExpressionStmt(assignment));
        }

        // Create catch clause
        Parameter catchParam = new Parameter(classType("Exception"), "e");
        BlockStmt catchBlock = new BlockStmt();
        catchBlock.addStatement(
            new ThrowStmt(
                new ObjectCreationExpr(
                    null, classType("ExceptionInInitializerError"), new NodeList<>(new NameExpr("e")))));
        CatchClause catchClause = new CatchClause(catchParam, catchBlock);

        // Create try-catch statement
        TryStmt tryStmt = new TryStmt(tryBlock, new NodeList<>(catchClause), null);
        initBody.addStatement(tryStmt);

        return initializer;
    }

    /**
     * Generates something like
     * <code>return (LinkedQueueVarHandleNode<E>) VH_PRODUCER_NODE.getAndSet(this, newValue)</code>
     */
    private BlockStmt varHandleGetAndSet(String varHandleFieldName, String newValueName, Type returnType) {
        BlockStmt body = new BlockStmt();
        CastExpr castExpr = new CastExpr(
                returnType,
                methodCallExpr(varHandleFieldName, "getAndSet", new ThisExpr(), new NameExpr(newValueName)));
        body.addStatement(new ReturnStmt(castExpr));
        return body;
    }

    private ArrayType varHandleRefArrayType(ArrayType in) {
        // VarHandle version uses E[] directly, not a wrapper
        return in;
    }
}
