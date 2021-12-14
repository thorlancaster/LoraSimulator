package com.lorasim.test;

import com.lorasim.network.ProtocolTransceiver;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;

public class TransceiverMonitor {
	private ArrayList<Map.Entry<String, MonitorableTransceiver>> transceivers;

	public TransceiverMonitor(){
		transceivers = new ArrayList<>();
	}

	public void addTransceiver(MonitorableTransceiver t, String name) {
		transceivers.add(new AbstractMap.SimpleEntry<String, MonitorableTransceiver>(name, t));
	}

	/**
	 * @param statusNum Status (as an integer) from MonitorableTransceiver
	 * @return Status as a fixed-width, descriptive String
	 */

	/**
	 * A MonitorableTransceiver should return a MonitorState with the following information:
	 * Last digit - State of the FSM
	 * Second to last digit - Physical Radio Status (0 idle, 1 receiving, 2 transmitting)
	 * Third to last digit - Physical Radio Channel (0 main, 1 RTCTS)
	 * @return State formatted as integer
	 */

	private String getStatusStr(int statusNum){
		int state = statusNum % 10;
		int radioState = (statusNum / 10) % 10;
		int radioChannel = (statusNum / 100) % 10;
		String stateStr =      "   >>>>>    ";
		switch(state){
			case 1: stateStr = "  WAIT_RTS  "; break;
			case 2: stateStr = "WAIT_RXSTART"; break;
			case 3: stateStr = " WAIT_RXEND "; break;
			case 4: stateStr = "  WAIT_TX   "; break;
			case 5: stateStr = "  WAIT_ACK  "; break;
		}
		String radioStateStr = "ERROR";
		switch(radioState){
			case 0: radioStateStr = "IDLE "; break;
			case 1: radioStateStr = " RX  "; break;
			case 2: radioStateStr = " TX  "; break;
		}
		String radioChannelStr = "ERROR";
		switch (radioChannel){
			case 0: radioChannelStr = "MAIN "; break;
			case 1: radioChannelStr = "RTCTS"; break;
		}
		return '[' + stateStr + " " + radioStateStr + " " +  radioChannelStr + ']';
	}

	public String getState(){
		int[] statuses = new int[transceivers.size()];
		for(int x = 0; x < statuses.length; x++){
			statuses[x] = transceivers.get(x).getValue().getMonitorState();
		}
		StringBuilder sb = new StringBuilder();
		for(int x = 0; x < statuses.length; x++){
			sb.append(transceivers.get(x).getKey() + ":" + getStatusStr(statuses[x]) + "       ");
		}
		return sb.toString();
	}

}
