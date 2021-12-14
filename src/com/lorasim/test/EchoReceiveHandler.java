package com.lorasim.test;

import com.lorasim.network.NetworkPacket;
import com.lorasim.network.ReceiveHandler;

public class EchoReceiveHandler implements ReceiveHandler {
	private NetworkPacket tempPacket = null;
	@Override
	public void receive(NetworkPacket p) {
		byte[] rxPayload = p.getPayload();
		byte[] txPayload = new byte[rxPayload.length];
		for(int x = 0; x < rxPayload.length; x++){
			txPayload[txPayload.length-1-x] = rxPayload[x];
		}
		tempPacket = new NetworkPacket(0, 0, 0, p.getOrigin(), 0, 0, NetworkPacket.TYPE_DATA, txPayload);
	}

	@Override
	public NetworkPacket send() {
		NetworkPacket rtn = tempPacket;
		tempPacket = null;
		return rtn;
	}
}
