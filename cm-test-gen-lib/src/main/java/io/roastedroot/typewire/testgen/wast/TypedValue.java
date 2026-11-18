package io.roastedroot.typewire.testgen.wast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class TypedValue {

    private final String type;
    private final Object value;

    public TypedValue(@JsonProperty("type") String type, @JsonProperty("value") Object value) {
        this.type = type;
        this.value = value;
    }

    public String type() {
        return type;
    }

    public Object value() {
        return value;
    }

    public String emitValue() {
        switch (type) {
            case "bool":
            case "u16":
            case "s32":
                return value.toString();
            case "s8":
                return "(byte) " + value;
            case "u8":
            case "s16":
                return "(short) " + value;
            case "u32":
            case "s64":
                return value + "L";
            case "u64":
                return "new BigInteger(\"" + value + "\")";
            case "f32":
                return "Float.intBitsToFloat(Integer.parseUnsignedInt(\"" + value + "\"))";
            case "f64":
                return "Double.longBitsToDouble(Long.parseUnsignedLong(\"" + value + "\"))";
            case "char":
                String text = (String) value;
                return "CharValue.of(0x"
                        + Integer.toHexString(text.codePointAt(0)).toUpperCase()
                        + ")";
            case "string":
                return "\"" + escape((String) value) + "\"";
            case "list":
                return emitList();
            case "record":
                return emitRecord();
            case "tuple":
                return emitTuple();
            case "flags":
                return emitFlags();
            case "variant":
                return emitVariant();
            case "enum":
                return emitCase((String) value, null);
            case "option":
                return emitOption();
            case "result":
                return emitResult();
            default:
                throw new UnsupportedOperationException("Unsupported type: " + type);
        }
    }

    private String emitList() {
        var out = new StringBuilder("List.of(");
        appendJoined(out, elements(), TypedValue::emitValue);
        return out.append(')').toString();
    }

    /** A record's fields arrive as {@code [label, value]} pairs, in declaration order. */
    private String emitRecord() {
        var out = new StringBuilder("Map.ofEntries(");
        var fields = asList(value, "record");
        for (int i = 0; i < fields.size(); i++) {
            var field = asList(fields.get(i), "record field");
            if (field.size() != 2) {
                throw new UnsupportedOperationException(
                        "record field must be a [label, value] pair, got: " + field);
            }
            appendSeparator(out, i);
            appendEntry(out, (String) field.get(0), element(field.get(1)).emitValue());
        }
        return out.append(')').toString();
    }

    /** A tuple despecializes to a record whose fields are labelled "0", "1", and so on. */
    private String emitTuple() {
        var out = new StringBuilder("Map.ofEntries(");
        var elements = elements();
        for (int i = 0; i < elements.size(); i++) {
            appendSeparator(out, i);
            appendEntry(out, Integer.toString(i), elements.get(i).emitValue());
        }
        return out.append(')').toString();
    }

    /** Flags arrive as the labels that are set. The rest are absent and lower as false. */
    private String emitFlags() {
        var out = new StringBuilder("Map.ofEntries(");
        var labels = asList(value, "flags");
        for (int i = 0; i < labels.size(); i++) {
            appendSeparator(out, i);
            appendEntry(out, (String) labels.get(i), "true");
        }
        return out.append(')').toString();
    }

    private String emitVariant() {
        var variant = asMap(value, "variant");
        Object payload = variant.get("payload");
        return emitCase((String) variant.get("case"), payload == null ? null : element(payload));
    }

    private String emitOption() {
        return value == null ? emitCase("none", null) : emitCase("some", element(value));
    }

    private String emitResult() {
        var result = asMap(value, "result");
        // The despecialized case labels are "ok" and "error", but the script spells them Ok and
        // Err.
        if (result.containsKey("Ok")) {
            return emitCase("ok", optionalElement(result.get("Ok")));
        }
        if (result.containsKey("Err")) {
            return emitCase("error", optionalElement(result.get("Err")));
        }
        throw new UnsupportedOperationException("result must carry Ok or Err, got: " + result);
    }

    private static String emitCase(String label, TypedValue payload) {
        return "VariantValue.of(\""
                + escape(label)
                + "\", "
                + (payload == null ? "null" : payload.emitValue())
                + ")";
    }

    private List<TypedValue> elements() {
        var raw = asList(value, type);
        var elements = new java.util.ArrayList<TypedValue>(raw.size());
        for (Object element : raw) {
            elements.add(element(element));
        }
        return elements;
    }

    private static void appendJoined(
            StringBuilder out,
            List<TypedValue> values,
            java.util.function.Function<TypedValue, String> emit) {
        for (int i = 0; i < values.size(); i++) {
            appendSeparator(out, i);
            out.append(emit.apply(values.get(i)));
        }
    }

    private static void appendSeparator(StringBuilder out, int index) {
        if (index > 0) {
            out.append(", ");
        }
    }

    private static void appendEntry(StringBuilder out, String label, String value) {
        out.append("Map.entry(\"").append(escape(label)).append("\", ").append(value).append(')');
    }

    private static TypedValue optionalElement(Object raw) {
        return raw == null ? null : element(raw);
    }

    private static TypedValue element(Object element) {
        var map = asMap(element, "typed value");
        return new TypedValue((String) map.get("type"), map.get("value"));
    }

    private static List<?> asList(Object raw, String what) {
        if (!(raw instanceof List)) {
            throw new UnsupportedOperationException(what + " value must be an array, got: " + raw);
        }
        return (List<?>) raw;
    }

    private static Map<?, ?> asMap(Object raw, String what) {
        if (!(raw instanceof Map)) {
            throw new UnsupportedOperationException(what + " must be an object, got: " + raw);
        }
        return (Map<?, ?>) raw;
    }

    /** Escapes a script-supplied string for embedding in generated Java source. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
