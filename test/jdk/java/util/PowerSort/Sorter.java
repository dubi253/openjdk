import java.util.Comparator;

/**
 * The interface Sorter.
 */
public interface Sorter {
    /**
     * Sorts A[left..right]
     *
     * @param <T>   the type parameter
     * @param A     the array to be sorted
     * @param left  the left inclusive
     * @param right the right exclusive
     * @param comp  the comp
     */
    <T> void sort(T[] A, int left, int right, Comparator<? super T> comp);

    /**
     * Zero merge cost.
     */
    void resetMergeCost();

    /**
     * Gets merge cost.
     *
     * @return the merge cost
     */
    long getMergeCost();

    @Override
    String toString();

    /**
     * Sort.
     *
     * @param <T>  the type parameter
     * @param A    the a
     * @param comp the comp
     */
    default <T> void sort(T[] A, Comparator<? super T> comp) {
        sort(A, 0, A.length, comp);
    }

    /**
     * Sort.
     *
     * @param <T> the type parameter
     * @param A   the a
     */
    default <T> void sort(T[] A) {
        sort(A, 0, A.length, PermutationRules.NaturalOrder.INSTANCE);
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

        /**
         * The Instance.
         */
        static final NaturalOrder INSTANCE = new NaturalOrder();
    }
}
