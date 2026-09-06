package run.endive.cm.bindgen;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import java.util.List;

/**
 * Builder methods for the JavaParser AST, short enough that building a node reads about as well as
 * writing the Java it stands for.
 *
 * <p>Every factory returns a fresh, unparented node, so a result may be handed to exactly one
 * parent.
 */
final class AstBuilders {

    private AstBuilders() {}

    /** A type by name, which may be qualified, such as {@code java.util.Map}. */
    static ClassOrInterfaceType type(String name) {
        ClassOrInterfaceType result = null;
        for (String segment : name.split("\\.")) {
            result = new ClassOrInterfaceType(result, segment);
        }
        return result;
    }

    /** {@code type<arguments>}. */
    static ClassOrInterfaceType generic(ClassOrInterfaceType type, Type... arguments) {
        return type.setTypeArguments(arguments);
    }

    /** {@code type<>}, whose arguments the compiler infers. */
    static ClassOrInterfaceType diamond(ClassOrInterfaceType type) {
        return type.setTypeArguments(new NodeList<>());
    }

    /** An expression naming a variable or a type, which may be qualified. */
    static Expression name(String name) {
        String[] segments = name.split("\\.");
        Expression result = new NameExpr(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            result = new FieldAccessExpr(result, segments[i]);
        }
        return result;
    }

    static StringLiteralExpr text(String value) {
        return new StringLiteralExpr(value);
    }

    /** A call on {@code scope}, or an unqualified one when {@code scope} is {@code null}. */
    static MethodCallExpr call(Expression scope, String method, Expression... arguments) {
        return new MethodCallExpr(enclosed(scope), method, NodeList.nodeList(arguments));
    }

    static MethodCallExpr call(Expression scope, String method, List<Expression> arguments) {
        return new MethodCallExpr(enclosed(scope), method, NodeList.nodeList(arguments));
    }

    static ObjectCreationExpr construct(ClassOrInterfaceType type, Expression... arguments) {
        return new ObjectCreationExpr(null, type, NodeList.nodeList(arguments));
    }

    static FieldAccessExpr field(Expression scope, String name) {
        return new FieldAccessExpr(enclosed(scope), name);
    }

    static FieldAccessExpr thisField(String name) {
        return new FieldAccessExpr(new ThisExpr(), name);
    }

    static CastExpr cast(Type type, Expression value) {
        return new CastExpr(type, value);
    }

    static ClassExpr classLiteral(Type type) {
        return new ClassExpr(type);
    }

    static ArrayAccessExpr element(Expression array, int index) {
        return new ArrayAccessExpr(enclosed(array), new IntegerLiteralExpr(String.valueOf(index)));
    }

    /** {@code new Object[] {values}}, or {@code new Object[0]} when there are none. */
    static ArrayCreationExpr objects(List<Expression> values) {
        ClassOrInterfaceType object = type("Object");
        if (values.isEmpty()) {
            NodeList<ArrayCreationLevel> empty =
                    NodeList.nodeList(new ArrayCreationLevel(new IntegerLiteralExpr("0")));
            return new ArrayCreationExpr(object, empty, null);
        }
        return new ArrayCreationExpr(
                object,
                NodeList.nodeList(new ArrayCreationLevel()),
                new ArrayInitializerExpr(NodeList.nodeList(values)));
    }

    static MethodReferenceExpr methodReference(Expression scope, String method) {
        return new MethodReferenceExpr(scope, new NodeList<>(), method);
    }

    static LambdaExpr lambda(String parameter, Expression body) {
        return new LambdaExpr(new Parameter(new UnknownType(), parameter), body);
    }

    static LambdaExpr lambda(String parameter, BlockStmt body) {
        return new LambdaExpr(new Parameter(new UnknownType(), parameter), body);
    }

    static Statement declare(Type type, String name, Expression value) {
        return new ExpressionStmt(
                new VariableDeclarationExpr(new VariableDeclarator(type, name, value)));
    }

    static Statement assign(Expression target, Expression value) {
        return new ExpressionStmt(new AssignExpr(target, value, AssignExpr.Operator.ASSIGN));
    }

    /**
     * The printer writes a tree structurally rather than by precedence, so an expression that
     * binds less tightly than the one reaching into it is parenthesized here instead.
     */
    private static Expression enclosed(Expression expression) {
        boolean loose =
                expression instanceof CastExpr
                        || expression instanceof ConditionalExpr
                        || expression instanceof LambdaExpr;
        return loose ? new EnclosedExpr(expression) : expression;
    }
}
