package run.endive.cm.bindgen;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.ListType;
import run.endive.cm.types.TupleType;
import run.endive.cm.types.ValType;

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
            case ENUM:
            case FLAGS:
                return AstBuilders.type(nominalJavaType(scope, index));
            case TUPLE:
                List<Type> elements = new ArrayList<>();
                for (ValType element : ((TupleType) defined).elementTypes()) {
                    elements.add(elementJavaType(element, scope));
                }
                return AstBuilders.generic(
                        unit.use(tupleClass(elements.size())), elements.toArray(new Type[0]));
            default:
                throw unsupported(defined.kind().name());
        }
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
                return true;
            default:
                return false;
        }
    }

    /** Whether values of {@code valType} need converting between Java and what the ABI carries. */
    private boolean needsConversion(ValType valType, WitScope scope) {
        return valType != null
                && valType.primValType() == null
                && convertsAtBoundary(definedAt(scope, valType.typeIdx()).kind());
    }

    /** Turns a Java value into what the ABI carries. */
    Expression toComponent(Expression value, ValType valType, WitScope scope) {
        return needsConversion(valType, scope) ? AstBuilders.call(value, "toComponent") : value;
    }

    /** Turns what the ABI carries into a Java value. */
    Expression fromComponent(Expression value, ValType valType, WitScope scope) {
        if (needsConversion(valType, scope)) {
            DefValType defined = definedAt(scope, valType.typeIdx());
            if (defined.kind() == DefValType.Kind.TUPLE) {
                return tupleFromComponent(value, (TupleType) defined, scope);
            }
            return AstBuilders.call(
                    AstBuilders.name(nominalJavaType(scope, valType.typeIdx())),
                    "fromComponent",
                    value);
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
            default:
                throw unsupported(defined.kind().name());
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
                // What crosses is the variant an enum despecializes to, not the Java enum the
                // embedder holds, because the generated code converts before it calls.
                return instanceOf(QualifiedTypes.VARIANT_DESCRIPTOR);
            case FLAGS:
            case TUPLE:
                // Both cross as the map the ABI carries, which RecordHostTypeDescriptor names.
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
        String simple = Names.type(witName);
        String declaredIn = scope.javaPackage();
        return declaredIn == null || declaredIn.equals(unit.packageName())
                ? simple
                : declaredIn + "." + simple;
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

    private static BindgenException unsupported(String described) {
        return new BindgenException(described.toLowerCase() + " is not yet supported");
    }
}
