package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.BorrowScope;
import io.roastedroot.typewire.abi.ResourceState;
import io.roastedroot.typewire.abi.ResourceTypeRef;
import java.util.Objects;

public final class ResourceHandle implements ResourceState {

    private final ResourceTypeRef resourceType;
    private final int rep;
    private final boolean own;
    private final BorrowScope.Callee borrowScope;
    private int numLends;

    ResourceHandle(
            ResourceTypeRef resourceType, int rep, boolean own, BorrowScope.Callee borrowScope) {
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
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
