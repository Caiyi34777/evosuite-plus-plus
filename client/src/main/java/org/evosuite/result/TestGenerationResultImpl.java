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
package org.evosuite.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.evosuite.BranchDistributionInformation;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.result.seedexpr.Event;
import org.evosuite.result.seedexpr.EventSequence;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;

class TestGenerationResultImpl implements TestGenerationResult {

	private List<Event> eventSequence = new ArrayList<Event>();
	
	private List<BranchInfo> missingBranches = new ArrayList<BranchInfo>();
	
	private Map<BranchInfo, String> coveredBranchWithTest = new HashMap<BranchInfo, String>();
	
	private double initialCoverage = 0;
	
	private long initializationOverhead = 0;
	
	private int elapseTime = 0;
	
	private double coverage = 0;
	
	private List<Double> progressInformation;
	
	private int[] distribution;
	
	private double availabilityRatio;
	
	private List<String> availableCalls = new ArrayList<>();
	
	private List<String> unavailableCalls = new ArrayList<>();
	
	private static final long serialVersionUID = 1306033906557741929L;

	private Status status = Status.ERROR;
	
	private String errorMessage = "";
	
	private Map<String, Set<Failure>> contractViolations = new LinkedHashMap<String, Set<Failure>>();
	
	private Map<String, TestCase> testCases = new LinkedHashMap<String, TestCase>();
	
	private Map<String, String> testCode = new LinkedHashMap<String, String>();
	
	private Map<String, Set<Integer>> testLineCoverage = new LinkedHashMap<String, Set<Integer>>();

	private Map<String, Set<BranchInfo>> testBranchCoverage = new LinkedHashMap<String, Set<BranchInfo>>();

	private Map<String, Set<MutationInfo>> testMutantCoverage = new LinkedHashMap<String, Set<MutationInfo>>();

	private Set<Integer> coveredLines = new LinkedHashSet<Integer>();

	private Set<Integer> uncoveredLines = new LinkedHashSet<Integer>();

	private Set<BranchInfo> coveredBranches = new LinkedHashSet<BranchInfo>();

	private Set<BranchInfo> uncoveredBranches = new LinkedHashSet<BranchInfo>();

	private Set<MutationInfo> coveredMutants = new LinkedHashSet<MutationInfo>();

	private Set<MutationInfo> uncoveredMutants = new LinkedHashSet<MutationInfo>();
	
	private Set<MutationInfo> exceptionMutants = new LinkedHashSet<MutationInfo>();

	private Map<String, String> testComments = new LinkedHashMap<String, String>();
	
	private String testSuiteCode = "";
	
	private String targetClass = "";
	
	//private String targetCriterion = "";
	private String[] targetCriterion;

    private LinkedHashMap<FitnessFunction<?>, Double> targetCoverages = new LinkedHashMap<FitnessFunction<?>, Double>();
	
	private GeneticAlgorithm<?> ga = null;

	private int age;
	
	private double IPFlagCoverage;
	
	private String uncoveredIPFlags;
	
	private Map<Integer, Double> uncoveredBranchDistribution;
	
	private Map<Integer, Integer> distributionMap;
	
	private long randomSeed;
	
	private List<BranchDistributionInformation> branchInformation;
	
	private Map<String, Boolean> methodCallAvailabilityMap = new HashMap<>();
	
	private ExceptionResult<TestChromosome> exceptionResult = new ExceptionResult<TestChromosome>();
	
	public ExceptionResult<TestChromosome> getExceptionResult() {
		return this.exceptionResult;
	}
	
