package run.endive.cm.types;

import java.util.Objects;

public final class Instance {

    private final InstanceExpr expr;

    private Instance(InstanceExpr expr) {
        this.expr = expr;
    }

    public static Instance of(InstanceExpr expr) {
        return new Instance(expr);
    }

    public InstanceExpr expr() {
        return expr;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Instance)) {
            return false;
        }
        Instance instance = (Instance) o;
        return Objects.equals(expr, instance.expr);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(expr);
    }

    @Override
    public String toString() {
        return "Instance{" + "expr=" + expr + '}';
    }
}
