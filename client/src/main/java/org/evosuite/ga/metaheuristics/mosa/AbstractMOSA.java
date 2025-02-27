/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.metaheuristics.mosa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.evosuite.ProgressMonitor;
import org.evosuite.Properties;
import org.evosuite.Properties.SelectionFunction;
import org.evosuite.coverage.FitnessFunctions;
import org.evosuite.coverage.branch.BranchCoverageGoal;
import org.evosuite.coverage.branch.BranchCoverageTestFitness;
import org.evosuite.coverage.branch.BranchFitness;
import org.evosuite.coverage.exception.ExceptionCoverageSuiteFitness;
import org.evosuite.coverage.fbranch.FBranchTestFitness;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.archive.Archive;
import org.evosuite.ga.comparators.DominanceComparator;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.metaheuristics.RuntimeRecord;
import org.evosuite.ga.metaheuristics.SearchListener;
import org.evosuite.ga.operators.mutation.MutationHistory;
import org.evosuite.graphs.cfg.BytecodeInstruction;
import org.evosuite.result.BranchInfo;
import org.evosuite.result.seedexpr.BranchCoveringEvent;
import org.evosuite.result.seedexpr.EventSequence;
import org.evosuite.seeding.smart.SensitivityMutator;
import org.evosuite.seeding.smart.SmartSeedBranchUpdateManager;
import org.evosuite.testcase.MutationPositionDiscriminator;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.TestMutationHistoryEntry;
import org.evosuite.testcase.TestMutationHistoryEntry.TestMutation;
import org.evosuite.testcase.factories.RandomLengthTestFactory;
import org.evosuite.testcase.secondaryobjectives.TestCaseSecondaryObjective;
import org.evosuite.testcase.statements.ArrayStatement;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.PrimitiveStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.statements.StringPrimitiveStatement;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;
import org.evosuite.utils.ArrayUtil;
import org.evosuite.utils.BudgetConsumptionMonitor;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;
import org.objectweb.asm.tree.MethodInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.bytecode.Opcode;

/**
 * Abstract class for MOSA or variants of MOSA.
 * 
 * @author Annibale Panichella, Fitsum M. Kifetew
 */
public abstract class AbstractMOSA<T extends Chromosome> extends GeneticAlgorithm<T> {

	private static final long serialVersionUID = 146182080947267628L;

	private static final Logger logger = LoggerFactory.getLogger(AbstractMOSA.class);

	/**
	 * Keep track of overall suite fitness functions and correspondent test fitness
	 * functions
	 */
	protected final Map<TestSuiteFitnessFunction, Class<?>> suiteFitnessFunctions;

	/**
	 * Object used to keep track of the execution time needed to reach the maximum
	 * coverage
	 */
	protected final BudgetConsumptionMonitor budgetMonitor;

	/**
	 * the coverage after initializing the coverage
	 */
	protected double initialCoverage = 0;

	/**
	 * the time to initialization the population
	 */
	public static long initializationOverhead = 0;

	/** EvoSeed Runtime Branch Type **/

	public static int smartBranchNum = 0;
	public static Map<String,String> runtimeBranchType = new HashMap<String,String>();

	/**
	 * Constructor.
	 * 
	 * @param factory a {@link org.evosuite.ga.ChromosomeFactory} object
	 */
	public AbstractMOSA(ChromosomeFactory<T> factory) {
		super(factory);

		this.suiteFitnessFunctions = new LinkedHashMap<TestSuiteFitnessFunction, Class<?>>();
		for (Properties.Criterion criterion : Properties.CRITERION) {
			TestSuiteFitnessFunction suiteFit = FitnessFunctions.getFitnessFunction(criterion);
			Class<?> testFit = FitnessFunctions.getTestFitnessFunctionClass(criterion);
			this.suiteFitnessFunctions.put(suiteFit, testFit);
		}

		this.budgetMonitor = new BudgetConsumptionMonitor();

		// set the secondary objectives of test cases (useful when MOSA compares two
		// test
		// cases to, for example, update the archive)
		TestCaseSecondaryObjective.setSecondaryObjectives();

		if (Properties.SELECTION_FUNCTION != SelectionFunction.RANK_CROWD_DISTANCE_TOURNAMENT) {
			LoggingUtils.getEvoLogger()
					.warn("Originally, MOSA was implemented with a '"
							+ SelectionFunction.RANK_CROWD_DISTANCE_TOURNAMENT.name()
							+ "' selection function. You may want to consider using it.");
		}
	}

