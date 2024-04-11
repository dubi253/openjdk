import java.util.Comparator;
import java.util.Arrays;

public class ParallelPowerSort implements Sorter {
    @Override
    public <T> void sort(T[] A, int left, int right, Comparator<? super T> comp) {
        Arrays.parallelSort(A, left, right, comp, false);
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
    public void resetNumberOfComparisons() {
        java.util.PowerSort.COUNT_COMPARISONS = true;
        java.util.PowerSort.totalComparisons = 0;
    }

    @Override
    public long getNumberOfComparisons() {
        return java.util.PowerSort.totalComparisons;
    }

    @Override
    public String toString() {
        return "ParallelPowerSort";
    }


}
