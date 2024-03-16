/*
 * @test
 * @library .
 * @summary Test PowerSort for correctness, time consumption, and memory usage
 * @build PermutationRules PowerSort PowerSortTest RuleApplication Sorter TestInputs TimSort WelfordVariance
 * @run main/timeout=300/othervm -Xmx2048m -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation PowerSortTest
 *
 * @author Zhan Jin
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

public class PowerSortTest {

    private final static boolean ABORT_IF_RESULT_IS_NOT_SORTED = true;
    private final static boolean TIME_ALL_RUNS_IN_ONE_MEASUREMENT = false;
    private final static boolean COUNT_MERGE_COSTS = true;
    private final static boolean VERBOSE_IN_SAME_INPUT = false;


    public static void main(String[] args) throws IOException {

        final int[] expectedRunLengths = new int[]{32};

        List<Sorter> algos = new ArrayList<>();

        algos.add(new TimSort());
        algos.add(new PowerSort(true, false, 32));
        algos.add(new ArrayParallelSort(true, false, 32));


        int reps = 100;
        if (args.length >= 1) {
            reps = Integer.parseInt(args[0]);
        }


        int repsPerInput = 10;
        if (args.length >= 2) {
            repsPerInput = Integer.parseInt(args[1]);
        }

        ++repsPerInput;  // skip count the first run

        List<Integer> testInputLengths = Arrays.asList(100000);
        if (args.length >= 3) {
            testInputLengths = new LinkedList<>();
            for (final String size : args[2].split(",")) {
                testInputLengths.add(Integer.parseInt(size.replaceAll("\\D", "")));  // replace all non-digits with empty string
            }
        }

        List<Integer> randomSeeds = Arrays.asList(0xA380);
        if (args.length >= 4) {
            randomSeeds = new LinkedList<>();
            for (final String seed : args[3].split(",")) {
                randomSeeds.add(Integer.parseInt(seed, 16));  // parse as hex
            }
        }

        StringBuilder testName = new StringBuilder("PowerSortTest");
        if (args.length >= 5) testName = new StringBuilder(args[4]);


        // print out the test name
        SimpleDateFormat format = new SimpleDateFormat("-yyyy-MM-dd_HH-mm-ss");
        testName.append(format.format(new Date()));
        testName.append("-Algorithms:");
        for (Sorter algo : algos) testName.append(",").append(algo.toString());
        testName.append("-RandomSeeds:");
        for (int seed : randomSeeds) testName.append("-").append(Integer.toHexString(seed));
        testName.append("-TestInputLengths:");
        for (int n : testInputLengths) testName.append("-").append(n);
        testName.append("-Repetition:").append(reps);

        System.out.println("Test name: " + testName);

        TestInputs testInputs = new TestInputs(randomSeeds, testInputLengths, expectedRunLengths);

        timeSorts(algos, reps, repsPerInput, testInputs);
    }

    @SuppressWarnings("unchecked")
    public static <T> void timeSorts(final List<Sorter> algos, final int repetition, final int repsPerInput, TestInputs testInputs) {
        warmup(algos, 10000);  // warmup the JVM to avoid timing noise, 12_000 rounds for each algorithm


        System.out.println("Runs with individual timing (skips first run):\n");

        for (RuleApplication<?> testInput : testInputs.getRules()) {
            System.out.println("==========" + testInput + "==========");

            for (final Sorter algo : algos) {

                testInput.resetRandom();  // reset the random seed for each algorithm

                final WelfordVariance samples = new WelfordVariance();
                final WelfordVariance mergeCostsSamples = new WelfordVariance();
                System.out.println("----------" + algo + "----------");
                if (COUNT_MERGE_COSTS) {
                    System.out.println("n  ms    merge-cost");
                } else {
                    System.out.println("n  ms");
                }
                for (int r = 0; r < repetition; ++r) {

                    testInput.generate();
                    Comparator<? super T> comp = (Comparator<? super T>) testInput.getComparator();

                    final WelfordVariance sameInputSamples = new WelfordVariance();
                    final WelfordVariance sameInputMergeCostsSamples = new WelfordVariance();

                    for (int i = 0; i < repsPerInput; i++) {
                        if (COUNT_MERGE_COSTS) algo.resetMergeCost();
                        T[] a = (T[]) testInput.get();

                        final long startNanos = System.nanoTime();
                        algo.sort(a, comp);
                        final long endNanos = System.nanoTime();

                        if (ABORT_IF_RESULT_IS_NOT_SORTED) {
                            testInput.checkSorted();
                        }

                        final double msDiff = (endNanos - startNanos) / 1e6;  // 1e6 is 10^6, so it's converting nanoseconds to milliseconds

                        if (i != 0) {
                            // Skip first iteration, often slower!
                            sameInputSamples.addSample(msDiff);
                            sameInputMergeCostsSamples.addSample(algo.getMergeCost());
                            samples.addSample(msDiff);
                            mergeCostsSamples.addSample(algo.getMergeCost());

                            if (VERBOSE_IN_SAME_INPUT) {
                                if (COUNT_MERGE_COSTS)
                                    System.out.println(r + "." + i + "  " + msDiff + "  " + algo.getMergeCost());
                                else
                                    System.out.println(r + "." + i + "  " + msDiff);
                            }
                        }

                        testInput.reset();
                    }

                    if (COUNT_MERGE_COSTS)
                        System.out.println(r + "  " + sameInputSamples + "  " + sameInputMergeCostsSamples);
                    else
                        System.out.println(r + "  " + sameInputSamples);


                }

                System.out.println("avg-ms=" + (float) (samples.mean()) + samples + ", avg-merge-cost=" + (float) (mergeCostsSamples.mean()) + mergeCostsSamples + ", algo=" + algo + ", testInput=" + testInput + ", repetition:" + repetition);

            }
        }

        System.out.println("#finished: " + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + "\n");

//        if (TIME_ALL_RUNS_IN_ONE_MEASUREMENT) {
//            System.out.println("\n\n\nRuns with overall timing (incl. input generation):");
//            for (final Sorter algo : algos) {
//                random = new Random(seed);
//                final String algoName = algo.toString();
//                for (final int size : testInputLengths) {
//                    int[] A = testInputs.next(size, random, null);
//                    final long startNanos = System.nanoTime();
//                    int total = 0;
//                    for (int r = 0; r < repetition; ++r) {
//                        if (r != 0) A = testInputs.next(size, random, A);
//                        algo.sort(A, 0, size - 1);
//                        total += A[A.length / 2];
//                        //					if (!Util.isSorted(A)) throw new AssertionError();
//                    }
//                    final long endNanos = System.nanoTime();
//                    final float msDiff = (endNanos - startNanos) / 1e6f;
//                    System.out.println("avg-ms=" + (msDiff / repetition) + ",  algo=" + algoName + ", n=" + size + "    (" + total + ")");
//                }
//            }
//        }


    }

    public static void warmup(List<Sorter> algos, int warmupRounds) {
        System.out.println("Doing warmup (" + warmupRounds + " rounds)");
        Integer[] gold = new Integer[10000];
        Random random = new java.util.Random();
        for (int i = 0; i < gold.length; i++)
            gold[i] = random.nextInt();
        for (Sorter algo : algos) {
            for (int i = 0; i < warmupRounds; i++) {
                Integer[] test = gold.clone();
                algo.sort(test);
            }
        }
        System.out.println("  end warm up");
    }

    public static void writeArrayToCSV(Object[] a, String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Object o : a) {
            sb.append(o.toString());
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("\n");
        Files.write(Paths.get(fileName), sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
