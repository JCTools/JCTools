package org.jctools.queues.atomic;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;

import static org.jctools.queues.util.GeneratorUtils.formatMultilineJavadoc;
import static org.jctools.queues.util.GeneratorUtils.prependGeneratedNoteJavadoc;
import static org.jctools.queues.util.GeneratorUtils.replaceType;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

/**
 * This generator takes in an JCTools 'LinkedQueue' Java source file and patches {@link sun.misc.Unsafe} accesses into
 * atomic {@link java.util.concurrent.atomic.AtomicLongFieldUpdater}. It outputs a Java source file with these patches.
 * <p>
 * An 'LinkedQueue' is one that is backed by a linked list and use a <code>producerNode</code> and a
 * <code>consumerNode</code> field to track the positions of each.
 */
public class JavaParsingAtomicLinkedQueueGenerator extends JavaParsingAtomicQueueGenerator {

    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingAtomicLinkedQueueGenerator.class, args);
    }

    public JavaParsingAtomicLinkedQueueGenerator(String sourceFileName) {
        this(sourceFileName, "org.jctools.queues.atomic", "Atomic");
    }

    /** Constructor for unpadded subclasses to pass through different package/prefix values. */
    protected JavaParsingAtomicLinkedQueueGenerator(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
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
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        super.visit(node, arg);

        replaceParentClasses(node);

        String nameAsString = node.getNameAsString();
        if (nameAsString.contains("Queue"))
            node.setName(translateQueueName(nameAsString));

        if (isCommentPresent(node, GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS)) {
            node.setComment(null);
            removeStaticFieldsAndInitialisers(node);
            patchAtomicFieldUpdaterAccessorMethods(node);
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
            n.setType(atomicRefArrayType((ArrayType) n.getType()));
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
            n.setType(simpleParametricType("LinkedQueueAtomicNode", "E"));
        }
    }

    String fieldUpdaterFieldName(String fieldName) {
        switch (fieldName) {
        case "producerNode":
            return "P_NODE_UPDATER";
        case "consumerNode":
            return "C_NODE_UPDATER";
        case "consumerIndex":
            return "C_INDEX_UPDATER";
        case "producerIndex":
            return "P_INDEX_UPDATER";
        case "producerLimit":
            return "P_LIMIT_UPDATER";
        default:
            throw new IllegalArgumentException("Unhandled field: " + fieldName);
        }
    }

    /**
     * Given a variable declaration of some sort, check it's name and type and
     * if it looks like any of the key type changes between unsafe and atomic
     * queues, perform the conversion to change it's type.
     */
    void processSpecialNodeTypes(NodeWithType<?, Type> node, String name) {
        Type type = node.getType();
        if (node instanceof MethodDeclaration && ("newBufferAndOffset".equals(name) || "nextArrayOffset".equals(name))) {
            node.setType(PrimitiveType.intType());
        } else if (PrimitiveType.longType().equals(type)) {
            switch(name) {
            case "offset":
            case "offsetInNew":
            case "offsetInOld":
            case "lookAheadElementOffset":
                node.setType(PrimitiveType.intType());
            }
        } else if (isRefType(type, "LinkedQueueNode")) {
            node.setType(simpleParametricType("LinkedQueueAtomicNode", "E"));
        } else if (isRefArray(type, "E")) {
            replaceType(node, atomicRefArrayType((ArrayType) type));
        }
    }

    /**
     * Patch each method whose name ends with {@code <prefix>FieldName} (capitalised) with a body
     * that delegates to an {@link java.util.concurrent.atomic.AtomicLongFieldUpdater} or
     * {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater} for the matching field.
     * Same handled prefixes as {@link JavaParsingAtomicArrayQueueGenerator}'s patcher. Additionally
     * synthesises an {@code xchgProducerNode} method that delegates to {@code getAndSet} on the
     * producer-node field updater — driven by name-match on the {@code producerNode} field, not by
     * suffix dispatch.
     *
     * @param n the AST node for the containing class
     */
    private void patchAtomicFieldUpdaterAccessorMethods(ClassOrInterfaceDeclaration n) {
        String className = n.getNameAsString();

        for (FieldDeclaration field : n.getFields()) {
            if (field.getModifiers().contains(Modifier.staticModifier())) {
                // Ignore statics
                continue;
            }
            // Skip final fields — see JavaParsingAtomicArrayQueueGenerator for the same guard.
            // Final fields can't have so/cas/sv accessors that need patching, and a final field
            // whose name happens to match a method suffix would otherwise get a stray updater.
            if (field.getModifiers().contains(Modifier.finalModifier())) {
                continue;
            }

            boolean fieldUsesUpdater = false;
            for (VariableDeclarator variable : field.getVariables()) {
                String variableName = variable.getNameAsString();
                String methodNameSuffix = capitalise(variableName);

                boolean variableUsesUpdater = false;
                for (MethodDeclaration method : n.getMethods()) {
                    variableUsesUpdater |= patchAtomicFieldUpdaterAccessorMethod(variableName, method, methodNameSuffix);
                }

                if ("producerNode".equals(variableName)) {
                    variableUsesUpdater = true;
                    String fieldUpdaterFieldName = fieldUpdaterFieldName(variableName);

                    MethodDeclaration method = n.addMethod("xchgProducerNode", Keyword.PROTECTED, Keyword.FINAL);
                    method.setType(simpleParametricType("LinkedQueueAtomicNode", "E"));
                    method.addParameter(simpleParametricType("LinkedQueueAtomicNode", "E"), "newValue");
                    method.setBody(fieldUpdaterGetAndSet(fieldUpdaterFieldName, "newValue"));
                }

                if (variableUsesUpdater) {
                    fieldUsesUpdater = true;
                    if (PrimitiveType.longType().equals(variable.getType())) {
                        n.getMembers().add(0, declareLongFieldUpdater(className, variableName));
                    } else {
                        // Use the variable's declared type for the AtomicReferenceFieldUpdater
                        // type-parameter, not a hard-coded LinkedQueueAtomicNode. Resolve a single-
                        // letter generic parameter to its erased bound (e.g. R -> Bar) the same
                        // way the array-queue patcher does — keeps the linked-queue patcher correct
                        // if a non-LinkedQueueNode reference field is ever added (e.g. Thread).
                        String typeName = variable.getType().asString();
                        if (typeName.length() == 1 && Character.isUpperCase(typeName.charAt(0))) {
                            typeName = resolveErasedBound(n, typeName);
                        } else if (variable.getType().isClassOrInterfaceType()) {
                            typeName = variable.getType().asClassOrInterfaceType().getNameAsString();
                        }
                        n.getMembers().add(0, declareRefFieldUpdater(className, typeName, variableName));
                    }
                }
            }

            if (fieldUsesUpdater) {
                field.addModifier(Keyword.VOLATILE);
            }
        }
    }

    /**
     * Generates something like
     * <code>return P_INDEX_UPDATER.getAndSet(this, newValue)</code>
     */
    private BlockStmt fieldUpdaterGetAndSet(String fieldUpdaterFieldName, String newValueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(
                methodCallExpr(fieldUpdaterFieldName, "getAndSet", new ThisExpr(), new NameExpr(newValueName))));
        return body;
    }

    private ClassOrInterfaceType atomicRefArrayType(ArrayType in) {
        ClassOrInterfaceType out = new ClassOrInterfaceType(null, "AtomicReferenceArray");
        out.setTypeArguments(in.getComponentType());
        return out;
    }

}
