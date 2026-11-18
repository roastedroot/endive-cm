package io.roastedroot.typewire.abi;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.TypeSpace;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import run.endive.runtime.Memory;
import run.endive.runtime.TrapException;
import run.endive.runtime.WasmRuntimeException;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.ValType;

public final class CanonicalAbi {

    /**
     * The most bytes a list may span. It is capped so that {@code 2 * length} can never overflow
     * the {@code i32} realloc arguments used to allocate it.
     */
    public static final int MAX_LIST_BYTE_LENGTH = (1 << 28) - 1;

    /** Same overflow-avoidance reasoning as {@link #MAX_LIST_BYTE_LENGTH}, for strings. */
    private static final int MAX_STRING_BYTE_LENGTH = (1 << 28) - 1;

    private static final int CANONICAL_FLOAT32_NAN_BITS = 0x7fc00000;
    private static final long CANONICAL_FLOAT64_NAN_BITS = 0x7ff8000000000000L;
    private static final int MIN_SURROGATE = 0xD800;
    private static final int MAX_SURROGATE = 0xDFFF;
    private static final int MAX_UNICODE_SCALAR_VALUE = 0x110000;

    private static final long[] EMPTY_CORE_VALUES = new long[0];

    public static final int MAX_FLAT_PARAMS = 16;
    public static final int MAX_FLAT_ASYNC_PARAMS = 4;
    public static final int MAX_FLAT_RESULTS = 1;

    private CanonicalAbi() {}

