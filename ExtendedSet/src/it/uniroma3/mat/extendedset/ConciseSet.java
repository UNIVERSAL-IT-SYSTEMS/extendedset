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


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.SortedSet;

/**
 * This is CONCISE: COmpressed 'N' Composable Integer SEt.
 * <p>
 * This class is an instance of {@link IntSet} internally represented by
 * compressed bitmaps though a RLE (Run-Length Encoding) compression algorithm.
 * See <a
 * href="http://ricerca.mat.uniroma3.it/users/colanton/docs/concise.pdf">http
 * ://ricerca.mat.uniroma3.it/users/colanton/docs/concise.pdf</a> for more
 * details.
 * <p>
 * Notice that the iterator by {@link #intIterator()} is <i>fail-fast</i>,
 * similar to most {@link Collection}-derived classes. If the set is
 * structurally modified at any time after the iterator is created, the iterator
 * will throw a {@link ConcurrentModificationException}. Thus, in the face of
 * concurrent modification, the iterator fails quickly and cleanly, rather than
 * risking arbitrary, non-deterministic behavior at an undetermined time in the
 * future. The iterator throws a {@link ConcurrentModificationException} on a
 * best-effort basis. Therefore, it would be wrong to write a program that
 * depended on this exception for its correctness: <i>the fail-fast behavior of
 * iterators should be used only to detect bugs.</i>
 * 
 * @author Alessandro Colantonio
 * @version $Id$
 * 
 * @see ExtendedSet
 * @see AbstractExtendedSet
 * @see FastSet
 * @see IndexedSet
 */
// TODO: REPLACE ALL "WordIterator_OLD" INSTANCES WITH "WordIterator" !!!
public class ConciseSet extends IntSet implements java.io.Serializable {
	/** generated serial ID */
	private static final long serialVersionUID = 560068054685367266L;

	/**
	 * This is the compressed bitmap, that is a collection of words. For each
	 * word:
	 * <ul>
	 * <li> <tt>1* (0x80000000)</tt> means that it is a 31-bit <i>literal</i>.
	 * <li> <tt>00* (0x00000000)</tt> indicates a <i>sequence</i> made up of at
	 * most one set bit in the first 31 bits, and followed by blocks of 31 0's.
	 * The following 5 bits (<tt>00xxxxx*</tt>) indicates which is the set bit (
	 * <tt>00000</tt> = no set bit, <tt>00001</tt> = LSB, <tt>11111</tt> = MSB),
	 * while the remaining 25 bits indicate the number of following 0's blocks.
	 * <li> <tt>01* (0x40000000)</tt> indicates a <i>sequence</i> made up of at
	 * most one <i>un</i>set bit in the first 31 bits, and followed by blocks of
	 * 31 1's. (see the <tt>00*</tt> case above).
	 * </ul>
	 * <p>
	 * Note that literal words 0xFFFFFFFF and 0x80000000 are allowed, thus
	 * zero-length sequences (i.e., such that getSequenceCount() == 0) cannot
	 * exists.
	 */
	private int[] words;

	/**
	 * Most significant set bit within the uncompressed bit string.
	 */
	private transient int last;

	/**
	 * Cached cardinality of the bit-set. Defined for efficient {@link #size()}
	 * calls. When -1, the cache is invalid.
	 */ 
	private transient int size;

	/**
	 * Index of the last word in {@link #words}
	 */ 
	private transient int lastWordIndex;

	/**
	 * <code>true</code> if the class must simulate the behavior of WAH
	 */
	private final boolean simulateWAH;
	
	/**
	 * User for <i>fail-fast</i> iterator. It counts the number of operations
	 * that <i>do</i> modify {@link #words}
	 */
	protected transient volatile int modCount = 0;
	
	/**
	 * The highest representable integer.
	 * <p>
	 * Its value is computed as follows. The number of bits required to
	 * represent the longest sequence of 0's or 1's is
	 * <tt>ceil(log<sub>2</sub>(({@link Integer#MAX_VALUE} - 31) / 31)) = 27</tt>.
	 * Indeed, at least one literal exists, and the other bits may all be 0's or
	 * 1's, that is <tt>{@link Integer#MAX_VALUE} - 31</tt>. If we use:
	 * <ul>
	 * <li> 2 bits for the sequence type; 
	 * <li> 5 bits to indicate which bit is set;
	 * </ul>
	 * then <tt>32 - 5 - 2 = 25</tt> is the number of available bits to
	 * represent the maximum sequence of 0's and 1's. Thus, the maximal bit that
	 * can be set is represented by a number of 0's equals to
	 * <tt>31 * (1 << 25)</tt>, followed by a literal with 30 0's and the
	 * MSB (31<sup>st</sup> bit) equal to 1
	 */
	public final static int MAX_ALLOWED_INTEGER = 31 * (1 << 25) + 30; // 1040187422

	/** 
	 * The lowest representable integer.
	 */
	public final static int MIN_ALLOWED_SET_BIT = 0;
	
	/** 
	 * Maximum number of representable bits within a literal
	 */
	private final static int MAX_LITERAL_LENGHT = 31;

	/**
	 * Literal that represents all bits set to 1 (and MSB = 1)
	 */
	private final static int ALL_ONES_LITERAL = 0xFFFFFFFF;
	
	/**
	 * Literal that represents all bits set to 0 (and MSB = 1)
	 */
	private final static int ALL_ZEROS_LITERAL = 0x80000000;
	
	/**
	 * All bits set to 1 and MSB = 0
	 */
	private final static int ALL_ONES_WITHOUT_MSB = 0x7FFFFFFF;
	
	/**
	 * All bits set to 0 and MSB = 0
	 */
	private final static int ALL_ZEROS_WITHOUT_MSB = 0x00000000;

	/**
	 * Sequence bit
	 */
	private final static int SEQUENCE_BIT = 0x40000000;

	/**
	 * Resets to an empty set
	 * 
	 * @see #ConciseSet()
	 * {@link #clear()}
	 */
	private void reset() {
		modCount++;
		words = null;
		last = -1;
		size = 0;
		lastWordIndex = -1;
	}

	/**
	 * Creates an empty integer set
	 */
	public ConciseSet() {
		this(false);
	}

