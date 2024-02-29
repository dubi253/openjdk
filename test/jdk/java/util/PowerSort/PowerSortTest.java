/*
 * @test
 * @library .
 * @summary Test PowerSort for correctness, time consumption, and memory usage
 * @build PermutationRules PowerSort PowerSortTest RuleApplication Sorter TestInputs TimSort WelfordVariance
 * @run main/timeout=200/othervm -Xmx2048m -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation PowerSortTest
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
    private final static boolean VERBOSE_IN_REPETITIONS = true;


    public static void main(String[] args) throws IOException {

        final int[] expectedRunLengths = new int[]{32};

        List<Sorter> algos = new ArrayList<>();

        algos.add(new TimSort());
        algos.add(new PowerSort(true, false, 32));


        int reps = 100;
        if (args.length >= 1) {
            reps = Integer.parseInt(args[0]);
        }

        if (args.length >= 2) {
            reps = Integer.parseInt(args[1]);
        }

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

        timeSorts(algos, reps, testInputs);
    }

    @SuppressWarnings("unchecked")
    public static <T> void timeSorts(final List<Sorter> algos, final int repetition, TestInputs testInputs) {
        warmup(algos, 10000);  // warmup the JVM to avoid timing noise, 12_000 rounds for each algorithm


        System.out.println("Runs with individual timing (skips first run):\n");

        for (RuleApplication<?> testInput : testInputs.getRules()) {
            System.out.println("==========" + testInput + "==========");

            for (final Sorter algo : algos) {

                testInput.resetRandom();  // reset the random seed for each algorithm

                final WelfordVariance samples = new WelfordVariance();
                if (VERBOSE_IN_REPETITIONS) {
                    System.out.println("----------" + algo + "----------");
                    if (COUNT_MERGE_COSTS) {
                        System.out.println("n\t ms\t \t merge-cost");
                    } else {
                        System.out.println("n\t ms");
                    }
                }
                for (int r = 0; r < repetition; ++r) {
                    if (COUNT_MERGE_COSTS) algo.resetMergeCost();
                    T[] a = (T[]) testInput.generate();

                    Comparator<? super T> comp = (Comparator<? super T>) testInput.getComparator();
                    final long startNanos = System.nanoTime();
                    algo.sort(a, comp);
                    final long endNanos = System.nanoTime();
                    if (ABORT_IF_RESULT_IS_NOT_SORTED) {
                        testInput.checkSorted();
                    }
                    final double msDiff = (endNanos - startNanos) / 1e6;  // 1e6 is 10^6, so it's converting nanoseconds to milliseconds
                    if (r != 0) {
                        // Skip first iteration, often slower!
                        samples.addSample(msDiff);
                        if (VERBOSE_IN_REPETITIONS) {
                            if (COUNT_MERGE_COSTS)
                                System.out.println(r + "\t " + msDiff + "\t " + algo.getMergeCost());
                            else
                                System.out.println(r + "\t " + msDiff);
                        }
                    }

                }

                System.out.println("avg-ms=" + (float) (samples.mean()) + ",\talgo=" + algo + ",\ttestInput=" + testInput + ",\trepetition:" + repetition + "\t" + samples);

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
//                    System.out.println("avg-ms=" + (msDiff / repetition) + ",\t algo=" + algoName + ", n=" + size + "    (" + total + ")");
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