	@SuppressWarnings("rawtypes")
	protected Map<FitnessFunction, Double> bestMap = new HashMap<>();
	@SuppressWarnings("rawtypes")
	protected Map<FitnessFunction, TestChromosome> bestTestMap = new HashMap<>();
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void checkBestFitness() {

		Set<FitnessFunction<T>> set = getUncoveredGoals();

		if (Properties.PRINT_FITNESS) {
			bestMap.clear();
			bestTestMap.clear();
			
			for (T t : this.population) {
				if (t instanceof TestChromosome) {
					for (FitnessFunction ff : set) {
						Double fitness = ff.getFitness(t);
						Double bestSoFar = bestMap.get(ff);
						if (bestSoFar == null) {
							bestSoFar = fitness;
							bestTestMap.put(ff, (TestChromosome)t);
						} else if (bestSoFar > fitness) {
							bestSoFar = fitness;
							bestTestMap.put(ff, (TestChromosome)t);
						}
						bestMap.put(ff, bestSoFar);
					}
				}
			}

			System.out.println(this.currentIteration + "th iteration ========================");
			for (FitnessFunction ff : bestMap.keySet()) {
				Double fitness = bestMap.get(ff);
				TestChromosome t = bestTestMap.get(ff);
				ff.getFitness(t);
				if(fitness < 2) {
					System.out.print(ff + ":");
					System.out.println(fitness);
					System.currentTimeMillis();					
				}
			}
			
		}

	}

