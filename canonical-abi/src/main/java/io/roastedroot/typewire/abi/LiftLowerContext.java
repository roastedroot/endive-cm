package io.roastedroot.typewire.abi;

import io.roastedroot.typewire.types.PointerType;
import run.endive.runtime.Memory;

/**
 * Everything a lift or lower needs beyond the values and their types. That means the memory the two
 * sides share, how strings are encoded, where handles live, and which call owns the transfer.
 *
 * <p>Notably absent is any type index space. A type reaches the Canonical ABI already resolved,
 * against the space in which it was written, so nothing here has to say what an index means.
 */
public final class LiftLowerContext {

    private final Memory memory;
    private final PointerType ptrType;
    private final StringEncoding stringEncoding;
    private final PostReturn postReturn;
    private final Realloc realloc;
    private final boolean async;
    private final Callback callback;
    private final HandleTable handles;
    private final BorrowScope borrowScope;

    private LiftLowerContext(
            Memory memory,
            PointerType ptrType,
            StringEncoding stringEncoding,
            PostReturn postReturn,
            Realloc realloc,
            boolean async,
            Callback callback,
            HandleTable handles,
            BorrowScope borrowScope) {
        this.memory = memory;
        this.ptrType = ptrType == null ? PointerType.I32 : ptrType;
        this.stringEncoding = stringEncoding == null ? StringEncoding.UTF8 : stringEncoding;
        this.postReturn = postReturn;
        this.realloc = realloc;
        this.async = async;
        this.callback = callback;
        this.handles = handles;
        this.borrowScope = borrowScope;
    }

    /**
     * This context with its borrow scope replaced. The rest of a context is fixed by the
     * {@code canonopt}s of a canonical definition and so is built once at link time, but the scope
     * belongs to an individual call and has to be supplied per invocation.
     */
    public LiftLowerContext withBorrowScope(BorrowScope borrowScope) {
        return new LiftLowerContext(
                memory,
                ptrType,
                stringEncoding,
                postReturn,
                realloc,
                async,
                callback,
                handles,
                borrowScope);
    }

    public Memory memory() {
        return memory;
    }

    public PointerType ptrType() {
        return ptrType;
    }

    public StringEncoding stringEncoding() {
        return stringEncoding;
    }

    public PostReturn postReturn() {
        return postReturn;
    }

    public Realloc realloc() {
        return realloc;
    }

    public boolean isAsync() {
        return async;
    }

    public Callback callback() {
        return callback;
    }

    public HandleTable handles() {
        return handles;
    }

    public BorrowScope borrowScope() {
        return borrowScope;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Memory memory;
        private PointerType ptrType;
        private StringEncoding stringEncoding;
        private PostReturn postReturn;
        private Realloc realloc;
        private boolean async;
        private Callback callback;
        private HandleTable handles;
        private BorrowScope borrowScope;

        private Builder() {}

        public Builder withMemory(Memory memory) {
            this.memory = memory;
            return this;
        }

        public Builder withPtrType(PointerType ptrType) {
            this.ptrType = ptrType;
            return this;
        }

        public Builder withStringEncoding(StringEncoding stringEncoding) {
            this.stringEncoding = stringEncoding;
            return this;
        }

        public Builder withPostReturn(PostReturn postReturn) {
            this.postReturn = postReturn;
            return this;
        }

        public Builder withRealloc(Realloc realloc) {
            this.realloc = realloc;
            return this;
        }

        public Builder withAsync(boolean async) {
            this.async = async;
            return this;
        }

        public Builder withCallback(Callback callback) {
            this.callback = callback;
            return this;
        }

        public Builder withHandles(HandleTable handles) {
            this.handles = handles;
            return this;
        }

        public Builder withBorrowScope(BorrowScope borrowScope) {
            this.borrowScope = borrowScope;
            return this;
        }

        public LiftLowerContext build() {
            return new LiftLowerContext(
                    memory,
                    ptrType,
                    stringEncoding,
                    postReturn,
                    realloc,
                    async,
                    callback,
                    handles,
                    borrowScope);
        }
    }
}
