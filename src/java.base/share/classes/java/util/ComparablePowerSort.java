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
 * This is a near duplicate of {@link PowerSort}, modified for use with
 * arrays of objects that implement {@link Comparable}, instead of using
 * explicit comparators.
 *
 * @author Zhan Jin
 */
class ComparablePowerSort {
    private static final int MIN_MERGE = 16;

    /**
     * The array being sorted.
     */
    private Object[] a;

    /**
     * Maximum initial size of tmp array, which is used for merging.  The array
     * can grow to accommodate demand.
     * <p>
     * Unlike Tim's original C version, we do not allocate this much storage
     * when sorting smaller arrays.  This change was required for performance.
     */
    private static final int INITIAL_TMP_STORAGE_LENGTH = 256;

    /**
     * Temp storage for merges. A workspace array may optionally be
     * provided in constructor, and if so will be used as long as it
     * is big enough.
     */
    private Object[] tmp;
    private static final int NULL_INDEX = Integer.MIN_VALUE;
    private int[] leftRunStart;
    private int[] leftRunEnd;
    private int top;

    private ComparablePowerSort(Object[] a, Object[] work, int workBase, int workLen) {
        this.a = a;
        int len = a.length;
        int tmpLen = (len < 2 * INITIAL_TMP_STORAGE_LENGTH) ?
            len >>> 1 : INITIAL_TMP_STORAGE_LENGTH;
        if (work == null || workLen < tmpLen || workBase + tmpLen > work.length) {
            tmp = new Object[tmpLen];
            workBase = 0;
        } else {
            tmp = work;
        }
        int lgnPlus2 = log2(len) + 2;
        leftRunStart = new int[lgnPlus2];
        leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        top = 0;
    }

    static void sort(Object[] a, int lo, int hi, Object[] work, int workBase, int workLen) {
        assert a != null && lo >= 0 && lo <= hi && hi <= a.length;
        int nRemaining = hi - lo;
        if (nRemaining < 2)
            return;  // Arrays of size 0 and 1 are always sorted

        if (nRemaining < MIN_MERGE) {
            int initRunLen = countRunAndMakeAscending(a, lo, hi);
            binarySort(a, lo, hi, lo + initRunLen);
            return;
        }

        /**
         * March over the array once, left to right, finding natural runs,
         * extending short natural runs to minRun elements, and merging runs
         * to maintain stack invariant.
         */
        ComparablePowerSort ps = new ComparablePowerSort(a, work, workBase, workLen);
        int minRun = minRunLength(nRemaining);
        int startA = lo, endA = countRunAndMakeAscending(a, lo, hi);
        // extend to minRunLen
        int lenA = endA - startA + 1;
        if (lenA < minRun) {
            endA = hi < startA + minRun - 1 ? hi : startA + minRun - 1;
            binarySort(a, startA, endA, startA + lenA);
            return;
        }

        while (endA < hi) {
            int startB = endA + 1, endB = countRunAndMakeAscending(a, startB, hi);
            // extend to minRunLen
            int lenB = endB - startB + 1;
            if (lenB < minRun) {
                endB = hi < startB + minRun - 1 ? hi : startB + minRun - 1;
                binarySort(a, startB, endB, startB + lenB);
            }
            int k = ps.nodePower(lo, hi, startA, startB, endB);
            assert k != ps.top;
            for (int l = ps.top; l > k; --l) {
                if (ps.leftRunStart[l] == NULL_INDEX) continue;
                ps.mergeRuns(a, ps.leftRunStart[l], ps.leftRunEnd[l] + 1, endA, ps.tmp);
                startA = ps.leftRunStart[l];
                ps.leftRunStart[l] = NULL_INDEX;
            }
            // store left half of merge between A and B
            ps.leftRunStart[k] = startA;
            ps.leftRunEnd[k] = endA;
            ps.top = k;
            startA = startB;
            endA = endB;
        }
        assert endA == hi;
        for (int l = ps.top; l > 0; --l) {
            if (ps.leftRunStart[l] == NULL_INDEX) continue;
            ps.mergeRuns(a, ps.leftRunStart[l], ps.leftRunEnd[l] + 1, hi, ps.tmp);
        }
    }

