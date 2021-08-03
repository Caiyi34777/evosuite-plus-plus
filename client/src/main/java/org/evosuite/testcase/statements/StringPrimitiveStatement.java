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
package org.evosuite.testcase.statements;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

import org.evosuite.Properties;
import org.evosuite.result.seedexpr.EventSequence;
import org.evosuite.result.seedexpr.RandomSamplingEvent;
import org.evosuite.result.seedexpr.SamplingDataType;
import org.evosuite.result.seedexpr.SearchEvent;
import org.evosuite.seeding.ConstantPool;
import org.evosuite.seeding.ConstantPoolManager;
import org.evosuite.seeding.smart.BranchSeedInfo;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.execution.CodeUnderTestException;
import org.evosuite.testcase.execution.Scope;
import org.evosuite.utils.Randomness;

/**
 * <p>
 * StringPrimitiveStatement class.
 * </p>
 * 
 * @author fraser
 */
public class StringPrimitiveStatement extends PrimitiveStatement<String> {

	private static final long serialVersionUID = 274445526699835887L;

	/**
	 * <p>
	 * Constructor for StringPrimitiveStatement.
	 * </p>
	 * 
	 * @param tc
	 *            a {@link org.evosuite.testcase.TestCase} object.
	 * @param value
	 *            a {@link java.lang.String} object.
	 */
	public StringPrimitiveStatement(TestCase tc, String value) {
		super(tc, String.class, value);
	}

