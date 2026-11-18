package io.roastedroot.typewire.abi;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.ResolvedType;
import java.util.ArrayList;
import java.util.List;
import run.endive.runtime.TrapException;

/**
 * A compiled memory-to-memory transfer for one type: the traversal {@link
 * CanonicalAbi#transfer} performs per call, flattened once into a straight-line list of
 * steps at fixed offsets.
 *
 * <p>Beyond skipping the repeated type walk, compiling enables the <em>span coalescing</em>
 * optimization the interpreted path cannot easily reach. Neighboring fields that
 * copy verbatim merge into a single {@code memcpy}, and merging continues across the padding
 * between them, so a {@code record { a: u8, b: u32 }} becomes one eight-byte copy rather
 * than two field copies. A run of copies is only broken by a field that needs real work, such
 * as a string to be transcoded, a list pointer to be re-allocated, or a discriminant to be validated.
 *
 * <p>A plan is bound to its {@link ResolvedType} and a {@link PointerType}, but not to any pair of contexts.
 * The same plan runs for any source and destination that agree on those. Offsets are shared between the two
 * sides, which holds because a matching pointer width gives the value an identical layout in both memories.
 */
final class TransferPlan {

    /** One step of a compiled transfer, applied at a fixed offset from both base pointers. */
    interface Step {
        void run(LiftLowerContext src, LiftLowerContext dst, int srcPtr, int dstPtr);
    }

    private final Step[] steps;

    private TransferPlan(Step[] steps) {
        this.steps = steps;
    }

    void run(LiftLowerContext src, LiftLowerContext dst, int srcPtr, int dstPtr) {
        for (Step step : steps) {
            step.run(src, dst, srcPtr, dstPtr);
        }
    }

    /** The number of steps left after coalescing, exposed for test assertions. */
    int stepCount() {
        return steps.length;
    }

    static TransferPlan compile(PointerType ptrType, ResolvedType t) {
        var builder = new Builder(ptrType);
        builder.append(t, 0);
        return builder.build();
    }

    /** Accumulates steps, merging adjacent verbatim copies as they are appended. */
    private static final class Builder {

        private final PointerType ptrType;
        private final List<Step> steps = new ArrayList<>();
        private int pendingStart = NO_PENDING;
        private int pendingEnd;

        private static final int NO_PENDING = -1;

        Builder(PointerType ptrType) {
            this.ptrType = ptrType;
        }

        TransferPlan build() {
            flush();
            return new TransferPlan(steps.toArray(new Step[0]));
        }

        private void copy(int off, int length) {
            if (length == 0) {
                return;
            }
            if (pendingStart == NO_PENDING) {
                pendingStart = off;
            }
            pendingEnd = Math.max(pendingEnd, off + length);
        }

        private void step(Step step) {
            flush();
            steps.add(step);
        }

        private void flush() {
            if (pendingStart == NO_PENDING) {
                return;
            }
            int off = pendingStart;
            int length = pendingEnd - pendingStart;
            pendingStart = NO_PENDING;
            if (length == 1 || length == 2 || length == 4 || length == 8) {
                steps.add(
                        (src, dst, srcPtr, dstPtr) ->
                                CanonicalAbi.copyScalar(
                                        src, dst, srcPtr + off, dstPtr + off, length));
                return;
            }
            steps.add(
                    (src, dst, srcPtr, dstPtr) ->
                            CanonicalAbi.copyBytes(src, dst, srcPtr + off, dstPtr + off, length));
        }

        private int sizeOf(ResolvedType t) {
            return t.elementSize(ptrType);
        }

        private int alignmentOf(ResolvedType t) {
            return t.alignment(ptrType);
        }

