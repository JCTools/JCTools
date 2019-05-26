package org.jctools.queues.atomic;

import java.io.File;
import java.io.PrintStream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleOpensDirective;
import com.github.javaparser.ast.modules.ModuleProvidesDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.github.javaparser.ast.modules.ModuleUsesDirective;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.UnparsableStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * This is a little hand cranked, perhaps hacked together util to take a Java
 * file and dump a trace of where the visitor goes. Helpful for debugging and
 * getting an idea on how to manipulate the AST.
 */
public final class TraceVisitor extends VoidVisitorAdapter<Void> {

    private final PrintStream out;
    private final boolean extended;

    public TraceVisitor(PrintStream out, boolean extended) {
        super();
        this.out = out;
        this.extended = extended;
    }

    @Override
    public void visit(AnnotationDeclaration n, Void arg) {
        out.println("AnnotationDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(AnnotationMemberDeclaration n, Void arg) {
        out.println("AnnotationMemberDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayAccessExpr n, Void arg) {
        out.println("ArrayAccessExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayCreationExpr n, Void arg) {
        out.println("ArrayCreationExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayCreationLevel n, Void arg) {
        out.println("ArrayCreationLevel: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayInitializerExpr n, Void arg) {
        out.println("ArrayInitializerExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ArrayType n, Void arg) {
        out.println("ArrayType: " + (extended ? n : n.getComponentType()));
        super.visit(n, arg);
    }

    @Override
    public void visit(AssertStmt n, Void arg) {
        out.println("AssertStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(AssignExpr n, Void arg) {
        out.println("AssignExpr: " + (extended ? n : n.getTarget() + " = " + n.getValue()));
        super.visit(n, arg);
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        out.println("BinaryExpr: " + (extended ? n : n.getLeft() + " " + n.getOperator() + " " + n.getRight()));
        super.visit(n, arg);
    }

    @Override
    public void visit(BlockComment n, Void arg) {
        out.println("BlockComment: " + (extended ? n : n.getContent()));
        super.visit(n, arg);
    }

    @Override
    public void visit(BlockStmt n, Void arg) {
        out.println("BlockStmt: " + (extended ? n : n.getStatements().size() + " statements"));
        super.visit(n, arg);
    }

    @Override
    public void visit(BooleanLiteralExpr n, Void arg) {
        out.println("BooleanLiteralExpr: " + (extended ? n : n.getValue()));
        super.visit(n, arg);
    }

    @Override
    public void visit(BreakStmt n, Void arg) {
        out.println("BreakStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(CastExpr n, Void arg) {
        out.println("CastExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(CatchClause n, Void arg) {
        out.println("CatchClause: " + (extended ? n : n.getParameter()));
        super.visit(n, arg);
    }

    @Override
    public void visit(CharLiteralExpr n, Void arg) {
        out.println("CharLiteralExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(ClassExpr n, Void arg) {
        out.println("ClassExpr: " + (extended ? n : n.getType()));
        super.visit(n, arg);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        out.println("ClassOrInterfaceDeclaration: " + (extended ? n : n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(ClassOrInterfaceType n, Void arg) {
        out.println("ClassOrInterfaceType: " + (extended ? n : n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(CompilationUnit n, Void arg) {
        out.println("CompilationUnit: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ConditionalExpr n, Void arg) {
        out.println("ConditionalExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        out.println("ConstructorDeclaration: " + (extended ? n : n.getDeclarationAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(ContinueStmt n, Void arg) {
        out.println("ContinueStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        out.println("DoStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(DoubleLiteralExpr n, Void arg) {
        out.println("DoubleLiteralExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(EmptyStmt n, Void arg) {
        out.println("EmptyStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(EnclosedExpr n, Void arg) {
        out.println("EnclosedExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumConstantDeclaration n, Void arg) {
        out.println("EnumConstantDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, Void arg) {
        out.println("EnumDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ExplicitConstructorInvocationStmt n, Void arg) {
        out.println("ExplicitConstructorInvocationStmt: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(ExpressionStmt n, Void arg) {
        out.println("ExpressionStmt: " + (extended ? n : n.getExpression()));
        super.visit(n, arg);
    }

    @Override
    public void visit(FieldAccessExpr n, Void arg) {
        out.println("FieldAccessExpr: " + (extended ? n : n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(FieldDeclaration n, Void arg) {
        out.println("FieldDeclaration: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(ForEachStmt n, Void arg) {
        out.println("ForEachStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        // Collector<CharSequence, ?, String> joiner = Collectors.joining(", ");
        // out.println("ForStmt: "
        // + (extended ? n : n.getInitialization().stream().map(node ->
        // Objects.toString(node)).collect(joiner)) + "; "
        // + n.getCompare()
        // + "; " + n.getUpdate().stream().map(node ->
        // Objects.toString(node)).collect(joiner)
        //
        // );
        super.visit(n, arg);
    }

    @Override
    public void visit(IfStmt n, Void arg) {
        out.println("IfStmt: " + (extended ? n : n.getCondition()));
        super.visit(n, arg);
    }

    @Override
    public void visit(ImportDeclaration n, Void arg) {
        out.println("ImportDeclaration: " + (extended ? n : n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(InitializerDeclaration n, Void arg) {
        out.println("InitializerDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(InstanceOfExpr n, Void arg) {
        out.println("InstanceOfExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(IntegerLiteralExpr n, Void arg) {
        out.println("IntegerLiteralExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(IntersectionType n, Void arg) {
        out.println("IntersectionType: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(JavadocComment n, Void arg) {
        out.println("JavadocComment: " + (extended ? n : n.getContent()));
        super.visit(n, arg);
    }

    @Override
    public void visit(LabeledStmt n, Void arg) {
        out.println("LabeledStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        out.println("LambdaExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(LineComment n, Void arg) {
        out.println("LineComment: " + (extended ? n : n.getContent()));
        super.visit(n, arg);
    }

    @Override
    public void visit(LocalClassDeclarationStmt n, Void arg) {
        out.println("LocalClassDeclarationStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(LongLiteralExpr n, Void arg) {
        out.println("LongLiteralExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(MarkerAnnotationExpr n, Void arg) {
        out.println("MarkerAnnotationExpr: " + (extended ? n : n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(MemberValuePair n, Void arg) {
        out.println("MemberValuePair: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        out.println("MethodCallExpr: " + (extended ? n : n.getNameAsString() + " " + n.getScope()));
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        out.println("MethodDeclaration: " + (extended ? n : n.getDeclarationAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodReferenceExpr n, Void arg) {
        out.println("MethodReferenceExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ModuleDeclaration n, Void arg) {
        out.println("ModuleDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ModuleExportsDirective n, Void arg) {
        out.println("ModuleExportsStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ModuleOpensDirective n, Void arg) {
        out.println("ModuleOpensStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ModuleProvidesDirective n, Void arg) {
        out.println("ModuleProvidesStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ModuleRequiresDirective n, Void arg) {
        out.println("ModuleRequiresStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ModuleUsesDirective n, Void arg) {
        out.println("ModuleUsesStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(Name n, Void arg) {
        out.println("Name: " + (extended ? n : n.getIdentifier()));
        super.visit(n, arg);
    }

    @Override
    public void visit(NameExpr n, Void arg) {
        out.println("NameExpr: " + (extended ? n : n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(NodeList n, Void arg) {
        out.println("NodeList: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(NormalAnnotationExpr n, Void arg) {
        out.println("NormalAnnotationExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(NullLiteralExpr n, Void arg) {
        out.println("NullLiteralExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        out.println("ObjectCreationExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(PackageDeclaration n, Void arg) {
        out.println("PackageDeclaration: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(Parameter n, Void arg) {
        out.println("Parameter: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(PrimitiveType n, Void arg) {
        out.println("PrimitiveType: " + (extended ? n : n.getType()));
        super.visit(n, arg);
    }

    @Override
    public void visit(ReturnStmt n, Void arg) {
        out.println("ReturnStmt: " + (extended ? n : n.getExpression()));
        super.visit(n, arg);
    }

    @Override
    public void visit(SimpleName n, Void arg) {
        out.println("SimpleName: " + (extended ? n : n.getIdentifier()));
        super.visit(n, arg);
    }

    @Override
    public void visit(SingleMemberAnnotationExpr n, Void arg) {
        out.println("SingleMemberAnnotationExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(StringLiteralExpr n, Void arg) {
        out.println("StringLiteralExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(SuperExpr n, Void arg) {
        out.println("SuperExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(SwitchEntry n, Void arg) {
        out.println("SwitchEntryStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        out.println("SwitchStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(SynchronizedStmt n, Void arg) {
        out.println("SynchronizedStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(ThisExpr n, Void arg) {
        out.println("ThisExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(ThrowStmt n, Void arg) {
        out.println("ThrowStmt: " + (extended ? n : n.getExpression()));
        super.visit(n, arg);
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        out.println("TryStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(TypeExpr n, Void arg) {
        out.println("TypeExpr: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(TypeParameter n, Void arg) {
        out.println("TypeParameter: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(UnaryExpr n, Void arg) {
        out.println("UnaryExpr: " + (extended ? n : n.getOperator() + " " + n.getExpression()));
        super.visit(n, arg);
    }

    @Override
    public void visit(UnionType n, Void arg) {
        out.println("UnionType: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(UnknownType n, Void arg) {
        out.println("UnknownType: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(UnparsableStmt n, Void arg) {
        out.println("UnparsableStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(VariableDeclarationExpr n, Void arg) {
        out.println("VariableDeclarationExpr: " + (extended ? n : n));
        super.visit(n, arg);
    }

    @Override
    public void visit(VariableDeclarator n, Void arg) {
        out.println("VariableDeclarator: " + (extended ? n
                : n.getNameAsString() + " of type " + n.getType() + " init " + n.getInitializer().orElse(null)));
        super.visit(n, arg);

    }

    @Override
    public void visit(VoidType n, Void arg) {
        out.println("VoidType: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        out.println("WhileStmt: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    @Override
    public void visit(WildcardType n, Void arg) {
        out.println("WildcardType: " + (extended ? n : ""));
        super.visit(n, arg);
    }

    public static void main(String[] args) throws Exception {
        for (String file : args) {
            System.out.println("Opening " + file);
            CompilationUnit cu = new JavaParser().parse(new File(file)).getResult().get();
            new TraceVisitor(System.out, false).visit(cu, null);
            System.out.println();
            System.out.println();
            System.out.println();
        }
    }

}
