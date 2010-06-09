/* 
 * (c) 2010 Alessandro Colantonio
 * <mailto:colanton@mat.uniroma3.it>
 * <http://ricerca.mat.uniroma3.it/users/colanton>
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.uniroma3.mat.extendedset;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Very similar to {@link ExtendedSet} but for the primitive <code>int</code>
 * type.
 * 
 * @author Alessandro Colantonio
 * @version $Id$
 */
public abstract class IntSet implements Cloneable, Comparable<IntSet> {
	/**
	 * Generates the intersection set
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the result of the operation
	 * 
	 * @see #retainAll(IntSet)
	 */
	public abstract IntSet intersection(IntSet other);

	/**
	 * Generates the union set
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the result of the operation
	 * 
	 * @see #addAll(IntSet)
	 */
	public abstract IntSet union(IntSet other);

	/**
	 * Generates the difference set
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the result of the operation
	 * 
	 * @see #removeAll(IntSet)
	 */
	public abstract IntSet difference(IntSet other);

	/**
	 * Generates the symmetric difference set
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the result of the operation
	 * 
	 * @see #flip(int)
	 */
	public abstract IntSet symmetricDifference(IntSet other);

	/**
	 * Generates the complement set. The returned set is represented by all the
	 * elements strictly less than {@link #last()} that do not exist in the
	 * current set.
	 * 
	 * @return the complement set
	 * 
	 * @see IntSet#complement()
	 */
	public abstract IntSet complemented();

	/**
	 * Complements the current set. The modified set is represented by all the
	 * elements strictly less than {@link #last()} that do not exist in the
	 * current set.
	 * 
	 * @see IntSet#complemented()
	 */
	public abstract void complement();

	/**
	 * Returns <code>true</code> if the specified {@link IntSet}
	 * instance contains any elements that are also contained within this
	 * {@link IntSet} instance
	 * 
	 * @param other
	 *            {@link IntSet} to intersect with
	 * @return a boolean indicating whether this {@link IntSet}
	 *         intersects the specified {@link IntSet}.
	 */
	public abstract boolean containsAny(IntSet other);

	/**
	 * Returns <code>true</code> if the specified {@link IntSet}
	 * instance contains at least <code>minElements</code> elements that are
	 * also contained within this {@link IntSet} instance
	 * 
	 * @param other
	 *            {@link IntSet} instance to intersect with
	 * @param minElements
	 *            minimum number of elements to be contained within this
	 *            {@link IntSet} instance
	 * @return a boolean indicating whether this {@link IntSet}
	 *         intersects the specified {@link IntSet}.
	 * @throws IllegalArgumentException
	 *             if <code>minElements &lt; 1</code>
	 */
	public abstract boolean containsAtLeast(IntSet other, int minElements);

	/**
	 * Computes the intersection set size.
	 * <p>
	 * This is faster than calling {@link #intersection(IntSet)} and
	 * then {@link #size()}
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the size
	 */
	public abstract int intersectionSize(IntSet other);

	/**
	 * Computes the union set size.
	 * <p>
	 * This is faster than calling {@link #union(IntSet)} and then
	 * {@link #size()}
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the size
	 */
	public int unionSize(IntSet other) {
		return other == null ? size() : size() + other.size() - intersectionSize(other);
	}

	/**
	 * Computes the symmetric difference set size.
	 * <p>
	 * This is faster than calling {@link #symmetricDifference(IntSet)}
	 * and then {@link #size()}
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the size
	 */
	public int symmetricDifferenceSize(IntSet other) {
		return other == null ? size() : size() + other.size() - 2 * intersectionSize(other);
	}

	/**
	 * Computes the difference set size.
	 * <p>
	 * This is faster than calling {@link #difference(IntSet)} and then
	 * {@link #size()}
	 * 
	 * @param other
	 *            {@link IntSet} instance that represents the right
	 *            operand
	 * @return the size
	 */
	public int differenceSize(IntSet other) {
		return other == null ? size() : size() - intersectionSize(other);
	}

	/**
	 * Computes the complement set size.
	 * <p>
	 * This is faster than calling {@link #complemented()} and then
	 * {@link #size()}
	 * 
	 * @return the size
	 */
	public int complementSize() {
		if (isEmpty())
			return 0;
		return last() - size() + 1;
	}

	/**
	 * Generates an empty set
	 * 
	 * @return the empty set
	 */
	public abstract IntSet empty();

	/**
	 * See the <code>clone()</code> of {@link Object}
	 * 
	 * @return cloned object
	 */
	@Override
	public abstract IntSet clone();

	/**
	 * Computes the compression factor of the equivalent bitmap representation
	 * (1 means not compressed, namely a memory footprint similar to
	 * {@link BitSet}, 2 means twice the size of {@link BitSet}, etc.)
	 * 
	 * @return the compression factor
	 */
	public abstract double bitmapCompressionRatio();

