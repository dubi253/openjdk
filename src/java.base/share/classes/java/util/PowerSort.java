/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Google Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util;

/**
 * Powersort implementation as described in the paper.
 * <p>
 * Natural runs are extended to minRunLen if needed before we continue
 * merging.
 * Unless useMsbMergeType is false, node powers are computed using
 * a most-significant-bit trick;
 * otherwise a loop is used.
 * If onlyIncreasingRuns is true, only weakly increasing runs are picked up.
 * <p>
 * reference: https://github.com/sebawild/nearly-optimal-mergesort-code/blob/7fcabfa141080ea8379ba6acdaab3ef44c94567d/src/wildinter/net/mergesort/PowerSort.java#L100
 *
 * @param <T> the type of the item to be sorted
 */
public class PowerSort<T> {
    private final int minRunLen;

    /**
     * The array being sorted.
     */
    private final T[] a;

    /**
     * The comparator for this sort.
     */
    private final Comparator<? super T> c;

    /**
     * Temp storage for merges. A workspace array may optionally be
     * provided in constructor, and if so will be used as long as it
     * is big enough.
     */
    private T[] tmp;
    private int tmpBase; // base of tmp array slice
    private int tmpLen;  // length of tmp array slice
    private static final int NULL_INDEX = Integer.MIN_VALUE;

    /**
     * When we get into galloping mode, we stay there until both runs win less
     * often than MIN_GALLOP consecutive times.
     */
    private static final int MIN_GALLOP = 7;

    /**
     * This controls when we get *into* galloping mode.  It is initialized
     * to MIN_GALLOP.  The mergeLo and mergeHi methods nudge it higher for
     * random data, and lower for highly structured data.
     */
    private int minGallop = MIN_GALLOP;


    /**
     * If true, count the merge costs
     */
    public static boolean COUNT_MERGE_COSTS = false;

    /**
     * Total merge costs of all merge calls
     */
    public static long totalMergeCosts = 0;

    /**
     * If true, count the number of comparisons
     */
    public static boolean COUNT_COMPARISONS = false;

    /**
     * Total number of comparisons
     */
    public static long totalComparisons = 0;

