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

import static org.jctools.queues.util.GeneratorUtils.prependGeneratedNoteJavadoc;
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
    protected boolean stripsPadding() {
        return true;
    }

    @Override
    public void organiseImports(CompilationUnit cu) {
        List<ImportDeclaration> importDecls = new ArrayList<>();
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            importDecls.add(translateChunkStaticImportOrSelf(importDeclaration));
        }
        cu.getImports().clear();
        for (ImportDeclaration importDecl : importDecls) {
            cu.addImport(importDecl);
        }
        cu.addImport(new ImportDeclaration("org.jctools.queues", false, true));
    }

    @Override
    protected void visitClass(ClassOrInterfaceDeclaration node, Void arg) {
        String nameAsString = node.getNameAsString();
        if (!nameAsString.contains("Queue") && !nameAsString.endsWith("Chunk"))
            return;
        replaceParentClasses(node);
        node.setName(translateQueueName(nameAsString));

        prependGeneratedNoteJavadoc(node, this.getClass(), sourceFileName);
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
     * Rewrites {@code new SomeArrayQueue<R>(...)} so the new-expression points at the unpadded
     * variant. Originally hard-coded to {@code SpscArrayQueue} only; generalised so any other
     * pool-queue type used by xadd queues (or future ones) is renamed too instead of silently
     * leaking a padded reference into the unpadded variant.
     */
    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        ClassOrInterfaceType type = n.getType();
        String name = type.getNameAsString();
        if (name.contains(queueClassNamePrefix) || !isOuterQueueOrChunkType(name)) {
            return;
        }
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, translateQueueName(name));
        type.getTypeArguments().ifPresent(newType::setTypeArguments);
        n.setType(newType);
    }

    @Override
    public void visit(VariableDeclarator n, Void arg) {
        super.visit(n, arg);
        translateQueueOrChunkType(n);
    }

    @Override
    public void visit(Parameter n, Void arg) {
        super.visit(n, arg);
        translateQueueOrChunkType(n);
    }

    private void translateQueueOrChunkType(NodeWithType<?, Type> holder) {
        Type type = holder.getType();
        if (!(type instanceof ClassOrInterfaceType)) {
            return;
        }
        String name = ((ClassOrInterfaceType) type).getNameAsString();
        if (name.contains(queueClassNamePrefix) || !isOuterQueueOrChunkType(name)) {
            return;
        }
        ClassOrInterfaceType newType = new ClassOrInterfaceType(null, translateQueueName(name));
        ((ClassOrInterfaceType) type).getTypeArguments().ifPresent(newType::setTypeArguments);
        holder.setType(newType);
    }

    /**
     * Stricter than {@link #isQueueOrChunkName(String)}: only top-level queue/chunk classes (not
     * helpers like {@code LinkedQueueNode}). Used for type references where translating a
     * helper-class name would be wrong.
     */
    private static boolean isOuterQueueOrChunkType(String name) {
        return name.endsWith("Queue") || name.endsWith("Chunk");
    }
}
