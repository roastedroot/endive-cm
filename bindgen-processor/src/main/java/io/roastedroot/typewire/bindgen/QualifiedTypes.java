package io.roastedroot.typewire.bindgen;

final class QualifiedTypes {

    static final String BIG_INTEGER = "java.math.BigInteger";
    static final String COLLECTORS = "java.util.stream.Collectors";
    static final String ENUM_SET = "java.util.EnumSet";
    static final String LINKED_HASH_MAP = "java.util.LinkedHashMap";
    static final String LIST = "java.util.List";
    static final String MAP = "java.util.Map";
    static final String ARRAYS = "java.util.Arrays";
    static final String OBJECTS = "java.util.Objects";
    static final String OPTIONAL = "java.util.Optional";
    static final String SET = "java.util.Set";

    static final String BYTE_LIST = "io.roastedroot.typewire.abi.ByteList";
    static final String CHAR_VALUE = "io.roastedroot.typewire.abi.CharValue";
    static final String FLAT_RECORD_VALUE = "io.roastedroot.typewire.abi.FlatRecordValue";
    static final String MEMORY = "run.endive.runtime.Memory";
    static final String RECORD_VALUE = "io.roastedroot.typewire.abi.RecordValue";
    static final String RESOURCE_VALUE = "io.roastedroot.typewire.abi.ResourceValue";
    static final String VARIANT_VALUE = "io.roastedroot.typewire.abi.VariantValue";

    static final String COMPONENT_FUNCTION = "io.roastedroot.typewire.runtime.ComponentFunction";
    static final String COMPONENT_INSTANCE = "io.roastedroot.typewire.runtime.ComponentInstance";
    static final String COMPONENT_LINKER = "io.roastedroot.typewire.runtime.ComponentLinker";
    static final String COMPONENT_STORE = "io.roastedroot.typewire.runtime.ComponentStore";
    static final String GUEST_RESOURCE = "io.roastedroot.typewire.runtime.GuestResource";
    static final String HOST_FUNCTION = "io.roastedroot.typewire.runtime.HostFunction";
    static final String HOST_INSTANCE = "io.roastedroot.typewire.runtime.HostInstance";
    static final String HOST_RESOURCE = "io.roastedroot.typewire.runtime.HostResource";
    static final String HOST_RESOURCE_TABLE = "io.roastedroot.typewire.runtime.HostResourceTable";
    static final String RESULT = "io.roastedroot.typewire.runtime.Result";

    /** The runtime names a tuple class by its arity, so an element count completes this. */
    static final String TUPLE = "io.roastedroot.typewire.runtime.Tuple";

    static final String LIST_DESCRIPTOR = "io.roastedroot.typewire.runtime.ListHostTypeDescriptor";
    static final String PRIMITIVE_DESCRIPTOR =
            "io.roastedroot.typewire.runtime.PrimitiveHostTypeDescriptor";
    static final String RECORD_DESCRIPTOR =
            "io.roastedroot.typewire.runtime.RecordHostTypeDescriptor";
    static final String RESOURCE_DESCRIPTOR =
            "io.roastedroot.typewire.runtime.ResourceHostTypeDescriptor";
    static final String VARIANT_DESCRIPTOR =
            "io.roastedroot.typewire.runtime.VariantHostTypeDescriptor";
    static final String VOID_DESCRIPTOR = "io.roastedroot.typewire.runtime.VoidHostTypeDescriptor";

    static final String CASE = "io.roastedroot.typewire.types.Case";
    static final String ENUM_TYPE = "io.roastedroot.typewire.types.EnumType";
    static final String FLAGS_TYPE = "io.roastedroot.typewire.types.FlagsType";
    static final String FUNC_TYPE = "io.roastedroot.typewire.types.FuncType";
    static final String LABEL_VAL_TYPE = "io.roastedroot.typewire.types.LabelValType";
    static final String LIST_TYPE = "io.roastedroot.typewire.types.ListType";
    static final String OPTION_TYPE = "io.roastedroot.typewire.types.OptionType";
    static final String PRIM_VAL_TYPE = "io.roastedroot.typewire.types.PrimValType";
    static final String RECORD_TYPE = "io.roastedroot.typewire.types.RecordType";
    static final String RESULT_TYPE = "io.roastedroot.typewire.types.ResultType";
    static final String TUPLE_TYPE = "io.roastedroot.typewire.types.TupleType";
    static final String TYPE = "io.roastedroot.typewire.types.Type";
    static final String VAL_TYPE = "io.roastedroot.typewire.types.ValType";
    static final String VARIANT_TYPE = "io.roastedroot.typewire.types.VariantType";
    static final String WASM_COMPONENT = "io.roastedroot.typewire.types.WasmComponent";

    private QualifiedTypes() {}
}
