package run.endive.cm.types;

public final class WasmComponent {

    private WasmComponent() {}

    public static WasmComponent.Builder builder() {
        return new WasmComponent.Builder();
    }

    public static final class Builder {
        private Builder() {}

        public WasmComponent build() {
            return new WasmComponent();
        }
    }
}
