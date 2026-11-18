package io.roastedroot.typewire.types;

public interface Specialized<T extends DefValType> {

    T despecialize();
}
