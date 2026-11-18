package io.roastedroot.typewire.abi;

import io.roastedroot.typewire.abi.CanonicalAbi.CoreValues;
import io.roastedroot.typewire.abi.CanonicalAbi.LongBuffer;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.ResolvedType;
import java.util.ArrayList;
import java.util.List;
import run.endive.runtime.TrapException;
import run.endive.wasm.types.ValType;

/**
 * A compiled transfer for a function's flat parameter or result list, the counterpart of
 * {@link TransferPlan} for values travelling as core Wasm values rather than in memory.
 *
 * <p>Whether the values spill into linear memory is decided once, at compile time. Both sides
 * always reach the same answer, because flattening depends only on the type and the pointer width,
 * and the transfer path requires those to match. So a plan is either a <em>spilled</em> one,
 * holding a {@link TransferPlan} for the tuple both sides agree on, or a <em>direct</em> one,
 * holding a step per value.
 */
final class FlatTransferPlan {

    /** One step of a compiled flat transfer, reading from {@code vi} and writing to {@code out}. */
    interface Step {
        void run(LiftLowerContext src, LiftLowerContext dst, CoreValues vi, LongBuffer out);
    }

    private final Step[] steps;
    private final TransferPlan spillPlan;
    private final int spillAlign;
    private final int spillSize;

    private FlatTransferPlan(Step[] steps, TransferPlan spillPlan, int spillAlign, int spillSize) {
        this.steps = steps;
        this.spillPlan = spillPlan;
        this.spillAlign = spillAlign;
        this.spillSize = spillSize;
    }

    static FlatTransferPlan compile(LiftLowerContext ctx, List<ResolvedType> ts, int maxFlat) {
        PointerType ptrType = ctx.ptrType();
        List<ValType> flatTypes = new ArrayList<>();
        for (ResolvedType t : ts) {
            flatTypes.addAll(t.flatten(ptrType));
        }
        if (flatTypes.size() > maxFlat) {
            ResolvedType tupleType = ResolvedType.tupleOf(ts);
            return new FlatTransferPlan(
                    null,
                    TransferPlan.compile(ptrType, tupleType),
                    tupleType.alignment(ptrType),
                    tupleType.elementSize(ptrType));
        }
        var builder = new Builder(ptrType);
        for (ResolvedType t : ts) {
            builder.append(t);
        }
        return new FlatTransferPlan(builder.build(), null, 0, 0);
    }

    long[] run(LiftLowerContext src, LiftLowerContext dst, long[] flat, long[] outParam) {
        var vi = new CoreValues(flat);
        if (spillPlan != null) {
            return CanonicalAbi.transferSpilledValues(
                    src, dst, vi, spillAlign, spillSize, outParam, spillPlan::run);
        }
        LongBuffer out = new LongBuffer();
        for (Step step : steps) {
            step.run(src, dst, vi, out);
        }
        return out.toArray();
    }

    /** The number of compiled steps, or {@code 1} when spilled; exposed for tests. */
    int stepCount() {
        return spillPlan != null ? 1 : steps.length;
    }

    /** Builds the per-value step list for the direct (non-spilled) case. */
    private static final class Builder {

        private final PointerType ptrType;
        private final List<Step> steps = new ArrayList<>();

        Builder(PointerType ptrType) {
            this.ptrType = ptrType;
        }

        Step[] build() {
            return steps.toArray(new Step[0]);
        }

