package run.endive.cm.bindgen;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import run.endive.cm.types.BorrowType;
import run.endive.cm.types.Case;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.OptionType;
import run.endive.cm.types.OwnType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.RecordType;
import run.endive.cm.types.ResultType;
import run.endive.cm.types.TupleType;
import run.endive.cm.types.ValType;
import run.endive.cm.types.VariantType;

/**
 * Maps a component value type onto the Java type carrying it, and onto the expressions that rebuild
 * the value type and describe it to {@link run.endive.cm.runtime.ComponentFunction#typed}.
 *
 * <p>The Java side follows what the runtime's descriptors already accept, unsigned widening
 * included, so that a {@code u32} arrives as a {@code Long} rather than as an {@code int} it would
 * not fit.
 *
 * <p>Anything but a primitive is named by index, so resolving one needs the {@link WitScope}
 * against which it was written.
 */
final class WitTypes {

    /** The largest tuple the runtime carries, which is the highest {@code TupleN} it declares. */
    private static final int MAX_TUPLE_SIZE = 8;

    /** The name bound to a present option payload while it is converted. */
    private static final String SOME = "some";

    /** The name bound to a list element while it is converted. */
    private static final String ELEMENT = "element";

    /** The Java type of a result case that carries no payload. */
    private static final String VOID = "Void";

    private final GeneratedUnit unit;

    /** What the scope holding the conversion being written already names. */
    private Set<String> taken = Set.of();

    /** Reaches the {@code Handles} of the interface a scope belongs to, or {@code null}. */
    private Function<WitScope, Expression> handles = scope -> null;

    WitTypes(GeneratedUnit unit) {
        this.unit = unit;
    }

    /**
     * Names where the {@code Handles} of each interface can be reached, which is what converts a
     * resource handle. A conversion with no {@code Handles} in reach refuses a handle.
     *
     * @param handles gives the expression reaching the {@code Handles} of the interface owning a
     *     scope, or {@code null} when none is in reach
     */
    void withHandles(Function<WitScope, Expression> handles) {
        this.handles = handles;
    }

    /** The Java type carrying values of {@code valType}. */
    Type javaType(ValType valType, WitScope scope) {
        if (valType.primValType() != null) {
            return primitiveJavaType(valType.primValType().kind());
        }
        int index = valType.typeIdx();
        DefValType defined = definedAt(scope, index);
        if (defined instanceof PrimValType) {
            return primitiveJavaType(defined.kind());
        }
        WitScope inner = scope.declaringScope(index);
        switch (defined.kind()) {
            case LIST:
                ListType list = (ListType) defined;
                if (holdsBytes(list, inner)) {
                    return new ArrayType(PrimitiveType.byteType());
                }
                return AstBuilders.generic(
                        unit.use(QualifiedTypes.LIST), javaType(list.elementType(), inner));
            case OPTION:
                return javaType(optionPayload((OptionType) defined, inner), inner);
            case ENUM:
            case FLAGS:
            case VARIANT:
            case RECORD:
                return AstBuilders.type(nominalJavaType(scope, index));
            case TUPLE:
                List<Type> elements = new ArrayList<>();
                for (ValType element : ((TupleType) defined).elementTypes()) {
                    elements.add(javaType(element, inner));
                }
                return AstBuilders.generic(
                        unit.use(tupleClass(elements.size())), elements.toArray(new Type[0]));
            case OWN:
            case BORROW:
                return AstBuilders.type(resourceJavaType(defined, inner));
            case RESULT:
                ResultType result = (ResultType) defined;
                return AstBuilders.generic(
                        unit.use(QualifiedTypes.RESULT),
                        caseJavaType(result.ok(), inner),
                        caseJavaType(result.error(), inner));
            default:
                throw unsupported(defined.kind().name());
        }
    }

    /** The Java type carrying a result case's payload, which is {@code Void} when there is none. */
    private Type caseJavaType(ValType payload, WitScope scope) {
        return payload == null ? AstBuilders.type(VOID) : javaType(payload, scope);
    }

    /**
     * The payload of an option, refused when another option is reached through it.
     *
     * <p>A nullable {@code T} is what an {@code option<T>} becomes, so {@code some(none)} and
     * {@code none} would both be {@code null} and neither could be told from the other.
     */
    private ValType optionPayload(OptionType option, WitScope scope) {
        ValType payload = option.valType();
        if (payload.primValType() == null
                && definedAt(scope, payload.typeIdx()).kind() == DefValType.Kind.OPTION) {
            throw new BindgenException(
                    "option<option<T>> is not supported, because a nullable option cannot tell"
                            + " some(none) from none");
        }
        return payload;
    }