	/**
	 * Creates an empty integer set
	 * 
	 * @param simulateWAH
	 *            <code>true</code> if the class must simulate the behavior of
	 *            WAH
	 */
	public ConciseSet(boolean simulateWAH) {
		this.simulateWAH = simulateWAH;
		reset();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet clone() {
		if (isEmpty())
			return empty();

		// NOTE: do not use super.clone() since it is 10 times slower!
		ConciseSet res = empty();
		res.last = last;
		res.lastWordIndex = lastWordIndex;
		res.modCount = 0;
		res.size = size;
		res.words = Arrays.copyOf(words, lastWordIndex + 1);
		return res;
	}

	/**
	 * Calculates the modulus division by 31 in a faster way than using <code>n % 31</code>
	 * <p>
	 * This method of finding modulus division by an integer that is one less
	 * than a power of 2 takes at most <tt>O(lg(32))</tt> time. The number of operations
	 * is at most <tt>12 + 9 * ceil(lg(32))</tt>.
	 * <p>
	 * See <a
	 * href="http://graphics.stanford.edu/~seander/bithacks.html">http://graphics.stanford.edu/~seander/bithacks.html</a>
	 * 
	 * @param n
	 *            number to divide
	 * @return <code>n % 31</code>
	 */
	private static int maxLiteralLengthModulus(int n) {
		int m = (n & 0xC1F07C1F) + ((n >>> 5) & 0xC1F07C1F);
		m = (m >>> 15) + (m & 0x00007FFF);
		if (m <= 31)
			return m == 31 ? 0 : m;
		m = (m >>> 5) + (m & 0x0000001F);
		if (m <= 31)
			return m == 31 ? 0 : m;
		m = (m >>> 5) + (m & 0x0000001F);
		if (m <= 31)
			return m == 31 ? 0 : m;
		m = (m >>> 5) + (m & 0x0000001F);
		if (m <= 31)
			return m == 31 ? 0 : m;
		m = (m >>> 5) + (m & 0x0000001F);
		if (m <= 31)
			return m == 31 ? 0 : m;
		m = (m >>> 5) + (m & 0x0000001F);
		return m == 31 ? 0 : m;
	}

	/**
	 * Calculates the multiplication by 31 in a faster way than using <code>n * 31</code>
	 * 
	 * @param n
	 *            number to multiply
	 * @return <code>n * 31</code>
	 */
	private static int maxLiteralLengthMultiplication(int n) {
		return (n << 5) - n;
	}

	/**
	 * Calculates the division by 31
	 * 
	 * @param n
	 *            number to divide
	 * @return <code>n / 31</code>
	 */
	private static int maxLiteralLengthDivision(int n) {
		return n / 31;
	}

	/**
	 * Checks whether a word is a literal one
	 * 
	 * @param word
	 *            word to check
	 * @return <code>true</code> if the given word is a literal word
	 */
	private static boolean isLiteral(int word) {
		// "word" must be 1*
		// NOTE: this is faster than "return (word & 0x80000000) == 0x80000000"
		return (word & 0x80000000) != 0;
	}

	/**
	 * Checks whether a word contains a sequence of 1's
	 * 
	 * @param word
	 *            word to check
	 * @return <code>true</code> if the given word is a sequence of 1's
	 */
	private static boolean isOneSequence(int word) {
		// "word" must be 01*
		return (word & 0xC0000000) == SEQUENCE_BIT;
	}

	/**
	 * Checks whether a word contains a sequence of 0's
	 * 
	 * @param word
	 *            word to check
	 * @return <code>true</code> if the given word is a sequence of 0's
	 */
	private static boolean isZeroSequence(int word) {
		// "word" must be 00*
		return (word & 0xC0000000) == 0;
	}

	/**
	 * Checks whether a word contains a sequence of 0's with no set bit, or 1's
	 * with no unset bit.
	 * <p>
	 * <b>NOTE:</b> when {@link #simulateWAH} is <code>true</code>, it is
	 * equivalent to (and as fast as) <code>!</code>{@link #isLiteral(int)}
	 * 
	 * @param word
	 *            word to check
	 * @return <code>true</code> if the given word is a sequence of 0's or 1's
	 *         but with no (un)set bit
	 */
	private static boolean isSequenceWithNoBits(int word) {
		// "word" must be 0?00000*
		return (word & 0xBE000000) == 0x00000000;
	}
	
	/**
	 * Gets the number of blocks of 1's or 0's stored in a sequence word
	 * 
	 * @param word
	 *            word to check
	 * @return the number of blocks that follow the first block of 31 bits
	 */
	private static int getSequenceCount(int word) {
		// get the 25 LSB bits
		return word & 0x01FFFFFF;
	}

	/**
	 * Clears the (un)set bit in a sequence
	 * 
	 * @param word
	 *            word to check
	 * @return the sequence corresponding to the given sequence and with no
	 *         (un)set bits
	 */
	private static int getSequenceWithNoBits(int word) {
		// clear 29 to 25 LSB bits
		return (word & 0xC1FFFFFF);
	}
	
	/**
	 * Gets the literal word that represents the first 31 bits of the given the
	 * word (i.e. the first block of a sequence word, or the bits of a literal word).
	 * <p>
	 * If the word is a literal, it returns the unmodified word. In case of a
	 * sequence, it returns a literal that represents the first 31 bits of the
	 * given sequence word.
	 * 
	 * @param word
	 *            word to check
	 * @return the literal contained within the given word, <i>with the most
	 *         significant bit set to 1</i>.
	 */
	private /*static*/ int getLiteral(int word) {
		if (isLiteral(word)) 
			return word;
		
		if (simulateWAH)
			return isZeroSequence(word) ? ALL_ZEROS_LITERAL  : ALL_ONES_LITERAL;

		// get bits from 30 to 26 and use them to set the corresponding bit
		// NOTE: "1 << (word >>> 25)" and "1 << ((word >>> 25) & 0x0000001F)" are equivalent
		// NOTE: ">>> 1" is required since 00000 represents no bits and 00001 the LSB bit set
		int literal = (1 << (word >>> 25)) >>> 1;  
		return isZeroSequence(word) 
				? (ALL_ZEROS_LITERAL | literal) 
				: (ALL_ONES_LITERAL & ~literal);
	}

	/**
	 * Gets the position of the flipped bit within a sequence word. If the
	 * sequence has no set/unset bit, returns -1.
	 * <p>
	 * Note that the parameter <i>must</i> a sequence word, otherwise the
	 * result is meaningless.
	 * 
	 * @param word
	 *            sequence word to check
	 * @return the position of the set bit, from 0 to 31. If the sequence has no
	 *         set/unset bit, returns -1.
	 */
	private static int getFlippedBit(int word) {
		// get bits from 30 to 26
		// NOTE: "-1" is required since 00000 represents no bits and 00001 the LSB bit set
		return ((word >>> 25) & 0x0000001F) - 1;  
	}

	/**
	 * Gets the number of set bits within the literal word
	 * 
	 * @param word
	 *            literal word
	 * @return the number of set bits within the literal word
	 */
	private static int getLiteralBitCount(int word) {
		return Integer.bitCount(getLiteralBits(word));
	}

	/**
	 * Gets the bits contained within the literal word
	 * 
	 * @param word literal word
	 * @return the literal word with the most significant bit cleared
	 */
	private static int getLiteralBits(int word) {
		return ALL_ONES_WITHOUT_MSB & word;
	}

	/**
	 * Clears bits from MSB (excluded, since it indicates the word type) to the
	 * specified bit (excluded). Last word is supposed to be a literal one.
	 * 
	 * @param lastSetBit
	 *            leftmost bit to preserve
	 */
	private void clearBitsAfterInLastWord(int lastSetBit) {
		words[lastWordIndex] &= ALL_ZEROS_LITERAL | (0xFFFFFFFF >>> (31 - lastSetBit));
	}

	/**
	 * Returns <code>true</code> when the given 31-bit literal string (namely,
	 * with MSB set) contains only one set bit
	 * 
	 * @param literal
	 *            literal word (namely, with MSB unset)
	 * @return <code>true</code> when the given literal contains only one set
	 *         bit
	 */
	private static boolean containsOnlyOneBit(int literal) {
		return (literal & (literal - 1)) == 0;
	}

	/**
	 * Assures that the length of {@link #words} is sufficient to contain
	 * the given index.
	 */
	private void ensureCapacity(int index) {
		int capacity = words == null ? 0 : words.length;
		if (capacity > index) 
			return;
		capacity = Math.max(capacity << 1, index + 1);

		if (words == null) {
			// nothing to copy
			words = new int[capacity];
			return;
		}
		words = Arrays.copyOf(words, capacity);
	}

	/**
	 * Removes unused allocated words at the end of {@link #words} only when they
	 * are more than twice of the needed space
	 */
	private void compact() {
		if (words != null && ((lastWordIndex + 1) << 1) < words.length)
			words = Arrays.copyOf(words, lastWordIndex + 1);
	}

	/**
	 * Iterates over words, from LSB to MSB.
	 * <p>
	 * It iterates over the <i>literals</i> represented by
	 * {@link ConciseSet#words}. In particular, when a word is a sequence, it
	 * "expands" the sequence to all the represented literals. It also maintains
	 * a modified copy of the sequence that stores the number of the remaining
	 * blocks to iterate.
	 */
	//TODO: replace with WordIterator!!!
	private class WordIterator_OLD {
		private int currentWordIndex;	// index of the current word
		private int currentWordCopy;	// copy of the current word
		private int currentLiteral;		// literal contained within the current word
		private int remainingWords;		// remaining words from "index" to the end
		
		/*
		 * Initialize data 
		 */
		{
			if (words != null) {
				currentWordIndex = 0;
				currentWordCopy = words[currentWordIndex];
				currentLiteral = getLiteral(currentWordCopy);
				remainingWords = lastWordIndex;
			} else {
				// empty set
				currentWordIndex = 0;
				currentWordCopy = 0;
				currentLiteral = 0;
				remainingWords = -1;
			}
		}

		/**
		 * Checks if the current word represents more than one literal
		 * 
		 * @return <code>true</code> if the current word represents more than
		 *         one literal
		 */
		private boolean hasCurrentWordManyLiterals() {
			/*
			 * The complete statement should be:
			 * 
			 *     return !isLiteral(currentWordCopy) && getSequenceCount(currentWordCopy) > 0;
			 *     
			 * that is equivalent to:
			 * 
			 *     return ((currentWordCopy & 0x80000000) == 0) && (currentWordCopy & 0x01FFFFFF) > 0;
			 *     
			 * and thus equivalent to...
			 */
			return (currentWordCopy & 0x81FFFFFF) > 0;
		}
		
		/**
		 * Checks whether other literals to analyze exist
		 * 
		 * @return <code>true</code> if {@link #currentWordIndex} is out of
		 *         the bounds of {@link #words}
		 */
		public final boolean endOfWords() {
			return remainingWords < 0;
		}

		/**
		 * Checks whether other literals to analyze exist
		 * 
		 * @return <code>true</code> if there are literals to iterate
		 */
		public final boolean hasMoreLiterals() {
			return hasMoreWords() || hasCurrentWordManyLiterals();
		}

		/**
		 * Checks whether other words to analyze exist
		 * 
		 * @return <code>true</code> if there are words to iterate
		 */
		public final boolean hasMoreWords() {
			return remainingWords > 0;
		}
		
		/**
		 * Prepares the next literal {@link #currentLiteral}, increases
		 * {@link #currentWordIndex}, decreases {@link #remainingWords} if
		 * necessary, and modifies the copy of the current word
		 * {@link #currentWordCopy}.
		 */ 
		public final void prepareNextLiteral() {
			if (!hasCurrentWordManyLiterals()) {
				if (remainingWords == -1)
					throw new NoSuchElementException();
				if (remainingWords > 0) 
					currentWordCopy = words[++currentWordIndex];
				remainingWords--;
			} else {
				// decrease the counter and avoid to generate again the 1-bit literal
				if (!simulateWAH)
					currentWordCopy = getSequenceWithNoBits(currentWordCopy);
				currentWordCopy--;
			}
			currentLiteral = getLiteral(currentWordCopy);
		}
	}
	
	/**
	 * Iterates over words, from MSB to LSB.
	 * <p>
	 * @see WordIterator_OLD
	 */
	//TODO: replace with ReverseWordIterator!!!
	private class ReverseWordIterator_OLD {
		private int currentWordIndex;	// index of the current word
		private int currentWordCopy;	// copy of the current word
		private int currentLiteral;		// literal contained within the current word

		/**
		 * Gets the literal word that represents the <i>last</i> 31 bits of the
		 * given the word (i.e. the last block of a sequence word, or the bits
		 * of a literal word).
		 * <p>
		 * If the word is a literal, it returns the unmodified word. In case of
		 * a sequence, it returns a literal that represents the last 31 bits of
		 * the given sequence word.
		 * <p>
		 * Different from {@link ConciseSet#getLiteral(int)}, when the word is
		 * a sequence that contains one (un)set bit, and the count is greater
		 * than zero, then it means that we are traversing the sequence from the
		 * end, and then the literal is represented by all ones or all zeros.
		 * 
		 * @param word
		 *            the word where to extract the literal
		 * @return the literal contained at the end of the given word, with the
		 *         most significant bit set to 1.
		 */
		private int getReverseLiteral(int word) {
			if (simulateWAH || isLiteral(word) || isSequenceWithNoBits(word) || getSequenceCount(word) == 0)
				return getLiteral(word);
			return isZeroSequence(word) ? ALL_ZEROS_LITERAL : ALL_ONES_LITERAL;
		}
		
		/*
		 * Initialize data 
		 */
		{
			if (words != null) {
				currentWordIndex = lastWordIndex;
				currentWordCopy = words[currentWordIndex];
				currentLiteral = getReverseLiteral(currentWordCopy);
			} else {
				// empty set
				currentWordIndex = -1;
				currentWordCopy = 0;
				currentLiteral = 0;
			}
		}
		
		/**
		 * Checks whether other literals to analyze exist
		 * 
		 * @return <code>true</code> if there are literals to iterate
		 */
		public final boolean hasMoreLiterals() {
			if (currentWordIndex > 1)
				return true;
			if (currentWordIndex < 0)
				return false;
			
			// now currentWordIndex == 0 or 1
			if (currentWordIndex == 1) {
				if (isLiteral(currentWordCopy) 
						|| getSequenceCount(currentWordCopy) == 0 
						|| (isZeroSequence(currentWordCopy) && isSequenceWithNoBits(currentWordCopy)))
					return !(words[0] == ALL_ZEROS_LITERAL 
							|| (isZeroSequence(words[0]) && isSequenceWithNoBits(words[0])));
				// I don't have to "jump" to words[0], namely I still have to finish words[1]
				return true;
			} 
			
			// now currentWordIndex == 0, namely the first element
			if (isLiteral(currentWordCopy))
				return false;
			
			// now currentWordCopy is a sequence
			if (getSequenceCount(currentWordCopy) == 0)
				return false;

			// now currentWordCopy is a non-empty sequence
			if (isOneSequence(currentWordCopy))
				return true;

			// now currentWordCopy is a zero sequence
			if (simulateWAH || isSequenceWithNoBits(currentWordCopy))
				return false;
			
			// zero sequence with a set bit at the beginning
			return true;
		}

		/**
		 * Checks whether other words to analyze exist
		 * 
		 * @return <code>true</code> if there are words to iterate
		 */
		public final boolean endOfWords() {
			return currentWordIndex < 0;
		}

		/**
		 * Prepares the next literal, similar to
		 * {@link WordIterator_OLD#prepareNextLiteral()}. <b>NOTE:</b> it supposes
		 * that {@link #hasMoreLiterals()} returns <code>true</code>.
		 */ 
		public final void prepareNextLiteral() {
			if (isLiteral(currentWordCopy) || getSequenceCount(currentWordCopy) == 0) {
				if (--currentWordIndex >= 0)
					currentWordCopy = words[currentWordIndex];
				if (currentWordIndex == -2)
					throw new NoSuchElementException();
			} else {
				currentWordCopy--;
			}
			currentLiteral = getReverseLiteral(currentWordCopy);
		}
	}
	
	/**
	 * When both word iterators currently point to sequence words, it decreases
	 * these sequences by the least sequence count between them and return such
	 * a count.
	 * <p>
	 * Conversely, when one of the word iterators does <i>not</i> point to a
	 * sequence word, it returns 0 and does not change the iterator.
	 * 
	 * @param itr1
	 *            first word iterator
	 * @param itr2
	 *            second word iterator
	 * @return the least sequence count between the sequence word pointed by the
	 *         given iterators
	 * @see #skipSequence(WordIterator_OLD)
	 */
	// TODO: REMOVE!!!
	private static int skipSequence(WordIterator_OLD itr1, WordIterator_OLD itr2) {
		int count = 0;
		if (isSequenceWithNoBits(itr1.currentWordCopy) 
				&& isSequenceWithNoBits(itr2.currentWordCopy)) {
			count = Math.min(
					getSequenceCount(itr1.currentWordCopy),
					getSequenceCount(itr2.currentWordCopy));
			if (count > 0) {
				// increase sequence counter
				itr1.currentWordCopy -= count;
				itr2.currentWordCopy -= count;
			}
		} 
		return count;
	}
	
	/**
	 * The same as {@link #skipSequence(WordIterator_OLD, WordIterator_OLD)}, but for
	 * {@link ReverseWordIterator_OLD} instances
	 */
	// TODO: REMOVE!!!
	private /*static*/ int skipSequence(ReverseWordIterator_OLD itr1, ReverseWordIterator_OLD itr2) {
		int count = 0;
		if (!isLiteral(itr1.currentWordCopy) && !isLiteral(itr2.currentWordCopy)) {
			if (simulateWAH)
				count = Math.min(
						getSequenceCount(itr1.currentWordCopy),
						getSequenceCount(itr2.currentWordCopy));
			else
				count = Math.min(
						getSequenceCount(itr1.currentWordCopy) - (isSequenceWithNoBits(itr1.currentWordCopy) ? 0 : 1),
						getSequenceCount(itr2.currentWordCopy) - (isSequenceWithNoBits(itr2.currentWordCopy) ? 0 : 1));
			if (count > 0) {
				// increase sequence counter
				itr1.currentWordCopy -= count;
				itr2.currentWordCopy -= count;
			}
		} 
		return count;
	}

	/**
	 * Possible operations
	 */
	private enum Operator {
		/** 
		 * Bitwise <code>and</code> between literals 
		 */
		AND {
			@Override 
			public int combineLiterals(int literal1, int literal2) {
				return literal1 & literal2;
			}

			@Override 
			public ConciseSet combineEmptySets(ConciseSet op1, ConciseSet op2) {
				return op1.empty();
			}

			/** Used to implement {@link #combineDisjointSets(ConciseSet, ConciseSet)} */
			private ConciseSet oneWayCombineDisjointSets(ConciseSet op1, ConciseSet op2) {
				// check whether the first operator starts with a sequence that
				// completely "covers" the second operator
				if (isSequenceWithNoBits(op1.words[0]) 
						&& maxLiteralLengthMultiplication(getSequenceCount(op1.words[0]) + 1) > op2.last) {
					// op2 is completely hidden by op1
					if (isZeroSequence(op1.words[0]))
						return op1.empty();
					// op2 is left unchanged, but the rest of op1 is hidden
					return op2.clone();
				}
				return null;
			}
			
			@Override
			public ConciseSet combineDisjointSets(ConciseSet op1, ConciseSet op2) {
				ConciseSet res = oneWayCombineDisjointSets(op1, op2);
				if (res == null)
					res = oneWayCombineDisjointSets(op2, op1);
				return res;
			}
		},
		
		/** 
		 * Bitwise <code>or</code> between literals
		 */
		OR {
			@Override 
			public int combineLiterals(int literal1, int literal2) {
				return literal1 | literal2;
			}

			@Override 
			public ConciseSet combineEmptySets(ConciseSet op1, ConciseSet op2) {
				if (!op1.isEmpty())
					return op1.clone();
				if (!op2.isEmpty())
					return op2.clone();
				return op1.empty();
			}
			
			/** Used to implement {@link #combineDisjointSets(ConciseSet, ConciseSet)} */
			private ConciseSet oneWayCombineDisjointSets(ConciseSet op1, ConciseSet op2) {
				// check whether the first operator starts with a sequence that
				// completely "covers" the second operator
				if (isSequenceWithNoBits(op1.words[0]) 
						&& maxLiteralLengthMultiplication(getSequenceCount(op1.words[0]) + 1) > op2.last) {
					// op2 is completely hidden by op1
					if (isOneSequence(op1.words[0]))
						return op1.clone();
					// op2 is left unchanged, but the rest of op1 must be appended...
					
					// ... first, allocate sufficient space for the result
					ConciseSet res = op1.empty();
					res.words = new int[op1.lastWordIndex + op2.lastWordIndex + 3];
					res.lastWordIndex = op2.lastWordIndex;
					
					// ... then, copy op2
					System.arraycopy(op2.words, 0, res.words, 0, op2.lastWordIndex + 1);
					
					// ... finally, append op1
					WordIterator wordIterator = op1.new WordIterator();
					wordIterator.prepareNext(maxLiteralLengthDivision(op2.last) + 1);
					wordIterator.flush(res);
					if (op1.size < 0 || op2.size < 0)
						res.size = -1;
					else
						res.size = op1.size + op2.size;
					res.last = op1.last;
					res.compact();
					return res;				
				}
				return null;
			}

			@Override
			public ConciseSet combineDisjointSets(ConciseSet op1, ConciseSet op2) {
				ConciseSet res = oneWayCombineDisjointSets(op1, op2);
				if (res == null)
					res = oneWayCombineDisjointSets(op2, op1);
				return res;
			}
		},

		/** 
		 * Bitwise <code>xor</code> between literals 
		 */
		XOR {
			@Override 
			public int combineLiterals(int literal1, int literal2) {
				return ALL_ZEROS_LITERAL | (literal1 ^ literal2);
			}

			@Override 
			public ConciseSet combineEmptySets(ConciseSet op1, ConciseSet op2) {
				if (!op1.isEmpty())
					return op1.clone();
				if (!op2.isEmpty())
					return op2.clone();
				return op1.empty();
			}
			
			/** Used to implement {@link #combineDisjointSets(ConciseSet, ConciseSet)} */
			private ConciseSet oneWayCombineDisjointSets(ConciseSet op1, ConciseSet op2) {
				// check whether the first operator starts with a sequence that
				// completely "covers" the second operator
				if (isSequenceWithNoBits(op1.words[0]) 
						&& maxLiteralLengthMultiplication(getSequenceCount(op1.words[0]) + 1) > op2.last) {
					// op2 is left unchanged by op1
					if (isZeroSequence(op1.words[0]))
						return OR.combineDisjointSets(op1, op2);
					// op2 must be complemented, then op1 must be appended 
					// it is better to perform it normally...
					return null;
				}
				return null;
			}

			@Override
			public ConciseSet combineDisjointSets(ConciseSet op1, ConciseSet op2) {
				ConciseSet res = oneWayCombineDisjointSets(op1, op2);
				if (res == null)
					res = oneWayCombineDisjointSets(op2, op1);
				return res;
			}
		},

		/** 
		 * Bitwise <code>and-not</code> between literals (i.e. <code>X and (not Y)</code>) 
		 */
		ANDNOT {
			@Override 
			public int combineLiterals(int literal1, int literal2) {
				return ALL_ZEROS_LITERAL | (literal1 & (~literal2));
			}

			@Override 
			public ConciseSet combineEmptySets(ConciseSet op1, ConciseSet op2) {
				if (!op1.isEmpty())
					return op1.clone();
				return op1.empty();
			}
			
			@Override
			public ConciseSet combineDisjointSets(ConciseSet op1, ConciseSet op2) {
				// check whether the first operator starts with a sequence that
				// completely "covers" the second operator
				if (isSequenceWithNoBits(op1.words[0]) 
						&& maxLiteralLengthMultiplication(getSequenceCount(op1.words[0]) + 1) > op2.last) {
					// op1 is left unchanged by op2
					if (isZeroSequence(op1.words[0]))
						return op1.clone();
					// op2 must be complemented, then op1 must be appended 
					// it is better to perform it normally...
					return null;
				}
				// check whether the second operator starts with a sequence that
				// completely "covers" the first operator
				if (isSequenceWithNoBits(op2.words[0]) 
						&& maxLiteralLengthMultiplication(getSequenceCount(op2.words[0]) + 1) > op1.last) {
					// op1 is left unchanged by op2
					if (isZeroSequence(op2.words[0]))
						return op1.clone();
					// op1 is cleared by op2
					return op1.empty();
				}
				return null;
			}
		},
		;

		/**
		 * Performs the operation on the given literals
		 * 
		 * @param literal1
		 *            left operand
		 * @param literal2
		 *            right operand
		 * @return literal representing the result of the specified operation
		 */
		public abstract int combineLiterals(int literal1, int literal2); 
		
		/**
		 * Performs the operation when one or both operands are empty set
		 * <p>
		 * <b>NOTE: the caller <i>MUST</i> assure that one or both the operands
		 * are empty!!!</b>
		 * 
		 * @param op1
		 *            left operand
		 * @param op2
		 *            right operand
		 * @return <code>null</code> if both operands are non-empty
		 */
		public abstract ConciseSet combineEmptySets(ConciseSet op1, ConciseSet op2);
		
		/**
		 * Performs the operation in the special case of "disjoint" sets, namely
		 * when the first (or the second) operand starts with a sequence (it
		 * does not matter if 0's or 1's) that completely covers all the bits of
		 * the second (or the first) operand.
		 * 
		 * @param op1
		 *            left operand
		 * @param op2
		 *            right operand
		 * @return <code>null</code> if operands are non-disjoint
		 */
		public abstract ConciseSet combineDisjointSets(ConciseSet op1, ConciseSet op2);
	}

	/**
	 * Sets the bit at the given absolute position within the uncompressed bit
	 * string. The bit <i>must</i> be appendable, that is it must represent an
	 * integer that is strictly greater than the maximum integer in the set.
	 * Note that the parameter range check is performed by the public method
	 * {@link #add(Integer)} and <i>not</i> in this method.
	 * <p>
	 * <b>NOTE:</b> This method assumes that the last element of {@link #words}
	 * (i.e. <code>getLastWord()</code>) <i>must</i> be one of the
	 * following:
	 * <ul>
	 * <li> a literal word with <i>at least one</i> set bit;
	 * <li> a sequence of ones.
	 * </ul>
	 * Hence, the last word in {@link #words} <i>cannot</i> be:
	 * <ul>
	 * <li> a literal word containing only zeros;
	 * <li> a sequence of zeros.
	 * </ul>
	 * 
	 * @param i
	 *            the absolute position of the bit to set (i.e., the integer to add) 
	 */
	private void append(int i) {
		// special case of empty set
		if (isEmpty()) {
			int zeroBlocks = maxLiteralLengthDivision(i);
			if (zeroBlocks == 0) {
				words = new int[1];
				lastWordIndex = 0;
			} else if (zeroBlocks == 1) {
				words = new int[2];
				lastWordIndex = 1;
				words[0] = ALL_ZEROS_LITERAL;
			} else {
				words = new int[2];
				lastWordIndex = 1;
				words[0] = zeroBlocks - 1;
			}
			last = i;
			size = 1;
			words[lastWordIndex] = ALL_ZEROS_LITERAL | (1 << maxLiteralLengthModulus(i));
			return;
		}
		
		// position of the next bit to set within the current literal
		int bit = maxLiteralLengthModulus(last) + i - last;

		// if we are outside the current literal, add zeros in
		// between the current word and the new 1-bit literal word
		if (bit >= MAX_LITERAL_LENGHT) {
			int zeroBlocks = maxLiteralLengthDivision(bit) - 1;
			bit = maxLiteralLengthModulus(bit);
			if (zeroBlocks == 0) {
				ensureCapacity(lastWordIndex + 1);
			} else {
				ensureCapacity(lastWordIndex + 2);
				appendFill(zeroBlocks, 0);
			}
			appendLiteral(ALL_ZEROS_LITERAL | 1 << bit);
		} else {
			words[lastWordIndex] |= 1 << bit;
			if (words[lastWordIndex] == ALL_ONES_LITERAL) {
				lastWordIndex--;
				appendLiteral(ALL_ONES_LITERAL);
			}
		}

		// update other info
		last = i;
		if (size >= 0)
			size++;
	}
	
	/**
	 * Append a literal word after the last word
	 * 
	 * @param word
	 *            the new literal word. Note that the leftmost bit <b>must</b>
	 *            be set to 1.
	 */
	private void appendLiteral(int word) {
		// TODO se sto inserendo 0x80000000 e ho solo una sequence 0x01FFFFFF,
		// allora il risultato � un insieme vuoto, mentre invece qui mi genera
		// 0x02000000 che � una sequence con il primo bit a 1!!!
		if (lastWordIndex < 0) {
			// empty set
			words[lastWordIndex = 0] = word;
			return;
		} 
		
		final int lastWord = words[lastWordIndex];
		if (word == ALL_ZEROS_LITERAL) {
			if (lastWord == ALL_ZEROS_LITERAL)
				words[lastWordIndex] = 1;
			else if (isZeroSequence(lastWord))
				words[lastWordIndex]++;
			else if (!simulateWAH && containsOnlyOneBit(getLiteralBits(lastWord)))
				words[lastWordIndex] = 1 | ((1 + Integer.numberOfTrailingZeros(lastWord)) << 25);
			else
				words[++lastWordIndex] = word;
		} else if (word == ALL_ONES_LITERAL) {
			if (lastWord == ALL_ONES_LITERAL)
				words[lastWordIndex] = SEQUENCE_BIT | 1;
			else if (isOneSequence(lastWord))
				words[lastWordIndex]++;
			else if (!simulateWAH && containsOnlyOneBit(~lastWord))
				words[lastWordIndex] = SEQUENCE_BIT | 1 | ((1 + Integer.numberOfTrailingZeros(~lastWord)) << 25);
			else
				words[++lastWordIndex] = word;
		} else {
			words[++lastWordIndex] = word;
		}
	}

	/**
	 * Append a sequence word after the last word
	 * 
	 * @param length
	 *            sequence length
	 * @param fillType
	 *            sequence word with a count that equals 0
	 */
	private void appendFill(int length, int fillType) {
		assert length > 0;
		assert lastWordIndex >= -1;
		
		fillType &= SEQUENCE_BIT;
		
		// it is actually a literal...
		if (length == 1) {
			appendLiteral(fillType == 0 ? ALL_ZEROS_LITERAL : ALL_ONES_LITERAL);
			return;
		} 

		// empty set
		if (lastWordIndex < 0) {
			words[lastWordIndex = 0] = fillType | (length - 1);
			return;
		} 
		
		final int lastWord = words[lastWordIndex];
		if (isLiteral(lastWord)) {
			if (fillType == 0 && lastWord == ALL_ZEROS_LITERAL) {
				words[lastWordIndex] = length;
			} else if (fillType == SEQUENCE_BIT && lastWord == ALL_ONES_LITERAL) {
				words[lastWordIndex] = SEQUENCE_BIT | length;
			} else if (!simulateWAH) {
				if (fillType == 0 && containsOnlyOneBit(getLiteralBits(lastWord))) {
					words[lastWordIndex] = length | ((1 + Integer.numberOfTrailingZeros(lastWord)) << 25);
				} else if (fillType == SEQUENCE_BIT && containsOnlyOneBit(~lastWord)) {
					words[lastWordIndex] = SEQUENCE_BIT | length | ((1 + Integer.numberOfTrailingZeros(~lastWord)) << 25);
				} else {
					words[++lastWordIndex] = fillType | (length - 1);
				}
			} else {
				words[++lastWordIndex] = fillType | (length - 1);
			}
		} else {
			if ((lastWord & 0xC0000000) == fillType)
				words[lastWordIndex] += length;
			else
				words[++lastWordIndex] = fillType | (length - 1);
		}
	}

	/**
	 * Iterates over words, from the rightmost (LSB) to the leftmost (MSB).
	 * <p>
	 * When {@link ConciseSet#simulateWAH} is <code>false</code>, mixed
	 * sequences are "broken" into a literal (i.e., the first block is coded
	 * with a literal in {@link #word}) and a "pure" sequence (i.e., the
	 * remaining blocks are coded with a sequence with no bits in {@link #word})
	 */
	private class WordIterator {
		/** copy of the current word */
		int word;
		
		/** current word index */
		int index;
		
		/** <code>true</code> if {@link #word} is a literal */
		boolean isLiteral;
		
		/** number of blocks in the current word (1 for literals, > 1 for sequences) */
		int count;
		
		/**
		 * Initialize data
		 */
		WordIterator() {
			isLiteral = false;
			index = -1;
			prepareNext();
		}
		
		/**
		 * @return <code>true</code> if there is no current word
		 */
		boolean exhausted() {
			return index > lastWordIndex;
		}

		/**
		 * Prepare the next value for {@link #word}
		 * 
		 * @param c
		 *            number of blocks to skip
		 * @return <code>false</code> if the next word does not exists
		 */
		boolean prepareNext(int c) {
			assert c <= count;
			count -= c;
			if (count == 0)
				return prepareNext();
			return true;
		}
		
		/**
		 * Prepare the next value for {@link #word}
		 * 
		 * @return <code>false</code> if the next word does not exists
		 */
		boolean prepareNext() {
			if (!simulateWAH && isLiteral && count > 1) {
				count--;
				isLiteral = false;
				word = getSequenceWithNoBits(words[index]) - 1;
				return true;
			}
			
			index++;
			if (index > lastWordIndex)
				return false;
			word = words[index];
			isLiteral = isLiteral(word);
			if (!isLiteral) {
				count = getSequenceCount(word) + 1;
				if (!simulateWAH && !isSequenceWithNoBits(word)) {
					isLiteral = true;
					int bit = (1 << (word >>> 25)) >>> 1;  
					word = isZeroSequence(word) 
							? (ALL_ZEROS_LITERAL | bit) 
							: (ALL_ONES_LITERAL & ~bit);
				} 
			} else {
				count = 1;
			}
			return true;
		}

		/**
		 * @return the literal word corresponding to each block contained in the
		 *         current sequence word
		 */
		int toLiteral()  {
			assert !isLiteral;
			return ALL_ZEROS_LITERAL | ((word << 1) >> MAX_LITERAL_LENGHT);
		}
		
		/**
		 * Copy all the remaining words in the given set
		 * 
		 * @param s
		 *            set where the words must be copied
		 * @return <code>false</code> if there are no word to copy
		 */
		private boolean flush(ConciseSet s) {
			// nothing to flush
			if (exhausted())
				return false;
			
			// try to "compress" the first few words
			do {
				if (isLiteral) 
					s.appendLiteral(word);
				else 
					s.appendFill(count, word);
			} while (prepareNext() && s.words[s.lastWordIndex] != word);
			
			// copy remaining words "as-is"
			int delta = lastWordIndex - index + 1;
			System.arraycopy(words, index, s.words, s.lastWordIndex + 1, delta);
			s.lastWordIndex += delta;
			s.last = last;
			return true;
		}
	}
	
	/**
	 * Recalculate a fresh value for {@link ConciseSet#last}
	 */
	private void updateLast() {
		last = 0;
		for (int i = 0; i <= lastWordIndex; i++) {
			int w = words[i];
			if (isLiteral(w))
				last += MAX_LITERAL_LENGHT;
			else
				last += maxLiteralLengthMultiplication(getSequenceCount(w) + 1);
		}

		int w = words[lastWordIndex];
		if (isLiteral(w)) 
			last -= Integer.numberOfLeadingZeros(getLiteralBits(w));
		else 
			last--;
	}
	
	/**
	 * Performs the given operation over the bit-sets
	 * 
	 * @param other
	 *            {@link ConciseSet} instance that represents the right
	 *            operand
	 * @param operator
	 *            operator
	 * @return the result of the operation
	 */
	private ConciseSet performOperation(ConciseSet other, Operator operator) {
		// non-empty arguments
		if (this.isEmpty() || other.isEmpty()) 
			return operator.combineEmptySets(this, other);
		
		// if the two operands are disjoint, the operation is faster
		ConciseSet res = operator.combineDisjointSets(this, other);
		if (res != null)
			return res;

		// Allocate a sufficient number of words to contain all possible results.
		// NOTE: since lastWordIndex is the index of the last used word in "words",
		// we require "+2" to have the actual maximum required space.
		// In any case, we do not allocate more than the maximum space required
		// for the uncompressed representation.
		// Another "+1" is required to allows for the addition of the last word
		// before compacting.
		res = empty();
		res.words = new int[1 + Math.min(
				this.lastWordIndex + other.lastWordIndex + 2, 
				maxLiteralLengthDivision(Math.max(this.last, other.last)) << (simulateWAH ? 1 : 0))];
		
		// scan "this" and "other"
		WordIterator thisItr = new WordIterator();
		WordIterator otherItr = other.new WordIterator();
		while (true) {
			if (!thisItr.isLiteral) {
				if (!otherItr.isLiteral) {
					int minCount = Math.min(thisItr.count, otherItr.count);
					res.appendFill(minCount, operator.combineLiterals(thisItr.word, otherItr.word));
					if (!thisItr.prepareNext(minCount) | !otherItr.prepareNext(minCount)) // NOT ||
						break;
				} else {
					res.appendLiteral(operator.combineLiterals(thisItr.toLiteral(), otherItr.word));
					thisItr.word--;
					if (!thisItr.prepareNext(1) | !otherItr.prepareNext()) // do NOT use "||"
						break;
				}
			} else if (!otherItr.isLiteral) {
				res.appendLiteral(operator.combineLiterals(thisItr.word, otherItr.toLiteral()));
				otherItr.word--;
				if (!thisItr.prepareNext() | !otherItr.prepareNext(1)) // do NOT use  "||"
					break;
			} else {
				res.appendLiteral(operator.combineLiterals(thisItr.word, otherItr.word));
				if (!thisItr.prepareNext() | !otherItr.prepareNext()) // do NOT use  "||"
					break;
			}
		}

		// invalidate the size
		res.size = -1;
		boolean invalidLast = true;

		// if one bit string is greater than the other one, we add the remaining
		// bits depending on the given operation. 
		switch (operator) {
		case AND:
			break;
		case OR:
			res.last = Math.max(this.last, other.last);
			invalidLast = false;
			invalidLast |= thisItr.flush(res);
			invalidLast |= otherItr.flush(res);
			break;
		case XOR:
			if (this.last != other.last) {
				res.last = Math.max(this.last, other.last);
				invalidLast = false;
			}
			invalidLast |= thisItr.flush(res);
			invalidLast |= otherItr.flush(res);
			break;
		case ANDNOT:
			if (this.last > other.last) {
				res.last = this.last;
				invalidLast = false;
			}
			invalidLast |= thisItr.flush(res);
			break;
		}

		// remove trailing zeros
		res.trimZeros();
		if (res.isEmpty())
			return res;

		// compute the greatest element
		if (invalidLast) 
			res.updateLast();

		// compact the memory
		res.compact();

		return res;
	}
	
	/**
	 * {@inheritDoc}
	 */
	// TODO: Update according to performOperation!!!
	@Override
	public int intersectionSize(IntSet c) {
		if (c == null || c.isEmpty())
			return 0;
		if (c == this)
			return size();
		if (isEmpty())
			return 0;

		// single-element intersection
		if (size == 1)
			return c.contains(last) ? 1 : 0;

		// convert the other set in order to perform a more complex intersection
		final ConciseSet other = convert(c);
		if (other.size == 1) 
			return contains(other.last) ? 1 : 0;
		
		// disjoint sets
		if (isSequenceWithNoBits(this.words[0]) 
				&& maxLiteralLengthMultiplication(getSequenceCount(this.words[0]) + 1) > other.last) {
			if (isZeroSequence(this.words[0]))
				return 0;
			return other.size();
		}
		if (isSequenceWithNoBits(other.words[0]) 
				&& maxLiteralLengthMultiplication(getSequenceCount(other.words[0]) + 1) > this.last) {
			if (isZeroSequence(other.words[0]))
				return 0;
			return this.size();
		}
		
		// resulting size
		int res = 0;

		// scan "this" and "other"
		WordIterator_OLD thisItr = this.new WordIterator_OLD();
		WordIterator_OLD otherItr = other.new WordIterator_OLD();
		while (!thisItr.endOfWords() && !otherItr.endOfWords()) {
			// perform the operation
			int curRes = getLiteralBitCount(thisItr.currentLiteral & otherItr.currentLiteral);
			res += curRes;

			// avoid loops when both are sequences and the result is a sequence
			if (curRes == ALL_ZEROS_WITHOUT_MSB || curRes == ALL_ONES_WITHOUT_MSB) 
				res += curRes * skipSequence(thisItr, otherItr);

			// next literals
			thisItr.prepareNextLiteral();
			otherItr.prepareNextLiteral();
		}

		// return the intersection size
		return res;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int get(int i) {
		if (i < 0)
			throw new IndexOutOfBoundsException();

		// initialize data
		int firstSetBitInWord = 0;
		int position = i;
		int setBitsInCurrentWord = 0;
		for (int j = 0; j <= lastWordIndex; j++) {
			int w = words[j];
			if (isLiteral(w)) {
				// number of bits in the current word
				setBitsInCurrentWord = getLiteralBitCount(w);
				
				// check if the desired bit is in the current word
				if (position < setBitsInCurrentWord) {
					int currSetBitInWord = -1;
					for (; position >= 0; position--)
						currSetBitInWord = Integer.numberOfTrailingZeros(w & (0xFFFFFFFF << (currSetBitInWord + 1)));
					return firstSetBitInWord + currSetBitInWord;
				}
				
				// skip the 31-bit block
				firstSetBitInWord += MAX_LITERAL_LENGHT;
			} else {
				// number of involved bits (31 * blocks)
				int sequenceLength = maxLiteralLengthMultiplication(getSequenceCount(w) + 1);
				
				// check the sequence type
				if (isOneSequence(w)) {
					if (simulateWAH || isSequenceWithNoBits(w)) {
						setBitsInCurrentWord = sequenceLength;
						if (position < setBitsInCurrentWord)
							return firstSetBitInWord + position;
					} else {
						setBitsInCurrentWord = sequenceLength - 1;
						if (position < setBitsInCurrentWord)
							// check whether the desired set bit is after the
							// flipped bit (or after the first block)
							return firstSetBitInWord + position + (position < getFlippedBit(w) ? 0 : 1);
					}
				} else {
					if (simulateWAH ||isSequenceWithNoBits(w)) {
						setBitsInCurrentWord = 0;
					} else {
						setBitsInCurrentWord = 1;
						if (position == 0)
							return firstSetBitInWord + getFlippedBit(w);
					}
				}

				// skip the 31-bit blocks
				firstSetBitInWord += sequenceLength;
			}
			
			// update the number of found set bits
			position -= setBitsInCurrentWord;
		}
		
		throw new IndexOutOfBoundsException(Integer.toString(i));
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int indexOf(int e) {
		if (e < 0)
			throw new IndexOutOfBoundsException();
		if (isEmpty())
			return -1;

		// returned value
		int index = 0;

		int blockIndex = maxLiteralLengthDivision(e);
		int bitPosition = maxLiteralLengthModulus(e);
		for (int i = 0; i <= lastWordIndex && blockIndex >= 0; i++) {
			int w = words[i];
			if (isLiteral(w)) {
				// check if the current literal word is the "right" one
				if (blockIndex == 0) {
					if ((w & (1 << bitPosition)) == 0)
						return -1;
					return index + Integer.bitCount(w & ~(0xFFFFFFFF << bitPosition));
				}
				blockIndex--;
				index += getLiteralBitCount(w);
			} else {
				if (simulateWAH) {
					if (isOneSequence(w) && blockIndex <= getSequenceCount(w))
						return index + maxLiteralLengthMultiplication(blockIndex) + bitPosition;
				} else {
					// if we are at the beginning of a sequence, and it is
					// a set bit, the bit already exists
					if (blockIndex == 0) {
						int l = getLiteral(w);
						if ((l & (1 << bitPosition)) == 0)
							return -1;
						return index + Integer.bitCount(l & ~(0xFFFFFFFF << bitPosition));
					}
					
					// if we are in the middle of a sequence of 1's, the bit already exist
					if (blockIndex > 0 
							&& blockIndex <= getSequenceCount(w) 
							&& isOneSequence(w))
						return index + maxLiteralLengthMultiplication(blockIndex) + bitPosition - (isSequenceWithNoBits(w) ? 0 : 1);
				}
				
				// next word
				int blocks = getSequenceCount(w) + 1;
				blockIndex -= blocks;
				if (isZeroSequence(w)) {
					if (!simulateWAH && !isSequenceWithNoBits(w))
						index++;
				} else {
					index += maxLiteralLengthMultiplication(blocks);
					if (!simulateWAH && !isSequenceWithNoBits(w))
						index--;
				}
			}
		}
		
		// not found
		return -1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet intersection(IntSet other) {
		return performOperation(convert(other), Operator.AND);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet union(IntSet other) {
		return performOperation(convert(other), Operator.OR);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet difference(IntSet  other) {
		return performOperation(convert(other), Operator.ANDNOT);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet symmetricDifference(IntSet other) {
		return performOperation(convert(other), Operator.XOR);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet complemented() {
		ConciseSet cloned = clone();
		cloned.complement();
		return cloned;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void complement() {
		modCount++;
		
		if (isEmpty())
			return;
		
		if (last == MIN_ALLOWED_SET_BIT) {
			clear();
			return;
		}
		
		// update size
		if (size >= 0)
			size = last - size + 1;

		// complement each word
		for (int i = 0; i <= lastWordIndex; i++) {
			int w = words[i];
			if (isLiteral(w)) 
				// negate the bits and set the most significant bit to 1
				words[i] = ALL_ZEROS_LITERAL | ~w;
			else
				// switch the sequence type
				words[i] ^= SEQUENCE_BIT;
		}

		// do not complement after the last element
		if (isLiteral(words[lastWordIndex]))
			clearBitsAfterInLastWord(maxLiteralLengthModulus(last));

		// remove trailing zeros
		trimZeros();
		if (isEmpty())
			return;

		// calculate the maximal element
		last = 0;
		int w = 0;
		for (int i = 0; i <= lastWordIndex; i++) {
			w = words[i];
			if (isLiteral(w)) 
				last += MAX_LITERAL_LENGHT;
			else 
				last += maxLiteralLengthMultiplication(getSequenceCount(w) + 1);
		}

		// manage the last word (that must be a literal or a sequence of 1's)
		if (isLiteral(w)) 
			last -= Integer.numberOfLeadingZeros(getLiteralBits(w));
		else 
			last--;
	}

	/**
	 * Removes trailing zeros
	 */
	private void trimZeros() {
		// loop over ALL_ZEROS_LITERAL words
		int w;
		do {
			w = words[lastWordIndex];
			if (w == ALL_ZEROS_LITERAL) {
				lastWordIndex--;
			} else if (isZeroSequence(w)) {
				if (simulateWAH || isSequenceWithNoBits(w)) {
					lastWordIndex--;
				} else {
					// convert the sequence in a 1-bit literal word
					words[lastWordIndex] = getLiteral(w);
					return;
				}
			} else {
				// one sequence or literal
				return;
			}
			if (lastWordIndex < 0) {
				reset();
				return;
			}
		} while (true);
	}
	
	/**
	 * Iterator for set bits of {@link ConciseSet}, from LSB to MSB
	 */
	//TODO: remove WordIterator_OLD...
	private class BitIterator_OLD implements ExtendedIntIterator {
		private WordIterator_OLD wordItr = new WordIterator_OLD();
		private int rightmostBitOfCurrentWord = 0;
		private int nextBitToCheck = 0;
		private int initialModCount = modCount;

		/**
		 * Gets the next bit in the current literal
		 * 
		 * @return 32 if there is no next bit, otherwise the next set bit within
		 *         the current literal
		 */
		private int getNextSetBit() {
			return Integer.numberOfTrailingZeros(wordItr.currentLiteral & (0xFFFFFFFF << nextBitToCheck));
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean hasNext() {
			return wordItr.hasMoreLiterals() || getNextSetBit() < MAX_LITERAL_LENGHT;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int next() {
			// check for concurrent modification 
			if (initialModCount != modCount)
				throw new ConcurrentModificationException();
			
			// loop until we find some set bit
			int nextSetBit = MAX_LITERAL_LENGHT;
			while (nextSetBit >= MAX_LITERAL_LENGHT) {
				// next bit in the current literal
				nextSetBit = getNextSetBit();

				if (nextSetBit < MAX_LITERAL_LENGHT) {
					// return the current bit and then set the next search
					// within the literal
					nextBitToCheck = nextSetBit + 1;
				} else {
					// advance one word
					rightmostBitOfCurrentWord += MAX_LITERAL_LENGHT;
					if (isZeroSequence(wordItr.currentWordCopy)) {
						// skip zeros
						int blocks = getSequenceCount(wordItr.currentWordCopy);
						rightmostBitOfCurrentWord += MAX_LITERAL_LENGHT * blocks;
						wordItr.currentWordCopy -= blocks;
					}
					nextBitToCheck = 0;
					wordItr.prepareNextLiteral();
				}
			}

			return rightmostBitOfCurrentWord + nextSetBit;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void skipAllBefore(int element) {
			if (element > MAX_ALLOWED_INTEGER)
				throw new IndexOutOfBoundsException(String.valueOf(element));

			// the element is before the next one
			if (element <= rightmostBitOfCurrentWord + nextBitToCheck)
				return;
			
			// the element is after the last one
			if (element > last){
				// make hasNext() return "false"
				wordItr.remainingWords = 0;
				wordItr.currentWordCopy = 0x00000000;
				wordItr.currentLiteral = ALL_ZEROS_LITERAL;
				return;
			}
			
			// next element
			nextBitToCheck = element - rightmostBitOfCurrentWord;
			
			// the element is in the current word
			if (nextBitToCheck < MAX_LITERAL_LENGHT)
				return;
			
			// the element should be after the current word, but there are no more words
			if (!wordItr.hasMoreLiterals()) {
				// makes hasNext() return "false"
				wordItr.remainingWords = 0;
				wordItr.currentLiteral = 0;
				return;
			}
			
			// the element is after the current word
			while (nextBitToCheck >= MAX_LITERAL_LENGHT) {
				if (isLiteral(wordItr.currentWordCopy) || !isSequenceWithNoBits(wordItr.currentWordCopy)) {
					// skip the current literal word or the first block of a
					// sequence with (un)set bit
					rightmostBitOfCurrentWord += MAX_LITERAL_LENGHT;
					nextBitToCheck -= MAX_LITERAL_LENGHT;
				} else {
					int blocks = getSequenceCount(wordItr.currentWordCopy);
					int bits = maxLiteralLengthMultiplication(1 + blocks);
					if (isZeroSequence(wordItr.currentWordCopy)) {
						if (bits > nextBitToCheck)
							nextBitToCheck = 0;
						else
							nextBitToCheck -= bits;
					} else {
						if (bits > nextBitToCheck) {
							blocks = maxLiteralLengthDivision(nextBitToCheck) - 1;
							bits = maxLiteralLengthMultiplication(blocks + 1);
						} 
						nextBitToCheck -= bits;
					}
					rightmostBitOfCurrentWord += bits;
					wordItr.currentWordCopy -= blocks;
				}
				wordItr.prepareNextLiteral();
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void remove() {
			// it is difficult to remove the current bit in a sequence word and
			// to contextually keep the word iterator updated!
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Iterator for set bits of {@link ConciseSet}, from MSB to LSB
	 */
	private class ReverseBitIterator implements ExtendedIntIterator {
		private ReverseWordIterator_OLD wordItr = new ReverseWordIterator_OLD();
		private int rightmostBitOfCurrentWord = maxLiteralLengthMultiplication(maxLiteralLengthDivision(last));
		private int nextBitToCheck = maxLiteralLengthModulus(last);
		private int initialModCount = modCount;

		/**
		 * Gets the next bit in the current literal
		 * 
		 * @return -1 if there is no next bit, otherwise the next set bit within
		 *         the current literal
		 */
		private int getNextSetBit() {
			return MAX_LITERAL_LENGHT 
				- Integer.numberOfLeadingZeros(wordItr.currentLiteral & ~(0xFFFFFFFF << (nextBitToCheck + 1)));
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean hasNext() {
			return wordItr.hasMoreLiterals() || getNextSetBit() >= 0;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int next() {
			// check for concurrent modification 
			if (initialModCount != modCount)
				throw new ConcurrentModificationException();
			
			// loop until we find some set bit
			int nextSetBit = -1;
			while (nextSetBit < 0) {
				// next bit in the current literal
				nextSetBit = getNextSetBit();

				if (nextSetBit >= 0) {
					// return the current bit and then set the next search
					// within the literal
					nextBitToCheck = nextSetBit - 1;
				} else {
					// advance one word
					rightmostBitOfCurrentWord -= MAX_LITERAL_LENGHT;
					if (isZeroSequence(wordItr.currentWordCopy)) {
						// skip zeros
						int blocks = getSequenceCount(wordItr.currentWordCopy);
						if (!isSequenceWithNoBits(wordItr.currentWordCopy))
							blocks--;
						if (blocks > 0) {
							rightmostBitOfCurrentWord -= maxLiteralLengthMultiplication(blocks);
							wordItr.currentWordCopy -= blocks;
						}
					}
					nextBitToCheck = MAX_LITERAL_LENGHT - 1;
					wordItr.prepareNextLiteral();
				}
			}

			return rightmostBitOfCurrentWord + nextSetBit;
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public void skipAllBefore(int element) {
			if (element < 0)
				throw new IndexOutOfBoundsException(String.valueOf(element));
			
			// the element is before the next one
			if (element >= rightmostBitOfCurrentWord + nextBitToCheck)
				return;
			
			// next element
			nextBitToCheck = element - rightmostBitOfCurrentWord;
			
			// the element is in the current word
			if (nextBitToCheck > 0)
				return;
			
			// the element should be after the current word, but there are no more words
			if (!wordItr.hasMoreLiterals()) {
				// makes hasNext() return "false"
				wordItr.currentWordIndex = -1;
				wordItr.currentWordCopy = ALL_ZEROS_LITERAL;
				return;
			}
			
			// the element is after the current word
			while (nextBitToCheck < 0) {
				if (isLiteral(wordItr.currentWordCopy) || getSequenceCount(wordItr.currentWordCopy) == 0) {
					// skip the current literal word or the first block of a
					// sequence with (un)set bit
					rightmostBitOfCurrentWord -= MAX_LITERAL_LENGHT;
					nextBitToCheck += MAX_LITERAL_LENGHT;
				} else {
					int blocks = getSequenceCount(wordItr.currentWordCopy);
					if (!isSequenceWithNoBits(wordItr.currentWordCopy))
						blocks--;
					int bits = maxLiteralLengthMultiplication(1 + blocks);
					if (bits > -nextBitToCheck) {
						blocks = maxLiteralLengthDivision(-nextBitToCheck - 1);
						bits = maxLiteralLengthMultiplication(blocks + 1);
					}
					rightmostBitOfCurrentWord -= bits;
					nextBitToCheck += bits;
					wordItr.currentWordCopy -= blocks;
				}
				wordItr.prepareNextLiteral();
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void remove() {
			// it is difficult to remove the current bit in a sequence word and
			// to contextually keep the word iterator updated!
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExtendedIntIterator intIterator() {
		if (isEmpty()) {
			return new ExtendedIntIterator() {
				@Override public void skipAllBefore(int element) {/*empty*/}
				@Override public boolean hasNext() {return false;}
				@Override public int next() {throw new NoSuchElementException();}
				@Override public void remove() {throw new UnsupportedOperationException();}
			};
		}
		return new BitIterator_OLD();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExtendedIntIterator descendingIntIterator() {
		if (isEmpty()) {
			return new ExtendedIntIterator() {
				@Override public void skipAllBefore(int element) {/*empty*/}
				@Override public boolean hasNext() {return false;}
				@Override public int next() {throw new NoSuchElementException();}
				@Override public void remove() {throw new UnsupportedOperationException();}
			};
		}
		return new ReverseBitIterator();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		reset();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int last() {
		if (isEmpty()) 
			throw new NoSuchElementException();
		return last;
	}

	/**
	 * Convert a given collection to a {@link ConciseSet} instance
	 */
	private ConciseSet convert(IntSet c) {
		if (c instanceof ConciseSet)
			return (ConciseSet) c;
		if (c == null)
			return empty();

		ConciseSet res = empty();
		ExtendedIntIterator itr = c.intIterator();
		while (itr.hasNext()) 
			res.add(itr.next());
		return res;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet convert(int... a) {
		ConciseSet res = empty();
		if (a != null) {
			a = Arrays.copyOf(a, a.length);
			Arrays.sort(a);
			for (int i : a)
				if (res.last != i)
					res.add(i);
		}
		return res;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public IntSet convert(Collection<Integer> c) {
		ConciseSet res = empty();
		Collection<Integer> sorted;
		if (c != null) {
			if (c instanceof SortedSet<?> && ((SortedSet<?>) c).comparator() == null) {
				sorted = c;
			} else {
				sorted = new ArrayList<Integer>(c);
				Collections.sort((List<Integer>) sorted);
			}
			for (int i : sorted)
				res.add(i);
		}
		return res;
	}

	/**
	 * Replace the current instance with another {@link ConciseSet} instance. It
	 * also returns <code>true</code> if the given set is actually different
	 * from the current one
	 * 
	 * @param other
	 *            {@link ConciseSet} instance to use to replace the current one
	 * @return <code>true</code> if the given set is different from the current
	 *         set
	 */
	private boolean replaceWith(ConciseSet other) {
		if (this == other)
			return false;
		
		boolean isSimilar = (this.lastWordIndex == other.lastWordIndex)
			&& (this.last == other.last);
		for (int i = 0; isSimilar && (i <= lastWordIndex); i++)
			isSimilar &= this.words[i] == other.words[i]; 

		if (isSimilar) {
			if (other.size >= 0)
				this.size = other.size;
			return false;
		}
		
		this.words = other.words;
		this.size = other.size;
		this.last = other.last;
		this.lastWordIndex = other.lastWordIndex;
		this.modCount++;
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean add(int e) {
		modCount++;

		// range check
		if (e < MIN_ALLOWED_SET_BIT || e > MAX_ALLOWED_INTEGER)
			throw new IndexOutOfBoundsException(String.valueOf(e));

		// the element can be simply appended
		if (e > last) {
			append(e);
			return true;
		}

		if (e == last)
			return false;

		// check if the element can be put in a literal word
		int blockIndex = maxLiteralLengthDivision(e);
		int bitPosition = maxLiteralLengthModulus(e);
		for (int i = 0; i <= lastWordIndex && blockIndex >= 0; i++) {
			int w = words[i];
			if (isLiteral(w)) {
				// check if the current literal word is the "right" one
				if (blockIndex == 0) {
					// bit already set
					if ((w & (1 << bitPosition)) != 0)
						return false;
					
					// By adding the bit we potentially create a sequence:
					// -- If the literal is made up of all zeros, it definitely
					//    cannot be part of a sequence (otherwise it would not have
					//    been created). Thus, we can create a 1-bit literal word
					// -- If there are MAX_LITERAL_LENGHT - 2 set bits, by adding 
					//    the new one we potentially allow for a 1's sequence 
					//    together with the successive word
					// -- If there are MAX_LITERAL_LENGHT - 1 set bits, by adding 
					//    the new one we potentially allow for a 1's sequence 
					//    together with the successive and/or the preceding words
					if (!simulateWAH) {
						int bitCount = getLiteralBitCount(w);
						if (bitCount >= MAX_LITERAL_LENGHT - 2)
							break;
					} else {
						if (containsOnlyOneBit(~w) || w == ALL_ONES_LITERAL)
							break;
					}
						
					// set the bit
					words[i] |= 1 << bitPosition;
					if (size >= 0)
						size++;
					return true;
				} 
				
				blockIndex--;
			} else {
				if (simulateWAH) {
					if (isOneSequence(w) && blockIndex <= getSequenceCount(w))
						return false;
				} else {
					// if we are at the beginning of a sequence, and it is
					// a set bit, the bit already exists
					if (blockIndex == 0 
							&& (getLiteral(w) & (1 << bitPosition)) != 0)
						return false;
					
					// if we are in the middle of a sequence of 1's, the bit already exist
					if (blockIndex > 0 
							&& blockIndex <= getSequenceCount(w) 
							&& isOneSequence(w))
						return false;
				}

				// next word
				blockIndex -= getSequenceCount(w) + 1;
			}
		}
		
		// the bit is in the middle of a sequence or it may cause a literal to
		// become a sequence, thus the "easiest" way to add it is by ORing
		return replaceWith(performOperation(convert(e), Operator.OR));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove(int o) {
		modCount++;

		if (isEmpty())
			return false;

		// the element cannot exist
		if (o > last) 
			return false;
		
		// check if the element can be removed from a literal word
		int blockIndex = maxLiteralLengthDivision(o);
		int bitPosition = maxLiteralLengthModulus(o);
		for (int i = 0; i <= lastWordIndex && blockIndex >= 0; i++) {
			final int w = words[i];
			if (isLiteral(w)) {
				// check if the current literal word is the "right" one
				if (blockIndex == 0) {
					// the bit is already unset
					if ((w & (1 << bitPosition)) == 0)
						return false;
					
					// By removing the bit we potentially create a sequence:
					// -- If the literal is made up of all ones, it definitely
					//    cannot be part of a sequence (otherwise it would not have
					//    been created). Thus, we can create a 30-bit literal word
					// -- If there are 2 set bits, by removing the specified
					//    one we potentially allow for a 1's sequence together with 
					//    the successive word
					// -- If there is 1 set bit, by removing the new one we 
					//    potentially allow for a 0's sequence 
					//    together with the successive and/or the preceding words
					if (!simulateWAH) {
						int bitCount = getLiteralBitCount(w);
						if (bitCount <= 2)
							break;
					} else {
						final int l = getLiteralBits(w);
						if (l == 0 || containsOnlyOneBit(l))
							break;
					}
						
					// unset the bit
					words[i] &= ~(1 << bitPosition);
					if (size >= 0)
						size--;
					
					// if the bit is the maximal element, update it
					if (o == last) {
						last -= maxLiteralLengthModulus(last) - (MAX_LITERAL_LENGHT 
								- Integer.numberOfLeadingZeros(getLiteralBits(words[i])));
					}
					return true;
				} 

				blockIndex--;
			} else {
				if (simulateWAH) {
					if (isZeroSequence(w) && blockIndex <= getSequenceCount(w))
						return false;
				} else {
					// if we are at the beginning of a sequence, and it is
					// an unset bit, the bit does not exist
					if (blockIndex == 0 
							&& (getLiteral(w) & (1 << bitPosition)) == 0)
						return false;
					
					// if we are in the middle of a sequence of 0's, the bit does not exist
					if (blockIndex > 0 
							&& blockIndex <= getSequenceCount(w) 
							&& isZeroSequence(w))
						return false;
				}
				
				// next word
				blockIndex -= getSequenceCount(w) + 1;
			}
		}
		
		// the bit is in the middle of a sequence or it may cause a literal to
		// become a sequence, thus the "easiest" way to remove it by ANDNOTing
		return replaceWith(performOperation(convert(o), Operator.ANDNOT));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(int o) {
		if (isEmpty() || o > last || o < 0)
			return false;

		// check if the element is within a literal word
		int block = maxLiteralLengthDivision(o);
		int bit = maxLiteralLengthModulus(o);
		for (int i = 0; i <= lastWordIndex; i++) {
			final int w = words[i];
			final int t = w & 0xC0000000; // the first two bits...
			switch (t) {
			case 0x80000000:	// LITERAL
			case 0xC0000000:	// LITERAL
				// check if the current literal word is the "right" one
				if (block == 0) 
					return (w & (1 << bit)) != 0;
				block--;
				break;
			case 0x00000000:	// ZERO SEQUENCE
				if (!simulateWAH)
					if (block == 0 && ((w >> 25) - 1) == bit)
						return true;
				block -= getSequenceCount(w) + 1;
				if (block < 0)
					return false;
				break;
			case 0x40000000:	// ONE SEQUENCE
				if (!simulateWAH)
					if (block == 0 && (0x0000001F & (w >> 25) - 1) == bit)
						return false;
				block -= getSequenceCount(w) + 1;
				if (block < 0)
					return true;
				break;
			}
		}
		
		// no more words
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	// TODO: Update according to performOperation!!!
	@Override
	public boolean containsAll(IntSet c) {
		if (c == null || c.isEmpty() || c == this)
			return true;
		if (isEmpty())
			return false;

		final ConciseSet other = convert(c);
		if (other.last > last)
			return false;
		if (size >= 0 && other.size > size)
			return false;
		if (other.size == 1) 
			return contains(other.last);

		// scan "this" and "other"
		WordIterator_OLD thisItr = this.new WordIterator_OLD();
		WordIterator_OLD otherItr = other.new WordIterator_OLD();
		while (!thisItr.endOfWords() && !otherItr.endOfWords()) {
			// check shared elements between the two sets
			int curRes = thisItr.currentLiteral & otherItr.currentLiteral;
			
			// check if this set does not completely contains the other set
			if (otherItr.currentLiteral != curRes)
				return false;

			// Avoid loops when both are sequence and the result is a sequence
			if (curRes == ALL_ZEROS_WITHOUT_MSB || curRes == ALL_ONES_WITHOUT_MSB) 
				skipSequence(thisItr, otherItr);

			// next literals
			thisItr.prepareNextLiteral();
			otherItr.prepareNextLiteral();
		}

		// the intersection is equal to the other set
		return otherItr.endOfWords();
	}

	/**
	 * {@inheritDoc}
	 */
	// TODO: Update according to performOperation!!!
	@Override
	public boolean containsAny(IntSet c) {
		if (c == null || c.isEmpty() || c == this)
			return true;
		if (isEmpty())
			return false;
		
		final ConciseSet other = convert(c);
		if (other.size == 1)
			return contains(other.last);

		// disjoint sets
		if (isSequenceWithNoBits(this.words[0]) 
				&& maxLiteralLengthMultiplication(getSequenceCount(this.words[0]) + 1) > other.last) {
			if (isZeroSequence(this.words[0]))
				return false;
			return true;
		}
		if (isSequenceWithNoBits(other.words[0]) 
				&& maxLiteralLengthMultiplication(getSequenceCount(other.words[0]) + 1) > this.last) {
			if (isZeroSequence(other.words[0]))
				return false;
			return true;
		}

		// scan "this" and "other"
		WordIterator_OLD thisItr = this.new WordIterator_OLD();
		WordIterator_OLD otherItr = other.new WordIterator_OLD();
		while (!thisItr.endOfWords() && !otherItr.endOfWords()) {
			// check shared elements between the two sets
			int curRes = thisItr.currentLiteral & otherItr.currentLiteral;
			
			// check if this set contains some bit of the other set
			if (curRes != ALL_ZEROS_LITERAL)
				return true;

			// Avoid loops when both are sequence and the result is a sequence
			skipSequence(thisItr, otherItr);

			// next literals
			thisItr.prepareNextLiteral();
			otherItr.prepareNextLiteral();
		}

		// the intersection is equal to the empty set
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	// TODO: Update according to performOperation!!!
	@Override
	public boolean containsAtLeast(IntSet c, int minElements) {
		if (minElements < 1)
			throw new IllegalArgumentException();
		if ((size >= 0 && size < minElements) || c == null || c.isEmpty() || isEmpty())
			return false;
		if (this == c)
			return size() >= minElements;

		// convert the other set in order to perform a more complex intersection
		ConciseSet other = convert(c);
		if (other.size >= 0 && other.size < minElements)
			return false;
		if (minElements == 1 && other.size == 1)
			return contains(other.last);
		if (minElements == 1 && size == 1)
			return other.contains(last);
		
		// disjoint sets
		if (isSequenceWithNoBits(this.words[0]) 
				&& maxLiteralLengthMultiplication(getSequenceCount(this.words[0]) + 1) > other.last) {
			if (isZeroSequence(this.words[0]))
				return false;
			return true;
		}
		if (isSequenceWithNoBits(other.words[0]) 
				&& maxLiteralLengthMultiplication(getSequenceCount(other.words[0]) + 1) > this.last) {
			if (isZeroSequence(other.words[0]))
				return false;
			return true;
		}

		// resulting size
		int res = 0;

		// scan "this" and "other"
		WordIterator_OLD thisItr = this.new WordIterator_OLD();
		WordIterator_OLD otherItr = other.new WordIterator_OLD();
		while (!thisItr.endOfWords() && !otherItr.endOfWords()) {
			// perform the operation
			int curRes = getLiteralBitCount(thisItr.currentLiteral & otherItr.currentLiteral);
			res += curRes;
			if (res >= minElements)
				return true;

			// Avoid loops when both are sequence and the result is a sequence
			if (curRes == ALL_ZEROS_WITHOUT_MSB || curRes == ALL_ONES_WITHOUT_MSB) 
				res += curRes * skipSequence(thisItr, otherItr);

			// next literals
			thisItr.prepareNextLiteral();
			otherItr.prepareNextLiteral();
		}

		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {
		return words == null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean retainAll(IntSet c) {
		modCount++;

		if (isEmpty() || c == this)
			return false;
		if (c == null || c.isEmpty()) {
			clear();
			return true;
		}
		
		ConciseSet other = convert(c);
		if (other.size == 1) {
			if (contains(other.last)) {
				if (size == 1) 
					return false;
				return replaceWith(convert(other.last));
			} 
			clear();
			return true;
		}
		
		return replaceWith(performOperation(other, Operator.AND));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addAll(IntSet c) {
		modCount++;
		if (c == null || c.isEmpty() || this == c)
			return false;

		ConciseSet other = convert(c);
		if (other.size == 1) 
			return add(other.last);
		
		return replaceWith(performOperation(convert(c), Operator.OR));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean removeAll(IntSet c) {
		modCount++;

		if (c == null || c.isEmpty() || isEmpty())
			return false;
		if (c == this) {
			clear();
			return true;
		}

		ConciseSet other = convert(c);
		if (other.size == 1) 
			return remove(other.last);
		
		return replaceWith(performOperation(convert(c), Operator.ANDNOT));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		if (size < 0) {
			size = 0;
			for (int i = 0; i <= lastWordIndex; i++) {
				int w = words[i];
				if (isLiteral(w)) {
					size += getLiteralBitCount(w);
				} else {
					if (isZeroSequence(w)) {
						if (!isSequenceWithNoBits(w))
							size++;
					} else {
						size += maxLiteralLengthMultiplication(getSequenceCount(w) + 1);
						if (!isSequenceWithNoBits(w))
							size--;
					}
				}
			}
		}
		return size;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConciseSet empty() {
		return new ConciseSet(simulateWAH);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
        int h = 1;
        for (int i = 0; i <= lastWordIndex; i++) 
            h = (h << 5) - h + words[i];
        return h;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof ConciseSet))
			return false;
		
		final ConciseSet other = (ConciseSet) obj;
		if (last != other.last)
			return false;
		if (!isEmpty())
	        for (int i = 0; i <= lastWordIndex; i++)
	            if (words[i] != other.words[i])
	                return false;
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int compareTo(IntSet o) {
		// empty set cases
		if (this.isEmpty() && o.isEmpty())
			return 0;
		if (this.isEmpty())
			return -1;
		if (o.isEmpty())
			return 1;
		
		final ConciseSet other = convert(o);
		
		// the word at the end must be the same
		int res = this.last - other.last;
		if (res != 0)
			return res < 0 ? -1 : 1;
		
		// scan words from MSB to LSB
		ReverseWordIterator_OLD thisIterator = this.new ReverseWordIterator_OLD();
		ReverseWordIterator_OLD otherIterator = other.new ReverseWordIterator_OLD();
		while (!thisIterator.endOfWords() && !otherIterator.endOfWords()) {
			// compare current literals
			res = getLiteralBits(thisIterator.currentLiteral) - getLiteralBits(otherIterator.currentLiteral);
			if (res != 0)
				return res < 0 ? -1 : 1;
			
			// avoid loops when both are sequences and the result is a sequence
			skipSequence(thisIterator, otherIterator);

			// next literals
			thisIterator.prepareNextLiteral();
			otherIterator.prepareNextLiteral();
		}
		return thisIterator.hasMoreLiterals() ? 1 : (otherIterator.hasMoreLiterals() ? -1 : 0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear(int from, int to) {
		ConciseSet toRemove = empty();
		toRemove.fill(from, to);
		this.removeAll(toRemove);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void fill(int from, int to) {
		ConciseSet toAdd = empty();
		toAdd.add(to);
		toAdd.complement();
		toAdd.add(to);

		ConciseSet toRemove = empty();
		toRemove.add(from);
		toRemove.complement();
		
		toAdd.removeAll(toRemove);
		
		this.addAll(toAdd);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flip(int e) {
		if (!add(e))
			remove(e);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public double bitmapCompressionRatio() {
		if (isEmpty())
			return 0D;
		return (lastWordIndex + 1) / Math.ceil((1 + last) / 32D);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public double collectionCompressionRatio() {
		if (isEmpty())
			return 0D;
		return (double) (lastWordIndex + 1) / size();
	}

	/*
	 * DEBUGGING METHODS
	 */
	
	/**
	 * Generates the 32-bit binary representation of a given word (debug only)
	 * 
	 * @param word
	 *            word to represent
	 * @return 32-character string that represents the given word
	 */
	private static String toBinaryString(int word) {
		String lsb = Integer.toBinaryString(word);
		StringBuilder pad = new StringBuilder();
		for (int i = lsb.length(); i < 32; i++) 
			pad.append('0');
		return pad.append(lsb).toString();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String debugInfo() {
		final StringBuilder s = new StringBuilder("INTERNAL REPRESENTATION:\n");
		final Formatter f = new Formatter(s, Locale.ENGLISH);

		if (isEmpty())
			return s.append("null\n").toString();
		
		f.format("Elements: %s\n", toString());
		
		// elements
		int firstBitInWord = 0;
		for (int i = 0; i <= lastWordIndex; i++) {
			// raw representation of words[i]
			f.format("words[%d] = ", i);
			String ws = toBinaryString(words[i]);
			if (isLiteral(words[i])) {
				s.append(ws.substring(0, 1));
				s.append("--");
				s.append(ws.substring(1));
			} else {
				s.append(ws.substring(0, 2));
				s.append('-');
				if (simulateWAH)
					s.append("xxxxx");
				else
					s.append(ws.substring(2, 7));
				s.append('-');
				s.append(ws.substring(7));
			}
			s.append(" --> ");

			// decode words[i]
			if (isLiteral(words[i])) {
				// literal
				s.append("literal: ");
				s.append(toBinaryString(words[i]).substring(1));
				f.format(" ---> [from %d to %d] ", firstBitInWord, firstBitInWord + MAX_LITERAL_LENGHT - 1);
				firstBitInWord += MAX_LITERAL_LENGHT;
			} else {
				// sequence
				if (isOneSequence(words[i])) {
					s.append('1');
				} else {
					s.append('0');
				}
				s.append(" block: ");
				s.append(toBinaryString(getLiteralBits(getLiteral(words[i]))).substring(1));
				if (!simulateWAH) {
					s.append(" (bit=");
					int bit = (words[i] & 0x3E000000) >>> 25;
					if (bit == 0) 
						s.append("none");
					else 
						s.append(String.format("%4d", bit - 1));
					s.append(')');
				}
				int count = getSequenceCount(words[i]);
				f.format(" followed by %d blocks (%d bits)", 
						getSequenceCount(words[i]),
						maxLiteralLengthMultiplication(count));
				f.format(" ---> [from %d to %d] ", firstBitInWord, firstBitInWord + (count + 1) * MAX_LITERAL_LENGHT - 1);
				firstBitInWord += (count + 1) * MAX_LITERAL_LENGHT;
			}
			s.append('\n');
		}
		
		// object attributes
		f.format("simulateWAH: %b\n", simulateWAH);
		f.format("last: %d\n", last);
		f.format("size: %s\n", (size == -1 ? "invalid" : Integer.toString(size)));
		f.format("words.length: %d\n", words.length);
		f.format("lastWordIndex: %d\n", lastWordIndex);

		// compression
		f.format("bitmap compression: %.2f%%\n", 100D * bitmapCompressionRatio());
		f.format("collection compression: %.2f%%\n", 100D * collectionCompressionRatio());

		return s.toString();
	}

	/**
	 * Save the state of the instance to a stream 
	 */
    private void writeObject(ObjectOutputStream s) throws IOException {
    	if (words != null && lastWordIndex < words.length - 1)
    		words = Arrays.copyOf(words, lastWordIndex + 1);
    	s.defaultWriteObject();
    }

	/**
	 * Reconstruct the instance from a stream 
	 */
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		lastWordIndex = words.length - 1;
		updateLast();
		size = -1;
    }
}
