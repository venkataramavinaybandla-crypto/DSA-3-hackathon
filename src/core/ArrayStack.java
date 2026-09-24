package core;

/**
 * Generic LIFO Stack implementation backed by DynamicArray.
 *
 * @param <T> the type of elements stored in the stack
 */
public class ArrayStack<T> {
    private final DynamicArray<T> elements;

    public ArrayStack() {
        this.elements = new DynamicArray<>();
    }

    public ArrayStack(int initialCapacity) {
        this.elements = new DynamicArray<>(initialCapacity);
    }

    public void push(T item) {
        elements.add(item);
    }

    public T pop() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot pop from an empty stack");
        }
        return elements.remove(elements.size() - 1);
    }

    public T peek() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot peek an empty stack");
        }
        return elements.get(elements.size() - 1);
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int size() {
        return elements.size();
    }

    public void clear() {
        elements.clear();
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}
