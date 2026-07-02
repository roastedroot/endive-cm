package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.TagType;

public final class CoreTagImportDesc extends CoreImportDesc {

    private final TagType tagType;

    private CoreTagImportDesc(byte attribute, int tagTypeIdx) {
        super(ExternalType.TAG);
        this.tagType = new TagType(attribute, tagTypeIdx);
    }

    public TagType tagType() {
        return tagType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private byte attribute;
        private int tagTypeIdx;

        public Builder withAttribute(byte attribute) {
            this.attribute = attribute;
            return this;
        }

        public Builder withTagTypeIdx(int tagTypeIdx) {
            this.tagTypeIdx = tagTypeIdx;
            return this;
        }

        public CoreTagImportDesc build() {
            return new CoreTagImportDesc(attribute, tagTypeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreTagImportDesc)) {
            return false;
        }
        CoreTagImportDesc that = (CoreTagImportDesc) o;
        return Objects.equals(tagType, that.tagType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tagType);
    }

    @Override
    public String toString() {
        return "CoreTagImportDesc{" + "tagType=" + tagType + '}';
    }
}
