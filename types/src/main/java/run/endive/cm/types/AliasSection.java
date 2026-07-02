package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AliasSection extends Section {

    private final List<Alias> aliases;

    private AliasSection(List<Alias> aliases) {
        super(SectionId.ALIAS);
        this.aliases = List.copyOf(aliases);
    }

    public List<Alias> aliases() {
        return aliases;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Alias> aliases = new ArrayList<>();

        public Builder addAlias(Alias alias) {
            Objects.requireNonNull(alias, "alias");
            this.aliases.add(alias);
            return this;
        }

        public AliasSection build() {
            return new AliasSection(aliases);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AliasSection)) {
            return false;
        }
        AliasSection that = (AliasSection) o;
        return Objects.equals(aliases, that.aliases);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(aliases);
    }

    @Override
    public String toString() {
        return "AliasSection{" + "aliases=" + aliases + '}';
    }
}