	/**
	 * This method is used to generate new individuals (offsprings) from the current
	 * population.
	 * 
	 * @return offspring population
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected List<T> breedNextGeneration() {

		EventSequence.enableRecord();

		TestChromosome potentialSeed = SmartSeedBranchUpdateManager.updateUncoveredBranchInfo(bestMap, bestTestMap);
		
		List<T> offspringPopulation = new ArrayList<T>(Properties.POPULATION);
		// we apply only Properties.POPULATION/2 iterations since in each generation
		// we generate two offsprings
		for (int i = 0; i < Properties.POPULATION / 2 && !this.isFinished(); i++) {

			// select best individuals
			T parent1 = this.selectionFunction.select(this.population);
			T parent2 = this.selectionFunction.select(this.population);

			this.removeUnusedVariables(parent1);
			this.removeUnusedVariables(parent2);

			if (Properties.APPLY_GRADEINT_ANALYSIS) {
				try {
					SensitivityMutator.testSensitity(parent1.getFitnessValues().keySet());
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ConstructionFailedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			T offspring1 = (T) parent1.clone();
			T offspring2 = (T) parent2.clone();

			// apply crossover
//			try {
//				if (Randomness.nextDouble() <= Properties.CROSSOVER_RATE) {
//					this.crossoverFunction.crossOver(offspring1, offspring2);
//				}
//			} catch (ConstructionFailedException e) {
//				logger.debug("CrossOver failed.");
//				continue;
//			}

			offspring1.updateAge(this.currentIteration);
			parent1.updateAge(this.currentIteration);
			offspring2.updateAge(this.currentIteration);
			parent2.updateAge(this.currentIteration);

			// apply mutation on offspring1
			this.mutate(offspring1, parent1);
			Set<?> uncoveredGoals = getUncoveredGoals();
			if (offspring1.isChanged()) {

				this.clearCachedResults(offspring1);
				this.calculateFitness(offspring1);

				BranchCoveringEvent e = EventSequence.deriveCoveredBranch(offspring1, parent1, uncoveredGoals);
				EventSequence.addEvent(e);

//				BranchDynamicAnalyzer.analyzeBranch(offspring1, parent1, uncoveredGoals);

				new MutationPositionDiscriminator().identifyRelevantMutations(offspring1, parent1);
				offspringPopulation.add(offspring1);
			}

			this.mutate(offspring2, parent2);
			if(i == 0 && potentialSeed != null) {
				offspring2 = (T) potentialSeed;
				offspring2.setChanged(true);
			}
			if (offspring2.isChanged()) {
				this.clearCachedResults(offspring2);
				this.calculateFitness(offspring2);

				BranchCoveringEvent e = EventSequence.deriveCoveredBranch(offspring2, parent2, uncoveredGoals);
				EventSequence.addEvent(e);

				new MutationPositionDiscriminator().identifyRelevantMutations(offspring2, parent2);
				offspringPopulation.add(offspring2);
			}
		}

		EventSequence.disableRecord();

		// Add new randomly generate tests
		long randomTestcaseTime = System.currentTimeMillis();
		for (int i = 0; i < Properties.POPULATION * Properties.P_TEST_INSERTION; i++) {
			T tch = null;
			if (this.getCoveredGoals().size() == 0 || Randomness.nextBoolean()) {
				RandomLengthTestFactory.workingBranch4ObjectGraph = null;
				FitnessFunction<?> ff = Randomness.choice(getUncoveredGoals());
				if (ff != null && ff instanceof BranchFitness) {
					RandomLengthTestFactory.workingBranch4ObjectGraph = ((BranchFitness) ff).getBranchGoal()
							.getBranch();
				}

				tch = this.chromosomeFactory.getChromosome();
				tch.setChanged(true);
			} else {
				tch = (T) Randomness.choice(this.getSolutions()).clone();
				tch.mutate();
				tch.mutate(); // TODO why is it mutated twice?
			}
			if (tch.isChanged()) {
				tch.updateAge(this.currentIteration);
				this.calculateFitness(tch);
				offspringPopulation.add(tch);
			}
		}

		logger.info("Number of offsprings = {}", offspringPopulation.size());
		
		return offspringPopulation;
	}

	/**
	 * Method used to mutate an offspring.
	 * 
	 * @param offspring
	 * @param parent
	 */
	private void mutate(T offspring, T parent) {
		TestChromosome tch = (TestChromosome) offspring;
		tch.clearMutationHistory();
		offspring.mutate();

		if (!offspring.isChanged()) {
			// if offspring is not changed, we try to mutate it once again
			offspring.mutate();
		}

		/**
		 * update the changing information of statement
		 */
		boolean isHistoryContainChange = isHistoryContainChange(tch.getMutationHistory());
		if (isHistoryContainChange) {
			List<Integer> changedPositions = tch.getChangedPositionsInOldTest();
			TestCase parentTestCase = ((TestChromosome) parent).getTestCase();
			for (int position = 0; position < parentTestCase.size(); position++) {
				parentTestCase.getStatement(position).setChanged(false);
			}

			for (Integer position : changedPositions) {
				if (position < parentTestCase.size()) {
					parentTestCase.getStatement(position).setChanged(true);
				}
			}
		}

		if (!this.hasMethodCall(offspring)) {
			tch.setTestCase(((TestChromosome) parent).getTestCase().clone());
			boolean changed = tch.mutationInsert();
			if (changed) {
				for (Statement s : tch.getTestCase()) {
					s.isValid();
				}
			}
			offspring.setChanged(changed);
		}
		this.notifyMutation(offspring);
	}

