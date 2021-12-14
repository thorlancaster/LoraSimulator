package com.lorasim.network;

import com.lorasim.misc.Utils;
import com.lorasim.physical.PhysicalPacket;
import com.lorasim.physical.PhysicalTransceiver;
import com.lorasim.test.MonitorableTransceiver;
import com.lorasim.test.PrettyPrint;

import java.io.PrintStream;
import java.util.Hashtable;

/**
 * Wraps an underlying PhysicalTransceiver to implement RDT over LoRa
 */
public class ProtocolTransceiver implements Runnable, MonitorableTransceiver {
	private int address;
	private int mainChannel = 433;
	private int rtsChannel = 434;
	public static final int STATE_WAIT_RTS = 1;
	public static final int STATE_WAIT_RXSTART = 2;
	public static final int STATE_WAIT_RXEND = 3;
	public static final int STATE_WAIT_TX = 4;
	public static final int STATE_WAIT_ACK = 5;

	public static final int MAX_BACKOFF_COUNT = 4; // Maximum 2^n value for exponential backoff
	public static final int TIMEOUT_RXSTART = 300; // Timeout to begin receiving data after CTS finished
	public static final int TIMEOUT_RTSBACKOFF = 300; // Timeout to wait before sending another RTS request (exponential backoff)
	public static final int TIMEOUT_ACKRECV = 3000; // Timeout to wait after sending data packet for an ACK
	public static final int CYCLE_SLEEP = 20; // Milliseconds to sleep between each run of main loop

	private int state = STATE_WAIT_RTS;
	private long timer = 0;
	private int timeout = 0;
	private int backoffCount = 0;
	private NetworkPacket manualPacket;
	private NetworkPacket queuedPacket;
	private PhysicalTransceiver physicalTransceiver;
	private ReceiveHandler receiveHandler;
	private Hashtable<Integer, Integer> routingTable;
	private Hashtable<Integer, Integer> txSequenceTable;
	private Hashtable<Integer, Integer> rxSequenceTable;

	public ProtocolTransceiver(int address){
		this.address = address;
		this.routingTable = new Hashtable<>();
		this.txSequenceTable = new Hashtable<>();
		this.rxSequenceTable = new Hashtable<>();
		physicalTransceiver = new PhysicalTransceiver(address);
		PrettyPrint.println("Created Transceiver with address " + address, PrettyPrint.COLOR_YELLOW);
	}

	// Helper function for pretty-printing
	private void println(String str, int prettyColor){
		PrettyPrint.println(this.address + ": " + str, prettyColor);
	}

