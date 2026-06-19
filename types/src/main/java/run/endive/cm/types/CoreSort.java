package run.endive.cm.types;

import java.util.Arrays;

public enum CoreSort {
    FUNC(0x00),
    TABLE(0x01),
    MEMORY(0x02),
    GLOBAL(0x03),
    TAG(0x04),
    TYPE(0x10),
    MODULE(0x11),
    INSTANCE(0x12);

    private final int id;

    CoreSort(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static CoreSort fromId(int id) {
        return Arrays.stream(CoreSort.values())
                .filter(sort -> sort.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown <core:sort> ID: " + id));
    }
}
