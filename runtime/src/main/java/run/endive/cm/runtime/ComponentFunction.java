package run.endive.cm.runtime;

@FunctionalInterface
public interface ComponentFunction {

    long[] apply(long... args);
}
