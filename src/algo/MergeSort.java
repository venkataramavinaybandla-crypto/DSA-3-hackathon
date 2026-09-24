package algo;

import core.DynamicArray;
import core.Paper;

/**
 * Stable Merge Sort algorithm implemented from scratch without standard library collections.
 *
 * <p>Sorts academic {@link Paper} objects by {@code citationCount} in descending order.
 *
 * <p>STABILITY GUARANTEE:
 * When two papers have identical citation counts, their original relative order is strictly
 * preserved. This property ensures predictability and allows callers to establish secondary
 * sort keys (such as publication year or title) by pre-sorting or relying on insertion order.
 *
 * <p>Time Complexity: O(n log n) in all cases (best, worst, average).
 * Space Complexity: O(n) auxiliary space for merge buffers.
 */
public final class MergeSort {

    private MergeSort() {
        // static utility class, prevent instantiation
    }

    /**
     * Sorts the given {@link DynamicArray} of papers in-place in descending order of citationCount.
     * Guaranteed to be stable (ties preserve original relative order).
     *
     * @param papers the array of papers to sort; does nothing if null or size <= 1
     */
    public static void sort(DynamicArray<Paper> papers) {
        if (papers == null || papers.size() <= 1) {
            return;
        }

        int n = papers.size();
        Paper[] arr = new Paper[n];
        for (int i = 0; i < n; i++) {
            arr[i] = papers.get(i);
        }

        Paper[] aux = new Paper[n];
        mergeSort(arr, aux, 0, n - 1);

        for (int i = 0; i < n; i++) {
            papers.set(i, arr[i]);
        }
    }

    /**
     * Returns a new {@link DynamicArray} containing the papers sorted in descending order
     * of citationCount, leaving the original array completely unmodified.
     *
     * @param papers the input array of papers
     * @return a new sorted DynamicArray of papers
     */
    public static DynamicArray<Paper> sortedCopy(DynamicArray<Paper> papers) {
        if (papers == null) {
            return new DynamicArray<>();
        }
        DynamicArray<Paper> copy = new DynamicArray<>(papers.size());
        for (int i = 0; i < papers.size(); i++) {
            copy.add(papers.get(i));
        }
        sort(copy);
        return copy;
    }

    /**
     * Sorts an array of Paper objects in-place in descending order of citationCount.
     *
     * @param arr the array to sort
     */
    public static void sort(Paper[] arr) {
        if (arr == null || arr.length <= 1) {
            return;
        }
        Paper[] aux = new Paper[arr.length];
        mergeSort(arr, aux, 0, arr.length - 1);
    }

    /**
     * Recursive divide-and-conquer merge sort.
     */
    private static void mergeSort(Paper[] arr, Paper[] aux, int low, int high) {
        if (low >= high) {
            return;
        }

        int mid = low + (high - low) / 2;
        mergeSort(arr, aux, low, mid);
        mergeSort(arr, aux, mid + 1, high);
        merge(arr, aux, low, mid, high);
    }

    /**
     * Merges two sorted subarrays arr[low..mid] and arr[mid+1..high] into aux and copies back.
     *
     * <p>Descending order condition:
     * When left.getCitationCount() >= right.getCitationCount(), we choose left.
     * Using >= ensures strict stability because the element from the left partition
     * (which preceded the equal element in the right partition) is placed first.
     */
    private static void merge(Paper[] arr, Paper[] aux, int low, int mid, int high) {
        for (int k = low; k <= high; k++) {
            aux[k] = arr[k];
        }

        int i = low;
        int j = mid + 1;

        for (int k = low; k <= high; k++) {
            if (i > mid) {
                arr[k] = aux[j++];
            } else if (j > high) {
                arr[k] = aux[i++];
            } else {
                int leftCitations = (aux[i] != null) ? aux[i].getCitationCount() : -1;
                int rightCitations = (aux[j] != null) ? aux[j].getCitationCount() : -1;

                // >= guarantees stability for descending sort:
                // Left element wins ties and stays ahead
                if (leftCitations >= rightCitations) {
                    arr[k] = aux[i++];
                } else {
                    arr[k] = aux[j++];
                }
            }
        }
    }
}
