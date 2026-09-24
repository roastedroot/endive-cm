package run.endive.cm.bindgen;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ResultType;

/**
 * The pieces common to every binding for a WIT function, whichever way the call runs.
 *
 * <p>A resource method's first parameter is the borrowed receiver, which Java carries as {@code
 * this}, so most of these take the count of leading parameters to drop.
 */
final class FunctionBindings {

    /** The name of the lambda parameter carrying an imported call's arguments. */
    private static final String ARGS = "args";

    /** The name of the local holding what a call to the component returned. */
    private static final String OUTCOME = "outcome";

    /** The name of the caught exception an imported call turns back into an error case. */
    private static final String CAUGHT = "caught";

    /**
     * The case labels a {@code result} despecializes to. The error label also names the accessor
     * the generated exception carries its payload behind.
     *
     * @see run.endive.cm.types.ResultType#despecialize()
     */
    private static final String OK = "ok";

    private static final String ERROR = "error";

    private final GeneratedUnit unit;
    private final WitTypes types;

    private FunctionBindings(GeneratedUnit unit, WitTypes types) {
        this.unit = unit;
        this.types = types;
    }

    static FunctionBindings forUnit(GeneratedUnit unit) {
        return new FunctionBindings(unit, new WitTypes(unit));
    }

    /** The type mapping these bindings are written against. */
    WitTypes types() {
        return types;
    }

    /** The Java signature of {@code function}, with neither modifiers nor a body. */
    MethodDeclaration signature(WitFunction function, int skip) {
        MethodDeclaration method = new MethodDeclaration();
        method.setName(Names.member(function.name()));
        method.setType(returnType(function));
        method.removeBody();
        return addParameters(method, function, skip);
    }

    /**
     * Adds {@code function}'s parameters to a method whose name and return type are given rather
     * than read off the function, which is how a resource's constructor is bound, since neither
     * the Java name nor the handle it returns is anything its own type says.
     */
    MethodDeclaration addParameters(MethodDeclaration method, WitFunction function, int skip) {
        for (LabelValType param : parameters(function, skip)) {
            method.addParameter(
                    types.javaType(param.valType(), function.scope()), Names.member(param.label()));
        }
        return method;
    }

    /**
     * A method calling the component through {@code callee}, converting what crosses in each
     * direction.
     *
     * @param leading arguments the call takes ahead of the function's own, such as a receiver
     */
    MethodDeclaration callMethod(
            WitFunction function, int skip, Expression callee, List<Expression> leading) {
        MethodDeclaration method = signature(function, skip).setPublic(true);
        List<Expression> arguments = new ArrayList<>(leading);
        arguments.addAll(callArguments(function, skip));

        BlockStmt body = new BlockStmt();
        Expression call = AstBuilders.call(callee, "apply", arguments);
        ResultType result = resultOf(function);
        if (result != null) {
            addResultHandling(body, function, call, result, skip);
        } else if (function.type().hasResult()) {
            body.addStatement(
                    new ReturnStmt(
                            types.fromComponent(
                                    AstBuilders.element(call, 0),
                                    function.type().result(),
                                    function.scope(),
                                    memberNames(function, skip))));
        } else {
            body.addStatement(call);
        }
        return method.setBody(body);
    }

    /**
     * Turns the {@code result} a component returned back into control flow, throwing the generated
     * exception for its error case and returning the ok payload otherwise.
     */
    private void addResultHandling(
            BlockStmt body, WitFunction function, Expression call, ResultType result, int skip) {
        String local = localName(OUTCOME, function, skip);
        body.addStatement(
                AstBuilders.declare(
                        unit.use(QualifiedTypes.VARIANT_VALUE),
                        local,
                        AstBuilders.cast(
                                unit.use(QualifiedTypes.VARIANT_VALUE),
                                AstBuilders.element(call, 0))));

        BlockStmt failed = new BlockStmt();
        failed.addStatement(
                new ThrowStmt(
                        result.hasError()
                                ? AstBuilders.construct(
                                        exceptionType(function),
                                        types.fromComponent(
                                                AstBuilders.call(new NameExpr(local), "value"),
                                                result.error(),
                                                function.scope(),
                                                memberNames(function, skip)))
                                : AstBuilders.construct(exceptionType(function))));
        body.addStatement(
                new IfStmt(
                        new UnaryExpr(
                                AstBuilders.call(
                                        AstBuilders.text(OK),
                                        "equals",
                                        AstBuilders.call(new NameExpr(local), "label")),
                                UnaryExpr.Operator.LOGICAL_COMPLEMENT),
                        failed,
                        null));
        if (result.hasOk()) {
            body.addStatement(
                    new ReturnStmt(
                            types.fromComponent(
                                    AstBuilders.call(new NameExpr(local), "value"),
                                    result.ok(),
                                    function.scope(),
                                    memberNames(function, skip))));
        }
    }

