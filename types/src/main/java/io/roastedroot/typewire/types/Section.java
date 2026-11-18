package io.roastedroot.typewire.types;

public abstract class Section {
    private final int id;

    Section(long id) {
        this.id = (int) id;
    }

    public int sectionId() {
        return id;
    }
}
