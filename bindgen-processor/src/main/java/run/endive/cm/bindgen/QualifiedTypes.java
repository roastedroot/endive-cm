package run.endive.cm.bindgen;

final class QualifiedTypes {

    static final String BIG_INTEGER = "java.math.BigInteger";
    static final String COLLECTORS = "java.util.stream.Collectors";
    static final String ENUM_SET = "java.util.EnumSet";
    static final String LINKED_HASH_MAP = "java.util.LinkedHashMap";
    static final String LIST = "java.util.List";
    static final String MAP = "java.util.Map";
    static final String OBJECTS = "java.util.Objects";
    static final String OPTIONAL = "java.util.Optional";
    static final String SET = "java.util.Set";

    static final String CHAR_VALUE = "run.endive.cm.abi.CharValue";
    static final String RESOURCE_VALUE = "run.endive.cm.abi.ResourceValue";
    static final String VARIANT_VALUE = "run.endive.cm.abi.VariantValue";

    static final String COMPONENT_FUNCTION = "run.endive.cm.runtime.ComponentFunction";
    static final String COMPONENT_INSTANCE = "run.endive.cm.runtime.ComponentInstance";
    static final String COMPONENT_LINKER = "run.endive.cm.runtime.ComponentLinker";
    static final String COMPONENT_STORE = "run.endive.cm.runtime.ComponentStore";
    static final String GUEST_RESOURCE = "run.endive.cm.runtime.GuestResource";
    static final String HOST_FUNCTION = "run.endive.cm.runtime.HostFunction";
    static final String HOST_INSTANCE = "run.endive.cm.runtime.HostInstance";
    static final String HOST_RESOURCE = "run.endive.cm.runtime.HostResource";
    static final String HOST_RESOURCE_TABLE = "run.endive.cm.runtime.HostResourceTable";

    /** The runtime names a tuple class by its arity, so an element count completes this. */
    static final String TUPLE = "run.endive.cm.runtime.Tuple";

    static final String LIST_DESCRIPTOR = "run.endive.cm.runtime.ListHostTypeDescriptor";
    static final String PRIMITIVE_DESCRIPTOR = "run.endive.cm.runtime.PrimitiveHostTypeDescriptor";
    static final String RECORD_DESCRIPTOR = "run.endive.cm.runtime.RecordHostTypeDescriptor";
    static final String RESOURCE_DESCRIPTOR = "run.endive.cm.runtime.ResourceHostTypeDescriptor";
    static final String VARIANT_DESCRIPTOR = "run.endive.cm.runtime.VariantHostTypeDescriptor";
    static final String VOID_DESCRIPTOR = "run.endive.cm.runtime.VoidHostTypeDescriptor";

    static final String CASE = "run.endive.cm.types.Case";
    static final String ENUM_TYPE = "run.endive.cm.types.EnumType";
    static final String FLAGS_TYPE = "run.endive.cm.types.FlagsType";
    static final String FUNC_TYPE = "run.endive.cm.types.FuncType";
    static final String LABEL_VAL_TYPE = "run.endive.cm.types.LabelValType";
    static final String LIST_TYPE = "run.endive.cm.types.ListType";
    static final String OPTION_TYPE = "run.endive.cm.types.OptionType";
    static final String PRIM_VAL_TYPE = "run.endive.cm.types.PrimValType";
    static final String TUPLE_TYPE = "run.endive.cm.types.TupleType";
    static final String TYPE = "run.endive.cm.types.Type";
    static final String VAL_TYPE = "run.endive.cm.types.ValType";
    static final String VARIANT_TYPE = "run.endive.cm.types.VariantType";
    static final String WASM_COMPONENT = "run.endive.cm.types.WasmComponent";

    private QualifiedTypes() {}
}
