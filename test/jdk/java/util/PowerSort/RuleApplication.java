import java.util.Comparator;
import java.util.Random;

public class RuleApplication<T> {
    private Random random;
    private final int randomSeed;
    private final int inputLength;
    private final int expectedRunLength;
    private final PermutationRules rule;

    private final Comparator<? super T> comp;

    private T[] generatedArray;

    private T[] testArray;

    public RuleApplication(int seed, int inputLength, int expectedRunLength, PermutationRules rule) {
        this.randomSeed = seed;
        this.random = new Random(seed);
        this.inputLength = inputLength;
        this.expectedRunLength = expectedRunLength;
        this.rule = rule;
        this.comp = rule.getComparator();
    }

    /**
     * Generate a new array using current Random and Rule.
     */
    public void generate() {
        generatedArray = rule.generate(inputLength, random, expectedRunLength);

        // copy the generated array to arrayToSort to avoid sorting the same array
        testArray = generatedArray.clone();
    }

    /**
     * Reset test array to the original generated array.
     */
    public void reset() {
        testArray = generatedArray.clone();
    }

    /**
     * Get the array to test.
     *
     * @return the array to test
     */
    public T[] get() {
        return testArray;
    }

    public void checkSorted() {
        for (int i = 0; i < testArray.length - 1; i++) {
            if (comp.compare(testArray[i + 1], testArray[i]) < 0) {
                throw new RuntimeException(this + " is not sorted at " + i + " " + testArray[i] + " " + testArray[i + 1]);
            }
        }
    }

    public Comparator<? super T> getComparator() {
        return comp;
    }

    public int getInputLength() {
        return inputLength;
    }

    public void resetRandom() {
        random = new Random(randomSeed);
    }

    @Override
    public String toString() {
        return rule.toString() + "-Len:" + inputLength + "-Seed:" + String.format("0x%X", randomSeed) + "-ExpRunLen:" + expectedRunLength;
    }
}