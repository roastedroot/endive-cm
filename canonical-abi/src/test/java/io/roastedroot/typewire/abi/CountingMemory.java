package io.roastedroot.typewire.abi;

import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.wasm.types.DataSegment;

/**
 * A {@link Memory} that forwards everything to a delegate while counting the bulk and scalar
 * writes it sees, so a test can assert that a list was moved with one copy rather than one
 * write per element.
 */
final class CountingMemory implements Memory {

    private final Memory delegate;
    private int bulkWrites;
    private int scalarWrites;

    CountingMemory(Memory delegate) {
        this.delegate = delegate;
    }

    int bulkWrites() {
        return bulkWrites;
    }

    int scalarWrites() {
        return scalarWrites;
    }

    void resetCounts() {
        bulkWrites = 0;
        scalarWrites = 0;
    }

    @Override
    public void write(int addr, byte[] data, int offset, int size) {
        bulkWrites++;
        delegate.write(addr, data, offset, size);
    }

    @Override
    public void writeByte(int addr, byte data) {
        scalarWrites++;
        delegate.writeByte(addr, data);
    }

    @Override
    public void writeShort(int addr, short data) {
        scalarWrites++;
        delegate.writeShort(addr, data);
    }

    @Override
    public void writeI32(int addr, int data) {
        scalarWrites++;
        delegate.writeI32(addr, data);
    }

    @Override
    public void writeLong(int addr, long data) {
        scalarWrites++;
        delegate.writeLong(addr, data);
    }

    @Override
    public void writeF32(int addr, float data) {
        scalarWrites++;
        delegate.writeF32(addr, data);
    }

    @Override
    public void writeF64(int addr, double data) {
        scalarWrites++;
        delegate.writeF64(addr, data);
    }

    // --- plain delegation ---------------------------------------------------------------

    @Override
    public int pages() {
        return delegate.pages();
    }

    @Override
    public int grow(int size) {
        return delegate.grow(size);
    }

    @Override
    public int initialPages() {
        return delegate.initialPages();
    }

    @Override
    public int maximumPages() {
        return delegate.maximumPages();
    }

    @Override
    public boolean shared() {
        return delegate.shared();
    }

    @Override
    @SuppressWarnings("removal")
    public Object lock(int address) {
        return delegate.lock(address);
    }

    @Override
    @SuppressWarnings("removal")
    public int waitOn(int address, int expected, long timeout) {
        return delegate.waitOn(address, expected, timeout);
    }

    @Override
    @SuppressWarnings("removal")
    public int waitOn(int address, long expected, long timeout) {
        return delegate.waitOn(address, expected, timeout);
    }

    @Override
    @SuppressWarnings("removal")
    public int notify(int address, int maxThreads) {
        return delegate.notify(address, maxThreads);
    }

    @Override
    public void initialize(Instance instance, DataSegment[] dataSegments) {
        delegate.initialize(instance, dataSegments);
    }

    @Override
    public void initPassiveSegment(int segmentId, int dest, int offset, int size) {
        delegate.initPassiveSegment(segmentId, dest, offset, size);
    }

    @Override
    public byte read(int addr) {
        return delegate.read(addr);
    }

    @Override
    public byte[] readBytes(int addr, int len) {
        return delegate.readBytes(addr, len);
    }

    @Override
    public int readInt(int addr) {
        return delegate.readInt(addr);
    }

    @Override
    public long readLong(int addr) {
        return delegate.readLong(addr);
    }

    @Override
    public short readShort(int addr) {
        return delegate.readShort(addr);
    }

    @Override
    public long readU16(int addr) {
        return delegate.readU16(addr);
    }

    @Override
    public long readF32(int addr) {
        return delegate.readF32(addr);
    }

    @Override
    public float readFloat(int addr) {
        return delegate.readFloat(addr);
    }

    @Override
    public double readDouble(int addr) {
        return delegate.readDouble(addr);
    }

    @Override
    public long readF64(int addr) {
        return delegate.readF64(addr);
    }

    @Override
    public void zero() {
        delegate.zero();
    }

    @Override
    public void fill(byte value, int fromIndex, int toIndex) {
        delegate.fill(value, fromIndex, toIndex);
    }

    @Override
    public void drop(int segment) {
        delegate.drop(segment);
    }
}
