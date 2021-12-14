package com.lorasim.network;

public class NetworkPacket {
	public static final int TYPE_DATA = 1;
	public static final int TYPE_ACK = 2;
	public static final int TYPE_RTS = 3;
	public static final int TYPE_CTS = 4;
	public static final int TYPE_ARX = 5;
	private byte[] payload;
	private int src; // This sender
	private int dest; // This receiver
	private int origin; // First sender
	private int finalNode; // Final receiver
	private int seqNum; // Sequence # (1-127, 0 to reset)
	private int ackNum; // Sequence #, coded for (N)ACKs
	private int phyChannel; // When converting from a PHY packet
	/*
	 * 28-bit, secure: data
	 * 15-bit, secure: ACK
	 * 8-bit, basic: RTS or CTS
	 */
	private int checksum; // Checksum:
	private int type;

	public NetworkPacket() {

	}

	/**
	 * Create a NetworkPacket.
	 * Checksum will be calculated automatically.
	 * @param src The address of the node that is sending the packet
	 * @param dest The address of the node that is receiving the packet
	 * @param origin The node where the packet originated (upstream)
	 * @param finalNode The final node in the chain of transmissions (downstream)
	 * @param seqNum The RDT Sequence number for the packet
	 * @param ackNum Sequence number to acknowledge
	 * @param type Type of the packet (DATA, ACK, RTS, CTS, or ARX)
	 * @param payload User data to send. Only valid for DATA packets
	 */
	public NetworkPacket(int src, int dest, int origin, int finalNode, int seqNum, int ackNum, int type, byte[] payload) {
		this.src = src;
		this.dest = dest;
		this.origin = origin;
		this.finalNode = finalNode;
		this.seqNum = seqNum;
		this.ackNum = ackNum;
		this.type = type;
		if (payload != null){
			this.payload = new byte[payload.length];
			for (int x = 0; x < payload.length; x++) {
				this.payload[x] = payload[x];
			}
		} else {
			if(type == TYPE_DATA)
				throw new IllegalArgumentException("Payload cannot be null for a data packet");
		}
		if(type == TYPE_DATA)
			checksum = 1;
		else if(type == TYPE_ACK)
			checksum = 2;
		else if(type == TYPE_RTS || type == TYPE_CTS || type == TYPE_ARX)
			checksum = 3;
		else
			throw new IllegalArgumentException("Invalid type: " + type);
	}

	public static NetworkPacket RtsPacket(int src, int dest, int seqNum){
		return new NetworkPacket(src, dest, 0, 0, seqNum, 0, TYPE_RTS, null);
	}
	public static NetworkPacket CtsPacket(int src, int dest, int seqNum){
		return new NetworkPacket(src, dest, 0, 0, seqNum, 0, TYPE_CTS, null);
	}
	public static NetworkPacket ArxPacket(int src, int dest, int seqNum){
		return new NetworkPacket(src, dest, 0, 0, seqNum, 0, TYPE_ARX, null);
	}
	public static NetworkPacket AckPacket(int src, int dest, int seqNum){
		return new NetworkPacket(src, dest, 0, 0, seqNum, 127-seqNum, TYPE_ACK, null);
	}

