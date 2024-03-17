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
public class ComparablePowerSort {
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
     * Creates a ComparablePowerSort instance to maintain the state of an ongoing
     * sort. The workspace array slice (workBase, workLen) is a reference to the
     * workspace array, which is used as long as it is big enough. If not, a
     * larger array is allocated and used instead.
     *
     * @param a         the array to be sorted
     * @param work      a workspace array (slice)
     * @param workBase  origin of usable space in work array
     * @param workLen   usable size of work array
     * @param minRunLen minimum run length
     */
    public ComparablePowerSort(Object[] a,
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
     * @param a                  the array to be sorted
     * @param lo                 the index of the first element, inclusive, to be sorted
     * @param hi                 the index of the last element, exclusive, to be sorted
     * @param work               a workspace array (slice)
     * @param workBase           origin of usable space in work array
     * @param workLen            usable size of work array
     * @param useMsbMergeType    if true, use the most-significant-bit trick
     * @param onlyIncreasingRuns if true, only sort increasing runs
     * @param minRunLen          minimum run length
     */
    public static void sort(Object[] a,
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

        int nRemaining = hi - lo;
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

    private static int log2(int n) {
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
        int k = gallopRight((Comparable<Object>) a[base2], a, base1, len1, 0);
        assert k >= 0;
        base1 += k;
        len1 -= k;
        if (len1 == 0)
            return;

        /*
         * Find where the last element of run1 goes in run2. Subsequent elements
         * in run2 can be ignored (because they're already in place).
         */
        len2 = gallopLeft((Comparable<Object>) a[base1 + len1 - 1], a,
                base2, len2, len2 - 1);
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
     * @return the int k,  0 <= k <= n such that a[b + k - 1] < key <= a[b + k],
     * pretending that a[b - 1] is minus infinity and a[b + n] is infinity.
     * In other words, key belongs at index b + k; or in other words,
     * the first k elements of a should precede key, and the last n - k
     * should follow it.
     */
    private static int gallopLeft(Comparable<Object> key, Object[] a,
                                  int base, int len, int hint) {
        assert len > 0 && hint >= 0 && hint < len;

        int lastOfs = 0;
        int ofs = 1;
        if (key.compareTo(a[base + hint]) > 0) {
            // Gallop right until a[base+hint+lastOfs] < key <= a[base+hint+ofs]
            int maxOfs = len - hint;
            while (ofs < maxOfs && key.compareTo(a[base + hint + ofs]) > 0) {
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
            while (ofs < maxOfs && key.compareTo(a[base + hint - ofs]) <= 0) {
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

            if (key.compareTo(a[base + m]) > 0)
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
     * @return the int k,  0 <= k <= n such that a[b + k - 1] <= key < a[b + k]
     */
    private static int gallopRight(Comparable<Object> key, Object[] a,
                                   int base, int len, int hint) {
        assert len > 0 && hint >= 0 && hint < len;

        int ofs = 1;
        int lastOfs = 0;
        if (key.compareTo(a[base + hint]) < 0) {
            // Gallop left until a[b+hint - ofs] <= key < a[b+hint - lastOfs]
            int maxOfs = hint + 1;
            while (ofs < maxOfs && key.compareTo(a[base + hint - ofs]) < 0) {
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
            while (ofs < maxOfs && key.compareTo(a[base + hint + ofs]) >= 0) {
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

            if (key.compareTo(a[base + m]) < 0)
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mergeLo(int base1, int len1, int base2, int len2) {
        assert len1 > 0 && len2 > 0 && base1 + len1 == base2;

        // Copy first run into temp array
        Object[] a = this.a; // For performance
        Object[] tmp = ensureCapacity(len1);

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

        int minGallop = this.minGallop;  // Use local variable for performance
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
                if (((Comparable) a[cursor2]).compareTo(tmp[cursor1]) < 0) {
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
                count1 = gallopRight((Comparable) a[cursor2], tmp, cursor1, len1, 0);
                if (count1 != 0) {
                    System.arraycopy(tmp, cursor1, a, dest, count1);
                    dest += count1;
                    cursor1 += count1;
                    len1 -= count1;
                    if (len1 <= 1)  // len1 == 1 || len1 == 0
                        break outer;
                }
                a[dest++] = a[cursor2++];
                if (--len2 == 0)
                    break outer;

                count2 = gallopLeft((Comparable) tmp[cursor1], a, cursor2, len2, 0);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mergeHi(int base1, int len1, int base2, int len2) {
        assert len1 > 0 && len2 > 0 && base1 + len1 == base2;

        // Copy second run into temp array
        Object[] a = this.a; // For performance
        Object[] tmp = ensureCapacity(len2);
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

        int minGallop = this.minGallop;  // Use local variable for performance
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
                if (((Comparable) tmp[cursor2]).compareTo(a[cursor1]) < 0) {
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
                count1 = len1 - gallopRight((Comparable) tmp[cursor2], a, base1, len1, len1 - 1);
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

                count2 = len2 - gallopLeft((Comparable) a[cursor1], tmp, tmpBase, len2, len2 - 1);
                if (count2 != 0) {
                    dest -= count2;
                    cursor2 -= count2;
                    len2 -= count2;
                    System.arraycopy(tmp, cursor2 + 1, a, dest + 1, count2);
                    if (len2 <= 1)
                        break outer; // len2 == 1 || len2 == 0
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
    private Object[] ensureCapacity(int minCapacity) {
        if (tmpLen < minCapacity) {
            // Compute smallest power of 2 > minCapacity
            int newSize = -1 >>> Integer.numberOfLeadingZeros(minCapacity);
            newSize++;

            if (newSize < 0) // Not bloody likely!
                newSize = minCapacity;
            else
                newSize = Math.min(newSize, a.length >>> 1);

            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            Object[] newArray = new Object[newSize];
            tmp = newArray;
            tmpLen = newSize;
            tmpBase = 0;
        }
        return tmp;
    }
}