    /** The lambda satisfying an imported function, whose parameter carries the lowered call. */
    LambdaExpr lambda(Expression body) {
        return AstBuilders.lambda(ARGS, body);
    }

    /** One argument an imported call arrives with, before any conversion. */
    Expression argument(int index) {
        return AstBuilders.element(new NameExpr(ARGS), index);
    }

    /** The lambda handing an imported call to the embedder. */
    LambdaExpr importLambda(Expression receiver, WitFunction function, int skip) {
        return importLambda(receiver, Names.member(function.name()), function, skip);
    }

    /**
     * The lambda handing an imported call to the embedder, under a Java name of its own, which is
     * what a resource's static function needs since its Java name carries the resource too.
     */
    LambdaExpr importLambda(Expression receiver, String method, WitFunction function, int skip) {
        Expression call = AstBuilders.call(receiver, method, lambdaArguments(function, skip));
        ResultType result = resultOf(function);
        if (result != null) {
            return AstBuilders.lambda(ARGS, catchingError(function, call, result));
        }
        if (function.type().hasResult()) {
            Expression lifted = types.toComponent(call, function.type().result(), function.scope());
            return lambda(AstBuilders.objects(List.of(lifted)));
        }
        BlockStmt body = new BlockStmt();
        body.addStatement(call);
        body.addStatement(new ReturnStmt(AstBuilders.objects(List.of())));
        return AstBuilders.lambda(ARGS, body);
    }

    /**
     * The body of an imported call returning a {@code result}, which turns the generated exception
     * back into an error case.
     *
     * <p>Only that exception is caught, since catching every {@link RuntimeException} would hand
     * the guest a well formed error for what is a bug in the embedder's own code.
     */
    private BlockStmt catchingError(WitFunction function, Expression call, ResultType result) {
        BlockStmt succeeded = new BlockStmt();
        if (result.hasOk()) {
            Expression lifted = types.toComponent(call, result.ok(), function.scope());
            succeeded.addStatement(new ReturnStmt(caseOf(OK, lifted)));
        } else {
            succeeded.addStatement(call);
            succeeded.addStatement(new ReturnStmt(caseOf(OK, new NullLiteralExpr())));
        }

        BlockStmt failed = new BlockStmt();
        Expression payload =
                result.hasError()
                        ? types.toComponent(
                                AstBuilders.call(new NameExpr(CAUGHT), ERROR),
                                result.error(),
                                function.scope())
                        : new NullLiteralExpr();
        failed.addStatement(new ReturnStmt(caseOf(ERROR, payload)));

        TryStmt attempt = new TryStmt();
        attempt.setTryBlock(succeeded);
        attempt.setCatchClauses(
                NodeList.nodeList(
                        new CatchClause(new Parameter(exceptionType(function), CAUGHT), failed)));
        BlockStmt body = new BlockStmt();
        body.addStatement(attempt);
        return body;
    }

    /** {@code new Object[] {VariantValue.of("<label>", <payload>)}}. */
    private Expression caseOf(String label, Expression payload) {
        return AstBuilders.objects(
                List.of(
                        AstBuilders.call(
                                unit.useName(QualifiedTypes.VARIANT_VALUE),
                                "of",
                                AstBuilders.text(label),
                                payload)));
    }

    /** Arguments handed to the embedder, converted from what the ABI carries. */
    List<Expression> lambdaArguments(WitFunction function, int skip) {
        List<LabelValType> params = function.type().params();
        List<Expression> values = new ArrayList<>();
        for (int i = skip; i < params.size(); i++) {
            values.add(types.fromComponent(argument(i), params.get(i).valType(), function.scope()));
        }
        return values;
    }

    /** Arguments handed to the component, converted from the Java values a caller passes. */
    List<Expression> callArguments(WitFunction function, int skip) {
        List<Expression> values = new ArrayList<>();
        for (LabelValType param : parameters(function, skip)) {
            values.add(
                    types.toComponent(
                            new NameExpr(Names.member(param.label())),
                            param.valType(),
                            function.scope(),
                            memberNames(function, skip)));
        }
        return values;
    }

