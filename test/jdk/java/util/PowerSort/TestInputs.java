import java.util.ArrayList;
import java.util.List;

public class TestInputs {
    private final List<Integer> randomSeeds;
    private final List<Integer> inputLengths;
    private final int[] expectedRunLengths;

    public TestInputs(List<Integer> randomSeeds, List<Integer> inputLengths, int[] expectedRunLengths) {
        this.randomSeeds = randomSeeds;
        this.inputLengths = inputLengths;
        this.expectedRunLengths = expectedRunLengths;
    }

    public Iterable<RuleApplication<?>> getRules() {
        List<RuleApplication<?>> applications = new ArrayList<>();
        for (PermutationRules rule : PermutationRules.values()) {
            for (Integer length : inputLengths) {
                for (Integer seed : randomSeeds) {
                    for (int runLength : expectedRunLengths) {
                        applications.add(new RuleApplication<>(seed, length, runLength, rule));
                    }
                }
            }
        }
        return applications;
    }
}
