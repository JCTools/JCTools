package org.jctools.queues.util;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Optional;

import static org.jctools.queues.util.GeneratorUtils.renameType;

/**
 * Shared base for the JCTools queue generators: holds the visitor machinery, name-translation
 * rules, parent-class renamer, and AST construction helpers that the atomic, VarHandle, and
 * unpadded hierarchies all need. Concrete generators pass a {@link #queueClassNamePrefix} (the
 * infix spliced into translated names — {@code "Atomic"}, {@code "VarHandle"}, {@code "Unpadded"},
 * {@code "AtomicUnpadded"}, {@code "VarHandleUnpadded"}) and an {@link #outputPackage} via the
 * constructor, plus their own AST rewrites in subclass-specific {@code visit(...)} overrides.
 * <p>
 * These generators are coupled with the structure and naming of fields, variables, and methods in
 * the JCTools queue sources and are not suitable for general-purpose use.
 */
public abstract class JavaParsingQueueGeneratorBase extends VoidVisitorAdapter<Void>
        implements JCToolsGenerator {

    /**
     * When set on a class using a single-line comment, the class has fields that have unsafe
     * 'ordered' reads and writes. These fields are candidates to be patched by the atomic and
     * VarHandle generators.
     */
    protected static final String GEN_DIRECTIVE_CLASS_CONTAINS_ORDERED_FIELD_ACCESSORS = "$gen:ordered-fields";

    /**
     * When set on a method using a single-line comment, the method is removed by the generator.
     */
    protected static final String GEN_DIRECTIVE_METHOD_IGNORE = "$gen:ignore";

    protected final String sourceFileName;

    /** The output package for files this generator produces. E.g. {@code "org.jctools.queues.atomic"}. */
    protected final String outputPackage;

    /**
     * The infix spliced into translated class names by {@link #translateQueueName(String)} —
     * e.g. {@code "Atomic"} so {@code SpscArrayQueue} becomes {@code SpscAtomicArrayQueue}.
     */
    protected final String queueClassNamePrefix;

    protected JavaParsingQueueGeneratorBase(String sourceFileName, String outputPackage, String queueClassNamePrefix) {
        this.sourceFileName = sourceFileName;
        this.outputPackage = outputPackage;
        this.queueClassNamePrefix = queueClassNamePrefix;
    }

    @Override
    public final String translateQueueName(String qName) {
        if (qName.contains("LinkedQueue") || qName.contains("LinkedArrayQueue")) {
            return qName.replace("Linked", "Linked" + queueClassNamePrefix);
        }
        // ArrayQueue check must come before Chunk check because some inner hierarchy classes
        // contain both "ArrayQueue" and end with "Chunk" (e.g. MpUnboundedXaddArrayQueueProducerChunk)
        if (qName.contains("ArrayQueue")) {
            return qName.replace("ArrayQueue", queueClassNamePrefix + "ArrayQueue");
        }
        // Standalone Chunk classes (e.g. MpUnboundedXaddChunk -> MpUnboundedXaddAtomicChunk)
        if (qName.endsWith("Chunk")) {
            return qName.replace("Chunk", queueClassNamePrefix + "Chunk");
        }
        throw new IllegalArgumentException("Unexpected queue name: " + qName);
    }

    @Override
    public final void visit(PackageDeclaration n, Void arg) {
        super.visit(n, arg);
        n.setName(outputPackage);
    }

    /**
     * Renames Chunk type references (e.g. {@code MpUnboundedXaddChunk} to
     * {@code MpUnboundedXaddAtomicChunk}) wherever they appear as types: field declarations,
     * method return types, generic parameters, casts, etc. The {@code contains(prefix)} guard
     * skips Chunk types that already carry the infix — both ones already renamed by an enclosing
     * visitor call and standalone references the generator itself synthesises.
     */
    @Override
    public final void visit(ClassOrInterfaceType n, Void arg) {
        super.visit(n, arg);
        String name = n.getNameAsString();
        if (name.endsWith("Chunk") && !name.contains(queueClassNamePrefix)) {
            renameType(n, translateQueueName(name));
        }
    }

    /**
     * Renames Chunk class references in name expressions, e.g. {@code MpmcUnboundedXaddChunk.NOT_USED}.
     * The {@code isUpperCase} guard avoids renaming local variables like {@code cChunk} that also
     * end with "Chunk" but are not class names.
     */
    @Override
    public final void visit(NameExpr n, Void arg) {
        super.visit(n, arg);
        String name = n.getNameAsString();
        if (name.endsWith("Chunk") && Character.isUpperCase(name.charAt(0)) && !name.contains(queueClassNamePrefix)) {
            n.setName(translateQueueName(name));
        }
    }

    /**
     * Renames the parents of {@code n} in its {@code extends} clause according to
     * {@link #translateQueueName(String)}, leaving {@code AbstractQueue} alone (it is the JDK
     * parent). Already-translated parents are skipped via the {@code contains(prefix)} guard.
     */
    protected final void replaceParentClasses(ClassOrInterfaceDeclaration n) {
        for (ClassOrInterfaceType parent : n.getExtendedTypes()) {
            String parentName = parent.getNameAsString();
            if ("AbstractQueue".equals(parentName)) {
                continue;
            }
            if (!parentName.contains(queueClassNamePrefix)) {
                parent.setName(translateQueueName(parentName));
            }
        }
    }

    /**
     * Returns whether {@code node} carries a comment whose trimmed content equals {@code wanted}.
     * Used to detect {@code $gen:ordered-fields} and {@code $gen:ignore} directives.
     */
    protected static boolean isCommentPresent(Node node, String wanted) {
        Optional<Comment> maybeComment = node.getComment();
        return maybeComment.isPresent() && wanted.equals(maybeComment.get().getContent().trim());
    }

    /**
     * Removes Unsafe-specific static infrastructure: static initializer blocks that reference
     * {@code Unsafe} or {@code *_OFFSET} (i.e. ones that compute Unsafe field offsets) and static
     * fields ending with {@code _OFFSET}. Unrelated static initializer blocks and {@code NOT_USED}-
     * style constants are preserved.
     */
    protected static void removeStaticFieldsAndInitialisers(ClassOrInterfaceDeclaration node) {
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
            if (name.endsWith("_OFFSET")) {
                return true;
            }
        }
        return false;
    }

    protected static String capitalise(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    protected static ClassOrInterfaceType classType(String className) {
        return new ClassOrInterfaceType(null, className);
    }

    protected static ClassOrInterfaceType simpleParametricType(String className, String... typeArgs) {
        ClassOrInterfaceType type = new ClassOrInterfaceType(null, new SimpleName(className), null);
        if (typeArgs.length > 0) {
            NodeList<Type> typeArguments = new NodeList<>();
            for (String typeArg : typeArgs) {
                typeArguments.add(classType(typeArg));
            }
            type.setTypeArguments(typeArguments);
        }
        return type;
    }

    protected static boolean isRefType(Type in, String className) {
        if (in instanceof ClassOrInterfaceType) {
            return className.equals(((ClassOrInterfaceType) in).getNameAsString());
        }
        return false;
    }

    protected static boolean isRefArray(Type in, String refClassName) {
        if (in instanceof ArrayType) {
            return isRefType(((ArrayType) in).getComponentType(), refClassName);
        }
        return false;
    }

    protected static MethodCallExpr methodCallExpr(String owner, String method, Expression... args) {
        MethodCallExpr methodCallExpr = new MethodCallExpr(new NameExpr(owner), method);
        for (Expression expr : args) {
            methodCallExpr.addArgument(expr);
        }
        return methodCallExpr;
    }

    /** Generates {@code field = newValue;} as a single-statement block. */
    protected static BlockStmt fieldAssignment(String fieldName, String valueName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ExpressionStmt(
                new AssignExpr(new NameExpr(fieldName), new NameExpr(valueName), AssignExpr.Operator.ASSIGN)));
        return body;
    }

    /** Generates {@code return field;} as a single-statement block. */
    protected static BlockStmt returnField(String fieldName) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(fieldName));
        return body;
    }

    protected static ImportDeclaration staticImportDeclaration(String name) {
        return new ImportDeclaration(name, true, true);
    }

    /**
     * Resolves the erased bound of a single-letter generic type parameter by looking at the class
     * declaration's type parameters. E.g. for {@code class Foo<R extends Bar<R,E>, E>}, resolving
     * {@code "R"} returns {@code "Bar"}. Returns {@code "Object"} if no bound is found.
     */
    protected static String resolveErasedBound(ClassOrInterfaceDeclaration n, String typeParamName) {
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
}
