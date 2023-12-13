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
    private int minRunLen;

    /**
     * The array being sorted.
     */
    private Object[] a;

    /**
     * Temp storage for merges. A workspace array may optionally be
     * provided in constructor, and if so will be used as long as it
     * is big enough.
     */
    private Object[] tmp;
    private int tmpBase; // base of tmp array slice
    private int tmpLen;  // length of tmp array slice
    private static final int NULL_INDEX = Integer.MIN_VALUE;

    private ComparablePowerSort(Object[] a,
                                Object[] work,
                                int workBase,
                                int workLen,
                                final int minRunLen) {
        this.minRunLen = minRunLen;

        this.a = a;
        int len = a.length;
        int tlen = len + 1;
        if (work == null || workLen < tlen || workBase + tlen > work.length) {
            tmp = new Object[tlen];
            tmpBase = 0;
            tmpLen = tlen;
        } else {
            tmp = work;
            tmpBase = workBase;
            tmpLen = workLen;
        }
    }

    /**
     * Sorts the given range, using the given workspace array slice
     * for temp storage when possible. This method is designed to be
     * invoked from public methods (in class Arrays) after performing
     * any necessary array bounds checks and expanding parameters into
     * the required forms.
     *
     * @param a the array to be sorted
     * @param lo the index of the first element, inclusive, to be sorted
     * @param hi the index of the last element, exclusive, to be sorted
     * @param work a workspace array (slice)
     * @param workBase origin of usable space in work array
     * @param workLen usable size of work array
     */
    static void sort(Object[] a,
                     int lo,
                     int hi,
                     Object[] work,
                     int workBase,
                     int workLen,
                     final boolean useMsbMergeType,
                     final boolean onlyIncreasingRuns,
                     final int minRunLen) {

        if (!useMsbMergeType && onlyIncreasingRuns)
            throw new UnsupportedOperationException();
        if (minRunLen > 1 && (!useMsbMergeType || onlyIncreasingRuns))
            throw new UnsupportedOperationException();

        assert a != null && lo >= 0 && lo <= hi && hi <= a.length;

        int nRemaining  = hi - lo;
        if (nRemaining < 2)
            return;  // Arrays of size 0 and 1 are always sorted

        hi--; // change from exclusive to inclusive

        /**
         * March over the array once, left to right, finding natural runs,
         * extending short natural runs to minRun elements, and merging runs
         * to maintain stack invariant.
         */
        ComparablePowerSort ps = new ComparablePowerSort(a, work, workBase, workLen, minRunLen);
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

    public void powersort(int left, int right) {
        int n = right - left + 1;
        int lgnPlus2 = log2(n) + 2;
        int[] leftRunStart = new int[lgnPlus2], leftRunEnd = new int[lgnPlus2];
        Arrays.fill(leftRunStart, NULL_INDEX);
        int top = 0;

        int startA = left, endA = extendAndReverseRunRight(a, startA, right);
        // extend to minRunLen
        int lenA = endA - startA + 1;
        if (lenA < minRunLen) {
            endA = Math.min(right, startA + minRunLen - 1);
            insertionsort(startA, endA, lenA);
        }
        while (endA < right) {
            int startB = endA + 1, endB = extendAndReverseRunRight(a, startB, right);
            // extend to minRunLen
            int lenB = endB - startB + 1;
            if (lenB < minRunLen) {
                endB = Math.min(right, startB + minRunLen - 1);
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

        int startA = left, endA = extendAndReverseRunRight(a, startA, right);
        while (endA < right) {
            int startB = endA + 1, endB = extendAndReverseRunRight(a, startB, right);
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


    @SuppressWarnings({"unchecked", "rawtypes"})
    private int extendWeaklyIncreasingRunRight(int i, final int right) {
        while (i < right && ((Comparable) a[i + 1]).compareTo(a[i]) >= 0) ++i;
        return i;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int extendAndReverseRunRight(Object[] a, int i, final int right) {
        assert i <= right;
        int j = i;
        if (j == right) return j;
        // Find end of run, and reverse range if descending
        if (((Comparable) a[j]).compareTo(a[++j]) > 0) { // Strictly Descending
            while (j < right && ((Comparable) a[j + 1]).compareTo(a[j]) < 0) ++j;
            reverseRange(a, i, j);
        } else { // Weakly Ascending
            while (j < right && ((Comparable) a[j + 1]).compareTo(a[j]) >= 0) ++j;
        }
        return j;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void insertionsort(int left, int right, int nPresorted) {
        assert right >= left;
        assert right - left + 1 >= nPresorted;
        for (int i = left + nPresorted; i <= right; ++i) {
            int j = i - 1;
            final Object v = a[i];
            while (((Comparable) v).compareTo(a[j]) < 0) {
                a[j + 1] = a[j];
                --j;
                if (j < left) break;
            }
            a[j + 1] = v;
        }
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
//			if (digitA) { l -= n; r -= n; }
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
     * Reverse the specified range of the specified array.
     *
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

    public static int log2(int n) {
        if (n == 0) throw new IllegalArgumentException("lg(0) undefined");
        return 31 - Integer.numberOfLeadingZeros(n);
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
     * using tmp as temporary storage.
     * tmp.length must be at least r+1.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mergeRuns(int l, int m, int r) {
        ensureCapacity(r + 1);
        --m; // mismatch in convention with Sedgewick
        int i, j;
        assert tmp.length >= r + 1;
        for (i = m + 1; i > l; --i) tmp[i - 1] = a[i - 1];
        for (j = m; j < r; ++j) tmp[r + m - j] = a[j + 1];
        for (int k = l; k <= r; ++k)
            a[k] = ((Comparable) tmp[j]).compareTo(tmp[i]) < 0 ? tmp[j--] : tmp[i++];
    }

    /**
     * Ensures that the external array tmp has at least the specified
     * number of elements, increasing its size if necessary.
     *
     * @param minCapacity the minimum required capacity of the tmp array
     */
    private void ensureCapacity(int minCapacity) {
        if (tmpLen < minCapacity) {
            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            Object[] newArray = new Object[minCapacity];
            tmp = newArray;
            tmpLen = minCapacity;
            tmpBase = 0;
        }
    }
}
