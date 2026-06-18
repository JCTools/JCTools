package org.jctools.queues.unpadded;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.jctools.queues.util.JavaParsingQueueGeneratorBase;

import static org.jctools.queues.util.GeneratorUtils.cleanupPaddingComments;
import static org.jctools.queues.util.GeneratorUtils.prependGeneratedNoteJavadoc;
import static org.jctools.queues.util.GeneratorUtils.removePaddingFields;
import static org.jctools.queues.util.GeneratorUtils.runJCToolsGenerator;

/**
 * Generates the unpadded variant of a JCTools queue source: rewrites the package to
 * {@code org.jctools.queues.unpadded}, splices {@code Unpadded} into the class name, drops the
 * byte-padding fields and any orphaned padding-related comments, and leaves the {@link sun.misc.Unsafe}
 * accessors untouched. The output mirrors the structure of the input minus the cache-line padding
 * — useful where memory footprint matters more than the false-sharing protection that the padded
 * variant provides.
 */
public class JavaParsingUnpaddedQueueGenerator extends JavaParsingQueueGeneratorBase {

    public static void main(String[] args) throws Exception {
        runJCToolsGenerator(JavaParsingUnpaddedQueueGenerator.class, args);
    }

    public JavaParsingUnpaddedQueueGenerator(String sourceFileName) {
        super(sourceFileName, "org.jctools.queues.unpadded", "Unpadded");
    }

    @Override
    public void cleanupComments(CompilationUnit cu) {
        cleanupPaddingComments(cu);
    }

    @Override
    public void organiseImports(CompilationUnit cu) {
        List<ImportDeclaration> importDecls = new ArrayList<>();
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            // Rewrite static imports from Chunk classes to point at the translated variant,
            // e.g. "import static o.j.q.MpmcUnboundedXaddChunk.NOT_USED" ->
            //      "import static o.j.q.unpadded.MpmcUnboundedXaddUnpaddedChunk.NOT_USED"
            if (importDeclaration.isStatic() && name.startsWith("org.jctools.queues.") && name.contains("Chunk.")) {
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                String className = name.substring("org.jctools.queues.".length(), name.lastIndexOf('.'));
                if (className.endsWith("Chunk")) {
                    String translatedClass = translateQueueName(className);
                    importDecls.add(new ImportDeclaration("org.jctools.queues.unpadded." + translatedClass + "." + simpleName, true, false));
                    continue;
                }
            }
            importDecls.add(importDeclaration);
        }
        cu.getImports().clear();
        for (ImportDeclaration importDecl : importDecls) {
            cu.addImport(importDecl);
        }
        cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        super.visit(node, arg);
        String nameAsString = node.getNameAsString();
        if (!nameAsString.contains("Queue") && !nameAsString.endsWith("Chunk"))
            return;
        replaceParentClasses(node);
        node.setName(translateQueueName(nameAsString));

        prependGeneratedNoteJavadoc(node, this.getClass(), sourceFileName);

        removePaddingFields(node);
    }

    /**
     * Rewrites {@code fieldOffset(SomeClass.class, ...)} so the class literal targets the unpadded
     * variant. Throws if a class literal of a translatable queue/chunk type appears as an argument
     * to any other method — silently leaving such a literal untranslated would produce an unpadded
     * variant that reflects into the padded class at runtime (NoSuchFieldException on a private
     * field) far from the cause.
     */
    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);
        boolean isFieldOffset = "fieldOffset".equals(n.getName().getIdentifier());
        for (Expression argument : n.getArguments()) {
            if (!argument.isClassExpr()) {
                continue;
            }
            ClassExpr classExpr = argument.asClassExpr();
            String type = classExpr.getTypeAsString();
            if (type.contains(queueClassNamePrefix) || !isQueueOrChunkName(type)) {
                continue;
            }
            if (!isFieldOffset) {
                throw new IllegalStateException("Unpadded generator does not know how to rewrite '"
                        + type + ".class' passed to '" + n.getName().getIdentifier()
                        + "(...)'. Add explicit handling in JavaParsingUnpaddedQueueGenerator.");
            }
            classExpr.setType(translateQueueName(type));
        }
    }

    private static boolean isQueueOrChunkName(String name) {
        return name.contains("LinkedQueue") || name.contains("ArrayQueue") || name.endsWith("Chunk");
    }

    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        super.visit(n, arg);
        // Update the ctor to match the class name
        String nameAsString = n.getNameAsString();
        if (nameAsString.contains("Queue") || nameAsString.endsWith("Chunk"))
            n.setName(translateQueueName(nameAsString));
    }

    /**
     * Replaces {@code new SpscArrayQueue<R>(...)} with {@code new SpscUnpaddedArrayQueue<R>(...)}.
     * Used by xadd queues which pool chunks internally via SpscArrayQueue.
     */
    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        if (isRefType(n.getType(), "SpscArrayQueue")) {
            ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "SpscUnpaddedArrayQueue");
            n.getType().getTypeArguments().ifPresent(newType::setTypeArguments);
            n.setType(newType);
        }
    }

    @Override
    public void visit(VariableDeclarator n, Void arg) {
        super.visit(n, arg);
        rewriteSpscArrayQueueType(n);
    }

    @Override
    public void visit(Parameter n, Void arg) {
        super.visit(n, arg);
        rewriteSpscArrayQueueType(n);
    }

    private void rewriteSpscArrayQueueType(NodeWithType<?, Type> holder) {
        Type type = holder.getType();
        if (!isRefType(type, "SpscArrayQueue")) {
            return;
        }
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, "SpscUnpaddedArrayQueue");
        if (type instanceof ClassOrInterfaceType) {
            ((ClassOrInterfaceType) type).getTypeArguments().ifPresent(newType::setTypeArguments);
        }
        holder.setType(newType);
    }
}
