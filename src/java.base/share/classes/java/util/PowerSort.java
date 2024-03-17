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
     * If true, use the most-significant-bit trick to compute node powers;
     * otherwise use a loop.
     */
    public static boolean COUNT_MERGE_COSTS = false;

    /**
     * Total merge costs of all merge calls
     */
    public static long totalMergeCosts = 0;

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
        ++workLen; // Temp array is 1 larger than the original array
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

        hi--; // change from exclusive to inclusive

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

    private void powersortIncreasingOnlyMSB(int left, int right) {
        int n = right - left + 1;
        int lgnPlus2 = log2(n) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = left, endA = extendWeaklyIncreasingRunRight(startA, right);
        while (endA < right) {
            int startB = endA + 1, endB = extendWeaklyIncreasingRunRight(startB, right);
            int k = nodePower(left, right, startA, startB, endB);
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
        assert endA == right;
        for (int l = top; l > 0; --l) {
            if (leftRunStart[l] == NULL_INDEX) continue;
            mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, right);
        }
    }

    /**
     * Powersort.
     *
     * @param left  the left
     * @param right the right
     */
    public void powersort(int left, int right) {
        int n = right - left + 1;
        int lgnPlus2 = log2(n) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = left, endA = extendAndReverseRunRight(startA, right);
        // extend to minRunLen
        int lenA = endA - startA + 1;
        if (lenA < this.minRunLen) {
            endA = Math.min(right, startA + this.minRunLen - 1);
            insertionsort(startA, endA, lenA);
        }
        while (endA < right) {
            int startB = endA + 1, endB = extendAndReverseRunRight(startB, right);
            // extend to minRunLen
            int lenB = endB - startB + 1;
            if (lenB < this.minRunLen) {
                endB = Math.min(right, startB + this.minRunLen - 1);
                insertionsort(startB, endB, lenB);
            }
            int k = nodePower(left, right, startA, startB, endB);
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
        assert endA == right;
        for (int l = top; l > 0; --l) {
            if (leftRunStart[l] == NULL_INDEX) continue;
            mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, right);
        }
    }

    private void powersortBitWise(int left, int right) {
        int n = right - left + 1;
        int lgnPlus2 = log2(n) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = left, endA = extendAndReverseRunRight(startA, right);
        while (endA < right) {
            int startB = endA + 1, endB = extendAndReverseRunRight(startB, right);
            int k = nodePowerBitwise(left, right, startA, startB, endB);
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
        assert endA == right;
        for (int l = top; l > 0; --l) {
            if (leftRunStart[l] == NULL_INDEX) continue;
            mergeRuns(leftRunStart[l], leftRunEnd[l] + 1, right);
        }
    }

    private int extendWeaklyIncreasingRunRight(int i, final int right) {
        // use local variables for performance
        T[] a = this.a;
        Comparator<? super T> c = this.c;

        while (i < right && c.compare(a[i + 1], a[i]) >= 0) ++i;
        return i;
    }

    private int extendAndReverseRunRight(int i, final int right) {
        // use local variables for performance
        T[] a = this.a;
        Comparator<? super T> c = this.c;


        assert i <= right;
        int j = i;
        if (j == right) return j;
        // Find end of run, and reverse range if descending
        if (c.compare(a[j], a[++j]) > 0) { // Strictly Descending
            while (j < right && c.compare(a[j + 1], a[j]) < 0) ++j;
            reverseRange(a, i, j);
        } else { // Weakly Ascending
            while (j < right && c.compare(a[j + 1], a[j]) >= 0) ++j;
        }
        return j;
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
        while (lo < hi) {
            Object t = a[lo];
            a[lo++] = a[hi];
            a[hi--] = t;
        }
    }

    /**
     * Sort A[left..right] by straight-insertion sort (both endpoints
     * inclusive), assuming the leftmost nPresorted elements form a weakly
     * increasing run
     */
    private void insertionsort(int left, int right, int nPresorted) {
        // use local variables for performance
        T[] a = this.a;
        Comparator<? super T> c = this.c;

        assert right >= left;
        assert right - left + 1 >= nPresorted;
        for (int i = left + nPresorted; i <= right; ++i) {
            int j = i - 1;
            final T v = a[i];
            while (c.compare(v, a[j]) < 0) {
                a[j + 1] = a[j];
                --j;
                if (j < left) break;
            }
            a[j + 1] = v;
        }
    }

    private int nodePower(int left, int right, int startA, int startB, int endB) {
        int n = (right - left + 1);
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

    /**
     * Merges runs A[l..m-1] and A[m..r] in-place into A[l..r]
     * with Sedgewick's bitonic merge (Program 8.2 in Algorithms in C++)
     * using tmp as temporary storage.
     * tmp.length must be at least r - l + 1.
     */
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
//        for (int k = l; k <= r; ++k)
//            a[k] = c.compare(tmp[i - l + tmpBase], tmp[j - l + tmpBase]) <= 0 ? tmp[i++ - l + tmpBase] : tmp[j-- - l + tmpBase];
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
        if (c.compare(key, a[base + hint]) > 0) {
            // Gallop right until a[base+hint+lastOfs] < key <= a[base+hint+ofs]
            int maxOfs = len - hint;
            while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) > 0) {
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
            // Gallop left until a[b+hint - ofs] <= key < a[b+hint - lastOfs]
            int maxOfs = hint + 1;
            while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) < 0) {
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