    /**
     * The {@code result} {@code valType} names, or {@code null} when it names anything else.
     *
     * <p>A function's own result is control flow rather than a value, so a caller resolves one here
     * instead of asking for a Java type for it.
     */
    ResultType resultType(ValType valType, WitScope scope) {
        if (valType == null || valType.primValType() != null) {
            return null;
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        return defined.kind() == DefValType.Kind.RESULT ? (ResultType) defined : null;
    }

    /**
     * The exception generated for the result at {@code index}, by its simple Java name.
     *
     * <p>The error payload's own name is what an embedder recognises, so it is used whenever the
     * payload has one. An anonymous result is shared by every function of the same signature, so
     * it falls back to the interface and the type's index instead.
     */
    static String exceptionName(WitScope scope, int index) {
        ValType error = ((ResultType) definedAt(scope, index)).error();
        String named =
                error == null || error.primValType() != null ? null : scope.nameAt(error.typeIdx());
        return named != null
                ? Names.type(named) + "Exception"
                : Names.type(scope.owner()) + "Result" + index + "Exception";
    }

    /** The exception generated for the result at {@code index}, as this unit has to write it. */
    ClassOrInterfaceType exceptionType(WitScope scope, int index) {
        return AstBuilders.type(qualify(scope, exceptionName(scope, index)));
    }

    /**
     * The types a host instance has to be told about, since a function type names one by index.
     * A named primitive such as {@code type instant = u64} is one of them, since a component using
     * it aliases it by name.
     *
     * <p>A kind belongs here once {@link #defValType} can rebuild it, and not before. Declaring a
     * kind ahead of that refuses an interface for merely declaring the type, whether or not
     * anything uses it. An allowlist also keeps out the {@code own} and {@code borrow} a resource
     * contributes to the same space, neither of which is a type to declare.
     */
    static boolean isDeclared(DefValType defined) {
        if (defined instanceof PrimValType) {
            return true;
        }
        switch (defined.kind()) {
            case LIST:
            case ENUM:
            case FLAGS:
            case TUPLE:
            case VARIANT:
            case OPTION:
            case RECORD:
            case RESULT:
                return true;
            default:
                return false;
        }
    }

    /** The value types {@code defined} names, which have to be declared before it can be. */
    static List<ValType> references(DefValType defined) {
        List<ValType> found = new ArrayList<>();
        switch (defined.kind()) {
            case LIST:
                found.add(((ListType) defined).elementType());
                break;
            case OPTION:
                found.add(((OptionType) defined).valType());
                break;
            case TUPLE:
                found.addAll(((TupleType) defined).elementTypes());
                break;
            case RECORD:
                for (LabelValType field : ((RecordType) defined).fields()) {
                    found.add(field.valType());
                }
                break;
            case VARIANT:
                for (Case declaredCase : ((VariantType) defined).cases()) {
                    if (declaredCase.hasValType()) {
                        found.add(declaredCase.valType());
                    }
                }
                break;
            case RESULT:
                ResultType result = (ResultType) defined;
                if (result.hasOk()) {
                    found.add(result.ok());
                }
                if (result.hasError()) {
                    found.add(result.error());
                }
                break;
            default:
                break;
        }
        return found;
    }

    /**
     * Whether a Java value of this kind differs from what the ABI carries, so that the generated
     * code has to convert at the boundary rather than pass it through.
     */
    private static boolean convertsAtBoundary(DefValType.Kind kind) {
        switch (kind) {
            case ENUM:
            case FLAGS:
            case TUPLE:
            case VARIANT:
            case OPTION:
            case RESULT:
            case RECORD:
            case OWN:
            case BORROW:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether values of {@code valType} need converting between Java and what the ABI carries.
     *
     * <p>A container converts whenever what it holds does, so that a nested value is converted
     * rather than passed through in the enclosing type's own representation.
     */
    private boolean needsConversion(ValType valType, WitScope scope) {
        if (valType == null || valType.primValType() != null) {
            return false;
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        if (defined.kind() == DefValType.Kind.LIST) {
            ListType list = (ListType) defined;
            WitScope inner = scope.declaringScope(valType.typeIdx());
            return holdsBytes(list, inner) || needsConversion(list.elementType(), inner);
        }
        return convertsAtBoundary(defined.kind());
    }

    /**
     * Whether {@code list} is a {@code list<u8>}, which Java carries as a {@code byte[]} so that it
     * crosses the boundary in one copy. A named {@code u8} counts as one.
     */
    private boolean holdsBytes(ListType list, WitScope scope) {
        return primitiveKind(list.elementType(), scope) == DefValType.Kind.U8;
    }

    /**
     * The primitive {@code valType} is, whether written inline or named as in {@code type instant =
     * u64}, or {@code null} when it is not a primitive.
     */
    DefValType.Kind primitiveKind(ValType valType, WitScope scope) {
        if (valType.primValType() != null) {
            return valType.primValType().kind();
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        return defined instanceof PrimValType ? defined.kind() : null;
    }

    /**
     * The canonical size and alignment of {@code valType} when it is a number, a {@code bool},
     * or a record made only of those, or {@code null} when storing it needs more than fixed writes.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#alignment">Alignment</a>
     */
    int[] flatLayout(ValType valType, WitScope scope) {
        DefValType.Kind primitive = primitiveKind(valType, scope);
        if (primitive != null) {
            int size = flatSize(primitive);
            return size == 0 ? null : new int[] {size, size};
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        if (defined.kind() != DefValType.Kind.RECORD) {
            return null;
        }
        return flatLayout((RecordType) defined, scope.declaringScope(valType.typeIdx()));
    }

    /** The canonical size and alignment of {@code record}, or {@code null} when it is not flat. */
    int[] flatLayout(RecordType record, WitScope scope) {
        int offset = 0;
        int alignment = 1;
        for (LabelValType field : record.fields()) {
            int[] layout = flatLayout(field.valType(), scope);
            if (layout == null) {
                return null;
            }
            offset = DefValType.alignTo(offset, layout[1]) + layout[0];
            alignment = Math.max(alignment, layout[1]);
        }
        return new int[] {DefValType.alignTo(offset, alignment), alignment};
    }

    /** The size of a primitive stored by a fixed write, or zero for one that is not. */
    private static int flatSize(DefValType.Kind kind) {
        switch (kind) {
            case BOOL:
            case S8:
            case U8:
                return 1;
            case S16:
            case U16:
                return 2;
            case S32:
            case U32:
            case F32:
                return 4;
            case S64:
            case U64:
            case F64:
                return 8;
            default:
                return 0;
        }
    }

    /** Whether {@code valType} names a {@code list<u8>}. */
    boolean isBytes(ValType valType, WitScope scope) {
        if (valType == null || valType.primValType() != null) {
            return false;
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        return defined.kind() == DefValType.Kind.LIST
                && holdsBytes((ListType) defined, scope.declaringScope(valType.typeIdx()));
    }

    /**
     * Turns a Java value into what the ABI carries, evaluating {@code value} once so that a call
     * may be converted in place.
     */
    Expression toComponent(Expression value, ValType valType, WitScope scope) {
        return toComponent(value, valType, scope, Set.of());
    }

    /**
     * @param taken the Java names already in scope where this conversion is written, which its
     *     lambda parameters must avoid
     */
    Expression toComponent(Expression value, ValType valType, WitScope scope, Set<String> taken) {
        this.taken = taken;
        return lower(value, valType, scope, 0);
    }

    /**
     * @param depth how many conversion lambdas enclose this one, which is what keeps their
     *     parameters from shadowing each other when a container holds another
     */
    private Expression lower(Expression value, ValType valType, WitScope scope, int depth) {
        if (!needsConversion(valType, scope)) {
            return value;
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        WitScope inner = scope.declaringScope(valType.typeIdx());
        switch (defined.kind()) {
            case OPTION:
                return lowerOption(value, (OptionType) defined, inner, depth);
            case LIST:
                if (holdsBytes((ListType) defined, inner)) {
                    return value;
                }
                NameExpr element = new NameExpr(elementName(depth));
                Expression lowered =
                        lower(element, ((ListType) defined).elementType(), inner, depth + 1);
                // A list whose elements the ABI takes as they are needs no copy.
                return lowered == element ? value : mapElements(value, lowered, depth);
            case RECORD:
                List<Expression> recordHandles = handlesArguments(valType, scope);
                // A record without handles is a RecordValue, which the ABI reads by position.
                return recordHandles.isEmpty()
                        ? value
                        : AstBuilders.call(value, "toComponent", recordHandles);
            case TUPLE:
                return lowerTuple(value, (TupleType) defined, inner, depth);
            case RESULT:
                return lowerResult(value, (ResultType) defined, inner, depth);
            case OWN:
                return AstBuilders.call(
                        handlesOf(defined, inner),
                        Names.ownHandle(resourceName(defined, inner)),
                        value);
            case BORROW:
                throw unsupported("a borrowed handle handed to the guest");
            default:
                return AstBuilders.call(value, "toComponent", handlesArguments(valType, scope));
        }
    }

    /** Turns what the ABI carries into a Java value, evaluating {@code value} once. */
    Expression fromComponent(Expression value, ValType valType, WitScope scope) {
        return fromComponent(value, valType, scope, Set.of());
    }

    /**
     * @param taken the Java names already in scope where this conversion is written
     */
    Expression fromComponent(Expression value, ValType valType, WitScope scope, Set<String> taken) {
        this.taken = taken;
        return lift(value, valType, scope, 0);
    }

    /**
     * @param depth how many conversion lambdas enclose this one
     */
    private Expression lift(Expression value, ValType valType, WitScope scope, int depth) {
        if (isBytes(valType, scope)) {
            return AstBuilders.call(unit.useName(QualifiedTypes.BYTE_LIST), "toBytes", value);
        }
        if (needsConversion(valType, scope)) {
            DefValType defined = definedAt(scope, valType.typeIdx());
            WitScope inner = scope.declaringScope(valType.typeIdx());
            switch (defined.kind()) {
                case TUPLE:
                    return liftTuple(value, (TupleType) defined, inner, depth);
                case OPTION:
                    return liftOption(value, (OptionType) defined, inner, depth);
                case RESULT:
                    return liftResult(value, (ResultType) defined, inner, depth);
                case LIST:
                    return liftElements(value, ((ListType) defined).elementType(), inner, depth);
                case BORROW:
                    return AstBuilders.call(
                            handlesOf(defined, inner),
                            Names.handleGetter(resourceName(defined, inner)),
                            AstBuilders.cast(unit.use(QualifiedTypes.RESOURCE_VALUE), value));
                case OWN:
                    return AstBuilders.call(
                            handlesOf(defined, inner),
                            Names.handleTaker(resourceName(defined, inner)),
                            AstBuilders.cast(unit.use(QualifiedTypes.RESOURCE_VALUE), value));
                default:
                    List<Expression> arguments = new ArrayList<>();
                    arguments.add(value);
                    arguments.addAll(handlesArguments(valType, scope));
                    return AstBuilders.call(
                            AstBuilders.name(nominalJavaType(scope, valType.typeIdx())),
                            "fromComponent",
                            arguments);
            }
        }
        Type target = javaType(valType, scope);
        if (isGeneric(target)) {
            unit.markUnchecked();
        }
        return AstBuilders.cast(target, value);
    }

    /**
     * A tuple is anonymous, so there is no generated class to call. Each element is named by its
     * class when every element can be, which needs no unchecked cast. Otherwise each element is
     * lifted by a conversion of its own.
     */
    private Expression liftTuple(Expression value, TupleType tuple, WitScope scope, int depth) {
        List<ValType> elements = tuple.elementTypes();
        List<Expression> arguments = new ArrayList<>();
        arguments.add(value);
        if (elements.stream().allMatch(element -> namedByClass(element, scope))) {
            for (ValType element : elements) {
                arguments.add(AstBuilders.classLiteral(javaType(element, scope)));
            }
        } else {
            for (ValType element : elements) {
                Expression lifted =
                        lift(new NameExpr(elementName(depth)), element, scope, depth + 1);
                arguments.add(AstBuilders.lambda(elementName(depth), lifted));
            }
        }
        return AstBuilders.call(
                unit.useName(tupleClass(elements.size())), "fromComponent", arguments);
    }

    /** Whether a tuple element lifts by a checked cast to its class alone. */
    private boolean namedByClass(ValType element, WitScope scope) {
        return !needsConversion(element, scope) && !isGeneric(javaType(element, scope));
    }

    /**
     * A tuple as the map the ABI carries. Each element is lowered by a conversion of its own once
     * any element needs one.
     */
    private Expression lowerTuple(Expression value, TupleType tuple, WitScope scope, int depth) {
        List<Expression> converters = new ArrayList<>();
        boolean converts = false;
        for (ValType element : tuple.elementTypes()) {
            NameExpr bound = new NameExpr(elementName(depth));
            Expression lowered = lower(bound, element, scope, depth + 1);
            converts |= lowered != bound;
            converters.add(AstBuilders.lambda(elementName(depth), lowered));
        }
        return AstBuilders.call(value, "toComponent", converts ? converters : List.of());
    }

    /** The runtime class carrying a tuple of {@code size} elements. */
    private static String tupleClass(int size) {
        if (size < 2 || size > MAX_TUPLE_SIZE) {
            throw unsupported("a tuple of " + size + " elements");
        }
        return QualifiedTypes.TUPLE + size;
    }

    /**
     * A result reached as a value, lifted the way a tuple is. Each payload is named by its class
     * when both can be, and lifted by a conversion of its own otherwise. A case without a payload
     * lifts as a {@code Void}.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#despecialization">Canonical ABI despecialization</a>
     */
    private Expression liftResult(Expression value, ResultType result, WitScope scope, int depth) {
        List<ValType> payloads = Arrays.asList(result.ok(), result.error());
        List<Expression> arguments = new ArrayList<>();
        arguments.add(value);
        if (payloads.stream()
                .allMatch(payload -> payload == null || namedByClass(payload, scope))) {
            for (ValType payload : payloads) {
                arguments.add(AstBuilders.classLiteral(caseJavaType(payload, scope)));
            }
        } else {
            for (ValType payload : payloads) {
                NameExpr bound = new NameExpr(elementName(depth));
                Expression lifted =
                        payload == null
                                ? AstBuilders.cast(AstBuilders.type(VOID), bound)
                                : lift(bound, payload, scope, depth + 1);
                arguments.add(AstBuilders.lambda(elementName(depth), lifted));
            }
        }
        return AstBuilders.call(unit.useName(QualifiedTypes.RESULT), "fromComponent", arguments);
    }

    /**
     * A result as the variant the ABI carries. Each payload is lowered by a conversion of its own
     * once either needs one.
     */
    private Expression lowerResult(Expression value, ResultType result, WitScope scope, int depth) {
        List<Expression> converters = new ArrayList<>();
        boolean converts = false;
        for (ValType payload : Arrays.asList(result.ok(), result.error())) {
            NameExpr bound = new NameExpr(elementName(depth));
            Expression lowered = payload == null ? bound : lower(bound, payload, scope, depth + 1);
            converts |= lowered != bound;
            converters.add(AstBuilders.lambda(elementName(depth), lowered));
        }
        return AstBuilders.call(value, "toComponent", converts ? converters : List.of());
    }

    /**
     * A nullable Java value as the {@code none} or {@code some} variant the ABI carries, which is
     * never a bare {@code null}, so that a nested option stays distinguishable from no payload.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#specialized-value-types">Explainer.md, specialized value types</a>
     */
    private Expression lowerOption(Expression value, OptionType option, WitScope scope, int depth) {
        String bound = someName(depth);
        Expression some =
                AstBuilders.call(
                        unit.useName(QualifiedTypes.VARIANT_VALUE),
                        "of",
                        AstBuilders.text("some"),
                        lower(new NameExpr(bound), optionPayload(option, scope), scope, depth + 1));
        Expression none =
                AstBuilders.call(
                        unit.useName(QualifiedTypes.VARIANT_VALUE),
                        "of",
                        AstBuilders.text("none"),
                        new NullLiteralExpr());
        Expression present =
                AstBuilders.call(unit.useName(QualifiedTypes.OPTIONAL), "ofNullable", value);
        return AstBuilders.call(
                AstBuilders.call(present, "map", AstBuilders.lambda(bound, some)), "orElse", none);
    }

    /** Lifts the variant an option is carried as, giving back a nullable Java value. */
    private Expression liftOption(Expression value, OptionType option, WitScope scope, int depth) {
        ValType payload = optionPayload(option, scope);
        Expression carried =
                AstBuilders.call(
                        AstBuilders.cast(unit.use(QualifiedTypes.VARIANT_VALUE), value), "value");
        if (!needsConversion(payload, scope)) {
            return lift(carried, payload, scope, depth);
        }
        String bound = someName(depth);
        Expression present =
                AstBuilders.call(unit.useName(QualifiedTypes.OPTIONAL), "ofNullable", carried);
        Expression mapped =
                AstBuilders.call(
                        present,
                        "map",
                        AstBuilders.lambda(
                                bound, lift(new NameExpr(bound), payload, scope, depth + 1)));
        return AstBuilders.call(mapped, "orElse", new NullLiteralExpr());
    }

    /** A list whose elements the ABI carries differently, converted one element at a time. */
    private Expression liftElements(Expression value, ValType element, WitScope scope, int depth) {
        unit.markUnchecked();
        Expression carried =
                AstBuilders.cast(
                        AstBuilders.generic(
                                unit.use(QualifiedTypes.LIST), AstBuilders.type("Object")),
                        value);
        return mapElements(
                carried, lift(new NameExpr(elementName(depth)), element, scope, depth + 1), depth);
    }

    /**
     * A conversion lambda's parameter, kept apart from the lambdas enclosing it and from whatever
     * the surrounding Java scope already names.
     */
    private String elementName(int depth) {
        return Names.free(depth == 0 ? ELEMENT : ELEMENT + depth, taken);
    }

    private String someName(int depth) {
        return Names.free(depth == 0 ? SOME : SOME + depth, taken);
    }

    /** {@code value.stream().map(element -> converted).collect(Collectors.toList())} */
    private Expression mapElements(Expression value, Expression converted, int depth) {
        Expression mapped =
                AstBuilders.call(
                        AstBuilders.call(value, "stream"),
                        "map",
                        AstBuilders.lambda(elementName(depth), converted));
        return AstBuilders.call(
                mapped,
                "collect",
                AstBuilders.call(unit.useName(QualifiedTypes.COLLECTORS), "toList"));
    }

    /** A cast to a generic type is the one Java cannot check, so it is what needs suppressing. */
    private static boolean isGeneric(Type type) {
        return type instanceof ClassOrInterfaceType
                && ((ClassOrInterfaceType) type).getTypeArguments().isPresent();
    }

    /**
     * Rebuilds {@code valType}, either as a primitive written inline or as the local holding a
     * compound type that was declared already.
     */
    Expression valType(ValType valType, WitScope scope, Map<Integer, Expression> declared) {
        if (valType.primValType() != null) {
            Expression kind =
                    AstBuilders.field(
                            unit.useName(QualifiedTypes.PRIM_VAL_TYPE),
                            valType.primValType().kind().name());
            Expression builder = AstBuilders.call(unit.useName(QualifiedTypes.VAL_TYPE), "builder");
            return AstBuilders.call(AstBuilders.call(builder, "withPrimValType", kind), "build");
        }
        Expression local = declared.get(valType.typeIdx());
        if (local == null) {
            throw undeclared(scope, valType.typeIdx());
        }
        return local.clone();
    }

    /** Rebuilds a declared type for declaring it into a host instance. */
    Expression defValType(DefValType defined, WitScope scope, Map<Integer, Expression> declared) {
        if (defined instanceof PrimValType) {
            return typeOf(
                    AstBuilders.field(
                            unit.useName(QualifiedTypes.PRIM_VAL_TYPE), defined.kind().name()));
        }
        switch (defined.kind()) {
            case LIST:
                ListType list = (ListType) defined;
                Expression element = valType(list.elementType(), scope, declared);
                Expression listBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.LIST_TYPE), "builder");
                return typeOf(
                        AstBuilders.call(
                                AstBuilders.call(listBuilder, "withElementType", element),
                                "build"));
            case ENUM:
                Expression enumBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.ENUM_TYPE), "builder");
                for (String label : ((EnumType) defined).labels()) {
                    enumBuilder =
                            AstBuilders.call(enumBuilder, "addLabel", AstBuilders.text(label));
                }
                return typeOf(AstBuilders.call(enumBuilder, "build"));
            case FLAGS:
                Expression flagsBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.FLAGS_TYPE), "builder");
                for (String label : ((FlagsType) defined).labels()) {
                    flagsBuilder =
                            AstBuilders.call(flagsBuilder, "addLabel", AstBuilders.text(label));
                }
                return typeOf(AstBuilders.call(flagsBuilder, "build"));
            case TUPLE:
                Expression tupleBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.TUPLE_TYPE), "builder");
                for (ValType elementType : ((TupleType) defined).elementTypes()) {
                    tupleBuilder =
                            AstBuilders.call(
                                    tupleBuilder,
                                    "addElementType",
                                    valType(elementType, scope, declared));
                }
                return typeOf(AstBuilders.call(tupleBuilder, "build"));
            case VARIANT:
                Expression variantBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.VARIANT_TYPE), "builder");
                for (Case declaredCase : ((VariantType) defined).cases()) {
                    variantBuilder =
                            AstBuilders.call(
                                    variantBuilder,
                                    "addCase",
                                    caseOf(declaredCase, scope, declared));
                }
                return typeOf(AstBuilders.call(variantBuilder, "build"));
            case OPTION:
                // The specialized form, since linking compares kinds without despecializing.
                Expression payload = valType(((OptionType) defined).valType(), scope, declared);
                Expression optionBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.OPTION_TYPE), "builder");
                return typeOf(
                        AstBuilders.call(
                                AstBuilders.call(optionBuilder, "withValType", payload), "build"));
            case RECORD:
                RecordType record = (RecordType) defined;
                Expression recordBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.RECORD_TYPE), "builder");
                for (LabelValType field : record.fields()) {
                    recordBuilder =
                            AstBuilders.call(
                                    recordBuilder,
                                    "addField",
                                    labelValType(
                                            field.label(),
                                            valType(field.valType(), scope, declared)));
                }
                return typeOf(AstBuilders.call(recordBuilder, "build"));
            case RESULT:
                ResultType result = (ResultType) defined;
                Expression resultBuilder =
                        AstBuilders.call(unit.useName(QualifiedTypes.RESULT_TYPE), "builder");
                if (result.hasOk()) {
                    resultBuilder =
                            AstBuilders.call(
                                    resultBuilder, "withOk", valType(result.ok(), scope, declared));
                }
                if (result.hasError()) {
                    resultBuilder =
                            AstBuilders.call(
                                    resultBuilder,
                                    "withError",
                                    valType(result.error(), scope, declared));
                }
                return typeOf(AstBuilders.call(resultBuilder, "build"));
            default:
                throw unsupported(defined.kind().name());
        }
    }

    /** {@code LabelValType.builder().withLabel(<label>).withValType(<valType>).build()}. */
    Expression labelValType(String label, Expression valType) {
        Expression builder =
                AstBuilders.call(unit.useName(QualifiedTypes.LABEL_VAL_TYPE), "builder");
        builder = AstBuilders.call(builder, "withLabel", AstBuilders.text(label));
        return AstBuilders.call(AstBuilders.call(builder, "withValType", valType), "build");
    }

    /**
     * The interfaces whose {@code Handles} converting a value of {@code valType} needs, in the
     * order a generated conversion takes them. A conversion reaches every handle a type carries,
     * through lists, options, fields and cases alike, and each converts through the {@code
     * Handles} of the interface declaring its resource.
     */
    List<WitScope> handleScopes(ValType valType, WitScope scope) {
        List<WitScope> found = new ArrayList<>();
        collectHandleScopes(valType, scope, found);
        return found;
    }

    /**
     * The same, for a type {@code scope} defines, which is what a generated type's own conversions
     * take as parameters.
     */
    List<WitScope> handleScopes(DefValType defined, WitScope scope) {
        List<WitScope> found = new ArrayList<>();
        collectHandleScopes(defined, scope, found);
        return found;
    }

    private void collectHandleScopes(ValType valType, WitScope scope, List<WitScope> found) {
        if (valType == null || valType.primValType() != null) {
            return;
        }
        int index = valType.typeIdx();
        collectHandleScopes(definedAt(scope, index), scope.declaringScope(index), found);
    }

    private void collectHandleScopes(DefValType defined, WitScope scope, List<WitScope> found) {
        if (defined.kind() == DefValType.Kind.OWN || defined.kind() == DefValType.Kind.BORROW) {
            WitScope declaring = scope.declaringScope(resourceIndex(defined));
            if (found.stream().noneMatch(s -> s == declaring)) {
                found.add(declaring);
            }
            return;
        }
        for (ValType reference : references(defined)) {
            collectHandleScopes(reference, scope, found);
        }
    }

    /** The type of the {@code Handles} the interface owning {@code scope} generates. */
    ClassOrInterfaceType handlesType(WitScope scope) {
        return AstBuilders.type(qualify(scope, InterfaceGenerator.HANDLES));
    }

    /**
     * The {@code Handles} a generated type's conversion takes, as this conversion reaches them.
     * Only an imported interface's bindings reach any, so a type carrying a handle anywhere else
     * is refused.
     */
    private List<Expression> handlesArguments(ValType valType, WitScope scope) {
        List<Expression> arguments = new ArrayList<>();
        for (WitScope declaring : handleScopes(valType, scope)) {
            Expression reached = handles.apply(declaring);
            if (reached == null) {
                WitScope named = scope.declaringScope(valType.typeIdx());
                throw new BindgenException(
                        "type \""
                                + named.nameAt(scope.declaringIndex(valType.typeIdx()))
                                + "\" carries a resource handle, which only an imported"
                                + " interface's bindings can convert so far");
            }
            arguments.add(reached);
        }
        return arguments;
    }

    /** Describes {@code valType} to a typed function or void when there is no type. */
    Expression descriptor(ValType valType, WitScope scope) {
        if (valType == null) {
            return instanceOf(QualifiedTypes.VOID_DESCRIPTOR);
        }
        if (valType.primValType() != null) {
            return primitiveDescriptor(valType.primValType());
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        if (defined instanceof PrimValType) {
            return primitiveDescriptor(defined);
        }
        switch (defined.kind()) {
            case LIST:
                if (holdsBytes((ListType) defined, scope.declaringScope(valType.typeIdx()))) {
                    return AstBuilders.call(unit.useName(QualifiedTypes.LIST_DESCRIPTOR), "bytes");
                }
                return instanceOf(QualifiedTypes.LIST_DESCRIPTOR);
            case ENUM:
            case VARIANT:
            case OPTION:
            case RESULT:
                // What crosses is the variant these despecialize to, not the Java value the
                // embedder holds, because the generated code converts before it calls.
                return instanceOf(QualifiedTypes.VARIANT_DESCRIPTOR);
            case FLAGS:
            case TUPLE:
            case RECORD:
                // All cross as the map the ABI carries, which RecordHostTypeDescriptor names.
                return instanceOf(QualifiedTypes.RECORD_DESCRIPTOR);
            default:
                throw unsupported(defined.kind().name());
        }
    }

    /** A descriptor for a handle, which is carried by its value rather than by its Java class. */
    Expression resourceDescriptor() {
        return instanceOf(QualifiedTypes.RESOURCE_DESCRIPTOR);
    }

    /**
     * A type is written by its simple name inside the package that declares it, and whole
     * elsewhere, since the generated units do not import from one another.
     */
    private String reference(WitScope scope, String witName) {
        return qualify(scope, Names.type(witName));
    }

    /** {@code simpleName} as this unit has to write it, qualified when it is another package. */
    private String qualify(WitScope scope, String simpleName) {
        String declaredIn = scope.javaPackage();
        return declaredIn == null || declaredIn.equals(unit.packageName())
                ? simpleName
                : declaredIn + "." + simpleName;
    }

    /** Rebuilds one case of a variant, whose payload type is written only when it has one. */
    private Expression caseOf(
            Case declaredCase, WitScope scope, Map<Integer, Expression> declared) {
        Expression builder = AstBuilders.call(unit.useName(QualifiedTypes.CASE), "builder");
        builder = AstBuilders.call(builder, "withLabel", AstBuilders.text(declaredCase.label()));
        if (declaredCase.hasValType()) {
            builder =
                    AstBuilders.call(
                            builder,
                            "withValType",
                            valType(declaredCase.valType(), scope, declared));
        }
        return AstBuilders.call(builder, "build");
    }

    private Expression typeOf(Expression built) {
        return AstBuilders.call(unit.useName(QualifiedTypes.TYPE), "of", built);
    }

    private Expression instanceOf(String descriptor) {
        return AstBuilders.call(unit.useName(descriptor), "instance");
    }

    /**
     * The {@code Handles} converting a handle to the resource {@code handle} names, which belongs
     * to the interface declaring that resource.
     */
    private Expression handlesOf(DefValType handle, WitScope scope) {
        int resource = resourceIndex(handle);
        Expression reached = handles.apply(scope.declaringScope(resource));
        if (reached == null) {
            throw unsupported(handle.kind().name());
        }
        return reached;
    }

    /** The interface type the embedder implements for the resource {@code handle} names. */
    private String resourceJavaType(DefValType handle, WitScope scope) {
        return qualify(
                scope.declaringScope(resourceIndex(handle)),
                Names.type(resourceName(handle, scope)));
    }

    /** The WIT name of the resource an {@code own} or a {@code borrow} names. */
    private static String resourceName(DefValType handle, WitScope scope) {
        int resource = resourceIndex(handle);
        String name = scope.declaringScope(resource).nameAt(scope.declaringIndex(resource));
        if (name == null) {
            throw unsupported("an unnamed resource");
        }
        return name;
    }

    private static int resourceIndex(DefValType handle) {
        return handle instanceof OwnType
                ? ((OwnType) handle).typeIdx()
                : ((BorrowType) handle).typeIdx();
    }

    /**
     * The Java type generated for a nominal type, which is named by the export declaring it.
     *
     * <p>Only the nominal kinds come through here. A structural type such as a list or an option
     * is written anonymously and has no name to find. A type used from another interface is named
     * and packaged by the interface declaring it.
     */
    private String nominalJavaType(WitScope scope, int index) {
        WitScope declaring = scope.declaringScope(index);
        String name = declaring.nameAt(scope.declaringIndex(index));
        if (name == null) {
            throw unsupported("an unnamed " + definedAt(scope, index).kind().name());
        }
        return reference(declaring, name);
    }

    private Expression primitiveDescriptor(DefValType primitive) {
        return AstBuilders.call(
                unit.useName(QualifiedTypes.PRIMITIVE_DESCRIPTOR),
                "forClass",
                AstBuilders.classLiteral(primitiveJavaType(primitive.kind())));
    }

    private ClassOrInterfaceType primitiveJavaType(DefValType.Kind kind) {
        switch (kind) {
            case BOOL:
                return AstBuilders.type("Boolean");
            case S8:
                return AstBuilders.type("Byte");
            case U8:
            case S16:
                return AstBuilders.type("Short");
            case U16:
            case S32:
                return AstBuilders.type("Integer");
            case U32:
            case S64:
                return AstBuilders.type("Long");
            case U64:
                // Exceeds a signed long, so the runtime carries it as a BigInteger.
                return unit.use(QualifiedTypes.BIG_INTEGER);
            case F32:
                return AstBuilders.type("Float");
            case F64:
                return AstBuilders.type("Double");
            case CHAR:
                return unit.use(QualifiedTypes.CHAR_VALUE);
            case STRING:
                return AstBuilders.type("String");
            default:
                throw unsupported(kind.name().toLowerCase());
        }
    }

    private static DefValType definedAt(WitScope scope, int index) {
        run.endive.cm.types.Type type = scope.at(index);
        if (type == null || type.defValType() == null) {
            throw unsupported("a named type");
        }
        return type.defValType();
    }

    /**
     * A type a function names but that the enclosing instance never declared, which is what a kind
     * outside {@link #isDeclared} amounts to. Naming that kind is what tells a reader which WIT
     * feature is missing.
     */
    private static BindgenException undeclared(WitScope scope, int index) {
        run.endive.cm.types.Type type = scope.at(index);
        if (type == null || type.defValType() == null) {
            return unsupported("a type that was never declared");
        }
        return unsupported(type.defValType().kind().name());
    }

    private static BindgenException unsupported(String described) {
        return new BindgenException(described.toLowerCase() + " is not yet supported");
    }
}
