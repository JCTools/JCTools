package org.jctools.queues.atomic;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;

import static org.jctools.queues.util.GeneratorUtils.prependGeneratedNoteJavadoc;
import static org.jctools.queues.util.GeneratorUtils.replaceType;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

/**
 * This generator takes in an JCTools 'ArrayQueue' Java source file and patches {@link sun.misc.Unsafe} accesses into
 * atomic {@link java.util.concurrent.atomic.AtomicLongFieldUpdater}. It outputs a Java source file with these patches.
 * <p>
 * An 'ArrayQueue' is one that is backed by a circular array and use a <code>producerLimit</code> and a
 * <code>consumerLimit</code> field to track the positions of each.
 */
public class JavaParsingAtomicArrayQueueGenerator extends JavaParsingAtomicQueueGenerator {

    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingAtomicArrayQueueGenerator.class, args);
    }

    public JavaParsingAtomicArrayQueueGenerator(String sourceFileName) {
        this(sourceFileName, "org.jctools.queues.atomic", "Atomic");
    }

    /** Constructor for unpadded subclasses to pass through different package/prefix values. */
    protected JavaParsingAtomicArrayQueueGenerator(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
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
        // Ignore internal class WeakIterator which we don't need to rename
        if (!nameAsString.equals("WeakIterator")) {
            node.setName(translateQueueName(nameAsString));
        }

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

        if (!node.getMethodsByName("failFastOffer").isEmpty()) {
            MethodDeclaration deprecatedMethodRedirect = node.addMethod("weakOffer", Keyword.PUBLIC);
            patchMethodAsDeprecatedRedirector(deprecatedMethodRedirect, "failFastOffer", PrimitiveType.intType(),
                    new Parameter(classType("E"), "e"));
        }

        prependGeneratedNoteJavadoc(node, getClass(), sourceFileName);
    }

    String fieldUpdaterFieldName(String fieldName) {
        switch (fieldName) {
        case "producerIndex":
            return "P_INDEX_UPDATER";
        case "consumerIndex":
            return "C_INDEX_UPDATER";
        case "producerLimit":
            return "P_LIMIT_UPDATER";
        case "blocked":
            return "BLOCKED";
        // Xadd queue family fields — used by MpUnboundedXaddChunk and its subclasses
        case "producerChunk":
            return "P_CHUNK_UPDATER";
        case "producerChunkIndex":
            return "P_CHUNK_INDEX_UPDATER";
        case "consumerChunk":
            return "C_CHUNK_UPDATER";
        case "index":
            return "INDEX_UPDATER";
        case "prev":
            return "PREV_UPDATER";
        case "next":
            return "NEXT_UPDATER";
        default:
            throw new IllegalArgumentException("Unhandled field: " + fieldName);
        }
    }

    /**
     * Replaces {@code new SpscArrayQueue<R>(...)} with the unpadded pool queue variant appropriate
     * for this generator (e.g. {@code new SpscAtomicUnpaddedArrayQueue<R>(...)}).
     * Used by the xadd queue family which pools chunks internally via SpscArrayQueue.
     */
    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        if (isRefType(n.getType(), "SpscArrayQueue")) {
            ClassOrInterfaceType newType = classType(unpaddedPoolQueueName);
            n.getType().getTypeArguments().ifPresent(newType::setTypeArguments);
            n.setType(newType);
            usesPoolQueue = true;
        }
    }

    /**
     * Given a variable declaration of some sort, check it's name and type and
     * if it looks like any of the key type changes between unsafe and atomic
     * queues, perform the conversion to change it's type.
     */
    void processSpecialNodeTypes(NodeWithType<?, Type> node, String name) {
        Type type = node.getType();
        if (("buffer".equals(name) || "consumerBuffer".equals(name) || "producerBuffer".equals(name)) && isRefArray(type, "E")) {
            replaceType(node, atomicRefArrayType((ArrayType) type));
        } else if (("sBuffer".equals(name) || "sequenceBuffer".equals(name) || "sequence".equals(name)) && isLongArray(type)) {
            replaceType(node, atomicLongArrayType());
        } else if (isRefType(type, "SpscArrayQueue")) {
            ClassOrInterfaceType newType = classType(unpaddedPoolQueueName);
            if (type instanceof ClassOrInterfaceType) {
                ((ClassOrInterfaceType) type).getTypeArguments().ifPresent(newType::setTypeArguments);
            }
            node.setType(newType);
            usesPoolQueue = true;
        } else if (PrimitiveType.longType().equals(type)) {
            switch(name) {
            case "mask":
            case "consumerMask":
            case "producerMask":
            case "offset":
            case "seqOffset":
            case "lookAheadSeqOffset":
            case "lookAheadElementOffset":
                node.setType(PrimitiveType.intType());
            }
        }
    }

    /**
     * Patch each method whose name ends with {@code <prefix>FieldName} (capitalised) with a body
     * that delegates to an {@link java.util.concurrent.atomic.AtomicLongFieldUpdater} or
     * {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater} for the matching field.
     * Handled prefixes: {@code so}, {@code sp}, {@code cas}, {@code getAndAdd},
     * {@code getAndIncrement}, {@code sv}, {@code lv}, {@code lp}. {@code lv}/{@code lp}/{@code sv}
     * become plain reads/writes on the field; the rest delegate to the field updater (with
     * {@code sp} mapped to {@code lazySet} since field updaters lack a plain-store primitive).
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
            // Skip final fields — e.g. MpUnboundedXaddChunk.pooled has accessor isPooled()
            // whose name matches the suffix pattern but must not be patched
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

                if (variableUsesUpdater) {
                    fieldUsesUpdater = true;
                    if (variable.getType().isReferenceType()) {
                        String typeName = variable.getType().asString();
                        if (typeName.length() == 1 && Character.isUpperCase(typeName.charAt(0))) {
                            // Resolve erased bound of generic type parameter (e.g. R -> MpUnboundedXaddAtomicChunk)
                            // AtomicReferenceFieldUpdater requires the erased field type, not Object
                            typeName = resolveErasedBound(n, typeName);
                        }
                        n.getMembers().add(0, declareRefFieldUpdater(className, typeName, variableName));
                    } else if (variable.getType().asPrimitiveType().equals(PrimitiveType.longType()))
                        n.getMembers().add(0, declareLongFieldUpdater(className, variableName));
                    else
                        throw new RuntimeException("Unexpected field type:" + variable);
                }
            }

            if (fieldUsesUpdater) {
                field.addModifier(Keyword.VOLATILE);
            }
        }
    }

    private boolean isLongArray(Type in) {
        if (in instanceof ArrayType) {
            ArrayType aType = (ArrayType) in;
            return PrimitiveType.longType().equals(aType.getComponentType());
        }
        return false;
    }

    private ClassOrInterfaceType atomicRefArrayType(ArrayType in) {
        ClassOrInterfaceType out = new ClassOrInterfaceType(null, "AtomicReferenceArray");
        out.setTypeArguments(in.getComponentType());
        return out;
    }

    private ClassOrInterfaceType atomicLongArrayType() {
        return new ClassOrInterfaceType(null, "AtomicLongArray");
    }

}
