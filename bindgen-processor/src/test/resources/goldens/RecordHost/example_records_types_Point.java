package endive.testing.example.records.types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The WIT record {@code point}, declared by {@code example:records/types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Point {

    private final Long x;

    private final Long y;

    public Point(Long x, Long y) {
        this.x = x;
        this.y = y;
    }

    public Long x() {
        return x;
    }

    public Long y() {
        return y;
    }

    /**
     * This record as the ABI carries it, which is a map keyed by field label.
     */
    public Map<String, Object> toComponent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("x", x);
        fields.put("y", y);
        return fields;
    }

    /**
     * The record a lifted value carries.
     */
    public static Point fromComponent(Object value) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return new Point((Long) fields.get("x"), (Long) fields.get("y"));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) {
            return false;
        }
        Point that = (Point) o;
        return Objects.equals(x, that.x) && Objects.equals(y, that.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Point{" + "x=" + x + ", y=" + y + "}";
    }
}
