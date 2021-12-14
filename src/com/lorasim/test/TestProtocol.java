package com.lorasim.test;

import com.lorasim.misc.Utils;
import com.lorasim.network.ReceiveHandler;
import com.lorasim.network.NetworkPacket;
import com.lorasim.network.ProtocolTransceiver;

import java.nio.charset.StandardCharsets;

public class TestProtocol {
	public static void main(String[] args){
		ProtocolTransceiver baseStation = new ProtocolTransceiver(1);
		ProtocolTransceiver forwarder1 = new ProtocolTransceiver(2);
		ProtocolTransceiver forwarder2 = new ProtocolTransceiver(3);
		ProtocolTransceiver client = new ProtocolTransceiver(4);

		TransceiverMonitor monitor = new TransceiverMonitor();
		monitor.addTransceiver(baseStation, "1");
		monitor.addTransceiver(forwarder1, "2");
		monitor.addTransceiver(forwarder2, "3");
		monitor.addTransceiver(client, "4");

		baseStation.startThread();
		forwarder1.startThread();
		forwarder2.startThread();
		client.startThread();

		int lossPercentage = 25;

		baseStation.addNeighbor(forwarder1, lossPercentage);
		forwarder1.addNeighbor(baseStation, lossPercentage);
		forwarder1.addNeighbor(forwarder2, lossPercentage);
		forwarder2.addNeighbor(forwarder1, lossPercentage);
		forwarder2.addNeighbor(client, lossPercentage);
		client.addNeighbor(forwarder2, lossPercentage);

		baseStation.addRoutingEntry(3, 2);
		forwarder1.addRoutingEntry(1, 1);
		forwarder1.addRoutingEntry(3, 3);
		forwarder2.addRoutingEntry(1, 2);
		forwarder2.addRoutingEntry(3, 4);
		client.addRoutingEntry(1, 3);

//		baseStation.setReceiveHandler(new EchoReceiveHandler());
//		client.setReceiveHandler(new EchoReceiveHandler());

		client.send(1, "Test message 123".getBytes());

//		for(int x = 0; x < 30000; x++){ // For debugging
//			System.out.println(monitor.getState());
//			Utils.sleep(500);
//		}

		Utils.sleep(3000);
//		System.exit(0);
	}
}
