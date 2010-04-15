/* (c) 2010 Alessandro Colantonio
 * <mailto:colanton@mat.uniroma3.it>
 * <http://ricerca.mat.uniroma3.it/users/colanton>
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */ 

package it.uniroma3.mat.extendedset.test;

import it.uniroma3.mat.extendedset.ConcisePlusSet;
import it.uniroma3.mat.extendedset.ConciseSet;
import it.uniroma3.mat.extendedset.FastSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

/**
 * Class for performance evaluation.
 * <p>
 * This class has been used to produce the pictures in <a
 * href="http://arxiv.org/abs/1004.0403">http://arxiv.org/abs/1004.0403</a>.
 * 
 * @author Alessandro Colantonio
 * @version $Id$
 */
public class Performance {
	/** 
	 * Class to test the WAH algorithm 
	 */
	private static class WAHSet extends ConciseSet {
		@SuppressWarnings("unused")
		public WAHSet() {super(true);}
		public WAHSet(Collection<? extends Integer> c) {super(true, c);}
	}

	/** number of times to repeat each test */
	private final static int REPETITIONS = 5;

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

		// CREATION
		for (int i = 0; i < REPETITIONS; i++) {
			try {
				cAddAndRemove[i] = (Collection) classToTest.newInstance();
				cAddAll[i] = (Collection) classToTest.newInstance();
				cRemoveAll[i] = (Collection) classToTest.newInstance();
				cRetainAll[i] = (Collection) classToTest.newInstance();
				cRighOperand[i] = (Collection) classToTest.newInstance(); 
			} catch (Exception e) {
				throw new RuntimeException(e);
			} 
		}
		
		// ADDITION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			for (Integer x : leftOperand)
				cAddAndRemove[i].add(x);
			for (Integer x : rightOperand)
				cRighOperand[i].add(x);
			for (Integer x : leftOperand)
				cAddAll[i].add(x);
			for (Integer x : leftOperand)
				cRetainAll[i].add(x);
			for (Integer x : leftOperand)
				cRemoveAll[i].add(x);
		}
		endTimer(classToTest, "1) add()", REPETITIONS * (4 * leftOperand.size() + rightOperand.size()));

		// REMOVAL
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			for (Integer x : rightOperand)
				cAddAndRemove[i].remove(x);
		}
		endTimer(classToTest, "2) remove()", rightOperand.size() * REPETITIONS);
		
		// CONTAINS
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			for (Integer x : rightOperand)
				cAddAll[i].contains(x);
		}
		endTimer(classToTest, "3) contains()", rightOperand.size() * REPETITIONS);
		
//		// BINARY SEARCH FOR SORTED LISTS
//		if (classToTest.getSimpleName().endsWith("List")) {
//			startTimer();
//			for (int i = 0; i < REPETITIONS; i++) {
//				for (Integer x : rightOperand)
//					Collections.binarySearch((List) (cAddAll[i]), x);
//			}
//			endTimer(classToTest, "3) contains() - bin", rightOperand.size() * REPETITIONS);
//		}
		
		// AND SIZE
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cAddAll[i].containsAll(cRighOperand[i]);
		}
		endTimer(classToTest, "4) containsAll()", REPETITIONS);
		
		// UNION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cAddAll[i].addAll(cRighOperand[i]);
		}
		endTimer(classToTest, "5) addAll()", REPETITIONS);
		
		// DIFFERENCE
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cRemoveAll[i].removeAll(cRighOperand[i]);
		}
		endTimer(classToTest, "6) removeAll()", REPETITIONS);
		
		// INTERSECTION
		startTimer();
		for (int i = 0; i < REPETITIONS; i++) {
			cRetainAll[i].retainAll(cRighOperand[i]);
		}
		endTimer(classToTest, "7) retainAll()", REPETITIONS);
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
		boolean onlyBitmaps = true;

		boolean calcMemory = true;
		boolean calcTime = true;
		
		boolean calcUniform = true;
		boolean calcMarkovian = true;
		boolean calcZipfian = true;
		
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
			System.out.println("#cardinality\tdensity\tFastSet\tConciseSet\tWAHSet\tConcisePlusSet");
			for (int cardinality = 1000; cardinality <= maxCardinality; cardinality *= 10) {
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
						integers = new RandomNumbers.Zipfian(cardinality, density, SHIFT, 4).generate();
						break;
					default:
						throw new RuntimeException("unexpected");
					}
					
					FastSet s0 = new FastSet(integers);
					System.out.format("%7d\t", (int) (s0.collectionCompressionRatio() * cardinality));
					
					ConciseSet s1 = new ConciseSet(integers);
					System.out.format("%7d\t", (int) (s1.collectionCompressionRatio() * cardinality));
					
					WAHSet s2 = new WAHSet(integers);
					System.out.format("%7d\t", (int) (s2.collectionCompressionRatio() * cardinality));

					ConcisePlusSet s3 = new ConcisePlusSet(integers);
					System.out.format("%7d\n", (int) (s3.collectionCompressionRatio() * cardinality));
				}
			}
		}
		
		Class<?>[] classes;
		if (onlyBitmaps)
			classes = new Class[] { FastSet.class, ConciseSet.class,
					ConcisePlusSet.class };
		else
			classes = new Class[] { ArrayList.class, LinkedList.class,
					TreeSet.class, HashSet.class, FastSet.class,
					ConciseSet.class, WAHSet.class, ConcisePlusSet.class };

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
			for (int cardinality = 1000; cardinality <= maxCardinality; cardinality *= 10) {
				RandomNumbers r = new RandomNumbers.Uniform(cardinality, 0.5, SHIFT);
				Collection<Integer> x = r.generate(), y = r.generate();
				for (Class<?> c : classes) { 
					testClass(c, x, y);
					testClass(c, x, y);
				}
				for (double density = .0001; density < 1D; density *= 1.7) {
					r = new RandomNumbers.Uniform(cardinality, density, SHIFT);
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