    /**
     * Sorts the specified portion of the specified array using a binary
     * insertion sort.  This is the best method for sorting small numbers
     * of elements.  It requires O(n log n) compares, but O(n^2) data
     * movement (worst case).
     *
     * If the initial part of the specified range is already sorted,
     * this method can take advantage of it: the method assumes that the
     * elements from index {@code lo}, inclusive, to {@code start},
     * exclusive are already sorted.
     *
     * @param a the array in which a range is to be sorted
     * @param lo the index of the first element in the range to be sorted
     * @param hi the index after the last element in the range to be sorted
     * @param start the index of the first element in the range that is
     *        not already known to be sorted ({@code lo <= start <= hi})
     */
    @SuppressWarnings({"fallthrough", "rawtypes", "unchecked"})
    private static void binarySort(Object[] a, int lo, int hi, int start) {
        assert lo <= start && start <= hi;
        if (start == lo)
            start++;
        for ( ; start < hi; start++) {
            Comparable pivot = (Comparable) a[start];

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
                if (pivot.compareTo(a[mid]) < 0)
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
                case 2:  a[left + 2] = a[left + 1];
                case 1:  a[left + 1] = a[left];
                    break;
                default: System.arraycopy(a, left, a, left + 1, n);
            }
            a[left] = pivot;
        }
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
     * @return the length of the run beginning at the specified position in
     * the specified array
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int countRunAndMakeAscending(Object[] a, int lo, int hi) {
        assert lo <= hi; //different from TimSort.java
        int runHi = lo;
        if (runHi == hi)
            return runHi;

        // Find end of run, and reverse range if descending
        if (((Comparable) a[runHi++]).compareTo(a[runHi++]) < 0) { // Descending
            while (runHi < hi && ((Comparable) a[runHi]).compareTo(a[runHi - 1]) < 0)
                runHi++;
            reverseRange(a, lo, runHi);
        } else {                              // Ascending
            while (runHi < hi && ((Comparable) a[runHi]).compareTo(a[runHi - 1]) >= 0)
                runHi++;
        }

        return runHi - lo;
    }



    /**
     * Reverse the specified range of the specified array.
     *
     * @param a the array in which a range is to be reversed
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

    public static int log2(int n) {
        if (n == 0) throw new IllegalArgumentException("lg(0) undefined");
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    /**
     * Returns the minimum acceptable run length for an array of the specified
     * length. Natural runs shorter than this will be extended with
     * {@link #binarySort}.
     *
     * Roughly speaking, the computation is:
     *
     *  If n < MIN_MERGE, return n (it's too small to bother with fancy stuff).
     *  Else if n is an exact power of 2, return MIN_MERGE/2.
     *  Else return an int k, MIN_MERGE/2 <= k <= MIN_MERGE, such that n/k
     *   is close to, but strictly less than, an exact power of 2.
     *
     * For the rationale, see listsort.txt.
     *
     * @param n the length of the array to be sorted
     * @return the length of the minimum run to be merged
     */
    private static int minRunLength(int n) {
        assert n >= 0;
        int r = 0;      // Becomes 1 if any 1 bits are shifted off
        while (n >= MIN_MERGE) {
            r |= (n & 1);
            n >>= 1;
        }
        return n + r;
    }

    private int nodePower(int left, int right, int startA, int startB, int endB) {
        int n = (right - left + 1);
        long l = (long) startA + (long) startB - ((long) left << 1); // 2*middleA
        long r = (long) startB + (long) endB + 1 - ((long) left << 1); // 2*middleB
        int a = (int) ((l << 30) / n); // middleA / 2n
        int b = (int) ((r << 30) / n); // middleB / 2n
        return Integer.numberOfLeadingZeros(a ^ b);
    }

    /**
     * Merges runs A[l..m-1] and A[m..r] in-place into A[l..r]
     * with Sedgewick's bitonic merge (Program 8.2 in Algorithms in C++)
     * using B as temporary storage.
     * B.length must be at least r+1.
     */
    private void mergeRuns(Object[] A, int l, int m, int r, Object[] B) {
        --m; // mismatch in convention with Sedgewick
        int i, j;
        assert B.length >= r + 1;
        for (i = m + 1; i > l; --i) B[i - 1] = A[i - 1];
        for (j = m; j < r; ++j) B[r + m - j] = A[j + 1];
        for (int k = l; k <= r; ++k)
            A[k] = ((Comparable) B[j]).compareTo(B[i]) < 0 ? B[j--] : B[i++];
    }
}
