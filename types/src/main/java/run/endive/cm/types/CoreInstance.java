package run.endive.cm.types;

import java.util.Objects;

public final class CoreInstance {

    private final CoreInstanceExpr expr;

    private CoreInstance(CoreInstanceExpr expr) {
        this.expr = expr;
    }

    public static CoreInstance of(CoreInstanceExpr expr) {
        return new CoreInstance(expr);
    }

    public CoreInstanceExpr expr() {
        return expr;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreInstance)) {
            return false;
        }
        CoreInstance instance = (CoreInstance) o;
        return Objects.equals(expr, instance.expr);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(expr);
    }

    @Override
    public String toString() {
        return "CoreInstance{" + "expr=" + expr + '}';
    }
}