    /** A descriptor for a handle, which is carried by its value rather than by its Java class. */
    Expression resourceDescriptor() {
        return types.resourceDescriptor();
    }

    /**
     * The descriptors {@link run.endive.cm.runtime.ComponentFunction#typed} checks against the
     * component's own type, the result first.
     *
     * @param result the descriptor for a handle the function returns, or {@code null} for none
     * @param receiver the descriptor for a borrowed receiver, or {@code null} for none
     */
    List<Expression> descriptors(WitFunction function, Expression result, Expression receiver) {
        FuncType type = function.type();
        List<Expression> all = new ArrayList<>();
        all.add(
                result != null
                        ? result
                        : types.descriptor(
                                type.hasResult() ? type.result() : null, function.scope()));
        List<LabelValType> params = type.params();
        for (int i = 0; i < params.size(); i++) {
            all.add(
                    i == 0 && receiver != null
                            ? receiver
                            : types.descriptor(params.get(i).valType(), function.scope()));
        }
        return all;
    }

    /**
     * Rebuilds {@code function}'s type, for declaring it into a host instance.
     *
     * @param leading the type of a receiver or of a returned handle, neither of which the
     *     function's own type can name
     * @param declared the locals holding the compound types the enclosing instance declared
     */
    Expression funcType(
            WitFunction function, int skip, Expression leading, Map<Integer, String> declared) {
        FuncType type = function.type();
        Expression builder = AstBuilders.call(unit.useName(QualifiedTypes.FUNC_TYPE), "builder");
        if (skip > 0) {
            builder =
                    AstBuilders.call(
                            builder, "addParam", param(type.params().get(0).label(), leading));
        }
        for (LabelValType param : parameters(function, skip)) {
            Expression valType = types.valType(param.valType(), function.scope(), declared);
            builder = AstBuilders.call(builder, "addParam", param(param.label(), valType));
        }
        if (skip == 0 && leading != null) {
            builder = AstBuilders.call(builder, "withResult", leading);
        } else if (type.hasResult()) {
            builder =
                    AstBuilders.call(
                            builder,
                            "withResult",
                            types.valType(type.result(), function.scope(), declared));
        }
        return AstBuilders.call(builder, "build");
    }

    private Expression param(String label, Expression valType) {
        return types.labelValType(label, valType);
    }

    /**
     * A {@code result} carries its error case as a thrown exception, so the Java return type is
     * the ok payload, and {@code result} and {@code result<_, E>} return nothing at all even
     * though {@link FuncType#hasResult()} holds for both.
     */
    private Type returnType(WitFunction function) {
        FuncType type = function.type();
        if (!type.hasResult()) {
            return new VoidType();
        }
        ResultType result = resultOf(function);
        if (result == null) {
            return types.javaType(type.result(), function.scope());
        }
        return result.hasOk() ? types.javaType(result.ok(), function.scope()) : new VoidType();
    }

    /**
     * The {@code result} {@code function} returns, or {@code null} when it returns anything else.
     *
     * <p>A world's own function is refused, because the exception generated for a result belongs
     * to the Java package of the interface declaring it and a world declares no such package.
     */
    private ResultType resultOf(WitFunction function) {
        FuncType type = function.type();
        if (!type.hasResult()) {
            return null;
        }
        ResultType result = types.resultType(type.result(), function.scope());
        if (result != null && function.scope().owner() == null) {
            throw new BindgenException(
                    "a result on a function a world declares in its own right is not yet"
                            + " supported, since the generated exception has no interface to"
                            + " belong to");
        }
        return result;
    }

    private ClassOrInterfaceType exceptionType(WitFunction function) {
        return types.exceptionType(function.scope(), function.type().result().typeIdx());
    }

    /** A local the generated body names, kept clear of the function's own parameter names. */
    private static String localName(String preferred, WitFunction function, int skip) {
        return Names.free(preferred, memberNames(function, skip));
    }

    /** The Java names a function's own parameters occupy in the method written around them. */
    private static Set<String> memberNames(WitFunction function, int skip) {
        Set<String> taken = new HashSet<>();
        for (LabelValType param : parameters(function, skip)) {
            taken.add(Names.member(param.label()));
        }
        return taken;
    }

    private static List<LabelValType> parameters(WitFunction function, int skip) {
        List<LabelValType> params = function.type().params();
        return params.subList(skip, params.size());
    }
}
