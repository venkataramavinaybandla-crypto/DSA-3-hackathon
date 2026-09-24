package core;

/**
 * Custom functional interface representing an operation that accepts a single input argument.
 *
 * @param <T> the type of the input to the operation
 */
@FunctionalInterface
public interface Consumer<T> {
    /**
     * Performs this operation on the given argument.
     *
     * @param item the input argument
     */
    void accept(T item);
}