        void append(ResolvedType t, int off) {
            if (Transferability.isBitwiseCopyable(ptrType, t)) {
                copy(off, sizeOf(t));
                return;
            }
            switch (t.kind()) {
                case BOOL:
                    step(
                            (src, dst, srcPtr, dstPtr) ->
                                    CanonicalAbi.transferBool(
                                            src, dst, srcPtr + off, dstPtr + off));
                    return;
                case CHAR:
                    step(
                            (src, dst, srcPtr, dstPtr) ->
                                    CanonicalAbi.transferChar(
                                            src, dst, srcPtr + off, dstPtr + off));
                    return;
                case FLAGS:
                    appendFlags(t, off);
                    return;
                case STRING:
                    step(
                            (src, dst, srcPtr, dstPtr) ->
                                    CanonicalAbi.transferString(
                                            src, dst, srcPtr + off, dstPtr + off));
                    return;
                case RECORD:
                    appendRecord(t, off);
                    return;
                case VARIANT:
                    appendVariant(t, off);
                    return;
                case LIST:
                case SIZED_LIST:
                    appendList(t, off);
                    return;
                case ERROR_CONTEXT:
                case OWN:
                case BORROW:
                case STREAM:
                case FUTURE:
                    throw new UnsupportedOperationException(
                            "transferring " + t.kind() + " values is not implemented yet");
                default:
                    throw new IllegalStateException("unhandled kind " + t.kind());
            }
        }

        private void appendFlags(ResolvedType t, int off) {
            int size = sizeOf(t);
            long mask = Transferability.flagsMask(t);
            step(
                    (src, dst, srcPtr, dstPtr) ->
                            CanonicalAbi.transferFlags(
                                    src, dst, srcPtr + off, dstPtr + off, size, mask));
        }

        private void appendRecord(ResolvedType t, int off) {
            int fieldOff = off;
            for (ResolvedType.Field f : t.fields()) {
                fieldOff = DefValType.alignTo(fieldOff, alignmentOf(f.type()));
                append(f.type(), fieldOff);
                fieldOff += sizeOf(f.type());
            }
        }

        private void appendVariant(ResolvedType t, int off) {
            var cases = t.cases();
            int discSize = t.discriminantSize();
            int payloadOff = off + DefValType.alignTo(discSize, t.maxCaseAlignment(ptrType));
            TransferPlan[] casePlans = new TransferPlan[cases.size()];
            for (int i = 0; i < cases.size(); i++) {
                ResolvedType.Case c = cases.get(i);
                casePlans[i] = c.hasType() ? compile(ptrType, c.type()) : null;
            }
            step(
                    (src, dst, srcPtr, dstPtr) -> {
                        long caseIndex =
                                CanonicalAbi.loadInt(src.memory(), srcPtr + off, discSize, false);
                        if (caseIndex >= casePlans.length) {
                            throw new TrapException("invalid variant discriminant");
                        }
                        CanonicalAbi.storeInt(dst.memory(), caseIndex, dstPtr + off, discSize);
                        TransferPlan payload = casePlans[(int) caseIndex];
                        if (payload != null) {
                            payload.run(src, dst, srcPtr + payloadOff, dstPtr + payloadOff);
                        }
                    });
        }

        private void appendList(ResolvedType t, int off) {
            if (t.isFixedSizeList()) {
                // A fixed-size list of copyable elements is itself copyable and was
                // handled by the caller, so this loop only ever walks elements that
                // genuinely need work.
                appendFixedSizeList(t.element(), t.fixedSize(), off);
                return;
            }
            appendUnboundedList(t.element(), off);
        }

        private void appendFixedSizeList(ResolvedType elemType, int length, int off) {
            int elemSize = sizeOf(elemType);
            TransferPlan elemPlan = compile(ptrType, elemType);
            step(
                    (src, dst, srcPtr, dstPtr) -> {
                        for (int i = 0; i < length; i++) {
                            int delta = off + i * elemSize;
                            elemPlan.run(src, dst, srcPtr + delta, dstPtr + delta);
                        }
                    });
        }

        private void appendUnboundedList(ResolvedType elemType, int off) {
            step(
                    (src, dst, srcPtr, dstPtr) ->
                            CanonicalAbi.transferUnboundedList(
                                    src, dst, srcPtr + off, dstPtr + off, elemType));
        }
    }
}
