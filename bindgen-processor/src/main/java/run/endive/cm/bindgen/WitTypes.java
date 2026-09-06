package run.endive.cm.bindgen;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.Map;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.ListType;
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
                return AstBuilders.type(enumJavaType(scope, index));
            default:
                throw unsupported(defined.kind().name());
        }
    }

    /** Whether values of {@code valType} need converting between Java and what the ABI carries. */
    private boolean needsConversion(ValType valType, WitScope scope) {
        return valType != null
                && valType.primValType() == null
                && definedAt(scope, valType.typeIdx()).kind() == DefValType.Kind.ENUM;
    }

    /** Turns a Java value into what the ABI carries. */
    Expression toComponent(Expression value, ValType valType, WitScope scope) {
        return needsConversion(valType, scope) ? AstBuilders.call(value, "toComponent") : value;
    }

    /** Turns what the ABI carries into a Java value. */
    Expression fromComponent(Expression value, ValType valType, WitScope scope) {
        if (needsConversion(valType, scope)) {
            return AstBuilders.call(
                    AstBuilders.name(enumJavaType(scope, valType.typeIdx())),
                    "fromComponent",
                    value);
        }
        return AstBuilders.cast(javaType(valType, scope), value);
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
            throw unsupported("a type that was never declared");
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

    private String enumJavaType(WitScope scope, int index) {
        String name = scope.nameAt(index);
        if (name == null) {
            throw unsupported("an unnamed enum");
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

    private static BindgenException unsupported(String described) {
        return new BindgenException(described.toLowerCase() + " is not yet supported");
    }
}
