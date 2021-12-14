package com.lorasim.network;

import com.lorasim.network.NetworkPacket;

public interface ReceiveHandler {
	/**
	 * When a packet is received, this function is called automatically by the transceiver.
	 * @param p Packet that was received
	 */
	public void receive(NetworkPacket p);

	/**
	 * After receive() is automatically called, this function is called repeatedly
	 * by the transceiver until it returns null.
	 * Packets returned by this function shall be sent by the transceiver
	 * @return packet to send
	 */
	public NetworkPacket send();
}