	/**
	 * Computes the compression factor of the equivalent integer collection (1
	 * means not compressed, namely a memory footprint similar to
	 * {@link ArrayList}, 2 means twice the size of {@link ArrayList}, etc.)
	 * 
	 * @return the compression factor
	 */
	public abstract double collectionCompressionRatio();

	/**
	 * A {@link Iterator}-like interface that allows to "skip" some elements of
	 * the set
	 */
	public abstract interface ExtendedIntIterator {
		/**
		 * @return <tt>true</tt> if the iterator has more elements.
		 */
		boolean hasNext();

		/**
		 * @return the next element in the iteration.
		 * @exception NoSuchElementException
		 *                iteration has no more elements.
		 */
		int next();

		/**
		 * Removes from the underlying collection the last element returned by
		 * the iterator (optional operation). This method can be called only
		 * once per call to <tt>next</tt>. The behavior of an iterator is
		 * unspecified if the underlying collection is modified while the
		 * iteration is in progress in any way other than by calling this
		 * method.
		 * 
		 * @exception UnsupportedOperationException
		 *                if the <tt>remove</tt> operation is not supported by
		 *                this Iterator.
		 * 
		 * @exception IllegalStateException
		 *                if the <tt>next</tt> method has not yet been called,
		 *                or the <tt>remove</tt> method has already been called
		 *                after the last call to the <tt>next</tt> method.
		 */
		void remove();

		/**
		 * Skips all the elements before the the specified element, so that
		 * {@link #next()} gives the given element or, if it does not exist, the
		 * element immediately after according to the sorting provided by this
		 * set.
		 * <p>
		 * If <code>element</code> is less than the next element, it does
		 * nothing
		 * 
		 * @param element
		 *            first element to not skip
		 */
		public abstract void skipAllBefore(int element);
	}

	/**
	 * @return a {@link ExtendedIntIterator} instance to iterate over the set
	 */
	public abstract ExtendedIntIterator intIterator();

	/**
	 * @return a {@link ExtendedIntIterator} instance to iterate over the set in
	 *         descending order
	 */
	public abstract ExtendedIntIterator descendingIntIterator();

	/**
	 * Prints debug info about the given {@link IntSet} implementation
	 * 
	 * @return a string that describes the internal representation of the
	 *         instance
	 */
	public abstract String debugInfo();

	/**
	 * Adds to the set all the elements between <code>first</code> and
	 * <code>last</code>, both included.
	 * 
	 * @param from
	 *            first element
	 * @param to
	 *            last element
	 */
	public abstract void fill(int from, int to);

	/**
	 * Removes from the set all the elements between <code>first</code> and
	 * <code>last</code>, both included.
	 * 
	 * @param from
	 *            first element
	 * @param to
	 *            last element
	 */
	public abstract void clear(int from, int to);

	/**
	 * Adds the element if it not existing, or removes it if existing
	 * 
	 * @param e
	 *            element to flip
	 * @see #symmetricDifference(IntSet)
	 */
	public abstract void flip(int e);

	/**
	 * Gets the <code>i</code><sup>th</sup> element of the set
	 * 
	 * @param i
	 *            position of the element in the sorted set
	 * @return the <code>i</code><sup>th</sup> element of the set
	 * @throws IndexOutOfBoundsException
	 *             if <code>i</code> is less than zero, or greater or equal to
	 *             {@link #size()}
	 */
	public abstract int get(int i);

	/**
	 * Provides position of element within the set.
	 * <p>
	 * It returns -1 if the element does not exist within the set.
	 * 
	 * @param e
	 *            element of the set
	 * @return the element position
	 */
	public abstract int indexOf(int e);

	/**
	 * Converts a given array into an instance of the current class.
	 * 
	 * @param a
	 *            array to use to generate the new instance
	 * @return the converted collection
	 */
	public abstract IntSet convert(int... a);

	/**
	 * Converts a given collection into an instance of the current class.
	 * 
	 * @param c
	 *            array to use to generate the new instance
	 * @return the converted collection
	 */
	public abstract IntSet convert(Collection<Integer> c);
	
	/**
	 * Returns the first (lowest) element currently in this set.
	 * 
	 * @return the first (lowest) element currently in this set
	 * @throws NoSuchElementException
	 *             if this set is empty
	 */
	public int first() {
		if (isEmpty())
			throw new NoSuchElementException();
		return intIterator().next();
	}


	/**
	 * Returns the last (highest) element currently in this set.
	 * 
	 * @return the last (highest) element currently in this set
	 * @throws NoSuchElementException
	 *             if this set is empty
	 */
	public abstract int last();

	/**
	 * @return the number of elements in this set (its cardinality)
	 */
	public abstract int size();

	/**
	 * @return <tt>true</tt> if this set contains no elements
	 */
	public abstract boolean isEmpty();