	// Main thread that implements protocol
	public void run(){
		while(true){
			if(state == STATE_WAIT_RTS){ // Waiting for another node to request to send data, or for a manual send request
				physicalTransceiver.setChannel(rtsChannel);
				NetworkPacket np = getNetworkPacket();
				if(np != null && np.getType() == NetworkPacket.TYPE_RTS && np.getDest() == address){
					// On receiving RTS packet:
					int src = np.getSrc();
					int seqNum = np.getSeqNum();
					Integer lastSeqNum = rxSequenceTable.get(src);
					if(lastSeqNum == null || seqNum == 0 || src != lastSeqNum){ // New, reset, or different sequence #
						rxSequenceTable.put(src, seqNum);
						state = 0;
						println("Received RTS packet packet from " + src + ", sending CTS", PrettyPrint.COLOR_CYAN);
						// Send a CTS
						NetworkPacket ctsPacket = NetworkPacket.CtsPacket(address, src, seqNum);
						physicalTransceiver.send(ctsPacket.getData());
						// Go to state WAIT_RXSTART
						state = STATE_WAIT_RXSTART;
						// Start a timer for rx to start
						timer = System.currentTimeMillis();
						timeout = TIMEOUT_RXSTART;
					} else { // Same (duplicate) sequence #
						rxSequenceTable.put(src, seqNum);
						println("Duplicate RTS packet packet from " + src + ", sending ARX", PrettyPrint.COLOR_RED);
						// Send an ARX
						NetworkPacket arxPacket = NetworkPacket.ArxPacket(address, src, seqNum);
						physicalTransceiver.send(arxPacket.getData());
					}
				}
				else if(manualPacket != null){
					int finalNode = manualPacket.getFinalNode();
					int nextNode = getRoute(finalNode);
					this.queuedPacket = new NetworkPacket(address, nextNode, address, finalNode,
							getSeqNumForNode(nextNode), 0, NetworkPacket.TYPE_DATA, manualPacket.getPayload());
					manualPacket = null;
					state = STATE_WAIT_TX;
				}
			}
			else if(state == STATE_WAIT_RXSTART) {
				physicalTransceiver.setChannel(mainChannel);
				if(!physicalTransceiver.rxInProgress() && System.currentTimeMillis() > timer + timeout){
					// If a timeout occurred, either they didn't hear our CTS
					// or we didn't hear their data packet.
					// Go back and wait for another RTS
					println("Timed out waiting for data packet", PrettyPrint.COLOR_RED);
					state = STATE_WAIT_RTS;
				}
				if (physicalTransceiver.rxInProgress()) {
					// If a packet starts to be received, wait for it to finish in STATE_WAIT_RXEND
					state = STATE_WAIT_RXEND;
				}
			}
			else if(state == STATE_WAIT_RXEND){
				if(!physicalTransceiver.rxInProgress()){ // Once reception ends...
					NetworkPacket np = getNetworkPacket();
					if(np == null || np.corrupt() || np.getDest() != address || np.getType() != NetworkPacket.TYPE_DATA){ // Dropped or corrupt
						// Go back to waiting for another packet
						if(np != null)
							println("Received irrelevant packet from" + np.getSrc(), PrettyPrint.COLOR_WHITE);
						state = STATE_WAIT_RTS;
					}
					else if(np.getSeqNum() != 0 && np.getSeqNum() == rxSequenceTable.get(np.getSrc())){ // Duplicate sequence #
						state = 0;
						// Send an ACK
						println("Received duplicate sequence packet from " + np.getSrc(), PrettyPrint.COLOR_RED);
						NetworkPacket ackPacket = NetworkPacket.AckPacket(address, np.getSrc(), np.getSeqNum());
						Utils.sleep(30); // TODO make this a defined delay
						physicalTransceiver.send(ackPacket.getData());
						state = STATE_WAIT_RTS;
					} else { // Valid, Non-duplicate data packet.
						int origin = np.getOrigin();
						int finalNode = np.getFinalNode();
						int nextNode = getRoute(finalNode);
						println("Received valid data packet from " + np.getSrc(), PrettyPrint.COLOR_GREEN);
						if(finalNode == address){ // If packet has arrived at the destination, forward that up the stack
							if(receiveHandler != null){
								receiveHandler.receive(np);
								this.queuedPacket = receiveHandler.send();
							} else {
								System.out.println("Packet arrived at destination: " + np);
								this.queuedPacket = null;
							}
						} else {
							this.queuedPacket = new NetworkPacket(address, nextNode, origin, finalNode, getSeqNumForNode(nextNode), 0, NetworkPacket.TYPE_DATA, np.getPayload());
						}
						state = 0;
						// Send an ACK
						println("Acknowledging valid data packet", PrettyPrint.COLOR_GREEN);
						NetworkPacket ackPacket = NetworkPacket.AckPacket(address, np.getSrc(), np.getSeqNum());
						Utils.sleep(30); // TODO make this a defined delay
						physicalTransceiver.send(ackPacket.getData());
						if(finalNode == address) {
							state = STATE_WAIT_RTS; // If packet was delivered, we are done
							println("PACKET SUCCESSFULLY DELIVERED: " + np.getPayloadString(), PrettyPrint.COLOR_BLUE);
						}
						else
							state = STATE_WAIT_TX; // Otherwise, keep forwarding it
						timeout = 0;
						backoffCount = 0;
						timer = System.currentTimeMillis();
					}
				}
			}
			else if(state == STATE_WAIT_TX){
				physicalTransceiver.setChannel(rtsChannel);
				if(!physicalTransceiver.rxInProgress() && System.currentTimeMillis() > timer + timeout){ // Backoff expired, try RTS again
					println("Sending RTS to " + queuedPacket.getDest(), PrettyPrint.COLOR_CYAN);

					NetworkPacket rtsPacket = NetworkPacket.RtsPacket(address, queuedPacket.getDest(), queuedPacket.getSeqNum());
					physicalTransceiver.send(rtsPacket.getData());
					timer = System.currentTimeMillis();
					// Next timeout is randomly chosen from (0, 2^backoffCount-1)
					timeout = (int) (Math.random() * (Math.pow(2, Math.min(MAX_BACKOFF_COUNT, backoffCount)) * TIMEOUT_RTSBACKOFF));
					if(backoffCount < MAX_BACKOFF_COUNT)
						backoffCount++;
					timeout = 1000;
				}
				NetworkPacket np = getNetworkPacket();
				if(np != null && np.getDest() == address && !np.corrupt()){
					if(np.getType() == NetworkPacket.TYPE_CTS) {
						println("Received CTS from " + np.getDest(), PrettyPrint.COLOR_CYAN);
						// If we received a CTS indicating that we are allowed to send,
						// Send the message to the next node and wait for an ACK
						state = 0;
						physicalTransceiver.setChannel(mainChannel);
						Utils.sleep(30); // TODO make this a defined delay
						physicalTransceiver.send(this.queuedPacket.getData());
						state = STATE_WAIT_ACK;
						timer = System.currentTimeMillis();
						timeout = TIMEOUT_ACKRECV;
					}
					if(np.getType() == NetworkPacket.TYPE_ARX) {
						println("Received ARX from " + np.getDest() + ", data was already forwarded", PrettyPrint.COLOR_YELLOW);
						state = STATE_WAIT_RTS;
					}
				}
			}
			else if(state == STATE_WAIT_ACK){
				physicalTransceiver.setChannel(mainChannel);
				if(!physicalTransceiver.rxInProgress() && System.currentTimeMillis() > timer + timeout){
					state = STATE_WAIT_TX;
					println("Timed out waiting for ACK, sending again ", PrettyPrint.COLOR_YELLOW);
				}
				NetworkPacket np = getNetworkPacket();
				if(np != null) {
					Utils.sleep(0);
					if (np.getDest() == address && !np.corrupt() && np.getType() == NetworkPacket.TYPE_ACK) {
						println("Received ACK from " + np.getSrc() + ", ready for next packet", PrettyPrint.COLOR_GREEN);
						state = STATE_WAIT_RTS;
					}
				}
			}
			Utils.sleep(CYCLE_SLEEP);
		}
	}

