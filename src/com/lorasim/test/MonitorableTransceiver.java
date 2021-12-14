package com.lorasim.test;

public interface MonitorableTransceiver {
	/**
	 * A MonitorableTransceiver should return a MonitorState with the following information:
	 * Last digit - State of the FSM
	 * Second to last digit - Physical Radio Status (0 idle, 1 receiving, 2 transmitting)
	 * Third to last digit - Physical Radio Channel (0 main, 1 RTCTS)
	 * @return State formatted as integer
	 */
	public int getMonitorState();
}
