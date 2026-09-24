package core;

/**
 * Test suite for foundational data structures in Phase 1:
 * DynamicArray, ArrayStack, ArrayQueue, LinkedList.
 */
public class DataStructuresTest {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("Running Phase 1 DataStructures Test Suite...\n");

        testDynamicArray();
        testArrayStack();
        testArrayQueue();
        testLinkedList();

        System.out.println("\n==========================================");
        System.out.println("DATA STRUCTURES TEST RESULTS: " + passedTests + " / " + totalTests + " PASSED");
        System.out.println("==========================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void assertTrue(String name, boolean cond) {
        totalTests++;
        if (cond) {
            passedTests++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name);
            throw new AssertionError("FAILED: " + name);
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        totalTests++;
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (eq) {
            passedTests++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name + " | Expected=" + expected + " Actual=" + actual);
            throw new AssertionError("FAILED: " + name);
        }
    }

    // ------------------------------------------------------------------ DynamicArray
    private static void testDynamicArray() {
        System.out.println("--- DynamicArray Tests ---");

        // 1. Empty state
        DynamicArray<String> arr = new DynamicArray<>();
        assertTrue("DynamicArray empty state isEmpty()", arr.isEmpty());
        assertEquals("DynamicArray empty state size()", 0, arr.size());

        // 2. Single add/access
        arr.add("hello");
        assertTrue("DynamicArray not empty after add", !arr.isEmpty());
        assertEquals("DynamicArray size 1 after add", 1, arr.size());
        assertEquals("DynamicArray get(0) == hello", "hello", arr.get(0));

        // 3. Resize triggered (initial capacity 2, add 20 elements -> multiple resizes)
        DynamicArray<Integer> smallArr = new DynamicArray<>(2);
        for (int i = 0; i < 20; i++) {
            smallArr.add(i * 10);
        }
        assertEquals("DynamicArray size 20 after multi-resize", 20, smallArr.size());
        assertTrue("DynamicArray capacity resized", smallArr.capacity() >= 20);
        for (int i = 0; i < 20; i++) {
            assertEquals("DynamicArray element after resize at index " + i, i * 10, smallArr.get(i));
        }

        // 4. remove() correctness and shifting verification
        DynamicArray<String> list = new DynamicArray<>();
        list.add("10");
        list.add("20");
        list.add("30");
        list.add("40");
        String removed = list.remove(1);
        assertEquals("DynamicArray remove returns removed item", "20", removed);
        assertEquals("DynamicArray size after remove", 3, list.size());
        assertEquals("DynamicArray element 0 after shift", "10", list.get(0));
        assertEquals("DynamicArray element 1 after shift", "30", list.get(1));
        assertEquals("DynamicArray element 2 after shift", "40", list.get(2));

        // 5. IndexOutOfBoundsException on invalid get()
        boolean threwNegative = false;
        try {
            list.get(-1);
        } catch (IndexOutOfBoundsException e) {
            threwNegative = true;
        }
        assertTrue("DynamicArray get(-1) throws IndexOutOfBoundsException", threwNegative);

        boolean threwOutOfBounds = false;
        try {
            list.get(list.size());
        } catch (IndexOutOfBoundsException e) {
            threwOutOfBounds = true;
        }
        assertTrue("DynamicArray get(size) throws IndexOutOfBoundsException", threwOutOfBounds);
    }

    // ------------------------------------------------------------------ ArrayStack
    private static void testArrayStack() {
        System.out.println("\n--- ArrayStack Tests ---");

        ArrayStack<String> stack = new ArrayStack<>();
        assertTrue("ArrayStack empty state isEmpty()", stack.isEmpty());
        assertEquals("ArrayStack empty state size()", 0, stack.size());

        // LIFO order with exact values
        stack.push("A");
        stack.push("B");
        stack.push("C");
        stack.push("D");
        stack.push("E");
        assertEquals("ArrayStack size after 5 pushes", 5, stack.size());
        assertEquals("ArrayStack peek() returns top", "E", stack.peek());
        assertEquals("ArrayStack size unchanged after peek", 5, stack.size());

        assertEquals("ArrayStack pop 1 (E)", "E", stack.pop());
        assertEquals("ArrayStack pop 2 (D)", "D", stack.pop());
        assertEquals("ArrayStack pop 3 (C)", "C", stack.pop());
        assertEquals("ArrayStack pop 4 (B)", "B", stack.pop());
        assertEquals("ArrayStack pop 5 (A)", "A", stack.pop());
        assertTrue("ArrayStack empty after popping all", stack.isEmpty());

        // IllegalStateException on empty pop() / peek()
        boolean threwPop = false;
        try {
            stack.pop();
        } catch (IllegalStateException e) {
            threwPop = true;
        }
        assertTrue("ArrayStack pop on empty throws IllegalStateException", threwPop);

        boolean threwPeek = false;
        try {
            stack.peek();
        } catch (IllegalStateException e) {
            threwPeek = true;
        }
        assertTrue("ArrayStack peek on empty throws IllegalStateException", threwPeek);
    }

    // ------------------------------------------------------------------ ArrayQueue
    private static void testArrayQueue() {
        System.out.println("\n--- ArrayQueue Tests ---");

        ArrayQueue<Integer> queue = new ArrayQueue<>();
        assertTrue("ArrayQueue empty state isEmpty()", queue.isEmpty());
        assertEquals("ArrayQueue empty state size()", 0, queue.size());

        // FIFO order with exact values
        queue.enqueue(100);
        queue.enqueue(200);
        queue.enqueue(300);
        assertEquals("ArrayQueue size after 3 enqueues", 3, queue.size());
        assertEquals("ArrayQueue peek() returns head", 100, queue.peek());

        assertEquals("ArrayQueue dequeue 1", 100, queue.dequeue());
        assertEquals("ArrayQueue dequeue 2", 200, queue.dequeue());
        assertEquals("ArrayQueue dequeue 3", 300, queue.dequeue());
        assertTrue("ArrayQueue empty after dequeuing all", queue.isEmpty());

        // Circular buffer wraparound scenario
        ArrayQueue<String> circ = new ArrayQueue<>(4);
        circ.enqueue("A");
        circ.enqueue("B");
        circ.enqueue("C");
        circ.enqueue("D"); // Buffer full (head=0, tail=0, size=4, capacity=4)

        // Dequeue 2 items (head becomes 2, size becomes 2)
        assertEquals("CircQueue dequeue A", "A", circ.dequeue());
        assertEquals("CircQueue dequeue B", "B", circ.dequeue());
        assertEquals("CircQueue size 2", 2, circ.size());

        // Enqueue 2 items (tail wraps around to indices 0 and 1)
        circ.enqueue("E");
        circ.enqueue("F");
        assertEquals("CircQueue size 4 after wraparound enqueue", 4, circ.size());

        // Dequeue all 4 and verify exact order across boundary
        assertEquals("CircQueue dequeue C", "C", circ.dequeue());
        assertEquals("CircQueue dequeue D", "D", circ.dequeue());
        assertEquals("CircQueue dequeue E", "E", circ.dequeue());
        assertEquals("CircQueue dequeue F", "F", circ.dequeue());
        assertTrue("CircQueue empty after wraparound dequeue", circ.isEmpty());

        // IllegalStateException on empty dequeue() / peek()
        boolean threwDequeue = false;
        try {
            circ.dequeue();
        } catch (IllegalStateException e) {
            threwDequeue = true;
        }
        assertTrue("ArrayQueue dequeue on empty throws IllegalStateException", threwDequeue);

        boolean threwPeek = false;
        try {
            circ.peek();
        } catch (IllegalStateException e) {
            threwPeek = true;
        }
        assertTrue("ArrayQueue peek on empty throws IllegalStateException", threwPeek);
    }

    // ------------------------------------------------------------------ LinkedList
    private static void testLinkedList() {
        System.out.println("\n--- LinkedList Tests ---");

        LinkedList<String> list = new LinkedList<>();
        assertTrue("LinkedList empty state isEmpty()", list.isEmpty());
        assertEquals("LinkedList empty state size()", 0, list.size());

        // addFirst / addLast ordering via getFirst, getLast, get(index)
        list.addLast("B");
        list.addFirst("A");
        list.addLast("C");
        assertEquals("LinkedList size is 3", 3, list.size());
        assertEquals("LinkedList get(0) == A", "A", list.get(0));
        assertEquals("LinkedList get(1) == B", "B", list.get(1));
        assertEquals("LinkedList get(2) == C", "C", list.get(2));
        assertEquals("LinkedList getFirst() == A", "A", list.getFirst());
        assertEquals("LinkedList getLast() == C", "C", list.getLast());

        // contains, size, isEmpty
        assertTrue("LinkedList contains A", list.contains("A"));
        assertTrue("LinkedList contains B", list.contains("B"));
        assertTrue("LinkedList contains C", list.contains("C"));
        assertTrue("LinkedList does not contain D", !list.contains("D"));

        // Middle element removal with unbroken links traversal via forEach
        LinkedList<String> list2 = new LinkedList<>();
        list2.addLast("A");
        list2.addLast("B");
        list2.addLast("C");
        list2.addLast("D");

        boolean removedMiddle = list2.remove("C");
        assertTrue("LinkedList remove C returns true", removedMiddle);
        assertEquals("LinkedList size 3 after remove C", 3, list2.size());
        assertTrue("LinkedList does not contain C anymore", !list2.contains("C"));

        // Verify unbroken links via forEach
        DynamicArray<String> collected = new DynamicArray<>();
        list2.forEach(collected::add);

        assertEquals("Collected size is 3", 3, collected.size());
        assertEquals("Collected[0] == A", "A", collected.get(0));
        assertEquals("Collected[1] == B", "B", collected.get(1));
        assertEquals("Collected[2] == D", "D", collected.get(2));
    }
}
