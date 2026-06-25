package run.endive.cm.types;

import java.util.Objects;

public abstract class Alias {

    private final int id;
    private final Sort sort;

    protected Alias(int id, Sort sort) {
        Objects.requireNonNull(sort, "sort");
        this.id = id;
        this.sort = sort;
    }

    public int id() {
        return id;
    }

    public Sort sort() {
        return sort;
    }

    public static final class ID {
        private ID() {}

        public static final int EXPORT = 0x00;
        public static final int CORE_EXPORT = 0x01;
        public static final int OUTER = 0x02;
    }
}
