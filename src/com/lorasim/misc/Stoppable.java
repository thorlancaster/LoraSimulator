package com.lorasim.misc;

public interface Stoppable {
	/**
	 * Call this method to gracefully stop the running thread.
	 * Classes implementing this interface should stop as soon as possible
	 * after this method is invoked.
	 */
	public void stop();
}