        /**
         * Appends the steps for one value. The normalizations are the same ones
         * {@link CanonicalAbi#transferFlat} applies, with narrow integers masked or sign-extended,
         * {@code bool} collapsed, {@code char} validated, {@code flags} stripped of slack bits, but
         * resolved to a concrete mask here instead of re-dispatched per call.
         */
        void append(ResolvedType t) {
            switch (t.kind()) {
                case BOOL:
                    steps.add(
                            (src, dst, vi, out) ->
                                    out.add((vi.next() & 0xFFFFFFFFL) != 0 ? 1L : 0L));
                    return;
                case U8:
                    steps.add((src, dst, vi, out) -> out.add(vi.next() & 0xFFL));
                    return;
                case U16:
                    steps.add((src, dst, vi, out) -> out.add(vi.next() & 0xFFFFL));
                    return;
                case S8:
                    steps.add(
                            (src, dst, vi, out) ->
                                    out.add(Integer.toUnsignedLong((byte) vi.next())));
                    return;
                case S16:
                    steps.add(
                            (src, dst, vi, out) ->
                                    out.add(Integer.toUnsignedLong((short) vi.next())));
                    return;
                case U32:
                case S32:
                case F32:
                    steps.add((src, dst, vi, out) -> out.add(vi.next() & 0xFFFFFFFFL));
                    return;
                case U64:
                case S64:
                case F64:
                    steps.add((src, dst, vi, out) -> out.add(vi.next()));
                    return;
                case CHAR:
                    steps.add(
                            (src, dst, vi, out) ->
                                    out.add(
                                            CanonicalAbi.convertI32ToChar(
                                                    vi.next() & 0xFFFFFFFFL)));
                    return;
                case FLAGS:
                    long mask = Transferability.flagsMask(t);
                    steps.add((src, dst, vi, out) -> out.add(vi.next() & mask & 0xFFFFFFFFL));
                    return;
                case STRING:
                    steps.add(CanonicalAbi::transferFlatString);
                    return;
                case RECORD:
                    for (ResolvedType.Field f : t.fields()) {
                        append(f.type());
                    }
                    return;
                case VARIANT:
                    appendVariant(t);
                    return;
                case LIST:
                case SIZED_LIST:
                    appendList(t);
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

        private void appendList(ResolvedType t) {
            if (t.isFixedSizeList()) {
                for (int i = 0; i < t.fixedSize(); i++) {
                    append(t.element());
                }
                return;
            }
            appendUnboundedList(t.element());
        }

        private void appendUnboundedList(ResolvedType elemType) {
            steps.add(
                    (src, dst, vi, out) ->
                            CanonicalAbi.transferFlatUnboundedList(src, dst, vi, out, elemType));
        }

        /**
         * Compiles a nested plan per case. At run time the discriminant selects one, and the joined
         * payload slots the chosen case leaves unused are skipped on the source cursor and
         * zero-padded on the destination, exactly as the interpreted path does.
         */
        private void appendVariant(ResolvedType t) {
            var cases = t.cases();
            List<ValType> flatTypes = t.flatten(ptrType);
            if (flatTypes.get(0) != ValType.I32) {
                throw new IllegalStateException("variant discriminant flat type must be i32");
            }
            int payloadSlots = flatTypes.size() - 1;
            FlatTransferPlan[] casePlans = new FlatTransferPlan[cases.size()];
            for (int i = 0; i < cases.size(); i++) {
                ResolvedType.Case c = cases.get(i);
                if (!c.hasType()) {
                    continue;
                }
                var caseBuilder = new Builder(ptrType);
                caseBuilder.append(c.type());
                casePlans[i] = new FlatTransferPlan(caseBuilder.build(), null, 0, 0);
            }
            steps.add(
                    (src, dst, vi, out) -> {
                        long caseIndex = vi.next() & 0xFFFFFFFFL;
                        if (caseIndex >= casePlans.length) {
                            throw new TrapException("invalid variant discriminant");
                        }
                        out.add(caseIndex);
                        int srcPayloadStart = vi.position();
                        int dstPayloadStart = out.size();
                        FlatTransferPlan payload = casePlans[(int) caseIndex];
                        if (payload != null) {
                            for (Step step : payload.steps) {
                                step.run(src, dst, vi, out);
                            }
                        }
                        vi.skipTo(srcPayloadStart + payloadSlots);
                        for (int i = out.size() - dstPayloadStart; i < payloadSlots; i++) {
                            out.add(0L);
                        }
                    });
        }
    }
}
