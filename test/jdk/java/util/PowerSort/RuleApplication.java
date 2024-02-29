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

    public RuleApplication(int seed, int inputLength, int expectedRunLength, PermutationRules rule) {
        this.randomSeed = seed;
        this.random = new Random(seed);
        this.inputLength = inputLength;
        this.expectedRunLength = expectedRunLength;
        this.rule = rule;
        this.comp = rule.getComparator();
    }

    public T[] generate() {
        generatedArray = rule.generate(inputLength, random, expectedRunLength);
        return generatedArray;
    }

    public void checkSorted() {
        for (int i = 0; i < generatedArray.length - 1; i++) {
            if (comp.compare(generatedArray[i + 1], generatedArray[i]) < 0) {
                throw new RuntimeException(this + " is not sorted at " + i + " " + generatedArray[i] + " " + generatedArray[i + 1]);
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