	public NetworkPacket(byte[] raw, int channel) {
		this.phyChannel = channel;
		if (raw.length >= 7) { // DATA
			type = TYPE_DATA;
			src = bitVector(raw, 0, 5);
			dest = bitVector(raw, 5, 10);
			if (bitVector(raw, 10, 11) != 1)
				throw new IllegalStateException("Malformed DATA Packet (sig != 1)");
			origin = bitVector(raw, 11, 16);
			finalNode = bitVector(raw, 16, 21);
			seqNum = bitVector(raw, 21, 28);
			checksum = bitVector(raw, 28, 56);
			payload = new byte[raw.length - 7];
			for (int x = 7; x < raw.length; x++) {
				payload[x - 7] = raw[x];
			}
		} else if (raw.length == 5) {
			type = TYPE_ACK;
			src = bitVector(raw, 0, 5);
			dest = bitVector(raw, 5, 10);
			if (bitVector(raw, 10, 11) != 0)
				throw new IllegalStateException("Malformed ACK Packet (sig != 0)");
			seqNum = bitVector(raw, 11, 18);
			ackNum = bitVector(raw, 18, 25);
			checksum = bitVector(raw, 25, 40);
		} else if (raw.length == 4) {
			src = bitVector(raw, 0, 5);
			dest = bitVector(raw, 5, 10);
			if (bitVector(raw, 10, 11) != 0)
				throw new IllegalStateException("Malformed RTCTS Packet (sig != 0)");
			seqNum = bitVector(raw, 11, 18);
			int typeNum = bitVector(raw, 18, 24);
			checksum = bitVector(raw, 24, 32);
			if (typeNum == 1)
				type = TYPE_RTS;
			else if (typeNum == 33)
				type = TYPE_CTS;
			else if (typeNum == 34)
				type = TYPE_ARX;
			else
				throw new IllegalStateException("Invalid subtype for RTCTS packet: " + typeNum);
		} else
			throw new IllegalStateException("Invalid packet length: " + raw.length);
	}

	public String toString(){
		String typeStr = "ERROR";
		switch(type){
			case TYPE_DATA:
				typeStr = "DATA";
				break;
			case TYPE_ACK:
				typeStr = "ACK";
				break;
			case TYPE_RTS:
				typeStr = "RTS";
				break;
			case TYPE_CTS:
				typeStr = "CTS";
				break;
			case TYPE_ARX:
				typeStr = "ARX";
				break;
		}
		return String.format("NetworkPacket {ch=%d, type=%s, src=%d, dest=%d, origin=%d, final=%d, seq=%d, ack=%d, payload=%s}",
				phyChannel, typeStr,  src,  dest,  origin,  finalNode,  seqNum,  ackNum,  payload == null ? "NULL" : new String(payload));
	}

	public byte[] getData() {
		if (type == TYPE_DATA) {
			byte[] rtn = new byte[7 + payload.length];
			rtn[0] |= (src << 3);
			rtn[0] |= (dest & 31) >> 2;
			rtn[1] |= (dest << 6);
			rtn[1] |= (1 << 5);
			rtn[1] |= (origin & 31);
			rtn[2] |= (finalNode << 3);
			rtn[2] |= (seqNum & 127) >> 4;
			rtn[3] |= (seqNum << 4);
			rtn[3] |= (checksum >> 24) & 15;
			rtn[4] |= (checksum >> 16);
			rtn[5] |= (checksum >> 8);
			rtn[6] |= (checksum >> 0);
			for (int x = 0; x < payload.length; x++) {
				rtn[7 + x] = payload[x];
			}
			return rtn;
		} else if (type == TYPE_ACK) {
			byte[] rtn = new byte[5];
			rtn[0] |= (src << 3);
			rtn[0] |= (dest & 31) >> 2;
			rtn[1] |= (dest << 6);
			rtn[1] |= (seqNum & 127) >> 2;
			rtn[2] |= (seqNum << 6);
			rtn[2] |= (ackNum & 127) >> 1;
			rtn[3] |= (ackNum << 7);
			rtn[3] |= (checksum >> 8) & 127;
			rtn[4] |= (checksum >> 0);
			return rtn;
		} else if (type == TYPE_RTS || type == TYPE_CTS || type == TYPE_ARX) {
			byte rtctsType = 0;
			if (type == TYPE_RTS)
				rtctsType = 1;
			if (type == TYPE_CTS)
				rtctsType = 33;
			if (type == TYPE_ARX)
				rtctsType = 34;
			byte[] rtn = new byte[4];
			rtn[0] |= (src << 3);
			rtn[0] |= (dest & 31) >> 2;
			rtn[1] |= (dest << 6);
			rtn[1] |= (seqNum & 127) >> 2;
			rtn[2] |= (seqNum << 6);
			rtn[2] |= rtctsType;
			rtn[3] = ((byte) checksum);
			return rtn;
		}
		return null;
	}