    /**
     * Whether resolved type {@code type}, or of any type reachable from it, matches the resolved
     * type predicate {@code typeMatch}.
     */
    public static boolean contains(ResolvedType type, Predicate<ResolvedType> typeMatch) {
        if (type == null) {
            return false;
        }
        if (typeMatch.test(type)) {
            return true;
        }
        switch (type.kind()) {
            case LIST:
            case SIZED_LIST:
            case STREAM:
            case FUTURE:
                return contains(type.element(), typeMatch);
            case RECORD:
                for (ResolvedType.Field f : type.fields()) {
                    if (contains(f.type(), typeMatch)) {
                        return true;
                    }
                }
                return false;
            case VARIANT:
                for (ResolvedType.Case c : type.cases()) {
                    if (c.hasType() && contains(c.type(), typeMatch)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    /** Whether {@code type} contains a {@code borrow} anywhere. */
    public static boolean containsBorrow(ResolvedType type) {
        return contains(type, u -> u.kind() == DefValType.Kind.BORROW);
    }

    /**
     * Whether {@code type}, whose own indices count in {@code space}, contains a {@code borrow}.
     */
    public static boolean containsBorrow(TypeSpace space, DefValType type) {
        return containsBorrow(ResolvedType.of(type, space));
    }

    public static boolean containsAsyncValue(TypeSpace space, DefValType type) {
        return contains(
                ResolvedType.of(type, space),
                u -> u.kind() == DefValType.Kind.STREAM || u.kind() == DefValType.Kind.FUTURE);
    }

    /**
     * The flattened core Wasm signature to which a component-level function type lowers. When the
     * flat parameter/result list exceeds the max size defined by the spec, it collapses to a single
     * pointer and the caller/callee agree to pass/receive the value(s) via memory instead.
     */
    public static FunctionType flattenFuncType(
            LiftLowerContext context, ResolvedFuncType funcType, Direction direction) {
        List<ValType> flatParams = new ArrayList<>(flattenParams(context, funcType));
        List<ValType> flatResults = new ArrayList<>(flattenResult(context, funcType));
        if (!context.isAsync()) {
            if (flatParams.size() > MAX_FLAT_PARAMS) {
                flatParams = new ArrayList<>(List.of(context.ptrType().coreValType()));
            }
            if (flatResults.size() > MAX_FLAT_RESULTS) {
                switch (direction) {
                    case LIFT:
                        flatResults = new ArrayList<>(List.of(context.ptrType().coreValType()));
                        break;
                    case LOWER:
                        flatParams.add(context.ptrType().coreValType());
                        flatResults = new ArrayList<>();
                        break;
                    default:
                        throw new IllegalStateException("unhandled direction " + direction);
                }
            }
            return FunctionType.of(flatParams, flatResults);
        }
        switch (direction) {
            case LIFT:
                if (flatParams.size() > MAX_FLAT_PARAMS) {
                    flatParams = new ArrayList<>(List.of(context.ptrType().coreValType()));
                }
                flatResults = new ArrayList<>(List.of(ValType.I32));
                break;
            case LOWER:
                if (flatParams.size() > MAX_FLAT_ASYNC_PARAMS) {
                    flatParams = new ArrayList<>(List.of(context.ptrType().coreValType()));
                }
                if (!flatResults.isEmpty()) {
                    flatParams.add(context.ptrType().coreValType());
                }
                flatResults = new ArrayList<>(List.of(ValType.I32));
                break;
            default:
                throw new IllegalStateException("unhandled direction " + direction);
        }
        return FunctionType.of(flatParams, flatResults);
    }

    private static List<ValType> flattenParams(LiftLowerContext context, ResolvedFuncType ft) {
        List<ValType> flat = new ArrayList<>();
        for (ResolvedFuncType.Param p : ft.params()) {
            flat.addAll(p.type().flatten(context.ptrType()));
        }
        return flat;
    }

    private static List<ValType> flattenResult(LiftLowerContext context, ResolvedFuncType ft) {
        if (!ft.hasResult()) {
            return List.of();
        }
        return ft.result().flatten(context.ptrType());
    }

    /**
     * Flattens each of the component model types of {@code types} into core types and concatenates
     * the results.
     */
    static List<ValType> flattenTypes(LiftLowerContext context, List<ResolvedType> types) {
        List<ValType> flat = new ArrayList<>();
        for (ResolvedType t : types) {
            flat.addAll(t.flatten(context.ptrType()));
        }
        return flat;
    }

    public static Object load(LiftLowerContext context, int pointer, ResolvedType type) {
        switch (type.kind()) {
            case BOOL:
                return convertIntToBool(loadInt(context.memory(), pointer, 1, false));
            case U8:
                return boxUnsigned(type.kind(), loadInt(context.memory(), pointer, 1, false));
            case U16:
                return boxUnsigned(type.kind(), loadInt(context.memory(), pointer, 2, false));
            case U32:
                return boxUnsigned(type.kind(), loadInt(context.memory(), pointer, 4, false));
            case U64:
                return boxUnsigned(type.kind(), loadInt(context.memory(), pointer, 8, false));
            case S8:
                return boxSigned(type.kind(), loadInt(context.memory(), pointer, 1, true));
            case S16:
                return boxSigned(type.kind(), loadInt(context.memory(), pointer, 2, true));
            case S32:
                return boxSigned(type.kind(), loadInt(context.memory(), pointer, 4, true));
            case S64:
                return boxSigned(type.kind(), loadInt(context.memory(), pointer, 8, true));
            case F32:
                return canonicalizeNan32(context.memory().readFloat(pointer));
            case F64:
                return canonicalizeNan64(context.memory().readDouble(pointer));
            case CHAR:
                return CharValue.of(convertI32ToChar(loadInt(context.memory(), pointer, 4, false)));
            case STRING:
                return loadString(context, pointer);
            case LIST:
            case SIZED_LIST:
                return loadList(context, pointer, type);
            case RECORD:
                return loadRecord(context, pointer, type);
            case VARIANT:
                return loadVariant(context, pointer, type);
            case FLAGS:
                return loadFlags(context, pointer, type);
            case OWN:
                return liftOwn(context, (int) loadInt(context.memory(), pointer, 4, false), type);
            case BORROW:
                return liftBorrow(
                        context, (int) loadInt(context.memory(), pointer, 4, false), type);
            case ERROR_CONTEXT:
            case STREAM:
            case FUTURE:
                throw new UnsupportedOperationException(
                        "loading " + type.kind() + " values is not implemented yet");
            default:
                throw new IllegalStateException("unhandled kind " + type.kind());
        }
    }

    static long loadInt(Memory memory, int pointer, int numBytes, boolean signed) {
        switch (numBytes) {
            case 1:
                return signed ? memory.read(pointer) : memory.readU8(pointer);
            case 2:
                return signed ? memory.readShort(pointer) : memory.readU16(pointer);
            case 4:
                return signed ? memory.readInt(pointer) : memory.readU32(pointer);
            case 8:
                return memory.readLong(pointer);
            default:
                throw new IllegalArgumentException("unsupported int width " + numBytes);
        }
    }

    private static boolean convertIntToBool(long i) {
        return i != 0;
    }

    static float canonicalizeNan32(float f) {
        return Float.isNaN(f) ? Float.intBitsToFloat(CANONICAL_FLOAT32_NAN_BITS) : f;
    }

    static double canonicalizeNan64(double f) {
        return Double.isNaN(f) ? Double.longBitsToDouble(CANONICAL_FLOAT64_NAN_BITS) : f;
    }

    static int convertI32ToChar(long i) {
        if (i >= MAX_UNICODE_SCALAR_VALUE) {
            throw new TrapException("invalid `char` bit pattern");
        }
        if (i >= MIN_SURROGATE && i <= MAX_SURROGATE) {
            throw new TrapException("invalid `char` bit pattern");
        }
        return (int) i;
    }

    private static List<Object> loadList(LiftLowerContext context, int pointer, ResolvedType type) {
        if (type.isFixedSizeList()) {
            return loadListElements(context, pointer, type.fixedSize(), type.element());
        }
        return loadUnboundedList(context, pointer, type.element());
    }

    private static List<Object> loadUnboundedList(
            LiftLowerContext context, int pointer, ResolvedType elementType) {
        int ptrSize = context.ptrType().size();
        int begin = (int) loadInt(context.memory(), pointer, ptrSize, false);
        int length = (int) loadInt(context.memory(), pointer + ptrSize, ptrSize, false);
        return loadListFromRange(context, begin, length, elementType);
    }

    private static List<Object> loadListFromRange(
            LiftLowerContext context, int pointer, int length, ResolvedType elementType) {
        int elemSize = elementType.elementSize(context.ptrType());
        int elemAlignment = elementType.alignment(context.ptrType());
        if ((long) length * elemSize > MAX_LIST_BYTE_LENGTH) {
            throw new TrapException(
                    "list byte length exceeds the maximum of " + MAX_LIST_BYTE_LENGTH);
        }
        if (pointer != DefValType.alignTo(pointer, elemAlignment)) {
            throw new TrapException(
                    "list pointer " + pointer + " is not aligned to " + elemAlignment);
        }
        if ((long) pointer + (long) length * elemSize > Memory.bytes(context.memory().pages())) {
            throw new TrapException(
                    "list of length " + length + " at " + pointer + " is out of bounds");
        }
        if (elementType.kind() == DefValType.Kind.U8) {
            return ByteList.of(context.memory().readBytes(pointer, length));
        }
        return loadListElements(context, pointer, length, elementType);
    }

    private static List<Object> loadListElements(
            LiftLowerContext context, int pointer, int length, ResolvedType elementType) {
        int elemSize = elementType.elementSize(context.ptrType());
        List<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(load(context, pointer + i * elemSize, elementType));
        }
        return result;
    }

    private static Map<String, Object> loadRecord(
            LiftLowerContext context, int pointer, ResolvedType type) {
        Map<String, Object> record = new LinkedHashMap<>();
        int p = pointer;
        for (ResolvedType.Field f : type.fields()) {
            p = DefValType.alignTo(p, f.type().alignment(context.ptrType()));
            record.put(f.label(), load(context, p, f.type()));
            p += f.type().elementSize(context.ptrType());
        }
        return record;
    }

    private static VariantValue loadVariant(
            LiftLowerContext context, int pointer, ResolvedType type) {
        var cases = type.cases();
        int discSize = type.discriminantSize();
        long caseIndex = loadInt(context.memory(), pointer, discSize, false);
        if (caseIndex >= cases.size()) {
            throw new TrapException("invalid variant discriminant");
        }
        var c = cases.get((int) caseIndex);
        int payloadPointer =
                DefValType.alignTo(pointer + discSize, type.maxCaseAlignment(context.ptrType()));
        if (!c.hasType()) {
            return VariantValue.of(c.label(), null);
        }
        return VariantValue.of(c.label(), load(context, payloadPointer, c.type()));
    }

    private static Map<String, Boolean> loadFlags(
            LiftLowerContext context, int pointer, ResolvedType type) {
        long i = loadInt(context.memory(), pointer, type.elementSize(context.ptrType()), false);
        return unpackFlagsFromInt(i, type.labels());
    }

    private static Map<String, Boolean> unpackFlagsFromInt(long i, List<String> labels) {
        Map<String, Boolean> record = new LinkedHashMap<>();
        for (String label : labels) {
            record.put(label, (i & 1) != 0);
            i >>= 1;
        }
        return record;
    }

    private static long utf16Tag(PointerType pointerType) {
        return 1L << (pointerType.size() * 8 - 1);
    }

    private static String loadString(LiftLowerContext context, int pointer) {
        int ptrSize = context.ptrType().size();
        long begin = loadInt(context.memory(), pointer, ptrSize, false);
        long taggedCodeUnits = loadInt(context.memory(), pointer + ptrSize, ptrSize, false);
        return loadStringFromRange(context, (int) begin, taggedCodeUnits);
    }

    private static String loadStringFromRange(
            LiftLowerContext context, int pointer, long taggedCodeUnits) {
        var range = stringRange(context, taggedCodeUnits);
        return decodeStrict(readStringBytes(context, pointer, range), range.charset);
    }

    static StringRange stringRange(LiftLowerContext context, long taggedCodeUnits) {
        switch (context.stringEncoding()) {
            case UTF8:
                return new StringRange(1, taggedCodeUnits, StandardCharsets.UTF_8);
            case UTF16:
                return new StringRange(2, 2 * taggedCodeUnits, StandardCharsets.UTF_16LE);
            case LATIN1_UTF16:
                long tag = utf16Tag(context.ptrType());
                if ((taggedCodeUnits & tag) != 0) {
                    return new StringRange(
                            2, 2 * (taggedCodeUnits ^ tag), StandardCharsets.UTF_16LE);
                }
                return new StringRange(taggedCodeUnits, StandardCharsets.ISO_8859_1);
            default:
                throw new IllegalStateException(
                        "unhandled string encoding " + context.stringEncoding());
        }
    }

    /** Applies the length, alignment, and bounds traps guarding a string range, then reads it. */
    static byte[] readStringBytes(LiftLowerContext context, int pointer, StringRange range) {
        if (range.byteLength > MAX_STRING_BYTE_LENGTH) {
            throw new TrapException(
                    "string byte length exceeds the maximum of " + MAX_STRING_BYTE_LENGTH);
        }
        long address = Integer.toUnsignedLong(pointer);
        if (address % range.alignment != 0) {
            throw new TrapException("unaligned pointer");
        }
        if (address + range.byteLength > Memory.bytes(context.memory().pages())) {
            throw new TrapException(stringOutOfBounds(range.byteLength, address));
        }
        try {
            return context.memory().readBytes(pointer, (int) range.byteLength);
        } catch (WasmRuntimeException e) {
            throw new TrapException(stringOutOfBounds(range.byteLength, address));
        }
    }

    private static String stringOutOfBounds(long byteLength, long address) {
        return "string content out-of-bounds - string pointer/length out of bounds of memory"
                + " (byte length "
                + byteLength
                + " at "
                + address
                + ")";
    }

    /** The byte range and charset a pointer and tagged code units pair resolves to. */
    static final class StringRange {
        final int alignment;
        final long byteLength;
        final Charset charset;

        StringRange(int alignment, long byteLength, Charset charset) {
            this.alignment = alignment;
            this.byteLength = byteLength;
            this.charset = charset;
        }

        StringRange(long byteLength, Charset charset) {
            this(2, byteLength, charset);
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        return decodeStrictToChars(bytes, charset).toString();
    }

    /**
     * Strictly decodes {@code bytes}, trapping on any malformed or unmappable input. Returns the
     * {@link CharBuffer} rather than a {@link String} so the transfer path can re-encode without
     * ever materializing one.
     */
    static CharBuffer decodeStrictToChars(byte[] bytes, Charset charset) {
        var decoder =
                charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(bytes);
        CharBuffer out =
                CharBuffer.allocate((int) Math.ceil(bytes.length * decoder.maxCharsPerByte()) + 1);

        CoderResult result = decoder.decode(in, out, false);
        if (result.isUnderflow() && in.hasRemaining()) {
            throw incompleteEncoding(charset);
        }
        requireDecoded(result, charset);
        requireDecoded(decoder.decode(in, out, true), charset);
        requireDecoded(decoder.flush(out), charset);
        out.flip();
        return out;
    }

    private static void requireDecoded(CoderResult result, Charset charset) {
        if (result.isError()) {
            throw invalidEncoding(charset);
        }
        if (result.isOverflow()) {
            throw new IllegalStateException("decode buffer too small for " + charset.name());
        }
    }

    public static void store(
            LiftLowerContext context, Object value, ResolvedType type, int pointer) {
        switch (type.kind()) {
            case BOOL:
                storeInt(context.memory(), (Boolean) value ? 1 : 0, pointer, 1);
                return;
            case U8:
            case U16:
            case U32:
            case U64:
            case S8:
            case S16:
            case S32:
            case S64:
                storeInt(
                        context.memory(),
                        ((Number) value).longValue(),
                        pointer,
                        elemSizeForIntKind(type.kind()));
                return;
            case F32:
                context.memory()
                        .writeF32(pointer, canonicalizeNan32(((Number) value).floatValue()));
                return;
            case F64:
                context.memory()
                        .writeF64(pointer, canonicalizeNan64(((Number) value).doubleValue()));
                return;
            case CHAR:
                storeInt(context.memory(), ((CharValue) value).codePoint(), pointer, 4);
                return;
            case STRING:
                storeString(context, (String) value, pointer);
                return;
            case LIST:
            case SIZED_LIST:
                storeList(context, value, pointer, type);
                return;
            case RECORD:
                if (value instanceof FlatRecordValue) {
                    ((FlatRecordValue) value).store(context.memory(), pointer);
                } else {
                    storeRecord(context, value, pointer, type);
                }
                return;
            case VARIANT:
                storeVariant(context, (VariantValue) value, pointer, type);
                return;
            case FLAGS:
                storeFlags(context, (Map<?, ?>) value, pointer, type);
                return;
            case OWN:
                storeInt(
                        context.memory(),
                        lowerOwn(context, (ResourceValue) value, type),
                        pointer,
                        4);
                return;
            case BORROW:
                storeInt(
                        context.memory(),
                        lowerBorrow(context, (ResourceValue) value, type),
                        pointer,
                        4);
                return;
            case ERROR_CONTEXT:
            case STREAM:
            case FUTURE:
                throw new UnsupportedOperationException(
                        "storing " + type.kind() + " values is not implemented yet");
            default:
                throw new IllegalStateException("unhandled kind " + type.kind());
        }
    }

    private static int elemSizeForIntKind(DefValType.Kind kind) {
        switch (kind) {
            case U8:
            case S8:
                return 1;
            case U16:
            case S16:
                return 2;
            case U32:
            case S32:
                return 4;
            case U64:
            case S64:
                return 8;
            default:
                throw new IllegalArgumentException("not an integer kind: " + kind);
        }
    }

    static void storeInt(Memory memory, long value, int pointer, int numBytes) {
        switch (numBytes) {
            case 1:
                memory.writeByte(pointer, (byte) value);
                return;
            case 2:
                memory.writeShort(pointer, (short) value);
                return;
            case 4:
                memory.writeI32(pointer, (int) value);
                return;
            case 8:
                memory.writeLong(pointer, value);
                return;
            default:
                throw new IllegalArgumentException("unsupported int width " + numBytes);
        }
    }

    private static void storeList(
            LiftLowerContext context, Object value, int pointer, ResolvedType type) {
        if (type.isFixedSizeList()) {
            var listValue = asList(value);
            if (listValue.size() != type.fixedSize()) {
                throw new IllegalArgumentException(
                        "expected "
                                + type.fixedSize()
                                + " elements for fixed-size list, got "
                                + listValue.size());
            }
            storeListElements(context, listValue, pointer, type.element());
            return;
        }
        storeUnboundedList(context, value, pointer, type.element());
    }

    private static void storeUnboundedList(
            LiftLowerContext context, Object listValue, int pointer, ResolvedType elementType) {
        int begin = storeListIntoRange(context, listValue, elementType);
        int ptrSize = context.ptrType().size();
        storeInt(context.memory(), begin, pointer, ptrSize);
        storeInt(context.memory(), listLength(listValue), pointer + ptrSize, ptrSize);
    }

    /** A list value as a {@link List}, converting a {@code list<u8>} given as {@code byte[]}. */
    private static List<?> asList(Object value) {
        return value instanceof byte[] ? ByteList.of((byte[]) value) : (List<?>) value;
    }

    private static int listLength(Object value) {
        return value instanceof byte[] ? ((byte[]) value).length : ((List<?>) value).size();
    }

    /**
     * Whether {@code value} is a {@code list<u8>} whose bytes can be copied to memory in one write.
     */
    private static boolean isBulkBytes(Object value, ResolvedType elementType) {
        return elementType.kind() == DefValType.Kind.U8
                && (value instanceof byte[] || value instanceof ByteList);
    }

    private static int storeListIntoRange(
            LiftLowerContext context, Object value, ResolvedType elementType) {
        if (isBulkBytes(value, elementType)) {
            byte[] bytes = ByteList.toBytes(value);
            if (bytes.length > MAX_LIST_BYTE_LENGTH) {
                throw new TrapException(
                        "list byte length exceeds the maximum of " + MAX_LIST_BYTE_LENGTH);
            }
            int ptr = allocate(context, 1, bytes.length);
            context.memory().write(ptr, bytes);
            return ptr;
        }
        List<?> listValue = (List<?>) value;
        int elemSize = elementType.elementSize(context.ptrType());
        long byteLength = (long) listValue.size() * elemSize;
        if (byteLength > MAX_LIST_BYTE_LENGTH) {
            throw new TrapException(
                    "list byte length exceeds the maximum of " + MAX_LIST_BYTE_LENGTH);
        }
        int align = elementType.alignment(context.ptrType());
        int ptr = allocate(context, align, (int) byteLength);
        storeListElements(context, listValue, ptr, elementType);
        return ptr;
    }

    /**
     * Calls the context's {@code realloc} to allocate {@code size} fresh bytes and validates the
     * result with the bounds/alignment checks.
     */
    private static int allocate(LiftLowerContext context, int align, int size) {
        var realloc =
                Objects.requireNonNull(
                        context.realloc(), "storing this value requires a realloc in the context");
        int ptr = realloc.apply(0, 0, align, size);
        // A realloc result is an unsigned i32 address, so reading it as a signed Java int would
        // put a failure return below zero and slip through the bounds check below.
        long addr = Integer.toUnsignedLong(ptr);
        if (addr % align != 0) {
            throw new TrapException("realloc return: unaligned pointer");
        }
        if (addr + size > Memory.bytes(context.memory().pages())) {
            throw new TrapException("realloc return: beyond end of memory");
        }
        return ptr;
    }

    private static void storeListElements(
            LiftLowerContext context, List<?> listValue, int pointer, ResolvedType elementType) {
        int elemSize = elementType.elementSize(context.ptrType());
        int i = 0;
        for (Object e : listValue) {
            store(context, e, elementType, pointer + i * elemSize);
            i++;
        }
    }

    private static void storeRecord(
            LiftLowerContext context, Object recordValue, int pointer, ResolvedType type) {
        int p = pointer;
        var fields = type.fields();
        for (int i = 0; i < fields.size(); i++) {
            ResolvedType.Field f = fields.get(i);
            p = DefValType.alignTo(p, f.type().alignment(context.ptrType()));
            store(context, fieldOf(recordValue, i, f), f.type(), p);
            p += f.type().elementSize(context.ptrType());
        }
    }

    /** A record's field, from a {@link RecordValue} by position or from a map by label. */
    private static Object fieldOf(Object recordValue, int index, ResolvedType.Field field) {
        if (recordValue instanceof RecordValue) {
            return ((RecordValue) recordValue).field(index);
        }
        return ((Map<?, ?>) recordValue).get(field.label());
    }

    private static void storeVariant(
            LiftLowerContext context, VariantValue value, int pointer, ResolvedType type) {
        var cases = type.cases();
        int caseIndex = -1;
        for (int i = 0; i < cases.size(); i++) {
            if (cases.get(i).label().equals(value.label())) {
                caseIndex = i;
                break;
            }
        }
        if (caseIndex < 0) {
            throw new IllegalArgumentException(
                    "no case labeled '" + value.label() + "' in variant");
        }
        int discSize = type.discriminantSize();
        storeInt(context.memory(), caseIndex, pointer, discSize);
        var c = cases.get(caseIndex);
        int payloadPointer =
                DefValType.alignTo(pointer + discSize, type.maxCaseAlignment(context.ptrType()));
        if (c.hasType()) {
            store(context, value.value(), c.type(), payloadPointer);
        }
    }

    private static void storeFlags(
            LiftLowerContext context, Map<?, ?> flagsValue, int pointer, ResolvedType type) {
        long i = packFlagsIntoInt(flagsValue, type.labels());
        storeInt(context.memory(), i, pointer, type.elementSize(context.ptrType()));
    }

    private static long packFlagsIntoInt(Map<?, ?> flagsValue, List<String> labels) {
        long i = 0;
        int shift = 0;
        for (String label : labels) {
            if (Boolean.TRUE.equals(flagsValue.get(label))) {
                i |= 1L << shift;
            }
            shift++;
        }
        return i;
    }

    private static void storeString(LiftLowerContext context, String value, int pointer) {
        var result = storeStringIntoRange(context, value);
        int ptrSize = context.ptrType().size();
        storeInt(context.memory(), result.pointer, pointer, ptrSize);
        storeInt(context.memory(), result.codeUnits, pointer + ptrSize, ptrSize);
    }

    static PointerAndCodeUnits storeStringIntoRange(LiftLowerContext context, CharSequence value) {
        switch (context.stringEncoding()) {
            case UTF8:
                return storeStringCopy(context, value, 1, StandardCharsets.UTF_8);
            case UTF16:
                return storeStringCopy(context, value, 2, StandardCharsets.UTF_16LE);
            case LATIN1_UTF16:
                return storeStringToLatin1OrUtf16(context, value);
            default:
                throw new IllegalStateException(
                        "unhandled string encoding " + context.stringEncoding());
        }
    }

    private static PointerAndCodeUnits storeStringCopy(
            LiftLowerContext context,
            CharSequence sourceValue,
            int destCodeUnitSize,
            Charset destCharset) {
        byte[] encoded = encodeStrict(sourceValue, destCharset);
        int ptr = allocateAndWrite(context, destCodeUnitSize, encoded);
        return new PointerAndCodeUnits(ptr, encoded.length / destCodeUnitSize);
    }

    private static PointerAndCodeUnits storeStringToLatin1OrUtf16(
            LiftLowerContext context, CharSequence sourceValue) {
        boolean fitsLatin1 = sourceValue.chars().allMatch(c -> c < 0x100);
        if (fitsLatin1) {
            byte[] encoded = encodeStrict(sourceValue, StandardCharsets.ISO_8859_1);
            int ptr = allocateAndWrite(context, 2, encoded);
            return new PointerAndCodeUnits(ptr, encoded.length);
        }
        byte[] encoded = encodeStrict(sourceValue, StandardCharsets.UTF_16LE);
        int ptr = allocateAndWrite(context, 2, encoded);
        return new PointerAndCodeUnits(ptr, (encoded.length / 2) | utf16Tag(context.ptrType()));
    }

    static byte[] encodeStrict(CharSequence sourceValue, Charset charset) {
        try {
            var buf =
                    charset.newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(CharBuffer.wrap(sourceValue));
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new TrapException("string is not valid for encoding " + charset.name());
        }
    }

    static int allocateAndWrite(LiftLowerContext context, int alignment, byte[] bytes) {
        if (bytes.length > MAX_STRING_BYTE_LENGTH) {
            throw new TrapException(
                    "string byte length exceeds the maximum of " + MAX_STRING_BYTE_LENGTH);
        }
        int ptr = allocate(context, alignment, bytes.length);
        context.memory().write(ptr, bytes);
        return ptr;
    }

    /** Pointer and code units pair returned by the {@code storeString*} family. */
    static final class PointerAndCodeUnits {
        final int pointer;
        final long codeUnits;

        PointerAndCodeUnits(int pointer, long codeUnits) {
            this.pointer = pointer;
            this.codeUnits = codeUnits;
        }
    }

    /**
     * A cursor for consuming core Wasm values one at a time from a flat argument or result list.
     */
    static final class CoreValues {
        private final long[] values;
        private int pos;

        CoreValues(long[] values) {
            this.values = values;
        }

        long next() {
            if (pos >= values.length) {
                throw new IllegalStateException("no more core values to lift");
            }
            return values[pos++];
        }

        int position() {
            return pos;
        }

        void skipTo(int newPos) {
            if (newPos > values.length) {
                throw new IllegalStateException("cannot skip past the end of the core values");
            }
            pos = newPos;
        }
    }

    /**
     * The maximum number of core values a function's parameters may flatten to before they are
     * passed indirectly through linear memory.
     */
    private static int maxFlatParams(LiftLowerContext context) {
        return context.isAsync() ? MAX_FLAT_ASYNC_PARAMS : MAX_FLAT_PARAMS;
    }

    /**
     * The maximum number of core values a function's results may flatten to before they are passed
     * indirectly through linear memory.
     */
    private static int maxFlatResults(LiftLowerContext context) {
        return context.isAsync() ? 0 : MAX_FLAT_RESULTS;
    }

    /**
     * Lifts a function's flat core <em>parameter values</em> {@code flatArgs} into the
     * component-level values of types {@code types}.
     *
     * @see #liftFlatValues
     */
    public static List<Object> liftFlatParams(
            LiftLowerContext context, long[] flatArgs, List<ResolvedType> types) {
        return liftFlatValues(context, maxFlatParams(context), new CoreValues(flatArgs), types);
    }

    /**
     * Lifts a function's flat core <em>results</em> {@code flatResults} into the component-level
     * values of types {@code types}.
     *
     * @see #liftFlatValues
     */
    public static List<Object> liftFlatResults(
            LiftLowerContext context, long[] flatResults, List<ResolvedType> types) {
        return liftFlatValues(context, maxFlatResults(context), new CoreValues(flatResults), types);
    }

    /**
     * Lifts a flat list of core parameters or results (delivered by {@code vi}) into the
     * component-level values of types {@code types}. When the flattened form exceeds
     * {@code maxFlat} core values, the values were passed indirectly through linear memory and
     * {@code vi} yields only a pointer to the spilled tuple.
     */
    private static List<Object> liftFlatValues(
            LiftLowerContext context, int maxFlat, CoreValues vi, List<ResolvedType> types) {
        List<ValType> flatTypes = flattenTypes(context, types);
        if (flatTypes.size() > maxFlat) {
            var tupleType = ResolvedType.tupleOf(types);
            int align = tupleType.alignment(context.ptrType());
            int size = tupleType.elementSize(context.ptrType());
            int ptr = (int) vi.next();
            if (ptr != DefValType.alignTo(ptr, align)) {
                throw new TrapException("unaligned pointer");
            }
            if ((long) ptr + size > Memory.bytes(context.memory().pages())) {
                throw new TrapException("spilled values at " + ptr + " are out of bounds");
            }
            var record = (Map<?, ?>) load(context, ptr, tupleType);
            return new ArrayList<>(record.values());
        }
        List<Object> result = new ArrayList<>(types.size());
        for (ResolvedType t : types) {
            result.add(liftFlat(context, vi, t));
        }
        return result;
    }

    static Object liftFlat(LiftLowerContext context, CoreValues coreValues, ResolvedType type) {
        switch (type.kind()) {
            case BOOL:
                return convertIntToBool(coreValues.next() & 0xFFFFFFFFL);
            case U8:
            case U16:
            case U32:
            case U64:
                return liftFlatUnsigned(coreValues, type.kind());
            case S8:
            case S16:
            case S32:
            case S64:
                return liftFlatSigned(coreValues, type.kind());
            case F32:
                return decodeI32AsFloat(coreValues.next());
            case F64:
                return decodeI64AsFloat(coreValues.next());
            case CHAR:
                return CharValue.of(convertI32ToChar(coreValues.next() & 0xFFFFFFFFL));
            case STRING:
                return liftFlatString(context, coreValues);
            case LIST:
            case SIZED_LIST:
                return liftFlatList(context, coreValues, type);
            case RECORD:
                return liftFlatRecord(context, coreValues, type);
            case VARIANT:
                return liftFlatVariant(context, coreValues, type);
            case FLAGS:
                return liftFlatFlags(coreValues, type);
            case OWN:
                return liftOwn(context, (int) coreValues.next(), type);
            case BORROW:
                return liftBorrow(context, (int) coreValues.next(), type);
            case ERROR_CONTEXT:
            case STREAM:
            case FUTURE:
                throw new UnsupportedOperationException(
                        "lifting " + type.kind() + " values is not implemented yet");
            default:
                throw new IllegalStateException("unhandled kind " + type.kind());
        }
    }

    private static Number liftFlatUnsigned(CoreValues coreValues, DefValType.Kind kind) {
        switch (kind) {
            case U8:
                return boxUnsigned(kind, unsignedBits(coreValues, 32, 8));
            case U16:
                return boxUnsigned(kind, unsignedBits(coreValues, 32, 16));
            case U32:
                return boxUnsigned(kind, unsignedBits(coreValues, 32, 32));
            case U64:
                return boxUnsigned(kind, unsignedBits(coreValues, 64, 64));
            default:
                throw new IllegalArgumentException("not an unsigned integer kind: " + kind);
        }
    }

    private static Number liftFlatSigned(CoreValues coreValues, DefValType.Kind kind) {
        switch (kind) {
            case S8:
                return boxSigned(kind, signedBits(coreValues, 32, 8));
            case S16:
                return boxSigned(kind, signedBits(coreValues, 32, 16));
            case S32:
                return boxSigned(kind, signedBits(coreValues, 32, 32));
            case S64:
                return boxSigned(kind, signedBits(coreValues, 64, 64));
            default:
                throw new IllegalArgumentException("not a signed integer kind: " + kind);
        }
    }

    /**
     * Boxes an already width-normalized unsigned integer into the Java numeric wrapper the host
     * binds to the given component type: {@code u8 -> Short}, {@code u16 -> Integer},
     * {@code u32 -> Long}, and {@code u64 -> BigInteger}. Shared by the flat-lift and memory-load
     * paths so both yield the same wrapper types.
     */
    private static Number boxUnsigned(DefValType.Kind kind, long bits) {
        switch (kind) {
            case U8:
                return (short) bits;
            case U16:
                return (int) bits;
            case U32:
                return bits;
            case U64:
                return toUnsignedBigInteger(bits);
            default:
                throw new IllegalArgumentException("not an unsigned integer kind: " + kind);
        }
    }

    /**
     * Boxes an already sign-extended signed integer into the Java numeric wrapper the host binds to
     * the given component type. {@code s8} becomes {@code Byte}, {@code s16 -> Short},
     * {@code s32 -> Integer}, and {@code s64 -> Long}. Shared by the flat-lift and memory-load
     * paths so both yield the same wrapper types.
     */
    private static Number boxSigned(DefValType.Kind kind, long bits) {
        switch (kind) {
            case S8:
                return (byte) bits;
            case S16:
                return (short) bits;
            case S32:
                return (int) bits;
            case S64:
                return bits;
            default:
                throw new IllegalArgumentException("not a signed integer kind: " + kind);
        }
    }

    private static long unsignedBits(CoreValues coreValues, int coreWidth, int typeWidth) {
        long i = coreWidth == 32 ? (coreValues.next() & 0xFFFFFFFFL) : coreValues.next();
        if (typeWidth >= coreWidth) {
            return i;
        }
        long mask = (1L << typeWidth) - 1;
        return i & mask;
    }

    private static long signedBits(CoreValues coreValues, int coreWidth, int typeWidth) {
        long i = unsignedBits(coreValues, coreWidth, typeWidth);
        if (typeWidth == 64) {
            return i;
        }
        long signBit = 1L << (typeWidth - 1);
        return (i & signBit) != 0 ? i - (1L << typeWidth) : i;
    }

    /** The {@code u64} whose bits {@code bits} holds. */
    public static BigInteger toUnsignedBigInteger(long bits) {
        return bits >= 0
                ? BigInteger.valueOf(bits)
                : BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(Long.SIZE - 1);
    }

    /**
     * Reinterprets the raw bits of a core value as an {@code f32}. Endive packs an {@code f32} into
     * the low 32 bits of a {@code long} (see {@link CoreValues}), so a value delivered through an
     * {@code i32} or {@code i64} joined variant slot decodes identically.
     */
    private static float decodeI32AsFloat(long bits) {
        return canonicalizeNan32(Float.intBitsToFloat((int) bits));
    }

    private static double decodeI64AsFloat(long bits) {
        return canonicalizeNan64(Double.longBitsToDouble(bits));
    }

    /**
     * Encodes an {@code f32} into the low 32 bits of a {@code long}, zero-extended so the upper 32
     * bits are clear. Zero-extension matters when the value lands in an {@code i64} joined variant
     * slot, where the Canonical ABI expects the unsigned 32-bit bit pattern rather than a
     * sign-extended one.
     */
    private static long encodeFloatAsI32(float f) {
        return Integer.toUnsignedLong(Float.floatToRawIntBits(canonicalizeNan32(f)));
    }

    private static long encodeFloatAsI64(double f) {
        return Double.doubleToRawLongBits(canonicalizeNan64(f));
    }

    private static String liftFlatString(LiftLowerContext context, CoreValues coreValues) {
        int ptr = (int) coreValues.next();
        long packedLength = coreValues.next() & 0xFFFFFFFFL;
        return loadStringFromRange(context, ptr, packedLength);
    }

    private static List<Object> liftFlatList(
            LiftLowerContext context, CoreValues coreValues, ResolvedType type) {
        var elementType = type.element();
        if (type.isFixedSizeList()) {
            List<Object> a = new ArrayList<>(type.fixedSize());
            for (int i = 0; i < type.fixedSize(); i++) {
                a.add(liftFlat(context, coreValues, elementType));
            }
            return a;
        }
        int ptr = (int) coreValues.next();
        int length = (int) coreValues.next();
        return loadListFromRange(context, ptr, length, elementType);
    }

    private static Map<String, Object> liftFlatRecord(
            LiftLowerContext context, CoreValues coreValues, ResolvedType type) {
        Map<String, Object> record = new LinkedHashMap<>();
        for (ResolvedType.Field f : type.fields()) {
            record.put(f.label(), liftFlat(context, coreValues, f.type()));
        }
        return record;
    }

    private static VariantValue liftFlatVariant(
            LiftLowerContext context, CoreValues coreValues, ResolvedType type) {
        var cases = type.cases();
        List<ValType> flatTypes = type.flatten(context.ptrType());
        if (flatTypes.get(0) != ValType.I32) {
            throw new IllegalStateException("variant discriminant flat type must be i32");
        }
        int payloadSlots = flatTypes.size() - 1;
        long caseIndex = coreValues.next() & 0xFFFFFFFFL;
        if (caseIndex >= cases.size()) {
            throw new TrapException("invalid variant discriminant");
        }
        var c = cases.get((int) caseIndex);
        int payloadStart = coreValues.position();
        Object v = c.hasType() ? liftFlat(context, coreValues, c.type()) : null;
        coreValues.skipTo(payloadStart + payloadSlots);
        return VariantValue.of(c.label(), v);
    }

    private static Map<String, Boolean> liftFlatFlags(CoreValues coreValues, ResolvedType type) {
        return unpackFlagsFromInt(coreValues.next() & 0xFFFFFFFFL, type.labels());
    }

    static long[] lowerFlat(LiftLowerContext context, Object value, ResolvedType type) {
        LongBuffer out = new LongBuffer();
        lowerFlatInto(context, value, type, out);
        return out.toArray();
    }

    /**
     * Lowers a component function's <em>parameter values</em> {@code values} of types {@code types}
     * into a flat list of core values. When the parameters spill into linear memory, storage is
     * freshly allocated via {@code realloc} and the returned list is the spill pointer.
     *
     * @see #lowerFlatValues
     */
    public static long[] lowerFlatParams(
            LiftLowerContext context, List<?> values, List<ResolvedType> types) {
        return lowerFlatValues(context, maxFlatParams(context), values, types, null);
    }

    /**
     * Lowers a component function's <em>results</em> {@code values} of types {@code types} into a
     * flat list of core values. When the results spill into linear memory and {@code outParam} is
     * non-null, its first element is the caller-provided spill pointer and no flat values are
     * returned. When {@code outParam} is null, storage is freshly allocated via {@code realloc} and
     * the returned list is the spill pointer.
     *
     * @see #lowerFlatValues
     */
    public static long[] lowerFlatResults(
            LiftLowerContext context, List<?> values, List<ResolvedType> types, long[] outParam) {
        return lowerFlatValues(context, maxFlatResults(context), values, types, outParam);
    }

    /**
     * Lowers the component-level values {@code values} of types {@code types} into a flat list of
     * core values. When the flattened form exceeds {@code maxFlat} core values, the values are
     * spilled into linear memory as a tuple. With no {@code outParam}, storage is freshly allocated
     * via {@code realloc} and the returned list is the spill pointer. Otherwise, the
     * caller-provided {@code outParam}'s first element is a pre-allocated pointer and no flat
     * values are returned.
     */
    private static long[] lowerFlatValues(
            LiftLowerContext context,
            int maxFlat,
            List<?> values,
            List<ResolvedType> types,
            long[] outParam) {

        if (values.isEmpty()) {
            return EMPTY_CORE_VALUES;
        }

        List<ValType> flatTypes = flattenTypes(context, types);
        if (flatTypes.size() > maxFlat) {
            var tupleType = ResolvedType.tupleOf(types);
            Map<String, Object> tupleValue = new LinkedHashMap<>();
            for (int i = 0; i < values.size(); i++) {
                tupleValue.put(Integer.toString(i), values.get(i));
            }
            int align = tupleType.alignment(context.ptrType());
            int size = tupleType.elementSize(context.ptrType());
            int ptr;
            long[] flatVals;
            if (outParam == null) {
                ptr = allocate(context, align, size);
                flatVals = new long[] {Integer.toUnsignedLong(ptr)};
            } else {
                ptr = (int) outParam[0];
                if (ptr != DefValType.alignTo(ptr, align)) {
                    throw new TrapException("unaligned pointer");
                }
                if ((long) ptr + size > Memory.bytes(context.memory().pages())) {
                    throw new TrapException("spill out-param at " + ptr + " is out of bounds");
                }
                flatVals = new long[0];
            }
            store(context, tupleValue, tupleType, ptr);
            return flatVals;
        }
        LongBuffer out = new LongBuffer();
        for (int i = 0; i < values.size(); i++) {
            lowerFlatInto(context, values.get(i), types.get(i), out);
        }
        return out.toArray();
    }

    private static void lowerFlatInto(
            LiftLowerContext context, Object value, ResolvedType type, LongBuffer out) {
        switch (type.kind()) {
            case BOOL:
                out.add((Boolean) value ? 1L : 0L);
                return;
            case U8:
            case U16:
            case U32:
                out.add(((Number) value).longValue() & 0xFFFFFFFFL);
                return;
            case U64:
                out.add(((Number) value).longValue());
                return;
            case S8:
            case S16:
            case S32:
                out.add(lowerFlatSigned(((Number) value).longValue(), 32));
                return;
            case S64:
                out.add(lowerFlatSigned(((Number) value).longValue(), 64));
                return;
            case F32:
                out.add(encodeFloatAsI32(((Number) value).floatValue()));
                return;
            case F64:
                out.add(encodeFloatAsI64(((Number) value).doubleValue()));
                return;
            case CHAR:
                out.add(Integer.toUnsignedLong(((CharValue) value).codePoint()));
                return;
            case STRING:
                lowerFlatString(context, (String) value, out);
                return;
            case LIST:
            case SIZED_LIST:
                lowerFlatList(context, value, type, out);
                return;
            case RECORD:
                lowerFlatRecord(context, value, type, out);
                return;
            case VARIANT:
                lowerFlatVariant(context, (VariantValue) value, type, out);
                return;
            case FLAGS:
                out.add(packFlagsIntoInt((Map<?, ?>) value, type.labels()) & 0xFFFFFFFFL);
                return;
            case OWN:
                out.add(Integer.toUnsignedLong(lowerOwn(context, (ResourceValue) value, type)));
                return;
            case BORROW:
                out.add(Integer.toUnsignedLong(lowerBorrow(context, (ResourceValue) value, type)));
                return;
            case ERROR_CONTEXT:
            case STREAM:
            case FUTURE:
                throw new UnsupportedOperationException(
                        "lowering " + type.kind() + " values is not implemented yet");
            default:
                throw new IllegalStateException("unhandled kind " + type.kind());
        }
    }

    /**
     * Applies the Canonical ABI's 2s-complement conversion of a signed component value into an
     * unsigned core value. For a 32-bit core value this masks to the low 32 bits by zero-extending
     * the two's-complement pattern into the long. For a 64-bit core value the {@code long} already
     * carries the correct pattern.
     */
    private static long lowerFlatSigned(long value, int coreBits) {
        return coreBits == 64 ? value : (value & 0xFFFFFFFFL);
    }

    private static void lowerFlatString(LiftLowerContext context, String value, LongBuffer out) {
        var result = storeStringIntoRange(context, value);
        out.add(Integer.toUnsignedLong(result.pointer));
        out.add(result.codeUnits & 0xFFFFFFFFL);
    }

    private static void lowerFlatList(
            LiftLowerContext context, Object value, ResolvedType type, LongBuffer out) {
        var elementType = type.element();
        if (!type.isFixedSizeList() && isBulkBytes(value, elementType)) {
            out.add(Integer.toUnsignedLong(storeListIntoRange(context, value, elementType)));
            out.add(Integer.toUnsignedLong(listLength(value)));
            return;
        }
        var list = asList(value);
        if (type.isFixedSizeList()) {
            if (list.size() != type.fixedSize()) {
                throw new IllegalArgumentException(
                        "expected "
                                + type.fixedSize()
                                + " elements for fixed-size list, got "
                                + list.size());
            }
            for (Object e : list) {
                lowerFlatInto(context, e, elementType, out);
            }
            return;
        }
        int ptr = storeListIntoRange(context, list, elementType);
        out.add(Integer.toUnsignedLong(ptr));
        out.add(Integer.toUnsignedLong(list.size()));
    }

    private static void lowerFlatRecord(
            LiftLowerContext context, Object recordValue, ResolvedType type, LongBuffer out) {
        var fields = type.fields();
        for (int i = 0; i < fields.size(); i++) {
            lowerFlatInto(
                    context, fieldOf(recordValue, i, fields.get(i)), fields.get(i).type(), out);
        }
    }

    private static void lowerFlatVariant(
            LiftLowerContext context, VariantValue value, ResolvedType type, LongBuffer out) {
        var cases = type.cases();
        int caseIndex = -1;
        for (int i = 0; i < cases.size(); i++) {
            if (cases.get(i).label().equals(value.label())) {
                caseIndex = i;
                break;
            }
        }
        if (caseIndex < 0) {
            throw new IllegalArgumentException(
                    "no case labeled '" + value.label() + "' in variant");
        }
        List<ValType> flatTypes = type.flatten(context.ptrType());
        if (flatTypes.get(0) != ValType.I32) {
            throw new IllegalStateException("variant discriminant flat type must be i32");
        }
        int payloadSlots = flatTypes.size() - 1;
        out.add(Integer.toUnsignedLong(caseIndex));
        var c = cases.get(caseIndex);
        int payloadStart = out.size();
        if (c.hasType()) {
            lowerFlatInto(context, value.value(), c.type(), out);
        }
        for (int i = out.size() - payloadStart; i < payloadSlots; i++) {
            out.add(0L);
        }
    }

    /**
     * Lifts an {@code own} handle, transferring ownership out of the source instance. The index is
     * removed from its table, so the component that held it can no longer name the resource.
     *
     * <p>Fails unless ownership is genuinely transferable. The index must name a handle of this
     * exact resource ownType, that handle must itself be owning rather than borrowed, and no borrow
     * of it may currently be outstanding.
     */
    static ResourceValue liftOwn(LiftLowerContext context, int index, ResolvedType ownType) {
        var rt = resourceTypeOf(ownType);
        var h = requireHandle(handles(context).remove(index), index);
        requireResourceType(h, rt, index);
        if (h.numLends() != 0) {
            throw new TrapException("cannot remove owned resource while borrowed");
        }
        if (!h.own()) {
            throw new TrapException(
                    "handle index " + index + " is a borrowed handle, expected an owned one");
        }
        return ResourceValue.owned(rt, h.rep());
    }

    /**
     * Lifts a {@code borrow} handle, leaving the source handle in place. The caller keeps it, and
     * only the right to use it for the duration of the call crosses the boundary.
     *
     * <p>Registering the source handle as a lender on the call's scope is what bounds that
     * duration. Until the call resolves the handle counts as lent and {@link #liftOwn} will refuse
     * to transfer it away.
     */
    static ResourceValue liftBorrow(LiftLowerContext context, int index, ResolvedType borrowType) {
        var rt = resourceTypeOf(borrowType);
        var h = requireHandle(handles(context).get(index), index);
        requireResourceType(h, rt, index);
        subtaskScope(context).addLender(h);
        return ResourceValue.borrowed(rt, h.rep());
    }

    /** Lowers an {@code own} handle by minting a fresh owning index in the destination. */
    static int lowerOwn(LiftLowerContext context, ResourceValue value, ResolvedType ownType) {
        var rt = resourceTypeOf(ownType);
        return handles(context).add(rt, value.rep(), true, null);
    }

    /**
     * Lowers a {@code borrow} handle, scoping it to the current call so that it must be dropped
     * before that call may return.
     *
     * <p>Lowering into the component that <em>implements</em> the resource type skips the table
     * entirely and passes the representation itself. A component receiving a borrow of its own
     * resource is handed a rep, not an index, and so must neither call {@code resource.rep} on it
     * nor drop it. That is sound because the only thing such a handle could have been used for is
     * recovering the rep it already is.
     */
    static int lowerBorrow(LiftLowerContext context, ResourceValue value, ResolvedType borrowType) {
        var rt = resourceTypeOf(borrowType);
        var handles = handles(context);
        if (rt.handleTable() == handles) {
            return value.rep();
        }
        var scope = taskScope(context);
        scope.borrow();
        return handles.add(rt, value.rep(), false, scope);
    }

    /**
     * The resource type an {@code own}/{@code borrow} denotes, settled when the type was resolved
     * against the space it was written in rather than looked up here.
     */
    private static ResourceTypeRef resourceTypeOf(ResolvedType type) {
        return (ResourceTypeRef) type.resourceType();
    }

    private static HandleTable handles(LiftLowerContext context) {
        return Objects.requireNonNull(
                context.handles(),
                "lifting or lowering a resource handle requires a handle table in the context");
    }

    /**
     * A handle table also holds waitables, waitable sets and error contexts, so an index naming one
     * of those where a resource handle is expected has to trap rather than be misread.
     */
    private static ResourceState requireHandle(Object element, int index) {
        if (!(element instanceof ResourceState)) {
            throw new TrapException("handle index " + index + " is not a resource handle");
        }
        return (ResourceState) element;
    }

    /**
     * Resource types are compared by identity, not structure, thus two instantiations of the same
     * component declare distinct resource types even though they share one declaration, and a
     * handle from one must not be usable against the other.
     */
    private static void requireResourceType(
            ResourceState resource, ResourceTypeRef expected, int index) {
        if (resource.resourceType() != expected) {
            throw new TrapException(
                    "handle index "
                            + index
                            + " used with the wrong type, "
                            + ResourceTypeRef.mismatch(expected, resource.resourceType()));
        }
    }

    private static BorrowScope.Callee taskScope(LiftLowerContext context) {
        var scope = context.borrowScope();
        if (!(scope instanceof BorrowScope.Callee)) {
            throw new IllegalStateException(
                    "lowering a borrow requires the context's borrow scope to be a task, got "
                            + scope);
        }
        return (BorrowScope.Callee) scope;
    }

    private static BorrowScope.Caller subtaskScope(LiftLowerContext context) {
        var scope = context.borrowScope();
        if (!(scope instanceof BorrowScope.Caller)) {
            throw new IllegalStateException(
                    "lifting a borrow requires the context's borrow scope to be a subtask, got "
                            + scope);
        }
        return (BorrowScope.Caller) scope;
    }

    /**
     * Bulk copies move through an intermediate {@code byte[]} because {@link Memory} exposes no
     * memory-to-memory primitive. Chunking bounds that buffer instead of letting it scale with the
     * list being copied.
     */
    private static final int COPY_CHUNK_BYTES = 64 * 1024;

    /**
     * Whether a component function of type {@code funcType}'s values can move directly between
     * {@code caller} and {@code callee} rather than through the lift/lower trampoline. A
     * {@code false} result indicates that the caller should fall back to using
     * {@link #liftFlatParams}/{@link #lowerFlatParams}. This rejects anything the transfer path
     * does not yet model rather than trying to be exhaustive.
     *
     * <p>Linear memory is required only where it is actually used. A function whose values all
     * travel as core Wasm values (no string, no unbounded list, and neither the parameters nor the
     * result spilling) never touches memory, so it transfers between contexts defined with no
     * {@code memory} canon opt at all. That covers the shapes
     * {@code canon lift}/{@code canon lower} take when declared with no options, which is common
     * for functions over integers, {@code enum}s and payload-free {@code variant}s.
     *
     * @param caller the context from which the call arrives, with parameters read from it and
     *     results written back into it
     * @param callee the context into which the call is dispatched, with parameters written into it
     *     and results read from it
     * @param funcType the component function type both sides agree on
     */
    public static boolean canTransfer(
            LiftLowerContext caller, LiftLowerContext callee, ResolvedFuncType funcType) {
        if (caller.isAsync() || callee.isAsync() || funcType.isAsync()) {
            return false;
        }
        if (caller.ptrType() != callee.ptrType()) {
            return false;
        }
        List<ResolvedType> paramTypes = paramTypesOf(funcType);
        List<ResolvedType> resultTypes = resultTypesOf(funcType);
        for (ResolvedType t : paramTypes) {
            if (!Transferability.isSupported(t)) {
                return false;
            }
        }
        for (ResolvedType t : resultTypes) {
            if (!Transferability.isSupported(t)) {
                return false;
            }
        }
        // Parameters move caller -> callee and results move callee -> caller, so each
        // direction needs memory on both ends and a realloc on the receiving end, but only
        // if anything actually goes through memory.
        if (needsMemory(caller, paramTypes, MAX_FLAT_PARAMS)
                && !canMoveThroughMemory(caller, callee)) {
            return false;
        }
        return !needsMemory(caller, resultTypes, MAX_FLAT_RESULTS)
                || canMoveThroughMemory(callee, caller);
    }

    /**
     * Whether component function type {@code funcType}'s values can be handed from {@code caller}
     * to {@code callee} with no adapter at all. If true, the caller's flat core arguments can be
     * passed to the callee's core function as they stand, and its core results returned without
     * lifting.
     *
     * <p>True only when nothing reaches linear memory (no string, no unbounded list, and neither
     * side spilling) and every flat slot survives untouched, which rules out {@code bool},
     * {@code char}, the narrow integers, sparse {@code flags} and every {@code variant}.
     *
     * <p>This covers the values only. A caller acting on it must also confirm the callee has no
     * post-return function to run. If it does, the values still need no work but the call itself
     * still needs a wrapper. Where both hold, the callee's core function can be registered as the
     * caller's import directly.
     */
    public static boolean isIdentityTransfer(
            LiftLowerContext caller, LiftLowerContext callee, ResolvedFuncType funcType) {
        if (!canTransfer(caller, callee, funcType)) {
            return false;
        }
        List<ResolvedType> paramTypes = paramTypesOf(funcType);
        List<ResolvedType> resultTypes = resultTypesOf(funcType);
        if (needsMemory(caller, paramTypes, MAX_FLAT_PARAMS)
                || needsMemory(caller, resultTypes, MAX_FLAT_RESULTS)) {
            return false;
        }
        for (ResolvedType t : paramTypes) {
            if (!Transferability.isFlatIdentity(caller.ptrType(), t)) {
                return false;
            }
        }
        for (ResolvedType t : resultTypes) {
            if (!Transferability.isFlatIdentity(caller.ptrType(), t)) {
                return false;
            }
        }
        return true;
    }

    /** A function type's parameter types, in order. */
    public static List<ResolvedType> paramTypesOf(ResolvedFuncType funcType) {
        List<ResolvedType> ts = new ArrayList<>(funcType.params().size());
        for (ResolvedFuncType.Param p : funcType.params()) {
            ts.add(p.type());
        }
        return ts;
    }

    /** A function type's result type as a list, empty when it returns nothing. */
    public static List<ResolvedType> resultTypesOf(ResolvedFuncType funcType) {
        return funcType.hasResult() ? List.of(funcType.result()) : List.of();
    }

    /**
     * Whether values of component types {@code types} reach linear memory at all. Either the flat
     * list exceeds {@code maxFlat} and spills into a tuple, or some value needs to be stored in
     * memory with a pointer.
     */
    private static boolean needsMemory(
            LiftLowerContext context, List<ResolvedType> types, int maxFlat) {
        if (types.isEmpty()) {
            return false;
        }
        if (flattenTypes(context, types).size() > maxFlat) {
            return true;
        }
        for (ResolvedType t : types) {
            if (contains(t, CanonicalAbi::storedInMemory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a value of this type must be stored in memory, behind a pointer the destination has
     * to allocate. A fixed-size list is stored inline and so needs no allocation of its own. Every
     * other {@code list}, including the despecialized form of a {@code map}, is a (pointer, length)
     * pair, as is a {@code string}.
     */
    private static boolean storedInMemory(ResolvedType type) {
        if (type.kind() == DefValType.Kind.STRING) {
            return true;
        }
        return type.kind() == DefValType.Kind.LIST;
    }

    /**
     * Whether values can be read out of {@code source}'s memory and allocated into {@code dest}'s.
     */
    private static boolean canMoveThroughMemory(LiftLowerContext source, LiftLowerContext dest) {
        return source.memory() != null && dest.memory() != null && dest.realloc() != null;
    }

    /**
     * Moves the value of type {@code type} at {@code sourcePointer} in {@code source}'s memory to
     * {@code destPoints} in {@code dest}'s memory, without materializing it as a Java value.
     *
     * <p>Observably equivalent to
     * {@code store(dest, load(source, sourcePointer, type), type, destPoints)}, with one deliberate
     * exception. {@code f32} and {@code f64} NaN payloads are preserved rather than canonicalized,
     * which the Canonical ABI explicitly permits ("hosts may instead choose to canonicalize to an
     * arbitrary fixed NaN value, or even to the original value of the NaN before lifting") and
     * which is what lets float-bearing aggregates copy in bulk. Every trap the lift path raises is
     * raised here too.
     *
     * <p>Both contexts must agree on {@link LiftLowerContext#ptrType()} and {@code dest} must
     * supply a {@code realloc} if {@code type} contains a string or an unbounded list.
     */
    public static void transfer(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            ResolvedType type) {
        requireTransferable(source, dest);
        if (Transferability.isBitwiseCopyable(source.ptrType(), type)) {
            copyBytes(source, dest, sourcePointer, destPointer, type.elementSize(source.ptrType()));
            return;
        }
        transferValue(source, dest, sourcePointer, destPointer, type);
    }

    /**
     * Transfers a function's flat core <em>parameter values</em> {@code flatArgs} of types
     * {@code types} from {@code source} to {@code dest}, returning the destination's flat
     * parameters.
     *
     * @see #transfer
     */
    public static long[] transferFlatParams(
            LiftLowerContext source,
            LiftLowerContext dest,
            long[] flatArgs,
            List<ResolvedType> types) {
        requireTransferable(source, dest);
        return transferFlatValues(
                source, dest, MAX_FLAT_PARAMS, new CoreValues(flatArgs), types, null);
    }

    /**
     * Transfers a function's flat core <em>results</em> {@code flatResults} of types {@code types}
     * from {@code source} to {@code dest}. When the results spill into linear memory,
     * {@code outParam}'s first element is the destination's caller-provided spill pointer and no
     * flat values are returned. When it is null, destination storage is freshly allocated and the
     * returned list is the spill pointer.
     *
     * @see #transfer
     */
    public static long[] transferFlatResults(
            LiftLowerContext source,
            LiftLowerContext dest,
            long[] flatResults,
            List<ResolvedType> types,
            long[] outParam) {
        requireTransferable(source, dest);
        return transferFlatValues(
                source, dest, MAX_FLAT_RESULTS, new CoreValues(flatResults), types, outParam);
    }

    /** Rejects context pairs the transfer path cannot serve. */
    static void requireTransferable(LiftLowerContext source, LiftLowerContext dest) {
        if (source.ptrType() != dest.ptrType()) {
            throw new IllegalArgumentException(
                    "cannot transfer between contexts with different pointer widths: "
                            + source.ptrType()
                            + " and "
                            + dest.ptrType());
        }
        if (source.isAsync() || dest.isAsync()) {
            throw new UnsupportedOperationException(
                    "transferring values between async contexts is not implemented yet");
        }
    }

    /**
     * Transfers a value that is known not to be wholly bitwise copyable, dispatching on its kind.
     * Source and destination offsets stay equal throughout. The two memories lay the value out
     * identically, so only the base pointers differ.
     */
    static void transferValue(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            ResolvedType type) {
        switch (type.kind()) {
            case BOOL:
                transferBool(source, dest, sourcePointer, destPointer);
                return;
            case U8:
            case S8:
                copyScalar(source, dest, sourcePointer, destPointer, 1);
                return;
            case U16:
            case S16:
                copyScalar(source, dest, sourcePointer, destPointer, 2);
                return;
            case U32:
            case S32:
            case F32:
                copyScalar(source, dest, sourcePointer, destPointer, 4);
                return;
            case U64:
            case S64:
            case F64:
                copyScalar(source, dest, sourcePointer, destPointer, 8);
                return;
            case CHAR:
                transferChar(source, dest, sourcePointer, destPointer);
                return;
            case STRING:
                transferString(source, dest, sourcePointer, destPointer);
                return;
            case LIST:
            case SIZED_LIST:
                transferList(source, dest, sourcePointer, destPointer, type);
                return;
            case RECORD:
                transferRecord(source, dest, sourcePointer, destPointer, type);
                return;
            case VARIANT:
                transferVariant(source, dest, sourcePointer, destPointer, type);
                return;
            case FLAGS:
                transferFlags(
                        source,
                        dest,
                        sourcePointer,
                        destPointer,
                        type.elementSize(source.ptrType()),
                        Transferability.flagsMask(type));
                return;
            case ERROR_CONTEXT:
            case OWN:
            case BORROW:
            case STREAM:
            case FUTURE:
                throw new UnsupportedOperationException(
                        "transferring " + type.kind() + " values is not implemented yet");
            default:
                throw new IllegalStateException("unhandled kind " + type.kind());
        }
    }

    /** Copies {@code length} bytes verbatim, in bounded chunks. */
    static void copyBytes(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            int length) {
        var sourceMemory = source.memory();
        var destMemory = dest.memory();
        int copied = 0;
        while (copied < length) {
            int n = Math.min(COPY_CHUNK_BYTES, length - copied);
            destMemory.write(
                    destPointer + copied, sourceMemory.readBytes(sourcePointer + copied, n));
            copied += n;
        }
    }

    /** Copies a single naturally-sized scalar, avoiding the {@code byte[]} bulk path. */
    static void copyScalar(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            int numBytes) {
        switch (numBytes) {
            case 1:
                dest.memory().writeByte(destPointer, source.memory().read(sourcePointer));
                return;
            case 2:
                dest.memory().writeShort(destPointer, source.memory().readShort(sourcePointer));
                return;
            case 4:
                dest.memory().writeI32(destPointer, source.memory().readInt(sourcePointer));
                return;
            case 8:
                dest.memory().writeLong(destPointer, source.memory().readLong(sourcePointer));
                return;
            default:
                throw new IllegalArgumentException("unsupported scalar width " + numBytes);
        }
    }

    /** Normalizes any non-zero source byte to {@code 1}, as lifting then lowering would. */
    static void transferBool(
            LiftLowerContext source, LiftLowerContext dest, int sourcePointer, int destPointer) {
        dest.memory()
                .writeByte(
                        destPointer, (byte) (source.memory().readU8(sourcePointer) != 0 ? 1 : 0));
    }

    /** Copies a {@code char}, trapping on surrogates and out-of-range scalar values. */
    static void transferChar(
            LiftLowerContext source, LiftLowerContext dest, int sourcePointer, int destPointer) {
        int c = convertI32ToChar(loadInt(source.memory(), sourcePointer, 4, false));
        storeInt(dest.memory(), c, destPointer, 4);
    }

    /** Copies a {@code flags} value, dropping the slack bits lifting would discard. */
    static void transferFlags(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            int size,
            long mask) {
        storeInt(
                dest.memory(),
                loadInt(source.memory(), sourcePointer, size, false) & mask,
                destPointer,
                size);
    }

    private static void transferRecord(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            ResolvedType type) {
        int off = 0;
        for (ResolvedType.Field f : type.fields()) {
            off = DefValType.alignTo(off, f.type().alignment(source.ptrType()));
            transferValue(source, dest, sourcePointer + off, destPointer + off, f.type());
            off += f.type().elementSize(source.ptrType());
        }
    }

    /**
     * Transfers a variant, validating the discriminant and then only the payload of the case it
     * selects. The bytes of the wider cases the source did not use are left as the destination
     * allocator produced them, exactly as {@code store} leaves them.
     *
     * <p>The payload offset is computed relative to the value's own base rather than by aligning
     * the absolute pointer as {@code load}/{@code store} do. The two agree whenever the pointer is
     * aligned to the variant's alignment, which the Canonical ABI requires, and only the relative
     * form keeps the source and destination offsets identical.
     */
    private static void transferVariant(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            ResolvedType type) {
        var cases = type.cases();
        int discSize = type.discriminantSize();
        long caseIndex = loadInt(source.memory(), sourcePointer, discSize, false);
        if (caseIndex >= cases.size()) {
            throw new TrapException("invalid variant discriminant");
        }
        storeInt(dest.memory(), caseIndex, destPointer, discSize);
        var c = cases.get((int) caseIndex);
        if (!c.hasType()) {
            return;
        }
        int payloadOff = DefValType.alignTo(discSize, type.maxCaseAlignment(source.ptrType()));
        transferValue(source, dest, sourcePointer + payloadOff, destPointer + payloadOff, c.type());
    }

    private static void transferList(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            ResolvedType type) {
        if (type.isFixedSizeList()) {
            transferListElements(
                    source, dest, sourcePointer, destPointer, type.fixedSize(), type.element());
            return;
        }
        transferUnboundedList(source, dest, sourcePointer, destPointer, type.element());
    }

    static void transferUnboundedList(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            ResolvedType elementType) {
        int ptrSize = source.ptrType().size();
        int begin = (int) loadInt(source.memory(), sourcePointer, ptrSize, false);
        int length = (int) loadInt(source.memory(), sourcePointer + ptrSize, ptrSize, false);
        int destBegin = transferListIntoRange(source, dest, begin, length, elementType);
        storeInt(dest.memory(), destBegin, destPointer, ptrSize);
        storeInt(dest.memory(), length, destPointer + ptrSize, ptrSize);
    }

    /**
     * Validates a source list's range, allocates the matching range in the destination and moves
     * the elements into it, returning the destination pointer.
     *
     * <p>The length is treated as unsigned, so a list with more than {@code 2^31} elements traps on
     * the byte-length check. {@link #loadListFromRange} computes the same product with a signed
     * length and lets such a list through as if it were empty.
     */
    static int transferListIntoRange(
            LiftLowerContext source,
            LiftLowerContext dest,
            int begin,
            int length,
            ResolvedType elementType) {
        int elemSize = elementType.elementSize(source.ptrType());
        int elemAlignment = elementType.alignment(source.ptrType());
        long byteLength = Integer.toUnsignedLong(length) * elemSize;
        if (byteLength > MAX_LIST_BYTE_LENGTH) {
            throw new TrapException(
                    "list byte length exceeds the maximum of " + MAX_LIST_BYTE_LENGTH);
        }
        if (begin != DefValType.alignTo(begin, elemAlignment)) {
            throw new TrapException(
                    "list pointer " + begin + " is not aligned to " + elemAlignment);
        }
        if (Integer.toUnsignedLong(begin) + byteLength > Memory.bytes(source.memory().pages())) {
            throw new TrapException(
                    "list of length " + length + " at " + begin + " is out of bounds");
        }
        int destBegin = allocate(dest, elemAlignment, (int) byteLength);
        transferListElements(source, dest, begin, destBegin, length, elementType);
        return destBegin;
    }

    /**
     * Moves {@code length} elements. When the element type copies verbatim the whole block becomes
     * one bulk copy.
     */
    private static void transferListElements(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            int length,
            ResolvedType elementType) {
        int elemSize = elementType.elementSize(source.ptrType());
        if (Transferability.isBitwiseCopyable(source.ptrType(), elementType)) {
            copyBytes(source, dest, sourcePointer, destPointer, length * elemSize);
            return;
        }
        for (int i = 0; i < length; i++) {
            transferValue(
                    source,
                    dest,
                    sourcePointer + i * elemSize,
                    destPointer + i * elemSize,
                    elementType);
        }
    }

    static void transferString(
            LiftLowerContext source, LiftLowerContext dest, int sourcePointer, int destPointer) {
        int ptrSize = source.ptrType().size();
        int begin = (int) loadInt(source.memory(), sourcePointer, ptrSize, false);
        long taggedCodeUnits = loadInt(source.memory(), sourcePointer + ptrSize, ptrSize, false);
        var result = transferStringIntoRange(source, dest, begin, taggedCodeUnits);
        storeInt(dest.memory(), result.pointer, destPointer, ptrSize);
        storeInt(dest.memory(), result.codeUnits, destPointer + ptrSize, ptrSize);
    }

    /**
     * Moves a string's bytes into a fresh destination allocation, transcoding only when the two
     * encodings disagree.
     *
     * <p>When the source bytes are already in the destination's encoding they are validated in
     * place and copied with no decode, no re-encode, and no lifting to {@link String}. That covers
     * the common case of two modules that agreed on UTF-8. The {@code latin1+utf16} pairings are
     * handled at the byte level too, because a tagged UTF-16 source still has to be re-checked
     * against Latin-1. Everything else decodes to a {@link CharBuffer} and re-encodes from it,
     * still without a life to {@code String}.
     */
    static PointerAndCodeUnits transferStringIntoRange(
            LiftLowerContext source, LiftLowerContext dest, int pointer, long taggedCodeUnits) {
        var range = stringRange(source, taggedCodeUnits);
        byte[] bytes = readStringBytes(source, pointer, range);
        var destEncoding = dest.stringEncoding();
        if (range.charset == StandardCharsets.UTF_8) {
            if (destEncoding == StringEncoding.UTF8) {
                validateUtf8(bytes);
                return new PointerAndCodeUnits(allocateAndWrite(dest, 1, bytes), bytes.length);
            }
        } else if (range.charset == StandardCharsets.UTF_16LE) {
            if (destEncoding == StringEncoding.UTF16) {
                validateUtf16Le(bytes);
                return new PointerAndCodeUnits(allocateAndWrite(dest, 2, bytes), bytes.length / 2);
            }
            if (destEncoding == StringEncoding.LATIN1_UTF16) {
                return transferUtf16ToLatin1OrUtf16(dest, bytes);
            }
        } else if (destEncoding == StringEncoding.LATIN1_UTF16) {
            // A Latin-1 source can only come from a latin1+utf16 context, so every byte is
            // valid and fits, so it stays untagged and copies as-is.
            return new PointerAndCodeUnits(allocateAndWrite(dest, 2, bytes), bytes.length);
        }
        return storeStringIntoRange(dest, decodeStrictToChars(bytes, range.charset));
    }

    private static PointerAndCodeUnits transferUtf16ToLatin1OrUtf16(
            LiftLowerContext dest, byte[] utf16Bytes) {
        validateUtf16Le(utf16Bytes);
        if (!fitsLatin1Utf16(utf16Bytes)) {
            return new PointerAndCodeUnits(
                    allocateAndWrite(dest, 2, utf16Bytes),
                    (utf16Bytes.length / 2) | utf16Tag(dest.ptrType()));
        }
        byte[] latin1 = new byte[utf16Bytes.length / 2];
        for (int i = 0; i < latin1.length; i++) {
            latin1[i] = utf16Bytes[2 * i];
        }
        return new PointerAndCodeUnits(allocateAndWrite(dest, 2, latin1), latin1.length);
    }

    /** Whether every UTF-16LE code unit is below {@code 0x100}, i.e. its high byte is zero. */
    private static boolean fitsLatin1Utf16(byte[] utf16Bytes) {
        for (int i = 1; i < utf16Bytes.length; i += 2) {
            if (utf16Bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Accepts exactly what a strict {@link StandardCharsets#UTF_8} decoder accepts, without
     * producing any characters. Rejects continuation bytes out of place, truncated sequences,
     * overlong encodings, surrogates and scalar values above {@code U+10FFFF}.
     */
    static void validateUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b0 = bytes[i] & 0xFF;
            if (b0 < 0x80) {
                i++;
                continue;
            }
            int length;
            int codePoint;
            if (b0 >= 0xC2 && b0 <= 0xDF) {
                length = 2;
                codePoint = b0 & 0x1F;
            } else if (b0 >= 0xE0 && b0 <= 0xEF) {
                length = 3;
                codePoint = b0 & 0x0F;
            } else if (b0 >= 0xF0 && b0 <= 0xF4) {
                length = 4;
                codePoint = b0 & 0x07;
            } else {
                throw invalidUtf8();
            }
            if (i + length > bytes.length) {
                throw incompleteUtf8();
            }
            for (int k = 1; k < length; k++) {
                int bk = bytes[i + k] & 0xFF;
                if ((bk & 0xC0) != 0x80) {
                    throw invalidUtf8();
                }
                codePoint = (codePoint << 6) | (bk & 0x3F);
            }
            if (length == 3 && (codePoint < 0x800 || isSurrogate(codePoint))) {
                throw invalidUtf8();
            }
            if (length == 4 && (codePoint < 0x10000 || codePoint > 0x10FFFF)) {
                throw invalidUtf8();
            }
            i += length;
        }
    }

    private static boolean isSurrogate(int codePoint) {
        return codePoint >= MIN_SURROGATE && codePoint <= MAX_SURROGATE;
    }

    private static TrapException invalidUtf8() {
        return invalidEncoding(StandardCharsets.UTF_8);
    }

    private static TrapException incompleteUtf8() {
        return incompleteEncoding(StandardCharsets.UTF_8);
    }

    private static TrapException invalidEncoding(Charset charset) {
        return new TrapException("invalid " + encodingName(charset) + " byte sequence");
    }

    private static TrapException incompleteEncoding(Charset charset) {
        return new TrapException("incomplete " + encodingName(charset) + " byte sequence");
    }

    private static String encodingName(Charset charset) {
        return charset.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Accepts exactly what a strict {@link StandardCharsets#UTF_16LE} decoder accepts. Every high
     * surrogate must be followed by a low surrogate, and no low surrogate may stand alone.
     */
    static void validateUtf16Le(byte[] bytes) {
        int i = 0;
        while (i + 1 < bytes.length) {
            int unit = codeUnitAt(bytes, i);
            i += 2;
            if (unit >= 0xDC00 && unit <= MAX_SURROGATE) {
                // A low surrogate can only appear as the second half of a pair, which the
                // high-surrogate branch below consumes.
                throw invalidUtf16();
            }
            if (unit < MIN_SURROGATE || unit > 0xDBFF) {
                continue;
            }
            if (i + 1 >= bytes.length) {
                throw invalidUtf16();
            }
            int low = codeUnitAt(bytes, i);
            i += 2;
            if (low < 0xDC00 || low > MAX_SURROGATE) {
                throw invalidUtf16();
            }
        }
    }

    private static int codeUnitAt(byte[] bytes, int index) {
        return (bytes[index] & 0xFF) | ((bytes[index + 1] & 0xFF) << 8);
    }

    private static TrapException invalidUtf16() {
        return invalidEncoding(StandardCharsets.UTF_16LE);
    }

    /**
     * Transfers a flat parameter or result list. Because neither context is async (since async is
     * not supported yet) and both share a pointer width, the two sides always agree on whether the
     * values spill into linear memory. Either both pass them flat, or both pass a pointer to the
     * same tuple layout, which reduces to a single {@link #transfer} of that tuple.
     */
    private static long[] transferFlatValues(
            LiftLowerContext source,
            LiftLowerContext dest,
            int maxFlat,
            CoreValues coreValues,
            List<ResolvedType> types,
            long[] outParam) {
        if (types.isEmpty()) {
            return EMPTY_CORE_VALUES;
        }
        List<ValType> flatTypes = flattenTypes(source, types);
        if (flatTypes.size() > maxFlat) {
            var tupleType = ResolvedType.tupleOf(types);
            return transferSpilledValues(
                    source,
                    dest,
                    coreValues,
                    tupleType.alignment(source.ptrType()),
                    tupleType.elementSize(source.ptrType()),
                    outParam,
                    (s, d, sourcePointer, destPointer) ->
                            transfer(s, d, sourcePointer, destPointer, tupleType));
        }
        LongBuffer out = new LongBuffer();
        for (ResolvedType t : types) {
            transferFlat(source, dest, coreValues, out, t);
        }
        return out.toArray();
    }

    /**
     * Handles the spilled case shared by the interpreted and compiled transfer paths. Reads the
     * source's spill pointer, obtains the destination's (freshly allocated, or the caller-provided
     * out-param), validates both, and hands them to {@code step}, which moves the tuple.
     */
    static long[] transferSpilledValues(
            LiftLowerContext source,
            LiftLowerContext dest,
            CoreValues coreValues,
            int align,
            int size,
            long[] outParam,
            TransferPlan.Step step) {
        int sourcePointer = (int) coreValues.next();
        if (sourcePointer != DefValType.alignTo(sourcePointer, align)) {
            throw new TrapException("unaligned pointer");
        }
        if ((long) sourcePointer + size > Memory.bytes(source.memory().pages())) {
            throw new TrapException("spilled values at " + sourcePointer + " are out of bounds");
        }
        int destPointer;
        long[] flatVals;
        if (outParam == null) {
            destPointer = allocate(dest, align, size);
            flatVals = new long[] {Integer.toUnsignedLong(destPointer)};
        } else {
            destPointer = (int) outParam[0];
            if (destPointer != DefValType.alignTo(destPointer, align)) {
                throw new TrapException("unaligned pointer");
            }
            if ((long) destPointer + size > Memory.bytes(dest.memory().pages())) {
                throw new TrapException("spill out-param at " + destPointer + " is out of bounds");
            }
            flatVals = EMPTY_CORE_VALUES;
        }
        step.run(source, dest, sourcePointer, destPointer);
        return flatVals;
    }

    /**
     * Transfers one value's worth of flat core values from {@code coreValues} to {@code out}.
     *
     * <p>Scalars mostly pass straight through, but not unconditionally. Lifting narrows
     * {@code u8}/{@code u16} to their type's width and sign-extends {@code s8}/{@code s16} before
     * lowering re-encodes them, so a source core value with bits set above the type's width must be
     * normalized here rather than forwarded. {@code bool} collapses to {@code 0}/{@code 1},
     * {@code char} is validated, {@code flags} drops its slack bits, and {@code f32}/{@code f64}
     * keep their exact bit pattern.
     */
    static void transferFlat(
            LiftLowerContext source,
            LiftLowerContext dest,
            CoreValues coreValues,
            LongBuffer out,
            ResolvedType type) {
        switch (type.kind()) {
            case BOOL:
                out.add((coreValues.next() & 0xFFFFFFFFL) != 0 ? 1L : 0L);
                return;
            case U8:
                out.add(coreValues.next() & 0xFFL);
                return;
            case U16:
                out.add(coreValues.next() & 0xFFFFL);
                return;
            case S8:
                out.add(Integer.toUnsignedLong((byte) coreValues.next()));
                return;
            case S16:
                out.add(Integer.toUnsignedLong((short) coreValues.next()));
                return;
            case U32:
            case S32:
            case F32:
                out.add(coreValues.next() & 0xFFFFFFFFL);
                return;
            case U64:
            case S64:
            case F64:
                out.add(coreValues.next());
                return;
            case CHAR:
                out.add(convertI32ToChar(coreValues.next() & 0xFFFFFFFFL));
                return;
            case STRING:
                transferFlatString(source, dest, coreValues, out);
                return;
            case LIST:
                transferFlatList(source, dest, coreValues, out, type);
                return;
            case RECORD:
                for (ResolvedType.Field f : type.fields()) {
                    transferFlat(source, dest, coreValues, out, f.type());
                }
                return;
            case VARIANT:
                transferFlatVariant(source, dest, coreValues, out, type);
                return;
            case FLAGS:
                out.add(coreValues.next() & Transferability.flagsMask(type) & 0xFFFFFFFFL);
                return;
            case ERROR_CONTEXT:
            case OWN:
            case BORROW:
            case STREAM:
            case FUTURE:
                throw new UnsupportedOperationException(
                        "transferring " + type.kind() + " values is not implemented yet");
            default:
                throw new IllegalStateException("unhandled kind " + type.kind());
        }
    }

    static void transferFlatString(
            LiftLowerContext source, LiftLowerContext dest, CoreValues coreValues, LongBuffer out) {
        int ptr = (int) coreValues.next();
        long taggedCodeUnits = coreValues.next() & 0xFFFFFFFFL;
        var result = transferStringIntoRange(source, dest, ptr, taggedCodeUnits);
        out.add(Integer.toUnsignedLong(result.pointer));
        out.add(result.codeUnits & 0xFFFFFFFFL);
    }

    private static void transferFlatList(
            LiftLowerContext source,
            LiftLowerContext dest,
            CoreValues coreValues,
            LongBuffer out,
            ResolvedType type) {
        var elementType = type.element();
        if (type.isFixedSizeList()) {
            for (int i = 0; i < type.fixedSize(); i++) {
                transferFlat(source, dest, coreValues, out, elementType);
            }
            return;
        }
        transferFlatUnboundedList(source, dest, coreValues, out, elementType);
    }

    static void transferFlatUnboundedList(
            LiftLowerContext source,
            LiftLowerContext dest,
            CoreValues coreValues,
            LongBuffer out,
            ResolvedType elementType) {
        int ptr = (int) coreValues.next();
        int length = (int) coreValues.next();
        int destPointer = transferListIntoRange(source, dest, ptr, length, elementType);
        out.add(Integer.toUnsignedLong(destPointer));
        out.add(Integer.toUnsignedLong(length));
    }

    /**
     * Transfers a variant across the joined flat layout, consuming and producing exactly the slots
     * {@code flatten_variant} defines regardless of which case is present. The source cursor skips
     * the joined slots the chosen case left unused, and the destination pads them with {@code 0},
     * mirroring {@link #liftFlatVariant} and {@link #lowerFlatVariant}.
     */
    private static void transferFlatVariant(
            LiftLowerContext source,
            LiftLowerContext dest,
            CoreValues coreValues,
            LongBuffer out,
            ResolvedType type) {
        var cases = type.cases();
        List<ValType> flatTypes = type.flatten(source.ptrType());
        if (flatTypes.get(0) != ValType.I32) {
            throw new IllegalStateException("variant discriminant flat type must be i32");
        }
        int payloadSlots = flatTypes.size() - 1;
        long caseIndex = coreValues.next() & 0xFFFFFFFFL;
        if (caseIndex >= cases.size()) {
            throw new TrapException("invalid variant discriminant");
        }
        out.add(caseIndex);
        var c = cases.get((int) caseIndex);
        int sourcePayloadStart = coreValues.position();
        int destPayloadStart = out.size();
        if (c.hasType()) {
            transferFlat(source, dest, coreValues, out, c.type());
        }
        coreValues.skipTo(sourcePayloadStart + payloadSlots);
        for (int i = out.size() - destPayloadStart; i < payloadSlots; i++) {
            out.add(0L);
        }
    }

    /** A growable primitive-{@code long} buffer used to accumulate flat lowered values. */
    static final class LongBuffer {
        private long[] buf = new long[8];
        private int size;

        void add(long v) {
            if (size == buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[size++] = v;
        }

        int size() {
            return size;
        }

        long[] toArray() {
            return Arrays.copyOf(buf, size);
        }
    }
}
