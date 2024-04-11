import java.util.Comparator;
import java.util.Arrays;

public class ParallelTimSort implements Sorter {

    @Override
    public <T> void sort(T[] A, int left, int right, Comparator<? super T> comp) {
        Arrays.parallelSort(A, left, right, comp, true);
    }

    @Override
    public void resetMergeCost() {
        java.util.TimSort.COUNT_MERGE_COSTS = true;
        java.util.TimSort.totalMergeCosts = 0;
    }

    @Override
    public void resetNumberOfComparisons() {
        java.util.TimSort.COUNT_MERGE_COSTS = true;
        java.util.TimSort.totalMergeCosts = 0;
    }

    @Override
    public long getNumberOfComparisons() {
        return java.util.TimSort.totalMergeCosts;
    }

    @Override
    public long getMergeCost() {
        return java.util.TimSort.totalMergeCosts;
    }

    @Override
    public String toString() {
        return "ParallelTimSort";
    }


}
