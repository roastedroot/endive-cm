package run.endive.cm.bindgen;

final class QualifiedTypes {

    static final String BIG_INTEGER = "java.math.BigInteger";
    static final String LINKED_HASH_MAP = "java.util.LinkedHashMap";
    static final String LIST = "java.util.List";
    static final String MAP = "java.util.Map";

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

    static final String LIST_DESCRIPTOR = "run.endive.cm.runtime.ListHostTypeDescriptor";
    static final String PRIMITIVE_DESCRIPTOR = "run.endive.cm.runtime.PrimitiveHostTypeDescriptor";
    static final String RESOURCE_DESCRIPTOR = "run.endive.cm.runtime.ResourceHostTypeDescriptor";
    static final String VARIANT_DESCRIPTOR = "run.endive.cm.runtime.VariantHostTypeDescriptor";
    static final String VOID_DESCRIPTOR = "run.endive.cm.runtime.VoidHostTypeDescriptor";

    static final String ENUM_TYPE = "run.endive.cm.types.EnumType";
    static final String FUNC_TYPE = "run.endive.cm.types.FuncType";
    static final String LABEL_VAL_TYPE = "run.endive.cm.types.LabelValType";
    static final String LIST_TYPE = "run.endive.cm.types.ListType";
    static final String PRIM_VAL_TYPE = "run.endive.cm.types.PrimValType";
    static final String TYPE = "run.endive.cm.types.Type";
    static final String VAL_TYPE = "run.endive.cm.types.ValType";
    static final String WASM_COMPONENT = "run.endive.cm.types.WasmComponent";

    private QualifiedTypes() {}
}