	/**
	 * If a PhysicalPacket is available on the radio, convert it to a
	 * NetworkPacket and return it
	 * @return received packet (or null if none is available)
	 */
	private NetworkPacket getNetworkPacket(){
		PhysicalPacket p = physicalTransceiver.receive();
		if(p == null) return null;
		return new NetworkPacket(p.getData(), p.getChannel());
	}

	public void addRoutingEntry(int dest, int next) {
		routingTable.put(dest, next);
	}

	/**
	 * Look up the path to a node in the routing table
	 * @param dest Destination node you are trying to get to
	 * @return Node to send to, to get to dest (or -1 if not found)
	 */
	public int getRoute(int dest){
		Integer rtn = routingTable.get(dest);
		if(rtn == null)
			return -1;
		return rtn;
	}

	private int getSeqNumForNode(int nodeId){
		Integer rtn = txSequenceTable.get(nodeId);
		if(rtn == null)
			rtn = 0;
		int rtnx = rtn;
		rtn = rtn + 1;
		if(rtn > 127)
			rtn = 1;
		txSequenceTable.put(nodeId, rtn);
		return rtnx;
	}

	public void addNeighbor(ProtocolTransceiver neighbor, int loss) {
		physicalTransceiver.addNeighbor(neighbor.physicalTransceiver, loss);
	}

	public void startThread(){
		new Thread(physicalTransceiver).start();
		new Thread(this).start();
	}

	private NetworkPacket receiveFromPHY(){
		PhysicalPacket p = physicalTransceiver.receive();
		if(p == null)
			return null;
		NetworkPacket n = new NetworkPacket(p.getData(), p.getChannel());
		return n;
	}


	public void send(int finalNode, byte[] data){
		NetworkPacket tx = new NetworkPacket(0, 0, 0, finalNode, 0, 0, NetworkPacket.TYPE_DATA, data);
		this.manualPacket = tx;
	}

	public void setReceiveHandler(ReceiveHandler receiveHandler) {
		this.receiveHandler = receiveHandler;
	}

//	public void setDebugStream(PrintStream p) {
//		this.debugStream = p;
//	}

	@Override
	/**
	 * A MonitorableTransceiver should return a MonitorState with the following information:
	 * Last digit - State of the FSM
	 * Second to last digit - Physical Radio Status (0 idle, 1 receiving, 2 transmitting)
	 * Third to last digit - Physical Radio Channel (0 main, 1 RTCTS)
	 * NOTE: This method returns state on a best-effort basis and occasional inconsistencies may occur
	 * @return State formatted as integer
	 */
	public int getMonitorState() {
		int fsmState = -1;
		int phyRadioStatus = -1;
		int channelNum = -1;
		boolean verify = true;
		while(verify){ // Run until results consistent across multiple runs.
			// Should make inconsistencies in state rare (possibly nonexistent)
			// The correct way would be to use a lock here
			Thread.yield();
			verify = false;
			if(fsmState != this.state) {
				verify = true;
				fsmState = this.state;
			}
			if(phyRadioStatus != this.physicalTransceiver.getRadioStatus()) {
				verify = true;
				phyRadioStatus = this.physicalTransceiver.getRadioStatus();
			}
			if(channelNum != (this.physicalTransceiver.getChannel() == mainChannel ? 0 : 1)){
				verify = true;
				channelNum = this.physicalTransceiver.getChannel() == mainChannel ? 0 : 1;
			}
		}
		return (fsmState % 10) + (phyRadioStatus * 10) + (channelNum * 100);
	}
}