    /**
     * Creates a TimSort instance to maintain the state of an ongoing sort.
     *
     * @param a         the array to be sorted
     * @param c         the comparator to determine the order of the sort
     * @param work      a workspace array (slice)
     * @param workBase  origin of usable space in work array
     * @param workLen   usable size of work array
     * @param minRunLen minimum run length
     */
    private PowerSort(T[] a,
                      Comparator<? super T> c,
                      T[] work,
                      int workBase,
                      int workLen,
                      final int minRunLen) {


        this.minRunLen = minRunLen;

        this.a = a;
        this.c = c;

        // Allocate temp storage (which may be increased later if necessary)
        int len = a.length;
        if (work == null || workLen < len || workBase + len > work.length) {
            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            T[] newArray = (T[]) java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), len);
            tmp = newArray;
            tmpBase = 0;
            tmpLen = len;
        } else {
            tmp = work;
            tmpBase = workBase;
            tmpLen = workLen;
        }

    }

    /*
     * The next method (package private and static) constitutes the
     * entire API of this class.
     */

    /**
     * Sorts the given range, using the given workspace array slice
     * for temp storage when possible. This method is designed to be
     * invoked from public methods (in class Arrays) after performing
     * any necessary array bounds checks and expanding parameters into
     * the required forms.
     *
     * @param <T>                the type parameter
     * @param a                  the array to be sorted
     * @param lo                 the index of the first element, inclusive, to be sorted
     * @param hi                 the index of the last element, exclusive, to be sorted
     * @param c                  the comparator to use
     * @param work               a workspace array (slice)
     * @param workBase           origin of usable space in work array
     * @param workLen            usable size of work array
     * @param useMsbMergeType    the use msb merge type
     * @param onlyIncreasingRuns the only increasing runs
     * @param minRunLen          the min run len
     */
    public static <T> void sort(T[] a,
                                int lo,
                                int hi,
                                Comparator<? super T> c,
                                T[] work,
                                int workBase,
                                int workLen,
                                final boolean useMsbMergeType,
                                final boolean onlyIncreasingRuns,
                                final int minRunLen) {

        if (!useMsbMergeType && onlyIncreasingRuns)
            throw new UnsupportedOperationException();
        if (minRunLen > 1 && (!useMsbMergeType || onlyIncreasingRuns))
            throw new UnsupportedOperationException();

        assert c != null && a != null && lo >= 0 && lo <= hi && hi <= a.length;

        int nRemaining = hi - lo;
        if (nRemaining < 2)
            return;  // Arrays of size 0 and 1 are always sorted

        // If array is small, do a "mini-PowerSort" with no merges
        if (nRemaining < minRunLen) {
            int initRunLen = countRunAndMakeAscending(a, lo, hi, c);
            binarySort(a, lo, hi, lo + initRunLen, c);
            return;
        }

        /**
         * March over the array once, left to right, finding natural runs,
         * extending short natural runs to minRun elements, and merging runs
         * to maintain stack invariant.
         */
        PowerSort<T> ps = new PowerSort<>(a, c, work, workBase, workLen, minRunLen);

        if (useMsbMergeType) {
            if (onlyIncreasingRuns)
                ps.powersortIncreasingOnlyMSB(lo, hi);
            else
                ps.powersort(lo, hi);
        } else {
            ps.powersortBitWise(lo, hi);
        }
    }

    private void powersortIncreasingOnlyMSB(int lo, int hi) {
        int n = hi - lo;
        int lgnPlus2 = log2(n) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = lo, endA = extendWeaklyIncreasingRunRight(startA, hi);
        while (endA < hi - 1) {
            int startB = endA + 1, endB = extendWeaklyIncreasingRunRight(startB, hi);
            int k = nodePower(lo, hi, startA, startB, endB);
            assert k != top;
            // clear left subtree bottom-up if needed
            for (int l = top; l > k; --l) {
                if (leftRunStart[l] == NULL_INDEX) continue;
                mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, endA);
                startA = leftRunStart[l];
                leftRunStart[l] = NULL_INDEX;
            }
            // store left half of merge between A and B
            leftRunStart[k] = startA;
            leftRunEnd[k] = endA;
            top = k;
            startA = startB;
            endA = endB;
        }
        assert endA == hi - 1;
        for (int l = top; l > 0; --l) {
            if (leftRunStart[l] == NULL_INDEX) continue;
            mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, hi - 1);
        }
    }

    /**
     * Normal Powersort. Sorts the given range.
     *
     * @param lo the index of the first element, inclusive, to be sorted
     * @param hi the index of the last element, exclusive, to be sorted
     */
    private void powersort(int lo, int hi) {
        int nRemaining = hi - lo;
        int lgnPlus2 = log2(nRemaining) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = lo, lenA = countRunAndMakeAscending(a, startA, hi, c);
        int endA = startA + lenA - 1;

        int minRun = minRunLength(nRemaining);

        if (lenA < minRun) {
            endA = hi < startA + minRun - 1 ? hi - 1 : startA + minRun - 1;
            binarySort(a, startA, endA + 1, startA + lenA, c);
        }

        while (endA < hi - 1) {
            // Identify next run
            int startB = endA + 1;
            int lenB = countRunAndMakeAscending(a, startB, hi, c);
            int endB = startB + lenB - 1;
            if (lenB < minRun) {
                endB = hi < startB + minRun ? hi - 1 : startB + minRun - 1;
                binarySort(a, startB, endB + 1, startB + lenB, c);
            }
            int k = nodePower(lo, hi, startA, startB, endB);
            assert k != top;
            for (int l = top; l > k; --l) {
                if (leftRunStart[l] == NULL_INDEX) continue;
                mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, endA);
                startA = leftRunStart[l];
                leftRunStart[l] = NULL_INDEX;
            }
            // store left half of merge between A and B
            leftRunStart[k] = startA;
            leftRunEnd[k] = endA;
            top = k;
            startA = startB;
            endA = endB;
        }
        assert endA == hi - 1;
        for (int l = top; l > 0; --l) {
            if (leftRunStart[l] == NULL_INDEX) continue;
            mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, hi - 1);
        }
    }

    private void powersortBitWise(int lo, int hi) {
        int n = hi - lo;
        int lgnPlus2 = log2(n) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = lo, lenA = countRunAndMakeAscending(a, startA, hi, c);
        int endA = startA + lenA - 1;
        while (endA < hi - 1) {
            int startB = endA + 1, endB = startB + countRunAndMakeAscending(a, startB, hi, c) - 1;
            int k = nodePowerBitwise(lo, hi, startA, startB, endB);
            assert k != top;
            // clear left subtree bottom-up if needed
            for (int l = top; l > k; --l) {
                if (leftRunStart[l] == NULL_INDEX) continue;
                mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, endA);
                startA = leftRunStart[l];
                leftRunStart[l] = NULL_INDEX;
            }
            // store left half of merge between A and B
            leftRunStart[k] = startA;
            leftRunEnd[k] = endA;
            top = k;
            startA = startB;
            endA = endB;
        }
        assert endA == hi - 1;
        for (int l = top; l > 0; --l) {
            if (leftRunStart[l] == NULL_INDEX) continue;
            mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, hi - 1);
        }
    }

    private int extendWeaklyIncreasingRunRight(int i, final int right) {
        // use local variables for performance
        T[] a = this.a;
        Comparator<? super T> c = this.c;

        while (i < right - 1 && c.compare(a[i + 1], a[i]) >= 0) {

            // count comparisons
            if (COUNT_COMPARISONS) totalComparisons++;

            ++i;
        }
        return i;
    }

    /**
     * Returns the length of the run beginning at the specified position in
     * the specified array and reverses the run if it is descending (ensuring
     * that the run will always be ascending when the method returns).
     * <p>
     * A run is the longest ascending sequence with:
     * <p>
     * a[lo] <= a[lo + 1] <= a[lo + 2] <= ...
     * <p>
     * or the longest descending sequence with:
     * <p>
     * a[lo] >  a[lo + 1] >  a[lo + 2] >  ...
     * <p>
     * For its intended use in a stable mergesort, the strictness of the
     * definition of "descending" is needed so that the call can safely
     * reverse a descending sequence without violating stability.
     *
     * @param a  the array in which a run is to be counted and possibly reversed
     * @param lo index of the first element in the run
     * @param hi index after the last element that may be contained in the run.
     *           It is required that {@code lo < hi}.
     * @param c  the comparator to used for the sort
     * @return the length of the run beginning at the specified position in
     * the specified array
     */
    private static <T> int countRunAndMakeAscending(T[] a, int lo, int hi,
                                                    Comparator<? super T> c) {
        assert lo < hi;
        int runHi = lo + 1;
        if (runHi == hi)
            return 1;

        // Find end of run, and reverse range if descending

        // count comparisons
        if (COUNT_COMPARISONS) totalComparisons++;

        if (c.compare(a[runHi++], a[lo]) < 0) { // Descending
            while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) < 0) {

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                runHi++;
            }
            reverseRange(a, lo, runHi);
        } else {                              // Ascending
            while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) >= 0) {

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                runHi++;
            }
        }

        return runHi - lo;
    }

    /**
     * Returns the minimum acceptable run length for an array of the specified
     * length. Natural runs shorter than this will be extended with
     * {@link #binarySort}.
     * <p>
     * Roughly speaking, the computation is:
     * <p>
     * If n < MIN_MERGE, return n (it's too small to bother with fancy stuff).
     * Else if n is an exact power of 2, return MIN_MERGE/2.
     * Else return an int k, MIN_MERGE/2 <= k <= MIN_MERGE, such that n/k
     * is close to, but strictly less than, an exact power of 2.
     * <p>
     * For the rationale, see listsort.txt.
     *
     * @param n the length of the array to be sorted
     * @return the length of the minimum run to be merged
     */
    private int minRunLength(int n) {
        assert n >= 0;
        int r = 0;      // Becomes 1 if any 1 bits are shifted off
        while (n >= this.minRunLen) {
            r |= (n & 1);
            n >>= 1;
        }
        return n + r;
    }

    private static int log2(int n) {
        if (n == 0) throw new IllegalArgumentException("lg(0) undefined");
        return 31 - numberOfLeadingZeros(n);
    }

    /**
     * Reverse the specified range of the specified array.
     *
     * @param a  the array in which a range is to be reversed
     * @param lo the index of the first element in the range to be reversed
     * @param hi the index after the last element in the range to be reversed
     */
    private static void reverseRange(Object[] a, int lo, int hi) {
        hi--;
        while (lo < hi) {
            Object t = a[lo];
            a[lo++] = a[hi];
            a[hi--] = t;
        }
    }

    /**
     * Sorts the specified portion of the specified array using a binary
     * insertion sort.  This is the best method for sorting small numbers
     * of elements.  It requires O(n log n) compares, but O(n^2) data
     * movement (worst case).
     * <p>
     * If the initial part of the specified range is already sorted,
     * this method can take advantage of it: the method assumes that the
     * elements from index {@code lo}, inclusive, to {@code start},
     * exclusive are already sorted.
     *
     * @param a     the array in which a range is to be sorted
     * @param lo    the index of the first element in the range to be sorted
     * @param hi    the index after the last element in the range to be sorted
     * @param start the index of the first element in the range that is
     *              not already known to be sorted ({@code lo <= start <= hi})
     * @param c     comparator to used for the sort
     */
    @SuppressWarnings("fallthrough")
    private static <T> void binarySort(T[] a, int lo, int hi, int start,
                                       Comparator<? super T> c) {
        assert lo <= start && start <= hi;
        if (start == lo)
            start++;
        for (; start < hi; start++) {
            T pivot = a[start];

            // Set left (and right) to the index where a[start] (pivot) belongs
            int left = lo;
            int right = start;
            assert left <= right;
            /*
             * Invariants:
             *   pivot >= all in [lo, left).
             *   pivot <  all in [right, start).
             */
            while (left < right) {
                int mid = (left + right) >>> 1;

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                if (c.compare(pivot, a[mid]) < 0)
                    right = mid;
                else
                    left = mid + 1;
            }
            assert left == right;

            /*
             * The invariants still hold: pivot >= all in [lo, left) and
             * pivot < all in [left, start), so pivot belongs at left.  Note
             * that if there are elements equal to pivot, left points to the
             * first slot after them -- that's why this sort is stable.
             * Slide elements over to make room for pivot.
             */
            int n = start - left;  // The number of elements to move
            // Switch is just an optimization for arraycopy in default case
            switch (n) {
                case 2:
                    a[left + 2] = a[left + 1];
                case 1:
                    a[left + 1] = a[left];
                    break;
                default:
                    System.arraycopy(a, left, a, left + 1, n);
            }
            a[left] = pivot;
        }
    }

    /**
     * Count the node power of the two runs.
     * Powersort builds on a modified bisection heuristic for computing nearly-optimal binary search trees that
     * might be independent interest. It has the same quality guarantees as Mehlhorn's original formulation,
     * but allows the tree to be built "bottom-up" as a Cartesian tree over a certain sequence, the "node powers".
     *
     * @param left   the index of the first element, inclusive
     * @param right  the index after the last element in the range, exclusive
     * @param startA the index of the first element in the first run
     * @param startB the index of the first element in the second run
     * @param endB   the index of the last element in the second run
     * @return
     */
    private int nodePower(int left, int right, int startA, int startB, int endB) {
        int n = right - left;
        long l = (long) startA + (long) startB - ((long) left << 1); // 2*middleA
        long r = (long) startB + (long) endB + 1 - ((long) left << 1); // 2*middleB
        int a = (int) ((l << 30) / n); // middleA / 2n
        int b = (int) ((r << 30) / n); // middleB / 2n
        return numberOfLeadingZeros(a ^ b);
    }

    private static int nodePowerBitwise(int left, int right, int startA, int startB, int endB) {
        assert right < (1 << 30); // otherwise nt2, l and r will overflow
        final int n = right - left + 1;
        int l = startA - (left << 1) + startB;
        int r = startB - (left << 1) + endB + 1;
        // a and b are given by l/nt2 and r/nt2, both are in [0,1).
        // we have to find the number of common digits in the
        // binary representation in the fractional part.
        int nCommonBits = 0;
        boolean digitA = l >= n, digitB = r >= n;
        while (digitA == digitB) {
            ++nCommonBits;
            l -= digitA ? n : 0;
            r -= digitA ? n : 0;
            l <<= 1;
            r <<= 1;
            digitA = l >= n;
            digitB = r >= n;
        }
        return nCommonBits + 1;
    }

