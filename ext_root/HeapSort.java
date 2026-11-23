import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Uses a manually implemented min-heap to find the lowest-scoring URL results.
public final class HeapSort {
    // This class only provides sorting operations, so it does not need instances.
    private HeapSort() {
    }

    // Returns up to limit lowest-scoring results without changing the input list.
    public static List<ScoredUrl> getLowestScoringUrls(List<ScoredUrl> results, int limit) {
        Objects.requireNonNull(results, "results");
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must not be negative.");
        }
        for (ScoredUrl result : results) {
            Objects.requireNonNull(result, "A result must not be null.");
        }

        // Copy the results so heap operations do not change the caller's list.
        List<ScoredUrl> minHeap = new ArrayList<>(results);

        // Turn the copied list into a min-heap, with the lowest score at the root.
        for (int parent = minHeap.size() / 2 - 1; parent >= 0; parent--) {
            siftDown(minHeap, parent);
        }

        // Remove the root repeatedly; each removed root is the next-lowest score.
        List<ScoredUrl> lowestScoringResults = new ArrayList<>(Math.min(limit, minHeap.size()));
        while (lowestScoringResults.size() < limit && !minHeap.isEmpty()) {
            lowestScoringResults.add(minHeap.get(0));

            // Move the last item to the root, then restore the min-heap property.
            ScoredUrl last = minHeap.remove(minHeap.size() - 1);
            if (!minHeap.isEmpty()) {
                minHeap.set(0, last);
                siftDown(minHeap, 0);
            }
        }

        return lowestScoringResults;
    }

    // Moves an item down until it is no larger than either child.
    private static void siftDown(List<ScoredUrl> heap, int root) {
        while (true) {
            int leftChild = root * 2 + 1;
            if (leftChild >= heap.size()) {
                return;
            }

            // Choose the child with the smaller score.
            int rightChild = leftChild + 1;
            int smallerChild = leftChild;
            if (rightChild < heap.size()
                    && heap.get(rightChild).getScore() < heap.get(leftChild).getScore()) {
                smallerChild = rightChild;
            }

            // Stop when the parent is already no larger than its smaller child.
            if (heap.get(root).getScore() <= heap.get(smallerChild).getScore()) {
                return;
            }

            // Move the smaller child up and continue restoring the heap.
            swap(heap, root, smallerChild);
            root = smallerChild;
        }
    }

    // Exchanges two results in the heap's array-backed tree.
    private static void swap(List<ScoredUrl> heap, int first, int second) {
        ScoredUrl temporary = heap.get(first);
        heap.set(first, heap.get(second));
        heap.set(second, temporary);
    }

    // Keeps a URL, its score, and its explanation together while sorting.
    public static final class ScoredUrl {
        private final String url;
        private final int score;
        private final String flags;

        // Creates a scored result using the fields returned by the existing analyzer.
        public ScoredUrl(String url, int score, String flags) {
            this.url = Objects.requireNonNull(url, "url");
            this.score = score;
            this.flags = Objects.requireNonNull(flags, "flags");
        }

        // Returns the URL for this result.
        public String getUrl() {
            return url;
        }

        // Returns this URL's numeric score.
        public int getScore() {
            return score;
        }

        // Returns the analyzer's score explanation.
        public String getFlags() {
            return flags;
        }
    }
}
