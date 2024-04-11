import java.util.Comparator;
public class TimSort implements Sorter {

    @Override
    public <T > void sort(T[] A, int left, int right, Comparator<? super T> comp) {
        if (comp == null)  comp = Sorter.NaturalOrder.INSTANCE;
        java.util.TimSort.sort(A, left, right, comp, null, 0, 0);
    }

    @Override
    public void resetMergeCost() {
        java.util.TimSort.COUNT_MERGE_COSTS = true;
        java.util.TimSort.totalMergeCosts = 0;
    }

    @Override
    public long getMergeCost() {
        return java.util.TimSort.totalMergeCosts;
    }

    @Override
    public void resetNumberOfComparisons() {
        java.util.TimSort.COUNT_COMPARISONS = true;
        java.util.TimSort.totalComparisons = 0;
    }

    @Override
    public long getNumberOfComparisons() {
        return java.util.TimSort.totalComparisons;
    }

    @Override
    public String toString() {
        return "TimSort";
    }

}
