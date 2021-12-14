package com.lorasim.physical;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class ReceiveManager{
	private ArrayList<PhysicalPacket> activePackets; // Packets active in the airspace
	private ReentrantLock packetWafer;
	private int expiryMs = 2000; // Packet will go into past queue this many ms after it has finished
	private int gcSize = 15; // With more than this many packets in the queue, apply expiryMs rule

	public ReceiveManager() {
		activePackets = new ArrayList<>();
		packetWafer = new ReentrantLock();
	}

	/**
	 * Add a packet to the packet list. This function should be called by
	 * a transceiver transmitting to this com.lorasim.physical.ReceiveManager's transmitter
	 * @param p packet to add
	 */
	public void addPacket(PhysicalPacket p) {
		packetWafer.lock();
		try {
			activePackets.add(p);
		}
		finally {
			packetWafer.unlock();
		}
	}

	public boolean isReceiving(PhysicalTransceiver t) {
		PhysicalPacket lastPacket = null;
		long millis = System.currentTimeMillis();
		packetWafer.lock();
		try {
			for (int i = activePackets.size()-1; i >= 0; i--) {
				lastPacket = activePackets.get(i);
				if (lastPacket.getChannel() == t.getChannel())
					break;
				else
					lastPacket = null;
			}
			if (lastPacket == null) {
				return false; // No packet
			}
			if (lastPacket.getEndMillis() < millis) {
				return false; // Packet finished transmitting
			}

			for (PhysicalPacket rxd : activePackets) {
				if (rxd != lastPacket && rxd.collidesWith(lastPacket)) {
					return false;
					// Interfering packet causes reception to fail
				}

			}
		}
		finally {
			packetWafer.unlock();
		}

		if (t.getLastChannelChangeMs() > lastPacket.getMillis()) { // Too recent of channel change
			return false;
		}

		return true; // One packet is still transmitting
	}

	public PhysicalPacket receive(PhysicalTransceiver t) {
		long millis = System.currentTimeMillis();

		ArrayList<PhysicalPacket> matches = new ArrayList<>();

		packetWafer.lock();
		if(activePackets.size() >= gcSize)
			for(int x = 0; x < activePackets.size(); x++){
				if(activePackets.size() >= gcSize && activePackets.get(x).getEndMillis() + expiryMs < millis){
					activePackets.remove(x);
					x--;
				}
				else
					break;
			}
		// Search over all active packets for ones that are receivable and old enough
		try {
			for (PhysicalPacket rxd : activePackets) {
				if (rxd.getChannel() == t.getChannel() && rxd.getMillis() > t.getLastChannelChangeMs()) { // Filter packets by correct channel
					if (rxd.canBeReceivedBy(t.getAddress())) { // Filter packets not already received or collided
						matches.add(rxd);
					}
				}
			}
		}
		finally {
			packetWafer.unlock();
		}

		// For all receivable packets, see if they collide with any other packet
		for (int x = 0; x < matches.size(); x++) {
			for (int y = x + 1; y < matches.size(); y++) {
				if (matches.get(x).collidesWith(matches.get(y))) {
					matches.get(x).markInterfered();
					matches.get(y).markInterfered();
					System.out.println("Packets collided!");
				}
			}
		}

		// Filter out packets that have not finished transmitting
		for(int x = 0; x < matches.size(); x++) {
			PhysicalPacket rxd = matches.get(x);
			if (!(rxd.getEndMillis() < millis)){ // Filter packets that have been fully TX'd
				matches.remove(rxd);
				x--;
			}
		}
		// Once collisions are removed, return the first match
		for (PhysicalPacket ap : matches) {
			if (ap.canBeReceivedBy(t.getAddress())) {
				ap.markReceivedBy(t.getAddress());
				return ap;
			}
		}
		return null;
	}

	// Clear out all packets from the queue
	public void clear() {
		try{
			packetWafer.lock();
			activePackets.clear();
		}
		finally {
			packetWafer.unlock();
		}
	}
}