	/**
	 * <p>
	 * Constructor for StringPrimitiveStatement.
	 * </p>
	 * 
	 * @param tc
	 *            a {@link org.evosuite.testcase.TestCase} object.
	 */
	public StringPrimitiveStatement(TestCase tc) {
		super(tc, String.class, "");
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.PrimitiveStatement#zero()
	 */
	/** {@inheritDoc} */
	@Override
	public void zero() {
		value = "";
	}

	private static String removeCharAt(String s, int pos) {
		return s.substring(0, pos) + s.substring(pos + 1);
	}

	private static String replaceCharAt(String s, int pos, char c) {
		return s.substring(0, pos) + c + s.substring(pos + 1);
	}

	private static String insertCharAt(String s, int pos, char c) {
		return s.substring(0, pos) + c + s.substring(pos);
	}

	private String StringInsert(String s, int pos) {
		final double ALPHA = 0.5;
		int count = 1;

		while (Randomness.nextDouble() <= Math.pow(ALPHA, count)
		        && s.length() < Properties.STRING_LENGTH) {
			count++;
			// logger.info("Before insert: '"+s+"'");
			s = insertCharAt(s, pos, Randomness.nextChar());
			// logger.info("After insert: '"+s+"'");
		}
		return s;
	}

	/** {@inheritDoc} */
	@Override
	public void delta() {
		
		String s = value;
		String oldValue = s;
		
		if(s == null) {
			randomize();
			return;
		}
		
		final double P2 = 1d / 3d;
		double random = Randomness.nextDouble();
		
		char randomChar;
		if (Randomness.nextDouble() <= Properties.PRIMITIVE_POOL) {
			randomChar = Randomness.nextChar();
		}
		else {
			ConstantPool constantPool = ConstantPoolManager.getInstance().getConstantPool(BranchSeedInfo.STRING);
			randomChar = constantPool.getRandomChar();
		}
		
		
//		double P = 1d / s.length();
		// Delete
		if (random < P2) {
			if(s.length()>0) {
				int pos = (int )(s.length() * Math.random());
				s = removeCharAt(s, pos);				
			}
			System.currentTimeMillis();
//			for (int i = s.length(); i > 0; i--) {
//				if (Randomness.nextDouble() < P) {
//					// logger.info("Before remove at "+i+": '"+s+"'");
//					s = removeCharAt(s, i - 1);
//					// logger.info("After remove: '"+s+"'");
//				}
//			}
		}
//		P = 1d / s.length();
		// Change
		else if (random >= P2 && random < 2*P2) {
			if(s.length() > 0) {
				int pos = (int )(s.length() * Math.random());
				char character = s.charAt(pos);
				
				char replaceChar = randomChar;
				if(Randomness.nextDouble() < 0.9) {
					try {
						int step = (int) (3*Math.random());
						if(Randomness.nextDouble() < 0.5) {
							replaceChar = (char) (character - step);						
						}
						else {
							replaceChar = (char) (character + step);
						}						
					}
					catch(Exception e) {
						System.currentTimeMillis();
					}
				}
				
				s = replaceCharAt(s, pos, replaceChar);		
				System.currentTimeMillis();
			}
			
			
//			for (int i = 0; i < s.length(); i++) {
//				if (Randomness.nextDouble() < P) {
//					// logger.info("Before change: '"+s+"'");
//					s = replaceCharAt(s, i, Randomness.nextChar());
//					// logger.info("After change: '"+s+"'");
//				}
//			}
		}

		// Insert
		else if (random >= 2*P2) {
			// for(int i = 0; i < s.length(); i++) {
			// if(Randomness.nextDouble() < P) {
			int pos = 0;
			if (s.length() > 0)
				pos = (int)(Math.random() * s.length());
			pos += (int)(Math.random() * 2);
			System.currentTimeMillis();
			if(pos > s.length()) {
				pos--;
			}
			s = insertCharAt(s, pos, randomChar);
//			s = StringInsert(s, pos);
			System.currentTimeMillis();
			// }
			// }
		}
		value = s;
		// logger.info("Mutated string now is: "+value);
		EventSequence.addEvent(new SearchEvent(System.currentTimeMillis(), SamplingDataType.STRING, String.valueOf(value), oldValue));
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.PrimitiveStatement#increment(java.lang.Object)
	 */
	/**
	 * <p>
	 * increment
	 * </p>
	 */
	public void increment() {
		String oldValue = String.valueOf(value);
		String s = value;
		if(s == null) {
			randomize();
			return;
		}
		else if (s.isEmpty()) {
			s += Randomness.nextChar();
		} else {
			s = replaceCharAt(s, Randomness.nextInt(s.length()), Randomness.nextChar());
		}

		value = s;
		EventSequence.addEvent(new SearchEvent(System.currentTimeMillis(), SamplingDataType.STRING, String.valueOf(value), oldValue));
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.PrimitiveStatement#randomize()
	 */
	/** {@inheritDoc} */
	@Override
	public void randomize() {
		if (Randomness.nextDouble() <= Properties.PRIMITIVE_POOL) {
			value = Randomness.nextString(Randomness.nextInt(Properties.STRING_LENGTH));
			EventSequence.addEvent(new RandomSamplingEvent(System.currentTimeMillis(), SamplingDataType.STRING, String.valueOf(value)));			
		}
		else {
			ConstantPool constantPool = ConstantPoolManager.getInstance().getConstantPool(BranchSeedInfo.STRING);
			String candidateString = constantPool.getRandomString();
			if(Properties.MAX_STRING > 0 && candidateString.length() < Properties.MAX_STRING)
				value = candidateString;
			else
				value = Randomness.nextString(Randomness.nextInt(Properties.STRING_LENGTH));
		}
	}

	@Override
	public Throwable execute(Scope scope, PrintStream out)
	        throws InvocationTargetException, IllegalArgumentException,
	        IllegalAccessException, InstantiationException {
		Throwable exceptionThrown = null;

		try {
			if(value == null)
				retval.setObject(scope, null);
			else {
				// String literals may not be longer than 32767
				if(((String)value).length() >= 32767)
					throw new CodeUnderTestException(new IllegalArgumentException("Maximum string length exceeded"));

				// In the JUnit code we produce, strings are generated as
				// String foo = "bar";
				// That means any reference comparison will behave different
				// as internally value is created as String foo = new String("bar").
				// Therefore we have to use the string object in the constant pool
				retval.setObject(scope, value.intern());
			}
		} catch (CodeUnderTestException e) {
			exceptionThrown = e;
		}
		return exceptionThrown;
	}
	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();		
		oos.writeObject(value);
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException,
	        IOException {
		ois.defaultReadObject();
		value = (String) ois.readObject();
	}
}
