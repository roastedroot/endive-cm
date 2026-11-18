package io.roastedroot.typewire.abi;

import static io.roastedroot.typewire.abi.TransferTestSupport.assertMemoryEquals;
import static io.roastedroot.typewire.abi.TransferTestSupport.newContext;
import static io.roastedroot.typewire.abi.TransferTestSupport.prim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roastedroot.typewire.types.Case;
import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ListType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.ValType;
import io.roastedroot.typewire.types.VariantType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;
import run.endive.runtime.TrapException;
import run.endive.wasm.types.MemoryLimits;

/**
 * Covers the flat (core value) side of the transfer path, where the normalizations differ from the
 * in-memory ones. A core value can carry bits above its component type's width, and lifting would
 * have masked or sign-extended them before lowering re-encoded them.
 */
class CanonicalAbiTransferFlatTests {

    @Test
    void transfersDirectParamsIdenticallyToLiftLower() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts =
                List.of(prim(PrimValType.U32), prim(PrimValType.S64), prim(PrimValType.F64));
        long[] args = {7L, -3L, Double.doubleToRawLongBits(2.5)};

        assertFlatParamsMatchLiftLower(types, ts, args);
    }

    @Test
    void transfersStringParamIdenticallyToLiftLower() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        var reference = newContext(types);
        List<ValType> ts = List.of(prim(PrimValType.STRING));
        new AbiHelper(types).store(src, "flat string", PrimValType.STRING, 0);
        long[] args = {src.memory().readU32(0), src.memory().readU32(4)};

        long[] actual = new AbiHelper(types).transferFlatParams(src, dst, args, ts);
        long[] expected =
                new AbiHelper(types)
                        .lowerFlatParams(
                                reference, new AbiHelper(types).liftFlatParams(src, args, ts), ts);

        assertThat(actual).isEqualTo(expected);
        assertMemoryEquals(dst.memory(), reference.memory());
    }

    @Test
    void narrowsUnsignedIntegersCarryingBitsAboveTheirWidth() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts = List.of(prim(PrimValType.U8), prim(PrimValType.U16));
        // A core i32 may carry more bits than the component type has, so lifting masks them
        // away, so the transfer must too rather than forwarding the raw value.
        long[] args = {0x1FFL, 0x1FFFFL};

        long[] actual =
                new AbiHelper(types)
                        .transferFlatParams(newContext(types), newContext(types), args, ts);

        assertThat(actual).containsExactly(0xFFL, 0xFFFFL);
        assertFlatParamsMatchLiftLower(types, ts, args);
    }

    @Test
    void signExtendsNarrowSignedIntegers() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts = List.of(prim(PrimValType.S8), prim(PrimValType.S16));
        long[] args = {0x80L, 0x8000L};

        long[] actual =
                new AbiHelper(types)
                        .transferFlatParams(newContext(types), newContext(types), args, ts);

        assertThat(actual).containsExactly(0xFFFFFF80L, 0xFFFF8000L);
        assertFlatParamsMatchLiftLower(types, ts, args);
    }

    @Test
    void collapsesNonZeroBoolToOne() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts = List.of(prim(PrimValType.BOOL));
        long[] args = {0x2A};

        long[] actual =
                new AbiHelper(types)
                        .transferFlatParams(newContext(types), newContext(types), args, ts);

        assertThat(actual).containsExactly(1L);
        assertFlatParamsMatchLiftLower(types, ts, args);
    }

    @Test
    void keepsNonCanonicalNanBitsOnTheFlatPath() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts = List.of(prim(PrimValType.F32));
        long[] args = {0x7f800001L};

        long[] actual =
                new AbiHelper(types)
                        .transferFlatParams(newContext(types), newContext(types), args, ts);

        assertThat(actual).containsExactly(0x7f800001L);
    }

    @Test
    void trapsOnInvalidCharOnTheFlatPath() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts = List.of(prim(PrimValType.CHAR));

        assertThatThrownBy(
                        () ->
                                new AbiHelper(types)
                                        .transferFlatParams(
                                                newContext(types),
                                                newContext(types),
                                                new long[] {0xD800L},
                                                ts))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void transfersVariantAcrossJoinedFlatSlots() {
        var types = new TransferTestSupport.Types();
        var variant =
                VariantType.builder()
                        .addCase(Case.builder().withLabel("none").build())
                        .addCase(
                                Case.builder()
                                        .withLabel("small")
                                        .withValType(prim(PrimValType.U8))
                                        .build())
                        .addCase(
                                Case.builder()
                                        .withLabel("wide")
                                        .withValType(prim(PrimValType.S64))
                                        .build())
                        .build();
        List<ValType> ts = List.of(types.add(variant));

        // The u8 case leaves the joined i64 payload slot narrower than the wide case does.
        // The source's stray high bits must not survive, and the slot must still be emitted.
        assertFlatParamsMatchLiftLower(types, ts, new long[] {1L, 0x1FFL});
        assertFlatParamsMatchLiftLower(types, ts, new long[] {0L, 0L});
        assertFlatParamsMatchLiftLower(types, ts, new long[] {2L, -5L});
    }

    @Test
    void trapsOnOutOfRangeVariantDiscriminantOnTheFlatPath() {
        var types = new TransferTestSupport.Types();
        var variant =
                VariantType.builder()
                        .addCase(Case.builder().withLabel("a").build())
                        .addCase(Case.builder().withLabel("b").build())
                        .build();
        List<ValType> ts = List.of(types.add(variant));

        assertThatThrownBy(
                        () ->
                                new AbiHelper(types)
                                        .transferFlatParams(
                                                newContext(types),
                                                newContext(types),
                                                new long[] {9L},
                                                ts))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void spillsParamsThroughMemoryWhenTheyExceedTheFlatLimit() {
        var types = new TransferTestSupport.Types();
        List<ValType> ts = new ArrayList<>();
        for (int i = 0; i < CanonicalAbi.MAX_FLAT_PARAMS + 1; i++) {
            ts.add(prim(PrimValType.U32));
        }
        var src = newContext(types);
        var dst = newContext(types);
        var reference = newContext(types);

        // Lay the spilled tuple out in the source exactly as the caller would have.
        int spillPtr = 64;
        for (int i = 0; i < ts.size(); i++) {
            src.memory().writeI32(spillPtr + 4 * i, 1000 + i);
        }
        long[] args = {spillPtr};

        long[] actual = new AbiHelper(types).transferFlatParams(src, dst, args, ts);
        long[] expected =
                new AbiHelper(types)
                        .lowerFlatParams(
                                reference, new AbiHelper(types).liftFlatParams(src, args, ts), ts);

        assertThat(actual).as("both sides spill, so a single pointer comes back").hasSize(1);
        assertThat(actual).isEqualTo(expected);
        assertMemoryEquals(dst.memory(), reference.memory());
    }

    @Test
    void writesSpilledResultsIntoACallerProvidedOutParam() {
        var types = new TransferTestSupport.Types();
        var wide =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U32)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .build();
        List<ValType> ts = List.of(types.add(wide));
        var src = newContext(types);
        var dst = newContext(types);
        var reference = newContext(types);

        int srcPtr = 64;
        src.memory().writeI32(srcPtr, 11);
        src.memory().writeI32(srcPtr + 4, 22);
        long[] outParam = {128};

        long[] actual =
                new AbiHelper(types)
                        .transferFlatResults(src, dst, new long[] {srcPtr}, ts, outParam);
        long[] expected =
                new AbiHelper(types)
                        .lowerFlatResults(
                                reference,
                                new AbiHelper(types).liftFlatResults(src, new long[] {srcPtr}, ts),
                                ts,
                                outParam);

        assertThat(actual).as("nothing comes back when the caller supplied the buffer").isEmpty();
        assertThat(actual).isEqualTo(expected);
        assertThat(dst.memory().readInt(128)).isEqualTo(11);
        assertThat(dst.memory().readInt(132)).isEqualTo(22);
        assertMemoryEquals(dst.memory(), reference.memory());
    }

    @Test
    void trapsOnMisalignedSpillOutParam() {
        var types = new TransferTestSupport.Types();
        var wide =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U32)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .build();
        List<ValType> ts = List.of(types.add(wide));
        var src = newContext(types);
        var dst = newContext(types);

        assertThatThrownBy(
                        () ->
                                new AbiHelper(types)
                                        .transferFlatResults(
                                                src, dst, new long[] {64}, ts, new long[] {129}))
                .isInstanceOf(TrapException.class);
    }

    // --- bulk copy ----------------------------------------------------------------------

    @Test
    void movesAListOfBytesWithASingleBulkCopy() {
        assertListMovesInOneCopy(prim(PrimValType.U8), 4096);
    }

    @Test
    void movesAListOfFloatsWithASingleBulkCopy() {
        assertListMovesInOneCopy(prim(PrimValType.F32), 1024);
    }

    @Test
    void movesAListOfNonCopyableElementsElementByElement() {
        var types = new TransferTestSupport.Types();
        var t = ListType.builder().withElementType(prim(PrimValType.BOOL)).build();
        var src = newContext(types);
        var dst = countingContext(types);
        int length = 32;
        src.memory().writeI32(0, 256);
        src.memory().writeI32(4, length);

        var counting = (CountingMemory) dst.memory();
        counting.resetCounts();
        new AbiHelper(types).transfer(src, dst, 0, 0, t);

        assertThat(counting.bulkWrites())
                .as("bool needs normalizing, so there is no bulk copy of the payload")
                .isZero();
        assertThat(counting.scalarWrites())
                .as("one write per element, plus the pointer and length")
                .isEqualTo(length + 2);
    }

    private void assertListMovesInOneCopy(ValType elementType, int length) {
        var types = new TransferTestSupport.Types();
        var t = ListType.builder().withElementType(elementType).build();
        var src = newContext(types);
        var dst = countingContext(types);
        src.memory().writeI32(0, 256);
        src.memory().writeI32(4, length);

        var counting = (CountingMemory) dst.memory();
        counting.resetCounts();
        new AbiHelper(types).transfer(src, dst, 0, 0, t);

        int elementSize = new AbiHelper(types).resolve(elementType).elementSize(PointerType.I32);
        int expectedChunks = Math.max(1, (length * elementSize + 65535) / 65536);
        assertThat(counting.bulkWrites())
                .as("payload copied in bulk, not per element")
                .isEqualTo(expectedChunks);
        assertThat(counting.scalarWrites())
                .as("only the destination pointer and length are written individually")
                .isEqualTo(2);
    }

    // --- ValueTransfer ------------------------------------------------------------------

    @Test
    void compiledValueTransferMatchesTheStaticPath() {
        var types = new TransferTestSupport.Types();
        var ft =
                FuncType.builder()
                        .addParam(param("n", prim(PrimValType.U8)))
                        .addParam(param("s", prim(PrimValType.STRING)))
                        .addParam(param("b", prim(PrimValType.BOOL)))
                        .withResult(prim(PrimValType.U32))
                        .build();
        List<ValType> paramTypes =
                List.of(prim(PrimValType.U8), prim(PrimValType.STRING), prim(PrimValType.BOOL));

        var src = newContext(types);
        new AbiHelper(types).store(src, "compiled", PrimValType.STRING, 16);
        long[] args = {0x1FFL, src.memory().readU32(16), src.memory().readU32(20), 3L};

        var viaStatics = newContext(types);
        long[] staticResult =
                new AbiHelper(types).transferFlatParams(src, viaStatics, args, paramTypes);

        var viaPlan = newContext(types);
        long[] planResult = new AbiHelper(types).compile(src, viaPlan, ft).transferParams(args);

        assertThat(planResult).isEqualTo(staticResult);
        assertMemoryEquals(viaPlan.memory(), viaStatics.memory());
    }

    @Test
    void compiledValueTransferMatchesTheStaticPathWhenSpilled() {
        var types = new TransferTestSupport.Types();
        var builder = FuncType.builder();
        List<ValType> paramTypes = new ArrayList<>();
        for (int i = 0; i < CanonicalAbi.MAX_FLAT_PARAMS + 1; i++) {
            builder.addParam(param("p" + i, prim(PrimValType.U32)));
            paramTypes.add(prim(PrimValType.U32));
        }
        var ft = builder.build();

        var src = newContext(types);
        for (int i = 0; i < paramTypes.size(); i++) {
            src.memory().writeI32(64 + 4 * i, 500 + i);
        }
        long[] args = {64};

        var viaStatics = newContext(types);
        long[] staticResult =
                new AbiHelper(types).transferFlatParams(src, viaStatics, args, paramTypes);

        var viaPlan = newContext(types);
        long[] planResult = new AbiHelper(types).compile(src, viaPlan, ft).transferParams(args);

        assertThat(planResult).isEqualTo(staticResult);
        assertMemoryEquals(viaPlan.memory(), viaStatics.memory());
    }

    @Test
    void compiledValueTransferMovesADirectResult() {
        var types = new TransferTestSupport.Types();
        var ft =
                FuncType.builder()
                        .addParam(param("x", prim(PrimValType.U32)))
                        .withResult(prim(PrimValType.U32))
                        .build();

        var callee = newContext(types);
        long[] flatResults = {0xDEADBEEFL};

        var viaStatics = newContext(types);
        long[] staticResult =
                new AbiHelper(types)
                        .transferFlatResults(
                                callee,
                                viaStatics,
                                flatResults,
                                List.of(prim(PrimValType.U32)),
                                null);

        var viaPlan = newContext(types);
        long[] planResult =
                new AbiHelper(types)
                        .compile(viaPlan, callee, ft)
                        .transferResults(flatResults, null);

        assertThat(planResult).containsExactly(0xDEADBEEFL);
        assertThat(planResult).isEqualTo(staticResult);
        assertMemoryEquals(viaPlan.memory(), viaStatics.memory());
    }

    @Test
    void compiledValueTransferMovesASpilledStringResult() {
        var types = new TransferTestSupport.Types();
        var ft =
                FuncType.builder()
                        .addParam(param("x", prim(PrimValType.U32)))
                        .withResult(prim(PrimValType.STRING))
                        .build();
        var callee = newContext(types);
        int spillPtr = 32;
        new AbiHelper(types).store(callee, "result", PrimValType.STRING, spillPtr);
        long[] flatResults = {spillPtr};

        var viaStatics = newContext(types);
        long[] staticResult =
                new AbiHelper(types)
                        .transferFlatResults(
                                callee,
                                viaStatics,
                                flatResults,
                                List.of(prim(PrimValType.STRING)),
                                null);

        var viaPlan = newContext(types);
        long[] planResult =
                new AbiHelper(types)
                        .compile(viaPlan, callee, ft)
                        .transferResults(flatResults, null);

        assertThat(planResult).as("a pointer to the destination's spilled tuple").hasSize(1);
        assertThat(planResult).isEqualTo(staticResult);
        assertMemoryEquals(viaPlan.memory(), viaStatics.memory());
        assertThat(new AbiHelper(types).load(viaPlan, (int) planResult[0], PrimValType.STRING))
                .isEqualTo("result");
    }

    @Test
    void movesParamsAndResultsInOppositeDirectionsAcrossEncodings() {
        var types = new TransferTestSupport.Types();
        var ft =
                FuncType.builder()
                        .addParam(param("s", prim(PrimValType.STRING)))
                        .withResult(prim(PrimValType.STRING))
                        .build();
        var caller = TransferTestSupport.newContext(types, StringEncoding.UTF8);
        var callee = TransferTestSupport.newContext(types, StringEncoding.UTF16);
        var transfer = new AbiHelper(types).compile(caller, callee, ft);
        String value = "héllo 😀";

        new AbiHelper(types).store(caller, value, PrimValType.STRING, 0);
        long[] calleeArgs =
                transfer.transferParams(
                        new long[] {caller.memory().readU32(0), caller.memory().readU32(4)});

        // The argument now lives in the callee's memory, encoded as UTF-16.
        byte[] expectedUtf16 = value.getBytes(StandardCharsets.UTF_16LE);
        assertThat(callee.memory().readBytes((int) calleeArgs[0], expectedUtf16.length))
                .isEqualTo(expectedUtf16);

        // A string result spills, so the callee returns a pointer to a tuple holding the
        // (ptr, code_units) pair it just received.
        int calleeSpillPtr = 256;
        callee.memory().writeI32(calleeSpillPtr, (int) calleeArgs[0]);
        callee.memory().writeI32(calleeSpillPtr + 4, (int) calleeArgs[1]);

        long[] callerResults = transfer.transferResults(new long[] {calleeSpillPtr}, null);

        assertThat(callerResults).hasSize(1);
        assertThat(new AbiHelper(types).load(caller, (int) callerResults[0], PrimValType.STRING))
                .isEqualTo(value);

        // Back in the caller's memory it is UTF-8 again.
        int callerStringPtr = caller.memory().readInt((int) callerResults[0]);
        long callerByteLength = caller.memory().readU32((int) callerResults[0] + 4);
        assertThat(caller.memory().readBytes(callerStringPtr, (int) callerByteLength))
                .isEqualTo(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void compiledValueTransferReturnsNothingForAVoidFunction() {
        var types = new TransferTestSupport.Types();
        var ft = FuncType.builder().addParam(param("x", prim(PrimValType.U32))).build();
        var transfer = new AbiHelper(types).compile(newContext(types), newContext(types), ft);

        assertThat(transfer.hasResult()).isFalse();
        assertThat(transfer.transferResults(new long[0], null)).isEmpty();
    }

    // --- canTransfer --------------------------------------------------------------------

    @Test
    void canTransferAcceptsAPlainSyncFunction() {
        var types = new TransferTestSupport.Types();
        var ft =
                FuncType.builder()
                        .addParam(param("s", prim(PrimValType.STRING)))
                        .withResult(prim(PrimValType.U32))
                        .build();

        assertThat(new AbiHelper(types).canTransfer(newContext(types), newContext(types), ft))
                .isTrue();
    }

    @Test
    void canTransferRejectsAsyncContexts() {
        var types = new TransferTestSupport.Types();
        var ft = FuncType.builder().addParam(param("x", prim(PrimValType.U32))).build();
        var async =
                LiftLowerContext.builder()
                        .withMemory(new ByteArrayMemory(new MemoryLimits(1)))
                        .withPtrType(PointerType.I32)
                        .withAsync(true)
                        .build();

        assertThat(new AbiHelper(types).canTransfer(newContext(types), async, ft)).isFalse();
        assertThat(new AbiHelper(types).canTransfer(async, newContext(types), ft)).isFalse();
    }

    @Test
    void canTransferRejectsMismatchedPointerWidths() {
        var types = new TransferTestSupport.Types();
        var ft = FuncType.builder().addParam(param("x", prim(PrimValType.U32))).build();
        var wide =
                LiftLowerContext.builder()
                        .withMemory(new ByteArrayMemory(new MemoryLimits(1)))
                        .withPtrType(PointerType.I64)
                        .build();

        assertThat(new AbiHelper(types).canTransfer(newContext(types), wide, ft)).isFalse();
    }

    @Test
    void canTransferRejectsTypesTheTransferPathDoesNotImplement() {
        var types = new TransferTestSupport.Types();
        var ft = FuncType.builder().addParam(param("e", prim(PrimValType.ERROR_CONTEXT))).build();

        assertThat(new AbiHelper(types).canTransfer(newContext(types), newContext(types), ft))
                .isFalse();
    }

    private static LiftLowerContext memorylessContext(TransferTestSupport.Types types) {
        return LiftLowerContext.builder().build();
    }

    @Test
    void canTransferAcceptsMemorylessContextsForValuesThatNeverReachMemory() {
        var types = new TransferTestSupport.Types();
        var ft =
                FuncType.builder()
                        .addParam(param("x", prim(PrimValType.U32)))
                        .addParam(param("y", prim(PrimValType.F64)))
                        .withResult(prim(PrimValType.S64))
                        .build();

        assertThat(
                        new AbiHelper(types)
                                .canTransfer(
                                        memorylessContext(types), memorylessContext(types), ft))
                .isTrue();
    }

    @Test
    void canTransferAcceptsMemorylessContextsForAFixedSizeListOfScalars() {
        var types = new TransferTestSupport.Types();
        var list =
                ListType.builder().withElementType(prim(PrimValType.U32)).withFixedSize(2).build();
        var ft = FuncType.builder().addParam(param("xs", types.add(list))).build();

        assertThat(
                        new AbiHelper(types)
                                .canTransfer(
                                        memorylessContext(types), memorylessContext(types), ft))
                .isTrue();
    }

    @Test
    void canTransferRejectsMemorylessContextsWhenAValueLivesBehindAPointer() {
        var types = new TransferTestSupport.Types();
        var withString = FuncType.builder().addParam(param("s", prim(PrimValType.STRING))).build();
        var withList =
                FuncType.builder()
                        .addParam(
                                param(
                                        "xs",
                                        types.add(
                                                ListType.builder()
                                                        .withElementType(prim(PrimValType.U8))
                                                        .build())))
                        .build();

        assertThat(
                        new AbiHelper(types)
                                .canTransfer(
                                        memorylessContext(types),
                                        memorylessContext(types),
                                        withString))
                .isFalse();
        assertThat(
                        new AbiHelper(types)
                                .canTransfer(
                                        memorylessContext(types),
                                        memorylessContext(types),
                                        withList))
                .isFalse();
    }

    @Test
    void canTransferRejectsMemorylessContextsWhenTheValuesSpill() {
        var types = new TransferTestSupport.Types();
        var builder = FuncType.builder();
        for (int i = 0; i < CanonicalAbi.MAX_FLAT_PARAMS + 1; i++) {
            builder.addParam(param("p" + i, prim(PrimValType.U32)));
        }

        assertThat(
                        new AbiHelper(types)
                                .canTransfer(
                                        memorylessContext(types),
                                        memorylessContext(types),
                                        builder.build()))
                .isFalse();
    }

    @Test
    void canTransferRejectsASpilledResultWithoutMemory() {
        var types = new TransferTestSupport.Types();
        var wide =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U32)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .build();
        var ft = FuncType.builder().withResult(types.add(wide)).build();

        assertThat(
                        new AbiHelper(types)
                                .canTransfer(
                                        memorylessContext(types), memorylessContext(types), ft))
                .isFalse();
    }

    @Test
    void canTransferRejectsAMissingReallocOnTheReceivingSide() {
        var types = new TransferTestSupport.Types();
        var ft = FuncType.builder().addParam(param("s", prim(PrimValType.STRING))).build();
        var noRealloc =
                LiftLowerContext.builder()
                        .withMemory(new ByteArrayMemory(new MemoryLimits(1)))
                        .build();

        // Parameters are allocated in the callee, so the callee is the one that needs it.
        assertThat(new AbiHelper(types).canTransfer(newContext(types), noRealloc, ft)).isFalse();
        assertThat(new AbiHelper(types).canTransfer(noRealloc, newContext(types), ft)).isTrue();
    }

    @Test
    void transfersAPayloadFreeVariantBetweenMemorylessContexts() {
        var types = new TransferTestSupport.Types();
        var variant = VariantType.builder().addCase(Case.builder().withLabel("x").build()).build();
        var ft = FuncType.builder().addParam(param("a", types.add(variant))).build();
        var caller = memorylessContext(types);
        var callee = memorylessContext(types);

        assertThat(new AbiHelper(types).canTransfer(caller, callee, ft)).isTrue();
        var transfer = new AbiHelper(types).compile(caller, callee, ft);

        assertThat(transfer.transferParams(new long[] {0L})).containsExactly(0L);
        assertThatThrownBy(() -> transfer.transferParams(new long[] {1L}))
                .isInstanceOf(TrapException.class);
    }

    // --- helpers ------------------------------------------------------------------------

    private static void assertFlatParamsMatchLiftLower(
            TransferTestSupport.Types types, List<ValType> ts, long[] args) {
        var src = newContext(types);
        var dst = newContext(types);
        var reference = newContext(types);

        long[] actual =
                new AbiHelper(types)
                        .transferFlatParams(src, dst, Arrays.copyOf(args, args.length), ts);
        long[] expected =
                new AbiHelper(types)
                        .lowerFlatParams(
                                reference,
                                new AbiHelper(types)
                                        .liftFlatParams(src, Arrays.copyOf(args, args.length), ts),
                                ts);

        assertThat(actual).isEqualTo(expected);
        assertMemoryEquals(dst.memory(), reference.memory());
    }

    private static LiftLowerContext countingContext(TransferTestSupport.Types types) {
        Memory memory = new CountingMemory(new ByteArrayMemory(new MemoryLimits(4)));
        int[] bumpPtr = {TransferTestSupport.HEAP_BASE};
        Realloc realloc =
                (oldPtr, oldSize, align, newSize) -> {
                    int ptr = DefValType.alignTo(bumpPtr[0], align);
                    bumpPtr[0] = ptr + newSize;
                    return ptr;
                };
        return LiftLowerContext.builder()
                .withMemory(memory)
                .withPtrType(PointerType.I32)
                .withStringEncoding(StringEncoding.UTF8)
                .withRealloc(realloc)
                .build();
    }

    private static LabelValType param(String label, ValType t) {
        return LabelValType.builder().withLabel(label).withValType(t).build();
    }

    private static LabelValType field(String label, ValType t) {
        return LabelValType.builder().withLabel(label).withValType(t).build();
    }
}
