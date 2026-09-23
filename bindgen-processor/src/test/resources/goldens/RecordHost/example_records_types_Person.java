package endive.testing.example.records.types;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;
import run.endive.cm.abi.CharValue;

/**
 * The WIT record {@code person}, declared by {@code example:records/types}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
@SuppressWarnings("unchecked")
public final class Person {

    private final String name;

    private final Boolean active;

    private final BigInteger id;

    private final CharValue initial;

    private final List<String> tags;

    private final Point home;

    public Person(String name, Boolean active, BigInteger id, CharValue initial, List<String> tags, Point home) {
        this.name = name;
        this.active = active;
        this.id = id;
        this.initial = initial;
        this.tags = tags;
        this.home = home;
    }

    public String name() {
        return name;
    }

    public Boolean active() {
        return active;
    }

    public BigInteger id() {
        return id;
    }

    public CharValue initial() {
        return initial;
    }

    public List<String> tags() {
        return tags;
    }

    public Point home() {
        return home;
    }

    /**
     * This record as the ABI carries it, which is a map keyed by field label.
     */
    public Map<String, Object> toComponent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("active", active);
        fields.put("id", id);
        fields.put("initial", initial);
        fields.put("tags", tags);
        fields.put("home", home.toComponent());
        return fields;
    }

    /**
     * The record a lifted value carries.
     */
    public static Person fromComponent(Object value) {
        Map<?, ?> fields = (Map<?, ?>) value;
        return new Person((String) fields.get("name"), (Boolean) fields.get("active"), (BigInteger) fields.get("id"), (CharValue) fields.get("initial"), (List<String>) fields.get("tags"), Point.fromComponent(fields.get("home")));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Person)) {
            return false;
        }
        Person that = (Person) o;
        return Objects.equals(name, that.name) && Objects.equals(active, that.active) && Objects.equals(id, that.id) && Objects.equals(initial, that.initial) && Objects.equals(tags, that.tags) && Objects.equals(home, that.home);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, active, id, initial, tags, home);
    }

    @Override
    public String toString() {
        return "Person{" + "name=" + name + ", active=" + active + ", id=" + id + ", initial=" + initial + ", tags=" + tags + ", home=" + home + "}";
    }
}
