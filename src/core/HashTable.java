package core;

/**
 * Generic hash table keyed by String using open addressing with linear probing and tombstone deletion.
 *
 * <p>Hash function: polynomial rolling hash with base 31, modulus 1_000_000_007 (a well-known
 * large Mersenne-adjacent prime that keeps intermediate values in long range and produces
 * good bit-distribution for typical string keys). The hash is then mapped into the current
 * table capacity via a second modulus.
 *
 * <p>Collision resolution: linear probing (step = 1), wrapping at capacity boundary.
 * Deleted slots are marked as TOMBSTONE so that searches probe through them rather than
 * stopping — critical for correctness of the open-addressing invariant.
 *
 * <p>Resize: triggered when (occupied + tombstones) / capacity exceeds LOAD_FACTOR_THRESHOLD (0.7).
 * On resize, capacity is doubled and all LIVE entries are rehashed into a fresh array;
 * tombstones are discarded (reclaimed) during rehash.
 *
 * @param <V> type of values stored; keys are always String
 */
public class HashTable<V> {

    // Polynomial rolling hash modulus — large prime for good distribution
    private static final long HASH_PRIME = 1_000_000_007L;
    // Polynomial base
    private static final int HASH_BASE = 31;
    // Resize when (occupied + tombstones) / capacity exceeds this threshold
    private static final double LOAD_FACTOR_THRESHOLD = 0.7;
    // Default initial capacity (must be > 0)
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private enum SlotState { EMPTY, OCCUPIED, TOMBSTONE }

    private static final class Slot<E> {
        SlotState state;
        String key;
        E value;

        Slot() {
            this.state = SlotState.EMPTY;
        }
    }

    private Slot<V>[] table;
    private int liveCount;      // number of OCCUPIED entries (the public "size")
    private int totalUsed;      // OCCUPIED + TOMBSTONE (used for load-factor check)
    private int capacity;

    @SuppressWarnings("unchecked")
    public HashTable() {
        this.capacity = DEFAULT_INITIAL_CAPACITY;
        this.table = new Slot[capacity];
        for (int i = 0; i < capacity; i++) {
            table[i] = new Slot<>();
        }
        this.liveCount = 0;
        this.totalUsed = 0;
    }

    @SuppressWarnings("unchecked")
    public HashTable(int initialCapacity) {
        if (initialCapacity <= 0) {
            initialCapacity = DEFAULT_INITIAL_CAPACITY;
        }
        this.capacity = initialCapacity;
        this.table = new Slot[capacity];
        for (int i = 0; i < capacity; i++) {
            table[i] = new Slot<>();
        }
        this.liveCount = 0;
        this.totalUsed = 0;
    }

    /**
     * Inserts or updates the entry for the given key.
     * If the key already exists, its value is replaced and size() does not change.
     *
     * @param key   non-null string key
     * @param value value to associate with key
     */
    public void put(String key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Resize before insert if load factor would be exceeded
        if ((double)(totalUsed + 1) / capacity >= LOAD_FACTOR_THRESHOLD) {
            resize();
        }

        int startIndex = hash(key);
        int firstTombstone = -1;

        for (int i = 0; i < capacity; i++) {
            int idx = (startIndex + i) % capacity;
            Slot<V> slot = table[idx];

            if (slot.state == SlotState.EMPTY) {
                // Key not present — insert into first tombstone if found, else here
                int insertIdx = (firstTombstone != -1) ? firstTombstone : idx;
                Slot<V> insertSlot = table[insertIdx];
                if (insertSlot.state != SlotState.OCCUPIED) {
                    // First tombstone reuse counts: totalUsed already includes the tombstone;
                    // replacing it keeps totalUsed the same but we must account for it below
                    if (insertSlot.state == SlotState.EMPTY) {
                        totalUsed++;
                    }
                    // else it's TOMBSTONE — totalUsed already counted it, stays the same
                }
                insertSlot.state = SlotState.OCCUPIED;
                insertSlot.key = key;
                insertSlot.value = value;
                liveCount++;
                return;

            } else if (slot.state == SlotState.TOMBSTONE) {
                if (firstTombstone == -1) {
                    firstTombstone = idx;
                }

            } else { // OCCUPIED
                if (key.equals(slot.key)) {
                    // Update existing key — no count changes
                    slot.value = value;
                    return;
                }
            }
        }

        // If we get here, every slot was OCCUPIED or TOMBSTONE and key wasn't found.
        // This should not happen if the resize threshold is set correctly, but handle defensively.
        if (firstTombstone != -1) {
            Slot<V> insertSlot = table[firstTombstone];
            // Tombstone reuse: totalUsed stays the same (tombstone was already counted)
            insertSlot.state = SlotState.OCCUPIED;
            insertSlot.key = key;
            insertSlot.value = value;
            liveCount++;
        }
    }

