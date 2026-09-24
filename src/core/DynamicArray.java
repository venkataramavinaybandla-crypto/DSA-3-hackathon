package core;

/**
 * Generic resizable dynamic array implementation from scratch without standard collections.
 *
 * @param <T> the type of elements stored in the array
 */
public class DynamicArray<T> {
    private static final int DEFAULT_CAPACITY = 8;

    private Object[] data;
    private int size;

    public DynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.data = new Object[initialCapacity == 0 ? DEFAULT_CAPACITY : initialCapacity];
        this.size = 0;
    }

    public void add(T item) {
        if (size == data.length) {
            ensureCapacity(data.length == 0 ? DEFAULT_CAPACITY : data.length * 2);
        }
        data[size++] = item;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T) data[index];
    }

    @SuppressWarnings("unchecked")
    public T set(int index, T item) {
        checkIndex(index);
        T old = (T) data[index];
        data[index] = item;
        return old;
    }

    @SuppressWarnings("unchecked")
    public T remove(int index) {
        checkIndex(index);
        T removed = (T) data[index];
        for (int i = index; i < size - 1; i++) {
            data[i] = data[i + 1];
        }
        data[size - 1] = null;
        size--;
        return removed;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(T item) {
        return indexOf(item) != -1;
    }

    public int indexOf(T item) {
        for (int i = 0; i < size; i++) {
            if (item == null) {
                if (data[i] == null) {
                    return i;
                }
            } else if (item.equals(data[i])) {
                return i;
            }
        }
        return -1;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            data[i] = null;
        }
        size = 0;
    }

    public int capacity() {
        return data.length;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            Object[] newData = new Object[minCapacity];
            for (int i = 0; i < size; i++) {
                newData[i] = data[i];
            }
            data = newData;
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + ", Size: " + size);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < size; i++) {
            sb.append(data[i]);
            if (i < size - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
