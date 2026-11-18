package io.roastedroot.typewire.abi;

import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.ResolvedType;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Static analysis backing the direct memory-to-memory transfer path. Distinguishes which
 * component-level types can move between two core module memories as a verbatim byte copy,
 * and which have to be walked because lifting and lowering are not each other's exact
 * inverse.
 *
 * <p>A type is <em>bitwise copyable</em> only if, for every possible byte pattern in the
 * source, {@code store(dst, load(src, t), t)} would reproduce those bytes unchanged. That
 * rules out more than it might first appear:
 *
 * <ul>
 *   <li>{@code bool} — a source byte of {@code 0x02} lifts to {@code true} and lowers back
 *       to {@code 0x01}.
 *   <li>{@code char} — lifting traps on surrogates and on values at or above {@code
 *       0x110000}, so the bytes must be inspected even though a valid one copies unchanged.
 *   <li>{@code flags} — lifting keeps only the low {@code labels.size()} bits and lowering
 *       re-packs the rest as zero, so a type with slack bits in its storage width needs
 *       masking. Flags that exactly fill their width ({@code 8}, {@code 16} or {@code 32}
 *       labels) have no slack and do copy verbatim.
 *   <li>{@code string} and unbounded {@code list} — the payload lives behind a pointer that
 *       must be re-allocated in the destination, and strings may additionally need
 *       transcoding.
 *   <li>{@code variant} (and its {@code enum}/{@code option}/{@code result} specializations),
 *       in which case the discriminant has to be range-checked against the case list.
 * </ul>
 *
 * <p>Integers copy verbatim. Floats do as well {@code f32}/{@code f64} as the Canonical ABI
 * explicitly permits a host to preserve "the original value of the NaN before lifting, allowing
 * them to optimize away both the canonicalization of lifting and the randomization of lowering".
 *
 * <p>Aggregates are copyable when all of their leaves are. Padding inside a record and
 * between the elements of a fixed-size list is copied along with the fields, which
 * substitutes the source's undefined bytes for the destination allocator's, thus turning a record
 * of integers into a single copy.
 *
 * <p>All results assume the source and destination agree on {@link PointerType}; callers
 * establish that before consulting this class, because a differing pointer width changes
 * the layout of every pointer-sized field.
 */
final class Transferability {

    private Transferability() {}

    /**
     * Whether a value of type {@code t} occupies the same bytes in both memories and can be moved
     * with a single verbatim copy, with no inspection, validation, or normalization.
     */
    static boolean isBitwiseCopyable(PointerType ptrType, ResolvedType t) {
        return isBitwiseCopyable(ptrType, t, new IdentityHashMap<>());
    }

    private static boolean isBitwiseCopyable(
            PointerType ptrType, ResolvedType t, Map<ResolvedType, Boolean> cache) {
        Boolean cached = cache.get(t);
        if (cached != null) {
            return cached;
        }
        boolean result = computeBitwiseCopyable(ptrType, t, cache);
        cache.put(t, result);
        return result;
    }

    private static boolean computeBitwiseCopyable(
            PointerType ptrType, ResolvedType t, Map<ResolvedType, Boolean> cache) {
        switch (t.kind()) {
            case U8:
            case U16:
            case U32:
            case U64:
            case S8:
            case S16:
            case S32:
            case S64:
            case F32:
            case F64:
                return true;
            case BOOL:
            case CHAR:
            case STRING:
            case VARIANT:
            case LIST:
            case ERROR_CONTEXT:
            case OWN:
            case BORROW:
            case STREAM:
            case FUTURE:
                return false;
            case FLAGS:
                return flagsFillWidth(ptrType, t);
            case RECORD:
                for (ResolvedType.Field f : t.fields()) {
                    if (!isBitwiseCopyable(ptrType, f.type(), cache)) {
                        return false;
                    }
                }
                return true;
            case SIZED_LIST:
                // Only a fixed-size list is stored inline. An unbounded one (including a
                // despecialized map) is a (pointer, length) pair whose payload must be
                // re-allocated in the destination.
                return isBitwiseCopyable(ptrType, t.element(), cache);
            default:
                throw new IllegalStateException("unhandled kind " + t.kind());
        }
    }

