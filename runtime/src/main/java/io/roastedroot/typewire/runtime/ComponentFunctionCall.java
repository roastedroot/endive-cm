package io.roastedroot.typewire.runtime;

@FunctionalInterface
public interface ComponentFunctionCall {
    Object[] apply(Object... args);
}
