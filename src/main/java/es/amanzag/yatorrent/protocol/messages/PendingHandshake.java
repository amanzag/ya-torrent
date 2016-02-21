/**
 * 
 */
package es.amanzag.yatorrent.protocol.messages;

import java.nio.ByteBuffer;

/**
 * @author Alberto Manzaneque
 *
 */
public class PendingHandshake extends PendingMessage {
	
	private final static byte PSTRLEN = (byte)19;
	private final static byte[] PSTR = "BitTorrent protocol".getBytes();
	private final static byte[] RESERVED_BYTES = new byte[]{0,0,0,0,0,0,0,0};
	
	private byte[] infoHash;
	private byte[] peerId;
	
	public PendingHandshake(byte[] infoHash, byte[] peerId) {
		super(RawMessage.Type.HANDSHAKE);
		this.infoHash = infoHash;
		this.peerId = peerId;
	}

	/* (non-Javadoc)
	 * @see nodomain.applewhat.torrentdemonio.protocol.messages.PendingMessage#writeToBuffer(java.nio.ByteBuffer)
	 */
	@Override
	public void writeToBuffer(ByteBuffer buffer) {
		buffer.put(PSTRLEN);
		buffer.put(PSTR);
		buffer.put(RESERVED_BYTES);
		buffer.put(infoHash);
		buffer.put(peerId);
	}

}
