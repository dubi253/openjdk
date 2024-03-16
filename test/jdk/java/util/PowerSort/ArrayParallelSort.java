import java.util.Comparator;
import java.util.Arrays;

public class ArrayParallelSort implements Sorter {

    private final boolean useMsbMergeType;
    private final boolean onlyIncreasingRuns;
    private final int myMinRunLen;

    private static int minRunLen = 24;

    public ArrayParallelSort(final boolean useMsbMergeType, final boolean onlyIncreasingRuns, final int minRunLen) {
        this.useMsbMergeType = useMsbMergeType;
        this.onlyIncreasingRuns = onlyIncreasingRuns;
        this.myMinRunLen = minRunLen;
    }

    @Override
    public <T> void sort(T[] A, int left, int right, Comparator<? super T> comp) {
        Arrays.parallelSort(A, left, right, comp);
    }

    @Override
    public void resetMergeCost() {
        java.util.PowerSort.COUNT_MERGE_COSTS = true;
        java.util.PowerSort.totalMergeCosts = 0;
    }

    @Override
    public long getMergeCost() {
        return java.util.PowerSort.totalMergeCosts;
    }

    @Override
    public String toString() {
        return "ArrayParallelSort" + (useMsbMergeType ? "-msb" : "") + (onlyIncreasingRuns ? "-inc" : "") + "-minRunLen-" + myMinRunLen;
    }


}
