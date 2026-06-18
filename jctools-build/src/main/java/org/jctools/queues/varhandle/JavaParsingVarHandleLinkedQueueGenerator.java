package org.jctools.queues.varhandle;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
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
        this(sourceFileName, "org.jctools.queues.varhandle", "VarHandle");
    }

    /** Constructor for unpadded subclasses to pass through different package/prefix values. */
    protected JavaParsingVarHandleLinkedQueueGenerator(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
        super(sourceFileName, outputPackage, queueClassNamePrefix);
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
    protected void visitClass(ClassOrInterfaceDeclaration node, Void arg) {
        replaceParentClasses(node);

        String nameAsString = node.getNameAsString();
        if (nameAsString.contains("Queue"))
            node.setName(translateQueueName(nameAsString));

        if (isCommentPresent(node, GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS)) {
            node.setComment(null);
            removeStaticFieldsAndInitialisers(node);
            patchVarHandleAccessorMethods(node);
        }

        for (MethodDeclaration method : node.getMethods()) {
            if (isCommentPresent(method, GEN_DIRECTIVE_METHOD_IGNORE)) {
                method.remove();
            }
        }

        prependGeneratedNoteJavadoc(node, getClass(), sourceFileName);
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
        // So we don't convert offset types for VarHandle. E[] arrays are also kept as-is
        // (atomic queues wrap E[] in AtomicReferenceArray<E>; VarHandle uses the raw array).
        if (isRefType(type, "LinkedQueueNode")) {
            node.setType(simpleParametricType("LinkedQueueVarHandleNode", "E"));
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
                }
            }

            // Don't add volatile modifier - keep fields as they are in the original source
        }

        // Prepend the VarHandle field declarations (in original order) and the static initializer
        // that wires them up. See JavaParsingVarHandleArrayQueueGenerator for the same pattern.
        if (!varHandleFields.isEmpty()) {
            for (int i = 0; i < varHandleFields.size(); i++) {
                n.getMembers().add(i, declareVarHandle(className, varHandleFields.get(i).name));
            }
            n.getMembers().add(varHandleFields.size(), createVarHandleStaticInitializerWithTypes(n, className, varHandleFields));
        }
    }

    @Override
    protected String resolveVarHandleClassType(ClassOrInterfaceDeclaration n, Type fieldType) {
        // Linked queues store producerNode/consumerNode as LinkedQueueNode<E> in source. By the
        // time the static-initializer builder runs, processSpecialNodeTypes has already rewritten
        // these to LinkedQueueVarHandleNode. Either way, findVarHandle's third argument must
        // erase to LinkedQueueVarHandleNode.
        if (isRefType(fieldType, "LinkedQueueNode") || isRefType(fieldType, "LinkedQueueVarHandleNode")) {
            return "LinkedQueueVarHandleNode";
        }
        return super.resolveVarHandleClassType(n, fieldType);
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
}
