package endive.testing.exports.example.records.shapes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;

/**
 * The WIT record {@code span}, declared by {@code example:records/shapes}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Span {

    private final Long start;

    private final Long end;

    public Span(Long start, Long end) {
        this.start = start;
        this.end = end;
    }

    public Long start() {
        return start;
    }

    public Long end() {
        return end;
    }

    /**
     * This record as the ABI carries it, which is a map keyed by field label.
     */
    public Map<String, Object> toComponent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("start", start);
        fields.put("end", end);
        return fields;
    }

    /**
     * The record a lifted value carries.
     */
    public static Span fromComponent(Object value) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return new Span((Long) fields.get("start"), (Long) fields.get("end"));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Span)) {
            return false;
        }
        Span that = (Span) o;
        return Objects.equals(start, that.start) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "Span{" + "start=" + start + ", end=" + end + "}";
    }
}
