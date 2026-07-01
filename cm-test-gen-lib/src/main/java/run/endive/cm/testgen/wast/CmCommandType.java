package run.endive.cm.testgen.wast;

public enum CmCommandType {
    MODULE,
    MODULE_DEFINITION,
    COMPONENT,
    ASSERT_MALFORMED,
    ASSERT_INVALID,
    ASSERT_UNLINKABLE,
    ASSERT_RETURN,
    ASSERT_TRAP,
    ACTION,
    REGISTER;

    public static CmCommandType fromString(String type) {
        switch (type) {
            case "module":
                return MODULE;
            case "module_definition":
                return MODULE_DEFINITION;
            case "component":
                return COMPONENT;
            case "assert_malformed":
                return ASSERT_MALFORMED;
            case "assert_invalid":
                return ASSERT_INVALID;
            case "assert_unlinkable":
                return ASSERT_UNLINKABLE;
            case "assert_return":
                return ASSERT_RETURN;
            case "assert_trap":
                return ASSERT_TRAP;
            case "action":
                return ACTION;
            case "register":
                return REGISTER;
            default:
                throw new IllegalArgumentException("Unknown command type: " + type);
        }
    }
}
