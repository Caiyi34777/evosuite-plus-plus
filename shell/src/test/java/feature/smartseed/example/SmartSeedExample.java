package feature.smartseed.example;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.evosuite.runtime.Random;

public class SmartSeedExample {
	
	public static String[] noise = 
		{"abc0", "abc1", "abc2", "abc3", "abc4", "abc5", "abc6", "abc7", "abc8", "abc9", "abc10"};
	public static int[] noiseInt = {
		-233, 15, 20, 90, 2000, 673, -992, 67, 500, 999999,34500010, 34500024, 34500009, 34500008, 34500006, 33500000	
	};
	public static long[] noiseLong = {
		8989l, 2147483690l, -5146483690l, 9223372036854775806l, -3372036854775808l 	
	};
	public static float[] noiseFloat = 
		{800f, -0.9f, -45.0999f, 0.9873f, 30000f, 0.989f, 0.963f};
	public static double[] noiseDouble = 
		{0.99, -23.66, 89, -100, 50.09 };
	
	public void dynamicExample1(int x, int y) {
		double z = Math.floor((y + 999999) / 1333);
		if (x == z) {
			System.currentTimeMillis();
		}
	}

	public void staticExample(int x, int y) {
		if (x / 20000 == 345) {
			System.currentTimeMillis();
		}
	}

	public void staticExample1(int x, int y) {
		if (x == 34500000) {
			System.currentTimeMillis();
		}
	}

	public void staticExample2(String x, int y) {
		x = x + "a";
		if (x.equals("34500000")) {
			System.currentTimeMillis();
		}
		
		if(x != x) {
			System.currentTimeMillis();
		}
	}

	public void staticExample3(String x, int y) {
		String a = x.substring(0, x.length() - 3);
		if (a.equals("it is a difficult string")) {
			System.currentTimeMillis();
		}
	}
	
	public void staticExample4(double x, int y, float f,long l) {
		if(x == 35.4 * 300) {
			System.currentTimeMillis();
		}
		if(y == -2147483646) {//ldc
			System.currentTimeMillis();
		}else if(y == 32700) {//sipush
			System.currentTimeMillis();
		}else if(y == 4) {//iconst
			System.currentTimeMillis();
		}else if(y == 120) {//bipush
			System.currentTimeMillis();
		}
		if(f == 0.98f) {
			System.currentTimeMillis();
		}else if(l == 2147483688l) {
			System.currentTimeMillis();
		}
	}

	public void equalsIgnoreCaseExample(String x, int y) {
		String a = "ignoreCase" + x;
		String b = "example";
		if (a.equalsIgnoreCase("IGNORECASEString".concat(b))) {
			System.currentTimeMillis();
		}
	}
	// evosuite perform well?
	public void stratWithExample(String x, int index) {
		String a = x.substring(2, x.length() - 3);
		String b = "example";
		if (a.startsWith(b, index)) {
			System.currentTimeMillis();
		}
	}
	
	public void stratWithExample(String x) {
		String a = x.replace('a', 'A');
		String b = "example";
		if (a.startsWith("Find the right String")) {
			System.currentTimeMillis();
		}
	}	
	public String endWithExample(String args[], String suffix) {
		String endString = "end";
		for(String a : args) 
			if (a.endsWith(endString)) {
				return a;
			}
			
		return args.length != 0 ? args[args.length - 1] : "NULL";
	}
	
	public void matchesExample(String x) {
		String regex = "^tr[A-F0-3]";
		if(x.matches(regex)) {
			System.currentTimeMillis();
		}
	}
	
	public void patternMatchesExample(String x) {
		String regex ="a*b";
		if(Pattern.matches(regex, x)) {
			System.currentTimeMillis();
		}
	}
	
	public void matcherMatchesExample(String x) {
		Pattern pattern = Pattern.compile("(\\d{3,4})\\-(\\d{7,8})");
        // get Matcher object
        Matcher matcher = pattern.matcher(x);
		if(matcher.matches()) {
			System.currentTimeMillis();
		}
	}
	
	public void combinationExample(String[] x) {
		String a = x[0];
		String b = endWithExample(x,a);
		if(b.equals("end")) {
			System.currentTimeMillis();
		}
	}
	
	public void iregExample(int x) {
		int n = Random.nextInt();
		if(n % x == 5356) {
			System.currentTimeMillis();
		}
	}
	
	public float ff = 8236.115f;
	public void fieldExample(float x) {
		float n = Random.nextFloat();
		if(ff == n + 2000) {
			System.currentTimeMillis();
		}
	}
	
	public void dloadExample(double x) {
		double n = Random.nextDouble();
		if(n * x == 0.97) {
			System.currentTimeMillis();
		}
	}
	
	public void switchcaseExample(String x,String y) {
		char[] x1 = x.toCharArray();
		char[] y1 = y.toCharArray();
		for(char c : x1) {
			switch(c) {
			case 'A':
			case 'a':
				break;
			case ':':
				break;
			case '!':
				break;
			case ' ':
				break;
			default:
				break;
			}
		}
	}
	
	public int compareNum = 55;
	
	public boolean singleOprand() {
		if(compareNum > 100) 
			return false;
		return true;
	}
	
	public int getCompareNum() {
		if(compareNum != 0)
			return compareNum;
		else 
			return compareNum = 1000;
	}
	
	public void invokeDiffOprand() {
		if(singleOprand()) {
			int num = 1000;
			if(getCompareNum() == 1000)
				return;
		}
	}
	
	public void setCompareNum(int compareNum) {
		this.compareNum = compareNum;
	}
	
	/**
	 * a = m()
	 * if(a == true)
	 */
	public boolean invokeMethod(int x) {
		if(x > noiseInt[2])
			return true;
		return false;
	}
	
	public void compareExample(int x) {
		boolean a = invokeMethod(x);
		if(a == true) {
			System.currentTimeMillis();
		}
	}
}