    /**
     * Returns the value for the given key, or null if the key is not present.
     *
     * @param key the string key to look up
     * @return associated value or null
     */
    public V get(String key) {
        if (key == null) {
            return null;
        }
        int idx = findSlot(key);
        if (idx == -1) {
            return null;
        }
        return table[idx].value;
    }

    /**
     * Returns true if the given key has an entry in the table.
     *
     * @param key the string key to check
     * @return true if key is present
     */
    public boolean containsKey(String key) {
        if (key == null) {
            return false;
        }
        return findSlot(key) != -1;
    }

    /**
     * Removes the entry for the given key and marks its slot as TOMBSTONE.
     *
     * @param key the string key to remove
     * @return the removed value, or null if the key was not present
     */
    public V remove(String key) {
        if (key == null) {
            return null;
        }
        int idx = findSlot(key);
        if (idx == -1) {
            return null;
        }
        V removed = table[idx].value;
        table[idx].state = SlotState.TOMBSTONE;
        table[idx].key = null;
        table[idx].value = null;
        liveCount--;
        // totalUsed stays the same (tombstone still counts)
        return removed;
    }

    /**
     * Returns the number of live (non-tombstone, non-empty) entries.
     *
     * @return live entry count
     */
    public int size() {
        return liveCount;
    }

    /**
     * Returns true if there are no live entries.
     *
     * @return true if size() == 0
     */
    public boolean isEmpty() {
        return liveCount == 0;
    }

    /**
     * Returns the current backing-array capacity (for testing/diagnostics).
     *
     * @return current capacity
     */
    public int capacity() {
        return capacity;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Computes the starting probe index for key using polynomial rolling hash.
     * hash(key) = (sum of hash_base^i * c_i) mod HASH_PRIME, then mod capacity.
     */
    private int hash(String key) {
        long h = 0;
        for (int i = 0; i < key.length(); i++) {
            h = (h * HASH_BASE + key.charAt(i)) % HASH_PRIME;
        }
        // h is non-negative (HASH_PRIME is positive); map into [0, capacity)
        return (int)(h % capacity);
    }

    /**
     * Linear-probe search for key. Returns table index if OCCUPIED slot with matching key
     * is found, -1 otherwise. Probes through TOMBSTONE slots.
     */
    private int findSlot(String key) {
        int startIndex = hash(key);
        for (int i = 0; i < capacity; i++) {
            int idx = (startIndex + i) % capacity;
            Slot<V> slot = table[idx];
            if (slot.state == SlotState.EMPTY) {
                return -1;  // key cannot exist past an EMPTY slot
            }
            if (slot.state == SlotState.OCCUPIED && key.equals(slot.key)) {
                return idx;
            }
            // TOMBSTONE: keep probing
        }
        return -1;
    }

    /**
     * Doubles the capacity and rehashes all live entries into a fresh array.
     * Tombstones are discarded during this process.
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        int newCapacity = capacity * 2;
        Slot<V>[] newTable = new Slot[newCapacity];
        for (int i = 0; i < newCapacity; i++) {
            newTable[i] = new Slot<>();
        }

        // Rehash only OCCUPIED (live) entries; tombstones are dropped
        for (int i = 0; i < capacity; i++) {
            Slot<V> slot = table[i];
            if (slot.state == SlotState.OCCUPIED) {
                // Recompute hash with new capacity
                long h = 0;
                for (int c = 0; c < slot.key.length(); c++) {
                    h = (h * HASH_BASE + slot.key.charAt(c)) % HASH_PRIME;
                }
                int startIdx = (int)(h % newCapacity);
                // Linear probe into new table
                for (int j = 0; j < newCapacity; j++) {
                    int idx = (startIdx + j) % newCapacity;
                    if (newTable[idx].state == SlotState.EMPTY) {
                        newTable[idx].state = SlotState.OCCUPIED;
                        newTable[idx].key = slot.key;
                        newTable[idx].value = slot.value;
                        break;
                    }
                }
            }
        }

        table = newTable;
        capacity = newCapacity;
        // After rehash, totalUsed equals liveCount (tombstones gone)
        totalUsed = liveCount;
    }
}
