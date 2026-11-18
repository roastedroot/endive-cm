package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roastedroot.typewire.types.BorrowType;
import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.OwnType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.TypeSpace;
import io.roastedroot.typewire.types.ValType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.TrapException;
import run.endive.wasm.types.MemoryLimits;

class CanonicalAbiResourceTests {

    private static final int REP = 42;

    // ---------------------------------------------------------------------------------
    // own
    // ---------------------------------------------------------------------------------

    @Test
    void lowerThenLiftAnOwnHandleRoundTripsTheRep() {
        var f = new Fixture();
        int index = CanonicalAbi.lowerOwn(f.ctx, ResourceValue.owned(f.rt, REP), f.ownType);

        assertThat(index).isEqualTo(1);
        assertThat(CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isEqualTo(ResourceValue.owned(f.rt, REP));
    }

    @Test
    void liftingAnOwnHandleTransfersItOutOfTheTable() {
        var f = new Fixture();
        int index = f.handles.add(f.rt, REP, true, null);

        CanonicalAbi.liftOwn(f.ctx, index, f.ownType);

        assertThat(f.handles.contains(index)).isFalse();
        assertThatThrownBy(() -> CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isInstanceOf(TrapException.class)
                .hasMessageContaining("unknown handle index");
    }

    @Test
    void liftingAnOwnHandleTrapsWhenTheHandleIsOnlyBorrowed() {
        var f = new Fixture();
        int index = f.handles.add(f.rt, REP, false, new StubTask());

        assertThatThrownBy(() -> CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isInstanceOf(TrapException.class)
                .hasMessageContaining("borrowed handle");
    }

    @Test
    void liftingAnOwnHandleTrapsWhileItIsLentOut() {
        var f = new Fixture();
        int index = f.handles.add(f.rt, REP, true, null);
        f.handles.handleAt(index).lend();

        assertThatThrownBy(() -> CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isInstanceOf(TrapException.class)
                .hasMessageContaining("cannot remove owned resource while borrowed");
    }

    @Test
    void liftingTrapsWhenTheHandleBelongsToADifferentResourceType() {
        var f = new Fixture();
        var other = new StubResourceType();
        int index = f.handles.add(other, REP, true, null);

        assertThatThrownBy(() -> CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isInstanceOf(TrapException.class)
                .hasMessageContaining("wrong type");
    }

    @Test
    void liftingTrapsWhenTheIndexNamesSomethingOtherThanAResourceHandle() {
        // A handle table also holds waitables and error contexts.
        var f = new Fixture();
        int index = f.handles.put(new Object());

        assertThatThrownBy(() -> CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isInstanceOf(TrapException.class)
                .hasMessageContaining("not a resource handle");
    }

    // ---------------------------------------------------------------------------------
    // borrow
    // ---------------------------------------------------------------------------------

    @Test
    void liftingABorrowLeavesTheHandleInPlaceAndLendsIt() {
        var f = new Fixture();
        int index = f.handles.add(f.rt, REP, true, null);
        var subtask = new FakeSubtask();

        var value = CanonicalAbi.liftBorrow(f.ctx.withBorrowScope(subtask), index, f.borrowType);

        assertThat(value).isEqualTo(ResourceValue.borrowed(f.rt, REP));
        assertThat(f.handles.contains(index)).isTrue();
        assertThat(f.handles.handleAt(index).numLends()).isEqualTo(1);
    }

    @Test
    void resolvingTheCallReturnsEveryHandleItBorrowed() {
        var f = new Fixture();
        int index = f.handles.add(f.rt, REP, true, null);
        var subtask = new FakeSubtask();
        var ctx = f.ctx.withBorrowScope(subtask);

        CanonicalAbi.liftBorrow(ctx, index, f.borrowType);
        CanonicalAbi.liftBorrow(ctx, index, f.borrowType);
        assertThat(f.handles.handleAt(index).numLends()).isEqualTo(2);

        subtask.releaseLenders();

        assertThat(f.handles.handleAt(index).numLends()).isZero();

        assertThat(CanonicalAbi.liftOwn(f.ctx, index, f.ownType))
                .isEqualTo(ResourceValue.owned(f.rt, REP));
    }

    @Test
    void loweringABorrowMintsAHandleAndChargesItToTheCall() {
        var f = new Fixture();
        var task = new StubTask();

        int index =
                CanonicalAbi.lowerBorrow(
                        f.ctx.withBorrowScope(task),
                        ResourceValue.borrowed(f.rt, REP),
                        f.borrowType);

        assertThat(task.numBorrows()).isEqualTo(1);
        var handle = f.handles.handleAt(index);
        assertThat(handle.rep()).isEqualTo(REP);
        assertThat(handle.own()).isFalse();
        assertThat(handle.borrowScope()).isSameAs(task);
    }

    @Test
    void loweringABorrowIntoTheImplementingComponentPassesTheRepItself() {
        var f = new Fixture();
        f.rt.impl = f.handles;
        var task = new StubTask();

        int lowered =
                CanonicalAbi.lowerBorrow(
                        f.ctx.withBorrowScope(task),
                        ResourceValue.borrowed(f.rt, REP),
                        f.borrowType);

        assertThat(lowered).isEqualTo(REP);
        assertThat(task.numBorrows()).isZero();
        assertThat(f.handles.size()).isZero();
    }

    // ---------------------------------------------------------------------------------
    // Through the flat and memory paths
    // ---------------------------------------------------------------------------------

    @Test
    void storesAndLoadsAnOwnHandleThroughMemory() {
        var f = new Fixture();
        CanonicalAbi.store(f.ctx, ResourceValue.owned(f.rt, REP), f.ownType, 0);

        // Only the table index reaches memory, and the rep does not.
        assertThat(f.ctx.memory().readInt(0)).isEqualTo(1);
        assertThat(CanonicalAbi.load(f.ctx, 0, f.ownType))
                .isEqualTo(ResourceValue.owned(f.rt, REP));
    }

    @Test
    void lowersAndLiftsAnOwnHandleThroughTheFlatPath() {
        var f = new Fixture();
        long[] flat =
                CanonicalAbi.lowerFlatParams(
                        f.ctx, List.of(ResourceValue.owned(f.rt, REP)), List.of(f.ownType));

        assertThat(flat).containsExactly(1L);
        assertThat(CanonicalAbi.liftFlatParams(f.ctx, flat, List.of(f.ownType)))
                .containsExactly(ResourceValue.owned(f.rt, REP));
    }

    @Test
    void handlesAreNeverMovedByTheDirectTransferPath() {
        // A handle index means something only relative to one instance's table, so no byte copy
        // can carry it. canTransfer has to reject these and fall back to lift/lower.
        var f = new Fixture();
        assertThat(Transferability.isSupported(f.ownType)).isFalse();
        assertThat(Transferability.isSupported(f.borrowType)).isFalse();
        assertThat(Transferability.isBitwiseCopyable(PointerType.I32, f.ownType)).isFalse();
        assertThat(Transferability.isFlatIdentity(PointerType.I32, f.borrowType)).isFalse();
    }

    @Test
    void loweringABorrowWithoutACallToChargeItToIsARuntimeError() {
        // A context whose types contain a borrow but which was given no scope is a
        // wiring mistake in the embedder, not something guest code can provoke.
        var f = new Fixture();
        assertThatThrownBy(
                        () ->
                                CanonicalAbi.lowerBorrow(
                                        f.ctx, ResourceValue.borrowed(f.rt, REP), f.borrowType))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borrow scope");
    }

    // ---------------------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------------------

    /** A single resource type at type index 0, with an empty handle table and no borrow scope. */
    private static final class Fixture {
        final StubResourceType rt = new StubResourceType();
        final StubHanleTable handles = new StubHanleTable();
        final Types types = new Types();
        final ValType ownValType;
        final ResolvedType ownType;
        final ResolvedType borrowType;
        final LiftLowerContext ctx;

        Fixture() {
            types.rt = rt;
            OwnType own = OwnType.builder().withTypeIdx(0).build();
            BorrowType borrow = BorrowType.builder().withTypeIdx(1).build();
            ownValType = types.add(own);
            types.add(borrow);
            ownType = ResolvedType.of(own, types);
            borrowType = ResolvedType.of(borrow, types);
            ctx =
                    LiftLowerContext.builder()
                            .withMemory(new ByteArrayMemory(new MemoryLimits(1)))
                            .withPtrType(PointerType.I32)
                            .withHandles(handles)
                            .build();
        }
    }

    /**
     * Both own and borrow here name the same resource type. The type index only says which handle
     * type carries it.
     */
    private static final class Types implements TypeResolver, TypeSpace {
        private final List<Type> types = new ArrayList<>();
        private ResourceTypeRef rt;

        @Override
        public Type getType(int index) {
            return types.get(index);
        }

        @Override
        public ResolvedType resolve(ValType valType) {
            return ResolvedType.of(resolveDefValType(valType), this);
        }

        @Override
        public ResourceTypeRef resourceType(int typeIdx) {
            return rt;
        }

        ValType add(DefValType t) {
            types.add(Type.of(t));
            return ValType.builder().withTypeIdx(types.size() - 1).build();
        }
    }

    private static final class StubResourceType implements ResourceTypeRef {
        private HandleTable impl;

        @Override
        public HandleTable handleTable() {
            return impl;
        }
    }

    private static final class StubHanleTable implements HandleTable {
        private final Map<Integer, Object> entries = new LinkedHashMap<>();
        private int next = 1;

        @Override
        public int add(ResourceTypeRef rt, int rep, boolean own, BorrowScope.Callee borrowScope) {
            return put(new StubHandle(rt, rep, own, borrowScope));
        }

        @Override
        public Object get(int index) {
            Object element = entries.get(index);
            if (element == null) {
                throw new TrapException("unknown handle index " + index);
            }
            return element;
        }

        @Override
        public Object remove(int index) {
            Object element = get(index);
            entries.remove(index);
            return element;
        }

        int put(Object element) {
            int index = next++;
            entries.put(index, element);
            return index;
        }

        boolean contains(int index) {
            return entries.containsKey(index);
        }

        int size() {
            return entries.size();
        }

        StubHandle handleAt(int index) {
            return (StubHandle) entries.get(index);
        }
    }

    private static final class StubHandle implements ResourceState {
        private final ResourceTypeRef resourceType;
        private final int rep;
        private final boolean own;
        private final BorrowScope.Callee borrowScope;
        private int numLends;

        StubHandle(
                ResourceTypeRef resourceType,
                int rep,
                boolean own,
                BorrowScope.Callee borrowScope) {
            this.resourceType = resourceType;
            this.rep = rep;
            this.own = own;
            this.borrowScope = borrowScope;
        }

        @Override
        public ResourceTypeRef resourceType() {
            return resourceType;
        }

        @Override
        public int rep() {
            return rep;
        }

        @Override
        public boolean own() {
            return own;
        }

        @Override
        public BorrowScope.Callee borrowScope() {
            return borrowScope;
        }

        @Override
        public int numLends() {
            return numLends;
        }

        @Override
        public void lend() {
            numLends++;
        }

        @Override
        public void returnLend() {
            numLends--;
        }
    }

    private static final class StubTask implements BorrowScope.Callee {
        private int numBorrows;

        @Override
        public void borrow() {
            numBorrows++;
        }

        @Override
        public void unborrow() {
            numBorrows--;
        }

        @Override
        public int numBorrows() {
            return numBorrows;
        }
    }

    private static final class FakeSubtask implements BorrowScope.Caller {
        private final List<ResourceState> lenders = new ArrayList<>();

        @Override
        public void addLender(ResourceState handle) {
            handle.lend();
            lenders.add(handle);
        }

        @Override
        public void releaseLenders() {
            lenders.forEach(ResourceState::returnLend);
            lenders.clear();
        }
    }
}