	private boolean isHistoryContainChange(MutationHistory<TestMutationHistoryEntry> mutationHistory) {
		for (TestMutationHistoryEntry entry : mutationHistory) {
			if (entry.getMutationType() == TestMutation.CHANGE) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This method checks whether the test has only primitive type statements.
	 * Indeed, crossover and mutation can lead to tests with no method calls
	 * (methods or constructors call), thus, when executed they will never cover
	 * something in the class under test.
	 * 
	 * @param test to check
	 * @return true if the test has at least one method or constructor call (i.e.,
	 *         the test may cover something when executed; false otherwise
	 */
	private boolean hasMethodCall(T test) {
		boolean flag = false;
		TestCase tc = ((TestChromosome) test).getTestCase();
		for (Statement s : tc) {
			if (s instanceof MethodStatement) {
				MethodStatement ms = (MethodStatement) s;
				boolean isTargetMethod = ms.getDeclaringClassName().equals(Properties.TARGET_CLASS);
				if (isTargetMethod) {
					return true;
				}
			}
			if (s instanceof ConstructorStatement) {
				ConstructorStatement ms = (ConstructorStatement) s;
				boolean isTargetMethod = ms.getDeclaringClassName().equals(Properties.TARGET_CLASS);
				if (isTargetMethod) {
					return true;
				}
			}
		}
		return flag;
	}

	/**
	 * This method clears the cached results for a specific chromosome (e.g.,
	 * fitness function values computed in previous generations). Since a test case
	 * is changed via crossover and/or mutation, previous data must be recomputed.
	 * 
	 * @param chromosome TestChromosome to clean
	 */
	private void clearCachedResults(T chromosome) {
		((TestChromosome) chromosome).clearCachedMutationResults();
		((TestChromosome) chromosome).clearCachedResults();
		((TestChromosome) chromosome).clearMutationHistory();
		((TestChromosome) chromosome).getFitnessValues().clear();
	}

	/**
	 * When a test case is changed via crossover and/or mutation, it can contains
	 * some primitive variables that are not used as input (or to store the output)
	 * of method calls. Thus, this method removes all these "trash" statements.
	 * 
	 * @param chromosome
	 * @return true or false depending on whether "unused variables" are removed
	 */
	private boolean removeUnusedVariables(T chromosome) {
		int sizeBefore = chromosome.size();
		TestCase t = ((TestChromosome) chromosome).getTestCase();
		List<Integer> to_delete = new ArrayList<Integer>(chromosome.size());
		boolean has_deleted = false;

		int num = 0;
		for (Statement s : t) {
			VariableReference var = s.getReturnValue();
			boolean delete = false;
			delete = delete || s instanceof PrimitiveStatement;
			delete = delete || s instanceof ArrayStatement;
			delete = delete || s instanceof StringPrimitiveStatement;
			if (!t.hasReferences(var) && delete) {
				to_delete.add(num);
				has_deleted = true;
			}
			num++;
		}
		Collections.sort(to_delete, Collections.reverseOrder());
		for (Integer position : to_delete) {
			t.remove(position);
		}
		int sizeAfter = chromosome.size();
		if (has_deleted) {
			logger.debug("Removed {} unused statements", (sizeBefore - sizeAfter));
		}
		return has_deleted;
	}

	/**
	 * This method extracts non-dominated solutions (tests) according to all covered
	 * goal (e.g., branches).
	 * 
	 * @param solutions list of test cases to analyze with the "dominance"
	 *                  relationship
	 * @return the non-dominated set of test cases
	 */
	private List<T> getNonDominatedSolutions(List<T> solutions) {
		DominanceComparator<T> comparator = new DominanceComparator<T>(this.getCoveredGoals());
		List<T> next_front = new ArrayList<T>(solutions.size());
		boolean isDominated;
		for (T p : solutions) {
			isDominated = false;
			List<T> dominatedSolutions = new ArrayList<T>(solutions.size());
			for (T best : next_front) {
				int flag = comparator.compare(p, best);
				if (flag == -1) {
					dominatedSolutions.add(best);
				}
				if (flag == +1) {
					isDominated = true;
				}
			}
			if (isDominated) {
				continue;
			}

			next_front.add(p);
			next_front.removeAll(dominatedSolutions);
		}
		return next_front;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void initializePopulation() {
		logger.info("executing initializePopulation function");

		this.notifySearchStarted();
		this.currentIteration = 0;

		// Create a random parent population P0
		this.generateInitialPopulation(Properties.POPULATION);

		// Determine fitness
		this.calculateFitness(true);
		this.notifyIteration();
	}

	/**
	 * Returns the goals that have been covered by the test cases stored in the
	 * archive.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Set<FitnessFunction<T>> getCoveredGoals() {
		Set<FitnessFunction<T>> coveredGoals = new LinkedHashSet<FitnessFunction<T>>();
		Archive.getArchiveInstance().getCoveredTargets().forEach(ff -> coveredGoals.add((FitnessFunction<T>) ff));
		return coveredGoals;
	}

	/**
	 * Returns the number of goals that have been covered by the test cases stored
	 * in the archive.
	 * 
	 * @return
	 */
	protected int getNumberOfCoveredGoals() {
		return Archive.getArchiveInstance().getNumberOfCoveredTargets();
	}

	protected void addUncoveredGoal(FitnessFunction<T> goal) {
		Archive.getArchiveInstance().addTarget((TestFitnessFunction) goal);
	}

	/**
	 * Returns the goals that have not been covered by the test cases stored in the
	 * archive.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Set<FitnessFunction<T>> getUncoveredGoals() {
		Set<FitnessFunction<T>> uncoveredGoals = new LinkedHashSet<FitnessFunction<T>>();
		Archive.getArchiveInstance().getUncoveredTargets().forEach(ff -> uncoveredGoals.add((FitnessFunction<T>) ff));
		return uncoveredGoals;
	}

	/**
	 * Returns the goals that have not been covered by the test cases stored in the
	 * archive.
	 * 
	 * @return
	 */
	protected int getNumberOfUncoveredGoals() {
		return Archive.getArchiveInstance().getNumberOfUncoveredTargets();
	}

	/**
	 * Returns the overall goals.
	 * 
	 * @return
	 */
	protected Set<FitnessFunction<T>> getTotalGoals() {
		Set<FitnessFunction<T>> totalGoals = new LinkedHashSet<FitnessFunction<T>>();
		totalGoals.addAll(getCoveredGoals());
		totalGoals.addAll(getUncoveredGoals());
		return totalGoals;
	}

	/**
	 * Returns the total number of goals, i.e., number of covered goals + number of
	 * uncovered goals.
	 * 
	 * @return
	 */
	protected int getTotalNumberOfGoals() {
		return Archive.getArchiveInstance().getNumberOfTargets();
	}

	/**
	 * Return the test cases in the archive as a list.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected List<T> getSolutions() {
		List<T> solutions = new ArrayList<T>();
		Archive.getArchiveInstance().getSolutions().forEach(test -> solutions.add((T) test));
		return solutions;
	}

	/**
	 * Generates a {@link org.evosuite.testsuite.TestSuiteChromosome} object with
	 * all test cases in the archive.
	 * 
	 * @return
	 */
	protected TestSuiteChromosome generateSuite() {
		TestSuiteChromosome suite = new TestSuiteChromosome();
		Archive.getArchiveInstance().getSolutions().forEach(test -> suite.addTest(test));
		return suite;
	}

	///// ----------------------

	/**
	 * Some methods of the super class (i.e.,
	 * {@link org.evosuite.ga.metaheuristics.GeneticAlgorithm} class) require a
	 * {@link org.evosuite.testsuite.TestSuiteChromosome} object. However, MOSA
	 * evolves {@link org.evosuite.testsuite.TestChromosome} objects. Therefore, we
	 * must override those methods and create a
	 * {@link org.evosuite.testsuite.TestSuiteChromosome} object with all the
	 * evolved {@link org.evosuite.testsuite.TestChromosome} objects (either in the
	 * population or in the {@link org.evosuite.ga.archive.Archive).
	 */

	/**
	 * Notify all search listeners but ProgressMonitor of fitness evaluation.
	 * 
	 * @param chromosome a {@link org.evosuite.ga.Chromosome} object.
	 */
	@Override
	protected void notifyEvaluation(Chromosome chromosome) {
		for (SearchListener listener : this.listeners) {
			if (listener instanceof ProgressMonitor) {
				continue; // ProgressMonitor requires a TestSuiteChromosome
			}
			listener.fitnessEvaluation(chromosome);
		}
	}

	/**
	 * Notify all search listeners but ProgressMonitor of a mutation.
	 * 
	 * @param chromosome a {@link org.evosuite.ga.Chromosome} object.
	 */
	@Override
	protected void notifyMutation(Chromosome chromosome) {
		for (SearchListener listener : this.listeners) {
			if (listener instanceof ProgressMonitor) {
				continue; // ProgressMonitor requires a TestSuiteChromosome
			}
			listener.modification(chromosome);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void calculateFitness(T c) {
		this.fitnessFunctions.forEach(fitnessFunction -> fitnessFunction.getFitness(c));

		// if one of the coverage criterion is Criterion.EXCEPTION, then we have to
		// analyse the results
		// of the execution to look for generated exceptions
		if (ArrayUtil.contains(Properties.CRITERION, Properties.Criterion.EXCEPTION)) {
			TestChromosome testChromosome = (TestChromosome) c;
			ExceptionCoverageSuiteFitness.calculateExceptionInfo(Arrays.asList(testChromosome.getLastExecutionResult()),
					new HashMap<>(), new HashMap<>(), new HashMap<>(), new ExceptionCoverageSuiteFitness());
		}

		this.notifyEvaluation(c);
		// update the time needed to reach the max coverage
		this.budgetMonitor.checkMaxCoverage(this.getNumberOfCoveredGoals());
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<T> getBestIndividuals() {
		// get final test suite (i.e., non dominated solutions in Archive)
		TestSuiteChromosome bestTestCases = Archive.getArchiveInstance()
				.mergeArchiveAndSolution(new TestSuiteChromosome());
		if (bestTestCases.getTestChromosomes().isEmpty()) {
			for (T test : this.getNonDominatedSolutions(this.population)) {
				bestTestCases.addTest((TestChromosome) test);
			}
		}

		// compute overall fitness and coverage
		this.computeCoverageAndFitness(bestTestCases);

		List<T> bests = new ArrayList<T>(1);
		bests.add((T) bestTestCases);

		return bests;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * <p>
	 * This method is used by the Progress Monitor at the and of each generation to
	 * show the total coverage reached by the algorithm. Since the Progress Monitor
	 * requires a {@link org.evosuite.testsuite.TestSuiteChromosome} object, this
	 * method artificially creates a
	 * {@link org.evosuite.testsuite.TestSuiteChromosome} object as the union of all
	 * solutions stored in the {@link org.evosuite.ga.archive.Archive}.
	 * </p>
	 * 
	 * <p>
	 * The coverage score of the {@link org.evosuite.testsuite.TestSuiteChromosome}
	 * object is given by the percentage of targets marked as covered in the
	 * archive.
	 * </p>
	 * 
	 * @return a {@link org.evosuite.testsuite.TestSuiteChromosome} object to be
	 *         consumable by the Progress Monitor.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T getBestIndividual() {
		TestSuiteChromosome best = this.generateSuite();
		if (best.getTestChromosomes().isEmpty()) {
			for (T test : this.getNonDominatedSolutions(this.population)) {
				best.addTest((TestChromosome) test);
			}
			for (TestSuiteFitnessFunction suiteFitness : this.suiteFitnessFunctions.keySet()) {
				best.setCoverage(suiteFitness, 0.0);
				best.setFitness(suiteFitness, 1.0);
			}
			best.setAge(this.currentIteration);
			return (T) best;
		}

		// compute overall fitness and coverage
		this.computeCoverageAndFitness(best);

		best.setAge(this.currentIteration);
		best.setProgressInfomation(getProgressInformation());
		best.setUncoveredBranchDistribution(this.getUncoveredBranchDistribution());
		best.setDistribution(this.getDistribution());
		best.setDistributionMap(this.getDistributionMap());

		Map<FitnessFunction<T>, String> IPFlags = findIPFlagBranches();
		Map<FitnessFunction<T>, String> uncoveredIPFlags = findUncoveredIPFlags(IPFlags);

		best.setUncoveredIPFlags(toIPFlagString(uncoveredIPFlags));
		best.setMethodCallAvailabilityMap(RuntimeRecord.methodCallAvailabilityMap);
		best.setInitialCoverage(this.initialCoverage);
		best.setInitializationOverhead(this.initializationOverhead);

		List<BranchInfo> missingBranches = findMissingBranches();
		best.setMissingBranches(missingBranches);

		Map<BranchInfo, String> coveredBranchWithTest = findCoveredBranches();
		best.setCoveredBranchWithTest(coveredBranchWithTest);

		double IPFlagCoverage = 0;
		if (IPFlags.size() != 0)
			IPFlagCoverage = (double) uncoveredIPFlags.size() / IPFlags.size();

		best.setIPFlagCoverage(1 - IPFlagCoverage);

		return (T) best;
	}

	private List<BranchInfo> findMissingBranches() {
		List<BranchInfo> list = new ArrayList<BranchInfo>();
		for (FitnessFunction<T> ff : getUncoveredGoals()) {
			if (ff instanceof BranchFitness) {
				BranchFitness bf = (BranchFitness) ff;
				BranchCoverageGoal goal = bf.getBranchGoal();
				BranchInfo branchInfo = new BranchInfo(goal.getBranch(), goal.getValue());
				list.add(branchInfo);
			}

		}
		return list;
	}

	private Map<BranchInfo, String> findCoveredBranches() {
		Map<BranchInfo, String> map = new HashMap<BranchInfo, String>();

		for (TestFitnessFunction tff : Archive.getArchiveInstance().getCoveredTargets()) {
			if (tff instanceof BranchFitness) {
				BranchFitness bf = (BranchFitness) tff;
				BranchCoverageGoal goal = bf.getBranchGoal();
				BranchInfo branchInfo = new BranchInfo(goal.getBranch(), goal.getValue());

				TestChromosome test = Archive.getArchiveInstance().getSolution(tff);
				map.put(branchInfo, test.toString());
			}
		}

		return map;
	}

	protected void computeCoverageAndFitness(TestSuiteChromosome suite) {
		for (Entry<TestSuiteFitnessFunction, Class<?>> entry : this.suiteFitnessFunctions.entrySet()) {
			TestSuiteFitnessFunction suiteFitnessFunction = entry.getKey();
			Class<?> testFitnessFunction = entry.getValue();

			int numberCoveredTargets = Archive.getArchiveInstance().getNumberOfCoveredTargets(testFitnessFunction);
			int numberUncoveredTargets = Archive.getArchiveInstance().getNumberOfUncoveredTargets(testFitnessFunction);
			int totalNumberTargets = numberCoveredTargets + numberUncoveredTargets;

			double coverage = totalNumberTargets == 0 ? 1.0
					: ((double) numberCoveredTargets) / ((double) totalNumberTargets);

			suite.setFitness(suiteFitnessFunction, ((double) numberUncoveredTargets));
			suite.setCoverage(suiteFitnessFunction, coverage);
			suite.setNumOfCoveredGoals(suiteFitnessFunction, numberCoveredTargets);
			suite.setNumOfNotCoveredGoals(suiteFitnessFunction, numberUncoveredTargets);
		}
	}

	private String toIPFlagString(Map<FitnessFunction<T>, String> uncoveredIPFlags) {
		StringBuffer buffer = new StringBuffer();
		for (FitnessFunction<T> ff : uncoveredIPFlags.keySet()) {
			BranchCoverageGoal goal = transformBranchCoverage(ff);
			buffer.append(goal + "~" + uncoveredIPFlags.get(ff) + "\n");
		}
		return buffer.toString();
	}

	private Map<FitnessFunction<T>, String> findUncoveredIPFlags(Map<FitnessFunction<T>, String> iPFlags) {
		Map<FitnessFunction<T>, String> uncoveredIPFlags = new HashMap<>();
		for (FitnessFunction<T> goal : iPFlags.keySet()) {
			if (getUncoveredGoals().contains(goal)) {
				uncoveredIPFlags.put(goal, iPFlags.get(goal));
			}
		}
		return uncoveredIPFlags;
	}

	private String getCalledInterproceduralFlagMethod(BranchCoverageGoal goal) {
		String methodSig = null;

		BytecodeInstruction instruction = goal.getBranch().getInstruction();

		BytecodeInstruction interproceduralFlagCall = instruction.getSourceOfStackInstruction(0);
//		boolean isInterproceduralFlag = false;
		if (interproceduralFlagCall != null && interproceduralFlagCall.getASMNode() instanceof MethodInsnNode) {
			MethodInsnNode mNode = (MethodInsnNode) interproceduralFlagCall.getASMNode();
			String desc = mNode.desc;
			String returnType = getReturnType(desc);
			boolean isInterproceduralFlag = returnType.equals("Z");

			if (isInterproceduralFlag) {
				methodSig = interproceduralFlagCall.getClassName() + "." + interproceduralFlagCall.getCalledMethod();
				System.currentTimeMillis();
			}
		}

		return methodSig;
	}

	private BranchCoverageGoal transformBranchCoverage(FitnessFunction<T> fitnessFunction) {
		BranchCoverageGoal goal = null;
		if (fitnessFunction instanceof FBranchTestFitness) {
			goal = ((FBranchTestFitness) fitnessFunction).getBranchGoal();
		} else if (fitnessFunction instanceof BranchCoverageTestFitness) {
			goal = ((BranchCoverageTestFitness) fitnessFunction).getBranchGoal();
		}

		return goal;
	}

	private Map<FitnessFunction<T>, String> findIPFlagBranches() {
		Map<FitnessFunction<T>, String> IPFlagBranches = new HashMap<>();
		for (FitnessFunction<T> fitnessFunction : fitnessFunctions) {
			BranchCoverageGoal goal = transformBranchCoverage(fitnessFunction);
			int opcode = goal.getBranch().getInstruction().getASMNode().getOpcode();
			if (opcode == Opcode.JSR || opcode == Opcode.JSR_W) {
				continue;
			}

			String methodString = getCalledInterproceduralFlagMethod(goal);
			if (goal != null && methodString != null) {
				IPFlagBranches.put(fitnessFunction, methodString);
			}

		}

		return IPFlagBranches;
	}

	private String getReturnType(String signature) {
		String r = signature.substring(signature.indexOf(")") + 1);
		return r;
	}


	public static void clear() {
		runtimeBranchType = new HashMap<String,String>();
		smartBranchNum = 0;
	}


	public Map<String,String> getRuntimeBranchType() {
		return runtimeBranchType;
	}

	public void setRuntimeBranchType(Map<String,String> runtimeBranchType) {
		this.runtimeBranchType = runtimeBranchType;
	}
	
	public int getSmartBranchNum() {
		return smartBranchNum;
	}

	public void setSmartBranchNum(int smartBranchNum) {
		this.smartBranchNum = smartBranchNum;
	}

}
