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


package it.uniroma3.mat.extendedset.test;

import it.uniroma3.mat.extendedset.intset.ArraySet;
import it.uniroma3.mat.extendedset.intset.ConciseSet;
import it.uniroma3.mat.extendedset.intset.FastSet;
import it.uniroma3.mat.extendedset.intset.development.Concise2Set;
import it.uniroma3.mat.extendedset.intset.development.Concise3Set;
import it.uniroma3.mat.extendedset.others.GenericExtendedSet;
import it.uniroma3.mat.extendedset.wrappers.IntegerSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Class for performance evaluation.
 * 
 * @author Alessandro Colantonio
 * @version $Id$
 */
public class Performance {
	/* test classes */
	private static class WAHSet extends ConciseSet {
		private static final long serialVersionUID = -5048707825606872979L;
		WAHSet() {super(true);}
	}
	private static class IntegerArraySet extends IntegerSet {IntegerArraySet() {super(new ArraySet());}}
//	private static class IntegerHashSet extends IntegerSet {IntegerHashSet() {super(new HashIntSet());}}
	private static class IntegerFastSet extends IntegerSet {IntegerFastSet() {super(new FastSet());}}
//	private static class IntegerConciseSet extends IntegerSet {IntegerConciseSet() {super(new ConciseSet());}}
//	private static class IntegerConcisePlusSet extends IntegerSet {IntegerConcisePlusSet() {super(new ConcisePlusSet());}}
//	private static class IntegerConcise2Set extends IntegerSet {IntegerConcise2Set() {super(new Concise2Set());}}
	private static class IntegerConcise3Set extends IntegerSet {IntegerConcise3Set() {super(new Concise3Set());}}
//	private static class IntegerWAHSet extends IntegerSet {IntegerWAHSet() {super(new WAHSet());}}

	/** 
	 * Class to test the sorted array
	 */
	@SuppressWarnings("unused")
	private static class ArrayListSet extends GenericExtendedSet<Integer> {
		ArrayListSet() {
			super(ArrayList.class, GenericExtendedSet.ALL_POSITIVE_INTEGERS);
		}
	}

	/** 
	 * Class to test the sorted linked lists
	 */
	@SuppressWarnings("unused")
	private static class LinkedListSet extends GenericExtendedSet<Integer> {
		LinkedListSet() {
			super(LinkedList.class, GenericExtendedSet.ALL_POSITIVE_INTEGERS);
		}
	}

	/** number of times to repeat each test */
	private final static int REPETITIONS = 10;

	/** minimum element */
	private final static int SHIFT = 1000;

	/** time measurement, in nanoseconds */
	private static long lastExecTime = -1;

	/** test results */
	private final static Map<String, Map<Class<?>, Double>> TIME_VALUES = new TreeMap<String, Map<Class<?>, Double>>(); 
	
	/**
	 * Start time measurement
	 */
	private static void startTimer() {
		lastExecTime = System.nanoTime();
	}
	
	/**
	 * Stop time measurement
	 * 
	 * @param c
	 *            class being tested
	 * @param name
	 *            method name
	 * @param div
	 *            division factor (elapsed time and allocated memory will be
	 *            divided by this number)
	 */
	private static void endTimer(Class<?> c, String name, long div) {
		// final time
		double t = ((double) (System.nanoTime() - lastExecTime)) / div;
		Map<Class<?>, Double> measure = TIME_VALUES.get(name);
		if (measure == null)
			TIME_VALUES.put(name, measure = new HashMap<Class<?>, Double>());
		measure.put(c, t);	
	}

