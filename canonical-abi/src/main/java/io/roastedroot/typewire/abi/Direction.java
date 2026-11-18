package io.roastedroot.typewire.abi;

/**
 * Whether a {@code canon} definition is lifting or lowering. This affects how {@link
 * CanonicalAbi#flattenFuncType} handles an over-large flat parameter/result list.
 */
public enum Direction {
    LIFT,
    LOWER
}
