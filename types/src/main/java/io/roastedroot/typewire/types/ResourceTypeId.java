package io.roastedroot.typewire.types;

/**
 * The runtime identity that a resolved {@code own} or {@code borrow} names.
 *
 * <p>Deliberately opaque here. What makes two resource types the same type is which instantiation
 * brought the type into existence, and this module has no way to express that. The Canonical ABI's
 * {@code ResourceTypeRef} is the real thing, and this is the hook that lets a {@link ResolvedType}
 * carry one, so that a handle's type is settled where the type is resolved rather than re-derived
 * from an index at every lift and lower.
 */
public interface ResourceTypeId {}