	/**
	 * Perform the time test
	 * @param classToTest
	 *            class of the {@link Collection} instance to test
	 * @param leftOperand
	 *            collection of integers representing the left operand
	 *            {@link Collection}
	 * @param rightOperand
	 *            collection of integers representing the right operand
	 *            {@link Collection}
	 */
	@SuppressWarnings("unchecked")
	private static void testClass(
			Class<?> classToTest, 
			Collection<Integer> leftOperand, 
			Collection<Integer> rightOperand) {
		// collections used for the test cases
		Collection<Integer>[] cAddAndRemove = new Collection[REPETITIONS];
		Collection<Integer>[] cAddAll = new Collection[REPETITIONS];
		Collection<Integer>[] cRemoveAll = new Collection[REPETITIONS];
		Collection<Integer>[] cRetainAll = new Collection[REPETITIONS];
		Collection<Integer>[] cRighOperand = new Collection[REPETITIONS];
		IntegerSet[] cLeftOperand = new IntegerSet[REPETITIONS];
		IntegerSet[] cUnionResults = new IntegerSet[REPETITIONS];
		IntegerSet[] cDifferenceResults = new IntegerSet[REPETITIONS];
		IntegerSet[] cIntersectionResults = new IntegerSet[REPETITIONS];

		// CREATION
		for (int i = 0; i < REPETITIONS; i++) {
			try {
				cAddAndRemove[i] = (Collection) classToTest.newInstance();
				cAddAll[i] = (Collection) classToTest.newInstance();
				cRemoveAll[i] = (Collection) classToTest.newInstance();
				cRetainAll[i] = (Collection) classToTest.newInstance();
				cRighOperand[i] = (Collection) classToTest.newInstance(); 
				cLeftOperand[i] = (IntegerSet) classToTest.newInstance(); 
			} catch (Exception e) {
				throw new RuntimeException(e);
			} 
		}
		
		// APPEND/ADDITION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			for (Integer x : rightOperand)
				cRighOperand[i].add(x);
			for (Integer x : leftOperand) {
				cAddAndRemove[i].add(x);
				cLeftOperand[i].add(x);
				cAddAll[i].add(x);
				cRetainAll[i].add(x);
				cRemoveAll[i].add(x);
			}
		}
		endTimer(classToTest, "01) add()", REPETITIONS * (4 * leftOperand.size() + rightOperand.size()));

