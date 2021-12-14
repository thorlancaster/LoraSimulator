package com.lorasim.physical;

import java.util.Date;
import java.util.Hashtable;

public class PhysicalPacket {
	private int sender; // Address of node that sent packet
	private Hashtable<Integer, Integer> receivedBy;
	private boolean collided = false;
	private int channel; // Frequency transmitted on
	private byte[] data; // Raw packet data
	private long millis; // Time packet was sent
	private int duration; // Number of milliseconds packet takes to send

	public PhysicalPacket(int sender, int channel, byte[] data) {
		this.sender = sender;
		this.receivedBy = new Hashtable<>();
		this.channel = channel;
		this.data = data;
		this.millis = new Date().getTime();
		this.duration = 100 + 20*data.length;
	}

	public boolean collidesWith(PhysicalPacket p2) {
		if(p2.channel != channel)
			return false; // Packets on different channels don't collide
		if((this.millis <= p2.millis) && (this.millis + this.duration > p2.millis))
			return true;
		if((p2.millis <= this.millis) && (p2.millis + p2.duration > this.millis))
			return true;
		return false;
	}

	public boolean wasReceivedBy(int nodeId) {
		return receivedBy.containsKey(nodeId);
	}

	public boolean canBeReceivedBy(int nodeId) {
		if(collided)
			return false;
		return !wasReceivedBy(nodeId);
	}

	public long getMillis() {
		return millis;
	}

	public int getChannel() {
		return channel;
	}

	public long getEndMillis() {
		return millis + duration;
	}

	public int getDuration(){
		return duration;
	}

	public void markReceivedBy(int nodeId) {
		receivedBy.put(nodeId, 0);
	}
	public void markInterfered() {
		collided = true;
	}

	public byte[] getData(){
		byte[] rtn = new byte[data.length];
		for(int x = 0;  x < data.length; x++){
			rtn[x] = data[x];
		}
		return rtn;
	}

	public String getDataStr() {
		StringBuilder sb = new StringBuilder();
		for(int x = 0; x < data.length; x++)
			sb.append((char)data[x]);
		return sb.toString();
	}

}