    /**
     * Whether transferring a value of type {@code t} through the flat (core value) path leaves
     * every slot unchanged, requiring no masking, no sign extension, no validation, and no memory access.
     * If true, the source's core values can be handed to the destination directly.
     *
     * <p>This is <em>not</em> {@link #isBitwiseCopyable}, and the two disagree in the
     * direction. In memory a {@code u8} occupies a single byte and copies verbatim, and eight {@code flags}
     * labels exactly fill their byte, thus both are bitwise copyable. Flattened, each occupies a whole {@code i32}
     * slot with slack bits above it that lifting discards and lowering re-zeroes, so neither is a flat identity.
     * Flat identity is the stricter of the two for every narrow type.
     */
    static boolean isFlatIdentity(PointerType ptrType, ResolvedType t) {
        return isFlatIdentity(ptrType, t, new IdentityHashMap<>());
    }

    private static boolean isFlatIdentity(
            PointerType ptrType, ResolvedType t, Map<ResolvedType, Boolean> cache) {
        Boolean cached = cache.get(t);
        if (cached != null) {
            return cached;
        }
        boolean result = computeFlatIdentity(ptrType, t, cache);
        cache.put(t, result);
        return result;
    }

    private static boolean computeFlatIdentity(
            PointerType ptrType, ResolvedType t, Map<ResolvedType, Boolean> cache) {
        switch (t.kind()) {
            case U32:
            case S32:
            case U64:
            case S64:
            case F32:
            case F64:
                return true;
            case BOOL:
            case CHAR:
            case U8:
            case U16:
            case S8:
            case S16:
            case STRING:
            case VARIANT:
            case LIST:
            case ERROR_CONTEXT:
            case OWN:
            case BORROW:
            case STREAM:
            case FUTURE:
                // Narrow integers are masked or sign-extended to their type's width, bool
                // collapses to 0/1, char is range-checked, and a variant's discriminant is
                // validated and its unused joined slots zero-padded.
                return false;
            case FLAGS:
                return flagsFillFlatSlot(t);
            case RECORD:
                for (ResolvedType.Field f : t.fields()) {
                    if (!isFlatIdentity(ptrType, f.type(), cache)) {
                        return false;
                    }
                }
                return true;
            case SIZED_LIST:
                // A fixed-size list flattens to its elements laid end to end; an unbounded
                // one is a (pointer, length) pair whose payload has to be re-allocated.
                return isFlatIdentity(ptrType, t.element(), cache);
            default:
                throw new IllegalStateException("unhandled kind " + t.kind());
        }
    }

    /**
     * Whether every type reachable from type {@code t} is one that the transfer path can carry,
     * so that a caller can decide up front to fall back rather than trapping partway through.
     *
     * <p>{@code own} and {@code borrow} are rejected permanently rather than pending: a handle
     * is an index into one component instance's table, and the bytes that encode it are
     * meaningless in another's, so handles always take the lift/lower path. The async value
     * types are rejected because they are not modeled here yet.
     */
    static boolean isSupported(ResolvedType t) {
        return !CanonicalAbi.contains(t, Transferability::isUnsupportedKind);
    }

    private static boolean isUnsupportedKind(ResolvedType t) {
        switch (t.kind()) {
            case ERROR_CONTEXT:
            case OWN:
            case BORROW:
            case STREAM:
            case FUTURE:
                return true;
            default:
                return false;
        }
    }

    /** The bit mask lifting a {@code flags} value applies, i.e. one bit per label. */
    static long flagsMask(ResolvedType t) {
        int n = t.labels().size();
        return n >= Long.SIZE ? -1L : (1L << n) - 1;
    }

    /**
     * Whether type {@code t}'s labels exactly fill its <em>in-memory</em> storage width, leaving
     * no slack bits for lifting to drop and lowering to re-zero.
     */
    static boolean flagsFillWidth(PointerType ptrType, ResolvedType t) {
        return t.labels().size() >= t.elementSize(ptrType) * Byte.SIZE;
    }

    /**
     * Whether type {@code t}'s labels fill its <em>flattened</em> slot, which is always an
     * {@code i32}.
     */
    static boolean flagsFillFlatSlot(ResolvedType t) {
        return t.labels().size() >= Integer.SIZE;
    }
}