	public byte[] getPayload(){
		byte[] rtn = new byte[payload.length];
		for(int x = 0; x < rtn.length; x++){
			rtn[x] = payload[x];
		}
		return rtn;
	}

	public String getPayloadString(){
		StringBuilder sb = new StringBuilder();
		for(int x = 0; x < payload.length; x++){
			sb.append((char)payload[x]);
		}
		return sb.toString();
	}

	public int getSrc(){
		return src;
	}
	public int getDest(){
		return dest;
	}
	public int getOrigin(){
		return origin;
	}
	public int getFinalNode(){
		return finalNode;
	}
	public int getSeqNum(){
		return seqNum;
	}
	public int getAckNum(){
		return ackNum;
	}
	public int getChecksum(){
		return checksum;
	}
	public int getType(){
		return type;
	}

	private static int bitVector(byte[] arr, int start, int end) {
		int rtn = 0;
		for (int x = start; x < end; x++) {
			int bit = ((arr[x / 8]) & (1 << (7 - (x & 7)))) > 0 ? 1 : 0;
			rtn = (rtn << 1) | bit;
		}
		return rtn;
	}

	private static void printByteArr(byte[] arr) {
		if (arr == null) {
			System.out.println("[null]");
			return;
		}
		System.out.print("[");
		for (int x = 0; x < arr.length * 8; x++) {
//			System.out.printf("%1$02X", arr[x]);
			System.out.print(bitVector(arr, x, x + 1));
//			if(x < arr.length - 1)
//				System.out.print(" ");
		}
		System.out.println("]");
	}

	public static void main(String[] args) {
		boolean testData = false;
		boolean testAck = false;
		boolean testRts = true;
		// Test packets of type DATA
		if (testData) {
			NetworkPacket lp = new NetworkPacket();
			lp.type = TYPE_DATA;
			lp.src = 5;
			lp.dest = 3;
			lp.origin = 1;
			lp.finalNode = 4;
			lp.seqNum = 42;
			lp.checksum = 124816;
			lp.payload = new byte[]{0x32, 0x21, 0x10};
			byte[] data = lp.getData();
			printByteArr(data);
			NetworkPacket lp2 = new NetworkPacket(data, 0);
			System.out.println(lp2.src + " = 5");
			System.out.println(lp2.dest + " = 3");
			System.out.println(lp2.origin + " = 1");
			System.out.println(lp2.finalNode + " = 4");
			System.out.println(lp2.seqNum + " = 42");
			System.out.println(lp2.checksum + " = 124816");
			printByteArr(lp2.payload);
			System.out.println(" = [32 21 10]");
		}
		// Test packets of type ACK
		if (testAck) {
			NetworkPacket lp = new NetworkPacket();
			lp.type = TYPE_ACK;
			lp.src = 5;
			lp.dest = 3;
			lp.seqNum = 42;
			lp.ackNum = ~((byte) (42));
			System.out.println(lp.ackNum);
			lp.checksum = 88;
			byte[] data = lp.getData();
			printByteArr(data);
			NetworkPacket lp2 = new NetworkPacket(data, 0);
			System.out.println(lp2.src + " = 5");
			System.out.println(lp2.dest + " = 3");
			System.out.println(lp2.seqNum + " = 42");
			System.out.println(lp2.ackNum + " = 85");
			System.out.println(lp2.checksum + " = 88");
		}
		// Test packets of type RTS/CTS/ARX
		if (testRts) {
			NetworkPacket lp = new NetworkPacket();
			lp.type = TYPE_ARX;
			lp.src = 5;
			lp.dest = 3;
			lp.seqNum = 42;
			lp.checksum = 88;
			byte[] data = lp.getData();
			printByteArr(data);
			NetworkPacket lp2 = new NetworkPacket(data, 0);
			System.out.println(lp2.src + " = 5");
			System.out.println(lp2.dest + " = 3");
			System.out.println(lp2.seqNum + " = 42");
			System.out.println(lp2.checksum + " = 88");
			System.out.println(lp.type + " = " + TYPE_ARX);
		}
	}

	public boolean corrupt() {
		return false;
		// TODO verify checksum
	}
}