		// REMOVAL
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			for (Integer x : rightOperand)
				cAddAndRemove[i].remove(x);
		}
		endTimer(classToTest, "02) remove()", rightOperand.size() * REPETITIONS);
		
		// CONTAINS
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			for (Integer x : rightOperand)
				cAddAll[i].contains(x);
		}
		endTimer(classToTest, "03) contains()", rightOperand.size() * REPETITIONS);
		
		// AND SIZE
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cAddAll[i].containsAll(cRighOperand[i]);
		}
		endTimer(classToTest, "04) containsAll()", REPETITIONS);
		
		// UNION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cAddAll[i].addAll(cRighOperand[i]);
		}
		endTimer(classToTest, "05) addAll()", REPETITIONS);
		
		// DIFFERENCE
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cRemoveAll[i].removeAll(cRighOperand[i]);
		}
		endTimer(classToTest, "06) removeAll()", REPETITIONS);
		
		// INTERSECTION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cRetainAll[i].retainAll(cRighOperand[i]);
		}
		endTimer(classToTest, "07) retainAll()", REPETITIONS);

		// UNION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cUnionResults[i] = cLeftOperand[i].union(cRighOperand[i]);
		}
		endTimer(classToTest, "08) union()", REPETITIONS);
		
		// DIFFERENCE
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cDifferenceResults[i] = cLeftOperand[i].difference(cRighOperand[i]);
		}
		endTimer(classToTest, "09) difference()", REPETITIONS);
		
		// INTERSECTION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cIntersectionResults[i] = cLeftOperand[i].intersection(cRighOperand[i]);
		}
		endTimer(classToTest, "10) intersection()", REPETITIONS);
	}
	
	/**
	 * Summary information
	 */
	private static void printSummary(int cardinality, double density, Class<?>[] classes) {
		for (Entry<String, Map<Class<?>, Double>> e : TIME_VALUES.entrySet()) {
			// method name
			System.out.format(Locale.ENGLISH, "%7d\t%.4f\t", cardinality, density);
			System.out.print(e.getKey());
			for (Class<?> c : classes) {
				Double op = e.getValue().get(c);
				System.out.format("\t%12d", (op == null ? 0 : op.intValue()));
			}
			System.out.println();
		}
	}

	/**
	 * TEST
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		boolean calcMemory = false;
		boolean calcTime = true;
		
		boolean calcUniform = true;
		boolean calcMarkovian = false;
		boolean calcZipfian = false;
		
		int minCardinality = 10000;
		int maxCardinality = 10000;
		
		/*
		 * MEMORY
		 */
		for (int i = 0; calcMemory && i < 3; i++) {
			System.out.println();
			switch (i) {
			case 0:
				if (!calcUniform)
					continue;
				System.out.println("#MEMORY UNIFORM");
				break;
			case 1:
				if (!calcMarkovian)
					continue;
				System.out.println("#MEMORY MARKOVIAN");
				break;
			case 2:
				if (!calcZipfian)
					continue;
				System.out.println("#MEMORY ZIPFIAN");
				break;
			default:
				throw new RuntimeException("unexpected");
			}
			System.out.println("#cardinality\tdensity\tFastSet\tConciseSet\tWAHSet\tConcise2Set");
			for (int cardinality = minCardinality; cardinality <= maxCardinality; cardinality *= 10) {
				for (double density = .0001; density < 1D; density *= 1.7) {
					System.out.format(Locale.ENGLISH, "%7d\t%.4f\t", cardinality, density);
					
					Collection<Integer> integers;
					switch (i) {
					case 0:
						integers = new RandomNumbers.Uniform(cardinality, density, SHIFT).generate();
						break;
					case 1:
						integers = new RandomNumbers.Markovian(cardinality, density, SHIFT).generate();
						break;
					case 2:
						integers = new RandomNumbers.Zipfian(cardinality, density, SHIFT, 2).generate();
						break;
					default:
						throw new RuntimeException("unexpected");
					}
					
					IntegerSet s0 = new IntegerSet(new FastSet());
					s0.addAll(integers);
					System.out.format("%7d\t", (int) (s0.collectionCompressionRatio() * cardinality));
					
					IntegerSet s1 = new IntegerSet(new ConciseSet());
					s1.addAll(integers);
					System.out.format("%7d\t", (int) (s1.collectionCompressionRatio() * cardinality));
					
					IntegerSet s2 = new IntegerSet(new WAHSet());
					s2.addAll(integers);
					System.out.format("%7d\t", (int) (s2.collectionCompressionRatio() * cardinality));

					IntegerSet s3 = new IntegerSet(new Concise2Set());
					s3.addAll(integers);
					System.out.format("%7d\n", (int) (s3.collectionCompressionRatio() * cardinality));
				}
			}
		}
		
		Class<?>[] classes = new Class[] { 
//				ArrayList.class, 
//				LinkedList.class,
//				ArrayListSet.class, 
//				LinkedListSet.class, 
//				HashSet.class,
//				TreeSet.class,
				IntegerArraySet.class, 
				IntegerFastSet.class, 
//				IntegerHashSet.class,
//				IntegerWAHSet.class, 
//				IntegerConciseSet.class,
//				IntegerConcisePlusSet.class,
//				IntegerConcise2Set.class,
				IntegerConcise3Set.class,
				};

		/*
		 * TIME
		 */
		for (int i = 0; calcTime && i < 3; i++) {
			System.out.println();
			switch (i) {
			case 0:
				if (!calcUniform)
					continue;
				System.out.println("#TIME UNIFORM");
				break;
			case 1:
				if (!calcMarkovian)
					continue;
				System.out.println("#TIME MARKOVIAN");
				break;
			case 2:
				if (!calcZipfian)
					continue;
				System.out.println("#TIME ZIPFIAN");
				break;
			default:
				throw new RuntimeException("unexpected");
			}
			System.out.print("#cardinality\tdensity\toperation");
			for (Class<?> c : classes) 
				System.out.print("\t" + c.getSimpleName());
			System.out.println();
			for (int cardinality = minCardinality; cardinality <= maxCardinality; cardinality *= 10) {
				RandomNumbers r;
				switch (i) {
				case 0:
					r = new RandomNumbers.Uniform(cardinality, 0.5, SHIFT);
					break;
				case 1:
					r = new RandomNumbers.Markovian(cardinality, 0.5, SHIFT);
					break;
				case 2:
					r = new RandomNumbers.Zipfian(cardinality, 0.5, SHIFT, 2);
					break;
				default:
					throw new RuntimeException("unexpected");
				}
				Collection<Integer> x = r.generate(), y = r.generate();
				for (Class<?> c : classes) { 
					testClass(c, x, y);
					testClass(c, x, y);
				}
				for (double density = .0001; density < 1D; density *= 1.7) {
//				for (double density = .0041; density < 1D; density *= 1.7) {
//				for (double density = 0.8272; density > 0.00005; density /= 1.7) {
					switch (i) {
					case 0:
						r = new RandomNumbers.Uniform(cardinality, density, SHIFT);
						break;
					case 1:
						r = new RandomNumbers.Markovian(cardinality, density, SHIFT);
						break;
					case 2:
						r = new RandomNumbers.Zipfian(cardinality, density, SHIFT, 2);
						break;
					default:
						throw new RuntimeException("unexpected");
					}
					x = r.generate(); 
					y = r.generate();
					for (Class<?> c : classes) {
						testClass(c, x, y);
						testClass(c, x, y);
					}
					printSummary(cardinality, density, classes);
				}
			}
		}

		System.out.println("\nDone!");
	}
}
