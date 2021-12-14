package com.lorasim.test;

public class Assertions {
	public static void assertTrue(boolean val, String message){
		if(!val)
			throw new RuntimeException(message);
	}
	public static void assertFalse(boolean val, String message){
		if(val)
			throw new RuntimeException(message);
	}
	public static void assertEqual(int val1, int val2, String message){
		if(val1 != val2)
			throw new RuntimeException(message);
	}
}
