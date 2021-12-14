package com.lorasim.test;

import com.lorasim.network.ProtocolTransceiver;
import com.lorasim.physical.PhysicalTransceiver;

public class TestPhysical {
	public static void main(String[] args) throws InterruptedException {

		PhysicalTransceiver baseStation = new PhysicalTransceiver(1);
		PhysicalTransceiver forwarder = new PhysicalTransceiver(2);
		PhysicalTransceiver client = new PhysicalTransceiver(3);

		baseStation.setDebugStream(System.out);
		forwarder.setDebugStream(System.out);
		client.setDebugStream(System.out);

		baseStation.addNeighbor(forwarder, 0);
		forwarder.addNeighbor(baseStation, 0);

		forwarder.addNeighbor(client, 0);
		client.addNeighbor(forwarder, 0);


		new Thread(baseStation).start();
		new Thread(forwarder).start();
		new Thread(client).start();

		System.out.println("Transmitting [Spraying it]...");
		for(int x = 0; x < 2; x++)
			baseStation.send("Test Message 123".getBytes());
		System.out.println("Nothing should arrive");

		Thread.sleep(1500);
		System.out.println("Transmitting [Saying it]...");
		for(int x = 0; x < 1; x++)
			baseStation.send("Test Message 321".getBytes());
		System.out.println("One message should arrive");

		Thread.sleep(3000);
		System.out.println("End Simulation");
		System.exit(0);
	}
}