	public void setExceptionResult(ExceptionResult<TestChromosome> exceptionResult) {
		if (exceptionResult == null) {
			System.out.println("Detected an attempt to set exception result to a null value.");
			return;
		}
		
		this.exceptionResult = exceptionResult;
	}
	
	
	/** Did test generation succeed? */
	public Status getTestGenerationStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status = status;
	}
	
	/** If there was an error, this contains the error message */
	public String getErrorMessage() {
		return errorMessage;
	}
	
	public void setErrorMessage(String errorMessage) {
		status = Status.ERROR;
		this.errorMessage = errorMessage;
	}
	
	/** The entire GA in its final state */
	public GeneticAlgorithm<?> getGeneticAlgorithm() {
		return ga;
	}
	
	public void setGeneticAlgorithm(GeneticAlgorithm<?> ga) {
		this.ga = ga;
	}
	
	/** Map from test method to ContractViolation */
	public Set<Failure> getContractViolations(String name)  {
		return contractViolations.get(name);
	}
	
	public void setContractViolations(String name, Set<Failure> violations) {
		contractViolations.put(name, violations);
	}
	
	public void setClassUnderTest(String targetClass) {
		this.targetClass = targetClass;
	}
	
	@Override
	public String getClassUnderTest() {
		return targetClass;
	}
	
	public void setTargetCoverage(FitnessFunction<?> function, double coverage) {
        this.targetCoverages.put(function, coverage);
	}
	
	public double getTargetCoverage(FitnessFunction<?> function) {
		return this.targetCoverages.containsKey(function) ? this.targetCoverages.get(function) : 0.0;
	}
	
	@Override
	public String[] getTargetCriterion() {
		return targetCriterion;
	}
	
	public void setTargetCriterion(String[] criterion) {
		this.targetCriterion = criterion;
	}
	
	/** Map from test method to EvoSuite test case */
	public TestCase getTestCase(String name) {
		return testCases.get(name);
	}
	
	public void setTestCase(String name, TestCase test) {
		testCases.put(name,  test);
	}

	/** Map from test method to EvoSuite test case */
	public String getTestCode(String name) {
		return testCode.get(name);
	}
	
	public void setTestCode(String name, String code) {
		testCode.put(name, code);
	}

	/** JUnit test suite source code */
	public String getTestSuiteCode() {
		return testSuiteCode;
	}
	
	public void setTestSuiteCode(String code) {
		this.testSuiteCode = code;
	}

	/** Lines covered by final test suite */ 
	public Set<Integer> getCoveredLines() {
		return coveredLines;
	}
	
	public void setCoveredLines(String name, Set<Integer> covered) {
		testLineCoverage.put(name, covered);
		coveredLines.addAll(covered);
	}

	public void setCoveredBranches(String name, Set<BranchInfo> covered) {
		testBranchCoverage.put(name, covered);
		coveredBranches.addAll(covered);
	}

	public void setCoveredMutants(String name, Set<MutationInfo> covered) {
		testMutantCoverage.put(name, covered);
		coveredMutants.addAll(covered);
	}

	@Override
	public String getComment(String name) {
		return testComments.get(name);
	}
	
	public void setComment(String name, String comment) {
		testComments.put(name, comment);
	}

	@Override
	public Set<Integer> getCoveredLines(String name) {
		return testLineCoverage.get(name);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for(String testName : testCases.keySet()) {
			builder.append("Test "+testName+": \n");
			builder.append(" "+testLineCoverage.get(testName));
			builder.append("\n");
			builder.append(" "+testBranchCoverage.get(testName));
			builder.append("\n");
			builder.append(" "+testMutantCoverage.get(testName));
			builder.append("\n");
		}
		builder.append("Uncovered lines: ");
		builder.append(uncoveredLines.toString());
		builder.append("\n");
		builder.append("Uncovered branches: ");
		builder.append(uncoveredBranches.toString());
		builder.append("\n");
		builder.append("Uncovered mutants: "+uncoveredMutants.size());
		builder.append("\n");
		builder.append("Covered mutants: "+coveredMutants.size());
		builder.append("\n");
		builder.append("Timeout mutants: "+exceptionMutants.size());
		builder.append("\n");
		builder.append("Failures: "+contractViolations);
		builder.append("\n");
		return builder.toString();
	}

	@Override
	public Set<BranchInfo> getCoveredBranches(String name) {
		return testBranchCoverage.get(name);
	}

	@Override
	public Set<MutationInfo> getCoveredMutants(String name) {
		return testMutantCoverage.get(name);
	}

	@Override
	public Set<BranchInfo> getCoveredBranches() {
		return coveredBranches;
	}

	@Override
	public Set<MutationInfo> getCoveredMutants() {
		return coveredMutants;
	}

	@Override
	public Set<Integer> getUncoveredLines() {
		return uncoveredLines;
	}
	
	public void setUncoveredLines(Set<Integer> lines) {
		uncoveredLines.addAll(lines);
	}

	@Override
	public Set<BranchInfo> getUncoveredBranches() {
		return uncoveredBranches;
	}

	public void setUncoveredBranches(Set<BranchInfo> branches) {
		uncoveredBranches.addAll(branches);
	}

	@Override
	public Set<MutationInfo> getUncoveredMutants() {
		return uncoveredMutants;
	}
	
	@Override
	public Set<MutationInfo> getExceptionMutants() {
		return exceptionMutants;
	}

	public void setExceptionMutants(Set<MutationInfo> mutants) {
		exceptionMutants.addAll(mutants);
	}

	public void setUncoveredMutants(Set<MutationInfo> mutants) {
		uncoveredMutants.addAll(mutants);
	}

	public int getElapseTime() {
		return elapseTime;
	}

	public void setElapseTime(int elapseTime) {
		this.elapseTime = elapseTime;
	}

	public double getCoverage() {
		return coverage;
	}

	public void setCoverage(double coverage) {
		this.coverage = coverage;
	}

	@Override
	public List<Double> getProgressInformation() {
		return progressInformation;
	}

	@Override
	public void setProgressInformation(List<Double> progressInformation) {
		this.progressInformation = progressInformation;
	}

	public int[] getDistribution() {
		return distribution;
	}

	public void setDistribution(int[] distribution) {
		this.distribution = distribution;
	}

	public double getAvailabilityRatio() {
		return availabilityRatio;
	}

	public void setAvailabilityRatio(double availabilityRatio) {
		this.availabilityRatio = availabilityRatio;
	}

	public List<String> getAvailableCalls() {
		return availableCalls;
	}

	public void setAvailableCalls(List<String> availableCalls) {
		this.availableCalls = availableCalls;
	}

	public List<String> getUnavailableCalls() {
		return unavailableCalls;
	}

	public void setUnavailableCalls(List<String> unavailableCalls) {
		this.unavailableCalls = unavailableCalls;
	}

	@Override
	public int getAge() {
		return this.age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	@Override
	public void setIPFlagCoverage(double IPFlagCoverage) {
		this.IPFlagCoverage = IPFlagCoverage;
	}

	@Override
	public double getIPFlagCoverage() {
		return this.IPFlagCoverage;
	}

	@Override
	public void setUncoveredIPFlags(String uncoveredIPFlags) {
		this.uncoveredIPFlags = uncoveredIPFlags;
	}

	@Override
	public String getUncoveredIPFlags() {
		return this.uncoveredIPFlags;
	}

	public Map<Integer, Double> getUncoveredBranchDistribution() {
		return uncoveredBranchDistribution;
	}

	public void setUncoveredBranchDistribution(Map<Integer, Double> uncoveredBranchDistribution) {
		this.uncoveredBranchDistribution = uncoveredBranchDistribution;
	}
	
	public Map<Integer, Integer> getDistributionMap(){
		return distributionMap;
	}
	
	public void setDistributionMap(Map<Integer, Integer> distributionMap) {
		this.distributionMap = distributionMap;
	}

	@Override
	public long getRandomSeed() {
		return this.randomSeed;
	}

	@Override
	public void setRandomSeed(long randomSeed) {
		this.randomSeed = randomSeed;
	}
	
	public List<BranchDistributionInformation> getBranchInformation() {
		return branchInformation;
	}

	public void setBranchInformation(List<BranchDistributionInformation> branchInformation) {
		this.branchInformation = branchInformation;
	}

	public Map<String, Boolean> getMethodCallAvailabilityMap() {
		return methodCallAvailabilityMap;
	}

	public void setMethodCallAvailabilityMap(Map<String, Boolean> methodCallAvailabilityMap) {
		this.methodCallAvailabilityMap = methodCallAvailabilityMap;
	}

	public double getInitialCoverage() {
		return initialCoverage;
	}

	public void setInitialCoverage(double initialCoverage) {
		this.initialCoverage = initialCoverage;
	}

	public long getInitializationOverhead() {
		return initializationOverhead;
	}

	public void setInitializationOverhead(long initializationOverhead) {
		this.initializationOverhead = initializationOverhead;
	}

	public List<BranchInfo> getMissingBranches() {
		return missingBranches;
	}

	public void setMissingBranches(List<BranchInfo> missingBranches) {
		this.missingBranches = missingBranches;
	}

	public Map<BranchInfo, String> getCoveredBranchWithTest() {
		return coveredBranchWithTest;
	}

	public void setCoveredBranchWithTest(Map<BranchInfo, String> coveredBranchWithTest) {
		this.coveredBranchWithTest = coveredBranchWithTest;
	}

	@Override
	public List<Event> getEventSequence() {
		return this.eventSequence;
	}
	
	@Override
	public void setEventSequence(List<Event> eventList) {
		this.eventSequence = eventList;
	}
	
}
