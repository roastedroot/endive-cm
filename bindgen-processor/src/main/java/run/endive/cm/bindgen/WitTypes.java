package run.endive.cm.bindgen;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.endive.cm.types.Case;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.OptionType;
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

    private final GeneratedUnit unit;

    WitTypes(GeneratedUnit unit) {
        this.unit = unit;
    }

    /** The Java type carrying values of {@code valType}. */
    Type javaType(ValType valType, WitScope scope) {
        if (valType.primValType() != null) {
            return primitiveJavaType(valType.primValType().kind());
        }
        int index = valType.typeIdx();
        DefValType defined = definedAt(scope, index);
        switch (defined.kind()) {
            case LIST:
                ListType list = (ListType) defined;
                return AstBuilders.generic(
                        unit.use(QualifiedTypes.LIST), javaType(list.elementType(), scope));
            case OPTION:
                return javaType(optionPayload((OptionType) defined, scope), scope);
            case ENUM:
            case FLAGS:
            case VARIANT:
            case RECORD:
                return AstBuilders.type(nominalJavaType(scope, index));
            case TUPLE:
                List<Type> elements = new ArrayList<>();
                for (ValType element : ((TupleType) defined).elementTypes()) {
                    elements.add(elementJavaType(element, scope));
                }
                return AstBuilders.generic(
                        unit.use(tupleClass(elements.size())), elements.toArray(new Type[0]));
            case RESULT:
                throw resultOutOfPlace();
            default:
                throw unsupported(defined.kind().name());
        }
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
     * <p>A result is control flow rather than a value, so a caller resolves one here instead of
     * asking for a Java type for it.
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
     *
     * <p>A kind belongs here once {@link #defValType} can rebuild it, and not before. Declaring a
     * kind ahead of that refuses an interface for merely declaring the type, whether or not
     * anything uses it. An allowlist also keeps out the {@code own} and {@code borrow} a resource
     * contributes to the same space, neither of which is a type to declare.
     */
    static boolean isCompound(DefValType.Kind kind) {
        switch (kind) {
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
            case RECORD:
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
        if (defined.kind() == DefValType.Kind.RESULT) {
            throw resultOutOfPlace();
        }
        if (defined.kind() == DefValType.Kind.LIST) {
            return needsConversion(((ListType) defined).elementType(), scope);
        }
        return convertsAtBoundary(defined.kind());
    }

    /**
     * Turns a Java value into what the ABI carries, evaluating {@code value} once so that a call
     * may be converted in place.
     */
    Expression toComponent(Expression value, ValType valType, WitScope scope) {
        return toComponent(value, valType, scope, 0);
    }

    /**
     * @param depth how many conversion lambdas enclose this one, which is what keeps their
     *     parameters from shadowing each other when a container holds another
     */
    private Expression toComponent(Expression value, ValType valType, WitScope scope, int depth) {
        if (!needsConversion(valType, scope)) {
            return value;
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        switch (defined.kind()) {
            case OPTION:
                return lowerOption(value, (OptionType) defined, scope, depth);
            case LIST:
                return mapElements(
                        value,
                        toComponent(
                                new NameExpr(elementName(depth)),
                                ((ListType) defined).elementType(),
                                scope,
                                depth + 1),
                        depth);
            default:
                return AstBuilders.call(value, "toComponent");
        }
    }

    /** Turns what the ABI carries into a Java value, evaluating {@code value} once. */
    Expression fromComponent(Expression value, ValType valType, WitScope scope) {
        return fromComponent(value, valType, scope, 0);
    }

    /**
     * @param depth how many conversion lambdas enclose this one
     */
    private Expression fromComponent(Expression value, ValType valType, WitScope scope, int depth) {
        if (needsConversion(valType, scope)) {
            DefValType defined = definedAt(scope, valType.typeIdx());
            switch (defined.kind()) {
                case TUPLE:
                    return tupleFromComponent(value, (TupleType) defined, scope);
                case OPTION:
                    return liftOption(value, (OptionType) defined, scope, depth);
                case LIST:
                    return liftElements(value, ((ListType) defined).elementType(), scope, depth);
                default:
                    return AstBuilders.call(
                            AstBuilders.name(nominalJavaType(scope, valType.typeIdx())),
                            "fromComponent",
                            value);
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
     * class instead, which is what gives the conversion the tuple type a caller expects.
     */
    private Expression tupleFromComponent(Expression value, TupleType tuple, WitScope scope) {
        List<ValType> elements = tuple.elementTypes();
        List<Expression> arguments = new ArrayList<>();
        arguments.add(value);
        for (ValType element : elements) {
            arguments.add(AstBuilders.classLiteral(elementJavaType(element, scope)));
        }
        return AstBuilders.call(
                unit.useName(tupleClass(elements.size())), "fromComponent", arguments);
    }

    /**
     * The Java type of a tuple element, which a conversion names by its class, so an element that
     * converts on its own or that carries a type argument has no way through.
     */
    private Type elementJavaType(ValType element, WitScope scope) {
        Type carrier = javaType(element, scope);
        if (isGeneric(carrier) || needsConversion(element, scope)) {
            throw unsupported(
                    "a tuple element of kind " + definedAt(scope, element.typeIdx()).kind().name());
        }
        return carrier;
    }

    /** The runtime class carrying a tuple of {@code size} elements. */
    private static String tupleClass(int size) {
        if (size < 2 || size > MAX_TUPLE_SIZE) {
            throw unsupported("a tuple of " + size + " elements");
        }
        return QualifiedTypes.TUPLE + size;
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
                        toComponent(
                                new NameExpr(bound),
                                optionPayload(option, scope),
                                scope,
                                depth + 1));
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
            return fromComponent(carried, payload, scope, depth);
        }
        String bound = someName(depth);
        Expression present =
                AstBuilders.call(unit.useName(QualifiedTypes.OPTIONAL), "ofNullable", carried);
        Expression mapped =
                AstBuilders.call(
                        present,
                        "map",
                        AstBuilders.lambda(
                                bound,
                                fromComponent(new NameExpr(bound), payload, scope, depth + 1)));
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
                carried,
                fromComponent(new NameExpr(elementName(depth)), element, scope, depth + 1),
                depth);
    }

    /** A conversion lambda's parameter, kept apart from the ones enclosing it. */
    private static String elementName(int depth) {
        return depth == 0 ? ELEMENT : ELEMENT + depth;
    }

    private static String someName(int depth) {
        return depth == 0 ? SOME : SOME + depth;
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
    Expression valType(ValType valType, WitScope scope, Map<Integer, String> declared) {
        if (valType.primValType() != null) {
            Expression kind =
                    AstBuilders.field(
                            unit.useName(QualifiedTypes.PRIM_VAL_TYPE),
                            valType.primValType().kind().name());
            Expression builder = AstBuilders.call(unit.useName(QualifiedTypes.VAL_TYPE), "builder");
            return AstBuilders.call(AstBuilders.call(builder, "withPrimValType", kind), "build");
        }
        String local = declared.get(valType.typeIdx());
        if (local == null) {
            throw undeclared(scope, valType.typeIdx());
        }
        return AstBuilders.name(local);
    }

    /** Rebuilds a compound type for declaring it into a host instance. */
    Expression defValType(DefValType defined, WitScope scope, Map<Integer, String> declared) {
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
                requireNoHandles(record, scope);
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
     * A handle names a resource the enclosing instance declares after its value types, so a record
     * carrying one has nothing to resolve by the time it is built.
     */
    void requireNoHandles(RecordType record, WitScope scope) {
        for (LabelValType field : record.fields()) {
            ValType valType = field.valType();
            if (valType.primValType() != null) {
                continue;
            }
            DefValType.Kind kind = definedAt(scope, valType.typeIdx()).kind();
            if (kind == DefValType.Kind.OWN || kind == DefValType.Kind.BORROW) {
                throw new BindgenException(
                        "field \""
                                + field.label()
                                + "\" names a resource handle, which a record cannot yet carry");
            }
        }
    }

    /** Describes {@code valType} to a typed function or void when there is no type. */
    Expression descriptor(ValType valType, WitScope scope) {
        if (valType == null) {
            return instanceOf(QualifiedTypes.VOID_DESCRIPTOR);
        }
        if (valType.primValType() != null) {
            Type carrier = primitiveJavaType(valType.primValType().kind());
            return AstBuilders.call(
                    unit.useName(QualifiedTypes.PRIMITIVE_DESCRIPTOR),
                    "forClass",
                    AstBuilders.classLiteral(carrier));
        }
        DefValType defined = definedAt(scope, valType.typeIdx());
        switch (defined.kind()) {
            case LIST:
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
    private Expression caseOf(Case declaredCase, WitScope scope, Map<Integer, String> declared) {
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
     * The Java type generated for a nominal type, which is named by the export declaring it.
     *
     * <p>Only the nominal kinds come through here. A structural type such as a list or an option
     * is written anonymously and has no name to find.
     */
    private String nominalJavaType(WitScope scope, int index) {
        String name = scope.nameAt(index);
        if (name == null) {
            throw unsupported("an unnamed " + definedAt(scope, index).kind().name());
        }
        return reference(scope, name);
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
     * outside {@link #isCompound} amounts to. Naming that kind is what tells a reader which WIT
     * feature is missing.
     */
    private static BindgenException undeclared(WitScope scope, int index) {
        run.endive.cm.types.Type type = scope.at(index);
        if (type == null || type.defValType() == null) {
            return unsupported("a type that was never declared");
        }
        return unsupported(type.defValType().kind().name());
    }

    /**
     * A result is encoded as control flow, which only a function's own result can carry, so one
     * reached as a value has nowhere to go.
     */
    private static BindgenException resultOutOfPlace() {
        return new BindgenException(
                "a result is only meaningful as a function's own result, so one reached as a"
                        + " parameter or inside another type is not supported");
    }

    private static BindgenException unsupported(String described) {
        return new BindgenException(described.toLowerCase() + " is not yet supported");
    }
}
