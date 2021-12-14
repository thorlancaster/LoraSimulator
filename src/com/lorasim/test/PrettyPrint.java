package com.lorasim.test;

public class PrettyPrint {
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";

	public static final int COLOR_RED = 1;
	public static final int COLOR_YELLOW = 2;
	public static final int COLOR_GREEN = 3;
	public static final int COLOR_CYAN = 4;
	public static final int COLOR_BLUE = 5;
	public static final int COLOR_PURPLE = 6;
	public static final int COLOR_WHITE = 7;

	public static void println(String str, int color){
		switch(color){
			case COLOR_RED:
				System.out.println(ANSI_RED + str + ANSI_RESET);
				break;
			case COLOR_YELLOW:
				System.out.println(ANSI_YELLOW + str + ANSI_RESET);
				break;
			case COLOR_GREEN:
				System.out.println(ANSI_GREEN + str + ANSI_RESET);
				break;
			case COLOR_CYAN:
				System.out.println(ANSI_CYAN + str + ANSI_RESET);
				break;
			case COLOR_BLUE:
				System.out.println(ANSI_BLUE + str + ANSI_RESET);
				break;
			case COLOR_PURPLE:
				System.out.println(ANSI_PURPLE + str + ANSI_RESET);
				break;
			case COLOR_WHITE:
				System.out.println(ANSI_WHITE + str + ANSI_RESET);
				break;
		}
	}
}