//    /**
//     * Merges runs A[l..m-1] and A[m..r] in-place into A[l..r]
//     * with Sedgewick's bitonic merge (Program 8.2 in Algorithms in C++)
//     * using tmp as temporary storage.
//     * tmp.length must be at least r - l + 1.
//     */
//    private void mergeRuns(int l, int m, int r) {
//        // use local variables for performance
//        T[] a = this.a;
//        Comparator<? super T> c = this.c;
//        T[] tmp = this.tmp;
//        // use work base and len for fork/join
//        int tmpBase = this.tmpBase;
//        int tmpLen = this.tmpLen;
//        assert tmpLen >= r - l + 1;
//
//        // Adjust convention with Sedgewick to make 'm' point to the last element of the first run,
//        // aligning with the indexing convention used in Sedgewick's bitonic merge algorithm mentioned in Algorithms in C++
//        // This adjustment ensures 'm' accurately represents the endpoint of the first run.
//        // A[l..m] and A[m+1..r] are the two runs to be merged.
//        --m;
//
//        if (COUNT_MERGE_COSTS) totalMergeCosts += (r - l + 1);
//
//
//        // Prepare the temporary array.
//        if (m + 1 - l >= 0) System.arraycopy(a, l, tmp, tmpBase, m + 1 - l);
//
//        // reverse the second half into tmp, using relative indices to tempBase
//        for (int j = m; j < r; ++j) tmp[(r + m - j) - l + tmpBase] = a[j + 1];
//
//        int i = l, j = r;
//        for (int k = l; k <= r; ++k) {
//            if (COUNT_COMPARISONS) totalComparisons++;
//            a[k] = c.compare(tmp[i - l + tmpBase], tmp[j - l + tmpBase]) <= 0 ? tmp[i++ - l + tmpBase] : tmp[j-- - l + tmpBase];
//        }
//    }

    /**
     * Merges runs A[l..m-1] and A[m..r] in-place into A[l..r]
     * with Sedgewick's bitonic merge (Program 8.2 in Algorithms in C++)
     * using tmp as temporary storage.
     * tmp.length must be at least r - l + 1.
     */
    private void mergeRuns(int l, int m, int r) {
        int base1 = l;
        int len1 = m - l;
        int base2 = m;
        int len2 = r - m + 1;
        assert len1 > 0 && len2 > 0;
        assert base1 + len1 == base2;

        // Count merge costs
        if (COUNT_MERGE_COSTS) totalMergeCosts += (len1 + len2);

        /*
         * Find where the first element of run2 goes in run1. Prior elements
         * in run1 can be ignored (because they're already in place).
         */
        int k = gallopRight(a[base2], a, base1, len1, 0, c);
        assert k >= 0;
        base1 += k;
        len1 -= k;
        if (len1 == 0)
            return;

        /*
         * Find where the last element of run1 goes in run2. Subsequent elements
         * in run2 can be ignored (because they're already in place).
         */
        len2 = gallopLeft(a[base1 + len1 - 1], a, base2, len2, len2 - 1, c);
        assert len2 >= 0;
        if (len2 == 0)
            return;

        // Merge remaining runs, using tmp array with min(len1, len2) elements
        if (len1 <= len2)
            mergeLo(base1, len1, base2, len2);
        else
            mergeHi(base1, len1, base2, len2);
    }

    /**
     * Locates the position at which to insert the specified key into the
     * specified sorted range; if the range contains an element equal to key,
     * returns the index of the leftmost equal element.
     *
     * @param key  the key whose insertion point to search for
     * @param a    the array in which to search
     * @param base the index of the first element in the range
     * @param len  the length of the range; must be > 0
     * @param hint the index at which to begin the search, 0 <= hint < n.
     *             The closer hint is to the result, the faster this method will run.
     * @param c    the comparator used to order the range, and to search
     * @return the int k,  0 <= k <= n such that a[b + k - 1] < key <= a[b + k],
     * pretending that a[b - 1] is minus infinity and a[b + n] is infinity.
     * In other words, key belongs at index b + k; or in other words,
     * the first k elements of a should precede key, and the last n - k
     * should follow it.
     */
    private static <T> int gallopLeft(T key, T[] a, int base, int len, int hint,
                                      Comparator<? super T> c) {
        assert len > 0 && hint >= 0 && hint < len;
        int lastOfs = 0;
        int ofs = 1;

        // count comparisons
        if (COUNT_COMPARISONS) totalComparisons++;

        if (c.compare(key, a[base + hint]) > 0) {
            // Gallop right until a[base+hint+lastOfs] < key <= a[base+hint+ofs]
            int maxOfs = len - hint;
            while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) > 0) {
                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to base
            lastOfs += hint;
            ofs += hint;
        } else { // key <= a[base + hint]
            // Gallop left until a[base+hint-ofs] < key <= a[base+hint-lastOfs]
            final int maxOfs = hint + 1;
            while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) <= 0) {

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to base
            int tmp = lastOfs;
            lastOfs = hint - ofs;
            ofs = hint - tmp;
        }
        assert -1 <= lastOfs && lastOfs < ofs && ofs <= len;

        /*
         * Now a[base+lastOfs] < key <= a[base+ofs], so key belongs somewhere
         * to the right of lastOfs but no farther right than ofs.  Do a binary
         * search, with invariant a[base + lastOfs - 1] < key <= a[base + ofs].
         */
        lastOfs++;
        while (lastOfs < ofs) {
            int m = lastOfs + ((ofs - lastOfs) >>> 1);

            // count comparisons
            if (COUNT_COMPARISONS) totalComparisons++;

            if (c.compare(key, a[base + m]) > 0)
                lastOfs = m + 1;  // a[base + m] < key
            else
                ofs = m;          // key <= a[base + m]
        }
        assert lastOfs == ofs;    // so a[base + ofs - 1] < key <= a[base + ofs]
        return ofs;
    }

    /**
     * Like gallopLeft, except that if the range contains an element equal to
     * key, gallopRight returns the index after the rightmost equal element.
     *
     * @param key  the key whose insertion point to search for
     * @param a    the array in which to search
     * @param base the index of the first element in the range
     * @param len  the length of the range; must be > 0
     * @param hint the index at which to begin the search, 0 <= hint < n.
     *             The closer hint is to the result, the faster this method will run.
     * @param c    the comparator used to order the range, and to search
     * @return the int k,  0 <= k <= n such that a[b + k - 1] <= key < a[b + k]
     */
    private static <T> int gallopRight(T key, T[] a, int base, int len,
                                       int hint, Comparator<? super T> c) {
        assert len > 0 && hint >= 0 && hint < len;

        int ofs = 1;
        int lastOfs = 0;
        if (c.compare(key, a[base + hint]) < 0) {
            // count comparisons
            if (COUNT_COMPARISONS) totalComparisons++;

            // Gallop left until a[b+hint - ofs] <= key < a[b+hint - lastOfs]
            int maxOfs = hint + 1;
            while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) < 0) {

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to b
            int tmp = lastOfs;
            lastOfs = hint - ofs;
            ofs = hint - tmp;
        } else { // a[b + hint] <= key
            // Gallop right until a[b+hint + lastOfs] <= key < a[b+hint + ofs]
            int maxOfs = len - hint;
            while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) >= 0) {

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to b
            lastOfs += hint;
            ofs += hint;
        }
        assert -1 <= lastOfs && lastOfs < ofs && ofs <= len;

        /*
         * Now a[b + lastOfs] <= key < a[b + ofs], so key belongs somewhere to
         * the right of lastOfs but no farther right than ofs.  Do a binary
         * search, with invariant a[b + lastOfs - 1] <= key < a[b + ofs].
         */
        lastOfs++;
        while (lastOfs < ofs) {
            int m = lastOfs + ((ofs - lastOfs) >>> 1);

            // count comparisons
            if (COUNT_COMPARISONS) totalComparisons++;

            if (c.compare(key, a[base + m]) < 0)
                ofs = m;          // key < a[b + m]
            else
                lastOfs = m + 1;  // a[b + m] <= key
        }
        assert lastOfs == ofs;    // so a[b + ofs - 1] <= key < a[b + ofs]
        return ofs;
    }

    /**
     * Merges two adjacent runs in place, in a stable fashion.  The first
     * element of the first run must be greater than the first element of the
     * second run (a[base1] > a[base2]), and the last element of the first run
     * (a[base1 + len1-1]) must be greater than all elements of the second run.
     * <p>
     * For performance, this method should be called only when len1 <= len2;
     * its twin, mergeHi should be called if len1 >= len2.  (Either method
     * may be called if len1 == len2.)
     *
     * @param base1 index of first element in first run to be merged
     * @param len1  length of first run to be merged (must be > 0)
     * @param base2 index of first element in second run to be merged
     *              (must be aBase + aLen)
     * @param len2  length of second run to be merged (must be > 0)
     */
    private void mergeLo(int base1, int len1, int base2, int len2) {
        assert len1 > 0 && len2 > 0 && base1 + len1 == base2;

        // Copy first run into temp array
        T[] a = this.a; // For performance
        T[] tmp = ensureCapacity(len1);
        int cursor1 = tmpBase; // Indexes into tmp array
        int cursor2 = base2;   // Indexes int a
        int dest = base1;      // Indexes int a
        System.arraycopy(a, base1, tmp, cursor1, len1);

        // Move first element of second run and deal with degenerate cases
        a[dest++] = a[cursor2++];
        if (--len2 == 0) {
            System.arraycopy(tmp, cursor1, a, dest, len1);
            return;
        }
        if (len1 == 1) {
            System.arraycopy(a, cursor2, a, dest, len2);
            a[dest + len2] = tmp[cursor1]; // Last elt of run 1 to end of merge
            return;
        }

        Comparator<? super T> c = this.c;  // Use local variable for performance
        int minGallop = this.minGallop;    //  "    "       "     "      "
        outer:
        while (true) {
            int count1 = 0; // Number of times in a row that first run won
            int count2 = 0; // Number of times in a row that second run won

            /*
             * Do the straightforward thing until (if ever) one run starts
             * winning consistently.
             */
            do {
                assert len1 > 1 && len2 > 0;

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                if (c.compare(a[cursor2], tmp[cursor1]) < 0) {
                    a[dest++] = a[cursor2++];
                    count2++;
                    count1 = 0;
                    if (--len2 == 0)
                        break outer;
                } else {
                    a[dest++] = tmp[cursor1++];
                    count1++;
                    count2 = 0;
                    if (--len1 == 1)
                        break outer;
                }
            } while ((count1 | count2) < minGallop);

            /*
             * One run is winning so consistently that galloping may be a
             * huge win. So try that, and continue galloping until (if ever)
             * neither run appears to be winning consistently anymore.
             */
            do {
                assert len1 > 1 && len2 > 0;
                count1 = gallopRight(a[cursor2], tmp, cursor1, len1, 0, c);
                if (count1 != 0) {
                    System.arraycopy(tmp, cursor1, a, dest, count1);
                    dest += count1;
                    cursor1 += count1;
                    len1 -= count1;
                    if (len1 <= 1) // len1 == 1 || len1 == 0
                        break outer;
                }
                a[dest++] = a[cursor2++];
                if (--len2 == 0)
                    break outer;

                count2 = gallopLeft(tmp[cursor1], a, cursor2, len2, 0, c);
                if (count2 != 0) {
                    System.arraycopy(a, cursor2, a, dest, count2);
                    dest += count2;
                    cursor2 += count2;
                    len2 -= count2;
                    if (len2 == 0)
                        break outer;
                }
                a[dest++] = tmp[cursor1++];
                if (--len1 == 1)
                    break outer;
                minGallop--;
            } while (count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
            if (minGallop < 0)
                minGallop = 0;
            minGallop += 2;  // Penalize for leaving gallop mode
        }  // End of "outer" loop
        this.minGallop = minGallop < 1 ? 1 : minGallop;  // Write back to field

        if (len1 == 1) {
            assert len2 > 0;
            System.arraycopy(a, cursor2, a, dest, len2);
            a[dest + len2] = tmp[cursor1]; //  Last elt of run 1 to end of merge
        } else if (len1 == 0) {
            throw new IllegalArgumentException(
                    "Comparison method violates its general contract!");
        } else {
            assert len2 == 0;
            assert len1 > 1;
            System.arraycopy(tmp, cursor1, a, dest, len1);
        }
    }

    /**
     * Like mergeLo, except that this method should be called only if
     * len1 >= len2; mergeLo should be called if len1 <= len2.  (Either method
     * may be called if len1 == len2.)
     *
     * @param base1 index of first element in first run to be merged
     * @param len1  length of first run to be merged (must be > 0)
     * @param base2 index of first element in second run to be merged
     *              (must be aBase + aLen)
     * @param len2  length of second run to be merged (must be > 0)
     */
    private void mergeHi(int base1, int len1, int base2, int len2) {
        assert len1 > 0 && len2 > 0 && base1 + len1 == base2;

        // Copy second run into temp array
        T[] a = this.a; // For performance
        T[] tmp = ensureCapacity(len2);
        int tmpBase = this.tmpBase;
        System.arraycopy(a, base2, tmp, tmpBase, len2);

        int cursor1 = base1 + len1 - 1;  // Indexes into a
        int cursor2 = tmpBase + len2 - 1; // Indexes into tmp array
        int dest = base2 + len2 - 1;     // Indexes into a

        // Move last element of first run and deal with degenerate cases
        a[dest--] = a[cursor1--];
        if (--len1 == 0) {
            System.arraycopy(tmp, tmpBase, a, dest - (len2 - 1), len2);
            return;
        }
        if (len2 == 1) {
            dest -= len1;
            cursor1 -= len1;
            System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
            a[dest] = tmp[cursor2];
            return;
        }

        Comparator<? super T> c = this.c;  // Use local variable for performance
        int minGallop = this.minGallop;    //  "    "       "     "      "
        outer:
        while (true) {
            int count1 = 0; // Number of times in a row that first run won
            int count2 = 0; // Number of times in a row that second run won

            /*
             * Do the straightforward thing until (if ever) one run
             * appears to win consistently.
             */
            do {
                assert len1 > 0 && len2 > 1;

                // count comparisons
                if (COUNT_COMPARISONS) totalComparisons++;

                if (c.compare(tmp[cursor2], a[cursor1]) < 0) {
                    a[dest--] = a[cursor1--];
                    count1++;
                    count2 = 0;
                    if (--len1 == 0)
                        break outer;
                } else {
                    a[dest--] = tmp[cursor2--];
                    count2++;
                    count1 = 0;
                    if (--len2 == 1)
                        break outer;
                }
            } while ((count1 | count2) < minGallop);

            /*
             * One run is winning so consistently that galloping may be a
             * huge win. So try that, and continue galloping until (if ever)
             * neither run appears to be winning consistently anymore.
             */
            do {
                assert len1 > 0 && len2 > 1;
                count1 = len1 - gallopRight(tmp[cursor2], a, base1, len1, len1 - 1, c);
                if (count1 != 0) {
                    dest -= count1;
                    cursor1 -= count1;
                    len1 -= count1;
                    System.arraycopy(a, cursor1 + 1, a, dest + 1, count1);
                    if (len1 == 0)
                        break outer;
                }
                a[dest--] = tmp[cursor2--];
                if (--len2 == 1)
                    break outer;

                count2 = len2 - gallopLeft(a[cursor1], tmp, tmpBase, len2, len2 - 1, c);
                if (count2 != 0) {
                    dest -= count2;
                    cursor2 -= count2;
                    len2 -= count2;
                    System.arraycopy(tmp, cursor2 + 1, a, dest + 1, count2);
                    if (len2 <= 1)  // len2 == 1 || len2 == 0
                        break outer;
                }
                a[dest--] = a[cursor1--];
                if (--len1 == 0)
                    break outer;
                minGallop--;
            } while (count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
            if (minGallop < 0)
                minGallop = 0;
            minGallop += 2;  // Penalize for leaving gallop mode
        }  // End of "outer" loop
        this.minGallop = minGallop < 1 ? 1 : minGallop;  // Write back to field

        if (len2 == 1) {
            assert len1 > 0;
            dest -= len1;
            cursor1 -= len1;
            System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
            a[dest] = tmp[cursor2];  // Move first elt of run2 to front of merge
        } else if (len2 == 0) {
            throw new IllegalArgumentException(
                    "Comparison method violates its general contract!");
        } else {
            assert len1 == 0;
            assert len2 > 0;
            System.arraycopy(tmp, tmpBase, a, dest - (len2 - 1), len2);
        }
    }

    /**
     * Ensures that the external array tmp has at least the specified
     * number of elements, increasing its size if necessary.  The size
     * increases exponentially to ensure amortized linear time complexity.
     *
     * @param minCapacity the minimum required capacity of the tmp array
     * @return tmp, whether or not it grew
     */
    private T[] ensureCapacity(int minCapacity) {
        if (tmpLen < minCapacity) {
            // Compute smallest power of 2 > minCapacity
            int newSize = -1 >>> Integer.numberOfLeadingZeros(minCapacity);
            newSize++;

            if (newSize < 0) // Not bloody likely!
                newSize = minCapacity;
            else
                newSize = Math.min(newSize, a.length >>> 1);

            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            T[] newArray = (T[]) java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), newSize);
            tmp = newArray;
            tmpLen = newSize;
            tmpBase = 0;
        }
        return tmp;
    }

    private static int numberOfLeadingZeros(int i) {
        if (i == 0) return 32;
        int n = 1;
        if (i >>> 16 == 0) {
            n += 16;
            i <<= 16;
        }
        if (i >>> 24 == 0) {
            n += 8;
            i <<= 8;
        }
        if (i >>> 28 == 0) {
            n += 4;
            i <<= 4;
        }
        if (i >>> 30 == 0) {
            n += 2;
            i <<= 2;
        }
        n -= i >>> 31;
        return n;
    }
}
