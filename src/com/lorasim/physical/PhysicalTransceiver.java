package com.lorasim.physical;

import com.lorasim.misc.Pair;
import com.lorasim.misc.Stoppable;
import com.lorasim.misc.Utils;
import com.lorasim.test.PrettyPrint;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;


public class PhysicalTransceiver implements Runnable, Stoppable {
	private ArrayList<Pair<PhysicalTransceiver, Integer>> neighbors;
	private ReceiveManager receiveManager;
	private ArrayList<PhysicalPacket> receivedPackets; // Successfully received without collisions or errors
	private Semaphore receivedPacketWafer;

	private int address; // Address of this transceiver
	private int channel = 433; // Frequency this transceiver is set to transmit/receive on (default 433MHz)
	private long lastChannelChangeMs = 0; // Time when the channel was last changed
	private long radioFreeMs = 0;
	private boolean stopped = false;
	private PrintStream debugStream;

	public PhysicalTransceiver(int address) {
		neighbors = new ArrayList<>();
		this.address = address;
		this.receiveManager = new ReceiveManager();
		this.receivedPackets = new ArrayList<>();
		this.receivedPacketWafer = new Semaphore(1);
	}

	public void setDebugStream(PrintStream p) {
		this.debugStream = p;
	}

	public void addNeighbor(PhysicalTransceiver t, int loss) {
		for (Pair<PhysicalTransceiver, Integer> neighbor : neighbors)
			if (neighbor.getKey().equals(t))
				return;
		neighbors.add(new Pair<>(t, loss));
	}

//	public boolean hasNeighbor(PhysicalTransceiver t) {
//		return neighbors.contains(t);
//	}

	public int getAddress() {
		return address;
	}

	long getLastChannelChangeMs() {
		return lastChannelChangeMs;
	}

	public void send(byte[] message) {
		send(message,true);
	}

	public void send(byte[] message, boolean blocking) {
		PhysicalPacket p = null;
		for (Pair<PhysicalTransceiver, Integer> neighbor : neighbors) {
			p = new PhysicalPacket(address, channel, message);
			int randPct = (int) (Math.random() * 100);
			if (neighbor.getValue() > randPct) {
				p.markInterfered();
				PrettyPrint.println("****Packet from " + this.address + " to " + neighbor.getKey().getAddress() + " was randomly dropped", PrettyPrint.COLOR_PURPLE);
			}
			neighbor.getKey().phyReceive(p);
		}
		p = new PhysicalPacket(address, channel, message);
		radioFreeMs = System.currentTimeMillis() + p.getDuration();
		if (blocking)
			Utils.sleep(p.getDuration() + 1);
		receiveManager.addPacket(p);
	}

	public boolean canSend() {
		return System.currentTimeMillis() > radioFreeMs;
	}


	public boolean rxInProgress() {
		return receiveManager.isReceiving(this);
	}

	/**
	 * Fetch a packet from the PHY receive buffer, if available
	 * @return a PhysicalPacket if one exists, otherwise null
	 */
	public PhysicalPacket receive() {
		try {
			receivedPacketWafer.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		try {
			if (receivedPackets.size() == 0)
				return null;
			return receivedPackets.remove(0);
		} finally {
			receivedPacketWafer.release();
		}
	}

	private void phyReceive(PhysicalPacket p) {
		receiveManager.addPacket(p);
	}

	public int getChannel() {
		return channel;
	}

	public void setChannel(int channel) {
		if (this.channel == channel)
			return;
		this.channel = channel;
		try {
			receivedPacketWafer.acquire();
			this.receiveManager.clear();
			this.receivedPackets.clear();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			receivedPacketWafer.release();
		}

		this.lastChannelChangeMs = System.currentTimeMillis();
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		try {
			while (!stopped) {
				PhysicalPacket rxPacket = receiveManager.receive(this);
				if (rxPacket != null) {
					if (debugStream != null)
						debugStream.printf("PHY: Transceiver %d received: %s\n", address, rxPacket.getDataStr());
					receivedPacketWafer.acquire();
					try {
						if (receivedPackets.size() > 0 && debugStream != null)
							debugStream.println("PHY: Receive buffer already has packet, had to drop one");
						receivedPackets.clear();
						receivedPackets.add(rxPacket);
					} finally {
						receivedPacketWafer.release();
					}
				}
				Thread.sleep(Utils.SLEEP_DELAY);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return Status of this radio (0 = idle, 1 = receiving, 2 = transmitting)
	 */
	public int getRadioStatus() {
		if (System.currentTimeMillis() < radioFreeMs)
			return 2;
		if (receiveManager.isReceiving(this))
			return 1;
		return 0;
	}
}