	/**
	 * Returns <tt>true</tt> if this set contains the specified element.
	 * 
	 * @param i
	 *            element whose presence in this set is to be tested
	 * @return <tt>true</tt> if this set contains the specified element
	 */
	public abstract boolean contains(int i);

	/**
	 * Adds the specified element to this set if it is not already present. It
	 * ensures that sets never contain duplicate elements.
	 * 
	 * @param i
	 *            element to be added to this set
	 * @return <tt>true</tt> if this set did not already contain the specified
	 *         element
	 * @throws IllegalArgumentException
	 *             if some property of the specified element prevents it from
	 *             being added to this set
	 */
	public abstract boolean add(int i);

	/**
	 * Removes the specified element from this set if it is present.
	 * 
	 * @param i
	 *            object to be removed from this set, if present
	 * @return <tt>true</tt> if this set contained the specified element
	 * @throws UnsupportedOperationException
	 *             if the <tt>remove</tt> operation is not supported by this set
	 */
	public abstract boolean remove(int i);

	/**
	 * Returns <tt>true</tt> if this set contains all of the elements of the
	 * specified collection.
	 * 
	 * @param c
	 *            collection to be checked for containment in this set
	 * @return <tt>true</tt> if this set contains all of the elements of the
	 *         specified collection
	 * @throws NullPointerException
	 *             if the specified collection contains one or more null
	 *             elements and this set does not permit null elements
	 *             (optional), or if the specified collection is null
	 * @see #contains(int)
	 */
	public abstract boolean containsAll(IntSet c);

	/**
	 * Adds all of the elements in the specified collection to this set if
	 * they're not already present.
	 * 
	 * @param c
	 *            collection containing elements to be added to this set
	 * @return <tt>true</tt> if this set changed as a result of the call
	 * 
	 * @throws NullPointerException
	 *             if the specified collection contains one or more null
	 *             elements and this set does not permit null elements, or if
	 *             the specified collection is null
	 * @throws IllegalArgumentException
	 *             if some property of an element of the specified collection
	 *             prevents it from being added to this set
	 * @see #add(int)
	 */
	public abstract boolean addAll(IntSet c);

	/**
	 * Retains only the elements in this set that are contained in the specified
	 * collection. In other words, removes from this set all of its elements
	 * that are not contained in the specified collection.
	 * 
	 * @param c
	 *            collection containing elements to be retained in this set
	 * @return <tt>true</tt> if this set changed as a result of the call
	 * @throws NullPointerException
	 *             if this set contains a null element and the specified
	 *             collection does not permit null elements (optional), or if
	 *             the specified collection is null
	 * @see #remove(int)
	 */
	public abstract boolean retainAll(IntSet c);

	/**
	 * Removes from this set all of its elements that are contained in the
	 * specified collection.
	 * 
	 * @param c
	 *            collection containing elements to be removed from this set
	 * @return <tt>true</tt> if this set changed as a result of the call
	 * @throws NullPointerException
	 *             if this set contains a null element and the specified
	 *             collection does not permit null elements (optional), or if
	 *             the specified collection is null
	 * @see #remove(int)
	 * @see #contains(int)
	 */
	public abstract boolean removeAll(IntSet c);

	/**
	 * Removes all of the elements from this set. The set will be empty after
	 * this call returns.
	 * 
	 * @throws UnsupportedOperationException
	 *             if the <tt>clear</tt> method is not supported by this set
	 */
	public abstract void clear();

	/**
	 * @return an array containing all the elements in this set, in the same
	 *         order.
	 */
	public int[] toArray() {
		if (isEmpty())
			return null;
		return toArray(new int[size()]);
	}

	/**
	 * Returns an array containing all of the elements in this set.
	 * <p>
	 * If this set fits in the specified array with room to spare (i.e., the
	 * array has more elements than this set), the element in the array
	 * immediately following the end of the set are left unchanged.
	 * 
	 * @param a
	 *            the array into which the elements of this set are to be
	 *            stored.
	 * @return the array containing all the elements in this set
	 * @throws NullPointerException
	 *             if the specified array is null
	 * @throws IllegalArgumentException
	 *             if this set does not fit in the specified array 
	 */
	public int[] toArray(int[] a) {
		if (a.length < size())
			throw new IllegalArgumentException();
		if (isEmpty())
			return a;
		ExtendedIntIterator itr = intIterator();
		int i = 0;
		while (itr.hasNext()) 
			a[i++] = itr.next();
		return a;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
        ExtendedIntIterator itr = intIterator();
    	if (!itr.hasNext())
    	    return "[]";

		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (;;) {
			int e = itr.next();
			sb.append(e);
			if (!itr.hasNext())
				return sb.append(']').toString();
			sb.append(", ");
		}
	}

	/**
	 * @return the {@link IntegerSet} view of this set
	 */
	public IntegerSet toIntegerSet() {
		return new IntegerSet(this);
	}
}
