import java.util.*;
import java.util.Comparator;

public enum PermutationRules {
    RANDOM_INTEGER("Random_Integer") {  // random integers

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            for (int i = 0; i < len; i++)
                result[i] = rnd.nextInt();
            return result;
        }
    },

    DESCENDING_INTEGER("Descending_Integer") {  // descending integers

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            result[0] = rnd.nextInt();
            for (int i = 1; i < len; i++)
                result[i] = result[i - 1] - 1;
            return result;
        }
    },

    ASCENDING_INTEGER("Ascending_Integer") {  // ascending integers

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            result[0] = rnd.nextInt();
            for (int i = 1; i < len; i++)
                result[i] = result[i - 1] + 1;
            return result;
        }
    },

    ASCENDING_3_RND_EXCH_INTEGER("Ascending_3_Rnd_Exch_Integer") {  // ascending integers with 3 random exchanges

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            if (len == 0) return new Integer[0];
            Integer[] result = new Integer[len];
            result[0] = rnd.nextInt();  // make ascending starting with a random number
            for (int i = 1; i < len; i++)
                result[i] = result[i - 1] + 1;
            for (int i = 0; i < 3; i++)
                swap(result, rnd.nextInt(result.length),
                        rnd.nextInt(result.length));
            return result;
        }
    },

    ASCENDING_10_RND_AT_END_INTEGER("Ascending_10_Rnd_At_End_Integer") {  // ascending integers with 10 random numbers at the end

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            if (len == 0) return new Integer[0];
            Integer[] result = new Integer[len];
            int endStart = len - 10;
            for (int i = 0; i < endStart; i++)
                result[i] = i;
            for (int i = endStart; i < len; i++)
                result[i] = rnd.nextInt(endStart + 10);
            return result;
        }
    },

    ALL_EQUAL_INTEGER("All_Equal_Integer") { // all equal integers

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            Arrays.fill(result, 666);
            return result;
        }
    },

    DUPS_GALORE_INTEGER("Dups_Galore_Integer") {  // many duplicates of a few integers

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            for (int i = 0; i < len; i++)
                result[i] = rnd.nextInt(4);
            return result;
        }
    },

    RANDOM_WITH_DUPS_INTEGER("Random_With_Dups_Integer") { // less duplicates but still enough to make it interesting

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            for (int i = 0; i < len; i++)
                result[i] = rnd.nextInt(len);
            return result;
        }
    },

    RANDOM_RUNS_INTEGER("Random_Runs_Integer") {  // random runs of random length

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int expRunLen) {
            Integer[] result = new Integer[len];
            for (int i = 0; i < len; i++)
                result[i] = rnd.nextInt(len);
            sortRandomRuns(result, 0, len - 1, expRunLen, rnd);
            return result;
        }
    },

    /**
     * Fills the given array A with a Timsort drag input of the correct length
     * where all lengths are multiplied by minRunLen.
     */
    TIMSORT_DRAG_RUNS_INTEGER("Timsort_Drag_Runs_Integer") {  // Timsort drag input

        private static LinkedList<Integer> RTimCache = null;
        private static int RTimCacheN = -1;

        @SuppressWarnings("unchecked")
        public Integer[] generate(int len, Random rnd, int minRunLen) {
            Integer[] result = new Integer[len];
            int n = len / minRunLen;
            if (RTimCacheN != n || RTimCache == null) {
                RTimCacheN = n;
                RTimCache = timsortDragRunlengths(n);
            }
            LinkedList<Integer> RTim = RTimCache;
            fillWithUpAndDownRuns(result, RTim, minRunLen, rnd);
            return result;
        }

    },

    RANDOM_FLOAT("Random_Float") {  // random floats

        @SuppressWarnings("unchecked")
        public Float[] generate(int len, Random rnd, int expRunLen) {
            Float[] result = new Float[len];
            for (int i = 0; i < len; i++)
                result[i] = rnd.nextFloat();
            return result;
        }
    };

    //TODO: Add more permutation rules

    private final String name;


    PermutationRules(String name) {
        this.name = name;
    }

    protected abstract <T> T[] generate(int len, Random rnd, int expRunLen);

    public String toString() {
        return name;
    }

    /**
     * Returns a comparator that compares two objects according to their natural order.
     *
     * @param <T> the type of the objects to be compared
     * @return a comparator that compares two objects according to their natural order.
     */
    public <T> Comparator<? super T> getComparator() {
        return NaturalOrder.INSTANCE;
    }

    /**
     * A comparator that implements the natural ordering of a group of
     * mutually comparable elements. May be used when a supplied
     * comparator is null. To simplify code-sharing within underlying
     * implementations, the compare method only declares type Object
     * for its second argument.
     * <p>
     * Arrays class implementor's note: It is an empirical matter
     * whether ComparableTimSort offers any performance benefit over
     * TimSort used with this comparator.  If not, you are better off
     * deleting or bypassing ComparableTimSort.  There is currently no
     * empirical case for separating them for parallel sorting, so all
     * public Object parallelSort methods use the same comparator
     * based implementation.
     */
    static final class NaturalOrder implements Comparator<Object> {
        @SuppressWarnings("unchecked")
        public int compare(Object first, Object second) {
            return ((Comparable<Object>) first).compareTo(second);
        }

        static final NaturalOrder INSTANCE = new NaturalOrder();
    }


    private static <T> void swap(T[] a, int i, int j) {
        T t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static <T> void sortRandomRuns(final T[] A, final int left, int right, final int expRunLen, final Random random) {
        for (int i = left; i < right; ) {
            int j = 1;
            while (random.nextInt(expRunLen) != 0) ++j;
            j = Math.min(right, i + j);
            Arrays.sort(A, i, j + 1);
            i = j + 1;
        }
    }

    /**
     * Recursively computes R_Tim(n) (see Buss and Knop 2018)
     */
    public static LinkedList<Integer> timsortDragRunlengths(int n) {
        LinkedList<Integer> res;
        if (n <= 3) {
            res = new LinkedList<>();
            res.add(n);
        } else {
            int nPrime = n / 2;
            int nPrimePrime = n - nPrime - (nPrime - 1);
            res = timsortDragRunlengths(nPrime);
            res.addAll(timsortDragRunlengths(nPrime - 1));
            res.add(nPrimePrime);
        }
        return res;
    }

    public static int total(List<Integer> l) {
        return l.stream().mapToInt(Integer::intValue).sum();
    }

    public static <T> void shuffle(final T[] A, final int left, final int right, final Random random) {
        int n = right - left + 1;
        for (int i = n; i > 1; i--)
            swap(A, left + i - 1, left + random.nextInt(i));
    }

    private static <T> void reverseRange(T[] a, int lo, int hi) {
        while (lo < hi) {
            T t = a[lo];
            a[lo++] = a[hi];
            a[hi--] = t;
        }
    }


    @SuppressWarnings("unchecked")
    public static <T> void fillWithUpAndDownRuns(final T[] A, final List<Integer> runLengths,
                                                 final int runLenFactor, final Random random) {
        int n = A.length;
        assert total(runLengths) * runLenFactor == n;
        // make i same type as A
        for (int i = 0; i < n; ++i) A[i] = (T) Integer.valueOf(i + 1);
        shuffle(A, 0, n - 1, random);
        boolean reverse = false;
        int i = 0;
        for (int l : runLengths) {
            int L = l * runLenFactor;
            Arrays.sort(A, Math.max(0, i - 1), i + L);
            if (reverse) reverseRange(A, Math.max(0, i - 1), i + L - 1);
            reverse = !reverse;
            i += L;
        }
    }

}
