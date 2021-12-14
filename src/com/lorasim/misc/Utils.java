package com.lorasim.misc;

public class Utils {
	public static final int SLEEP_DELAY = 15;

	public static void sleep(int ms){
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {

		}
	}
}
