package run.endive.cm.runtime;

@FunctionalInterface
public interface ComponentFunction {

    Object[] apply(Object... args);
}
