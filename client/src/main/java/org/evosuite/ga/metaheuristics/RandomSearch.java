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
/**
 * 
 */
package org.evosuite.ga.metaheuristics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * RandomSearch class.
 * </p>
 * 
 * @author Gordon Fraser
 */
public class RandomSearch<T extends Chromosome> extends GeneticAlgorithm<T> implements Hybridable{

	private static final Logger logger = LoggerFactory.getLogger(RandomSearch.class);

	/**
	 * <p>
	 * Constructor for RandomSearch.
	 * </p>
	 * 
	 * @param factory
	 *            a {@link org.evosuite.ga.ChromosomeFactory} object.
	 */
	public RandomSearch(ChromosomeFactory<T> factory) {
		super(factory);
	}

	private static final long serialVersionUID = -7685015421245920459L;

	/* (non-Javadoc)
	 * @see org.evosuite.ga.GeneticAlgorithm#evolve()
	 */
	/** {@inheritDoc} */
	@Override
	protected void evolve() {
		T newChromosome = chromosomeFactory.getChromosome();
		if(newChromosome.size() != 0) {
			getFitnessFunction().getFitness(newChromosome);
			notifyEvaluation(newChromosome);
			if (newChromosome.compareTo(getBestIndividual()) <= 0) {
				logger.info("New fitness: " + newChromosome.getFitness());
				population.set(0, newChromosome);
			}
		}
		currentIteration++;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<TestChromosome> getSeeds(){
		return (List<TestChromosome>) this.getPopulation();
	}

	/* (non-Javadoc)
	 * @see org.evosuite.ga.GeneticAlgorithm#initializePopulation()
	 */
	/** {@inheritDoc} */
	@Override
	public void initializePopulation() {
		generateRandomPopulation(1);
		calculateFitnessAndSortPopulation();
	}

	/* (non-Javadoc)
	 * @see org.evosuite.ga.GeneticAlgorithm#generateSolution()
	 */
	/** {@inheritDoc} */
	@Override
	public void generateSolution() {
		notifySearchStarted();
		if (population.isEmpty())
			initializePopulation();

		currentIteration = 0;
		while (!isFinished()) {
			evolve();
			this.notifyIteration();
		}
		updateBestIndividualFromArchive();
		notifySearchFinished();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void updatePopulation(TestSuiteChromosome previousSeeds) {
		List<TestChromosome> pop = (List<TestChromosome>) this.population;
		
		if(pop.size() + previousSeeds.getTestChromosomes().size() < Properties.POPULATION) {
			pop.addAll(previousSeeds.getTestChromosomes());			
		}
		else {
			List<TestChromosome> selected = new ArrayList<TestChromosome>();
			for(int i=0; i<10; i++) {
				selected.add(previousSeeds.getTestChromosome(i));
			}
			
			pop.addAll(previousSeeds.getTestChromosomes());	
			while(pop.size() > Properties.POPULATION) {
				pop.remove(0);
			}
		}
	}

	@Override
	public void generateSolution(TestSuiteChromosome previousSeeds) {
		notifySearchStarted();
		if (population.isEmpty()) {
			initializePopulation();
		}
		
//		System.currentTimeMillis();
		
		this.updatePopulation(previousSeeds);

//		currentIteration = 0;
//		while (!isFinished()) {
//			evolve();
//			this.notifyIteration();
//		}
//		updateBestIndividualFromArchive();
		notifySearchFinished();
		
	}

}
