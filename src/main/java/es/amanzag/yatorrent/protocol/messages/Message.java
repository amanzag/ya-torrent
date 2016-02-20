/**
 * 
 */
package es.amanzag.yatorrent.protocol.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import es.amanzag.yatorrent.protocol.BitField;
import es.amanzag.yatorrent.protocol.TorrentProtocolException;

/**
 * @author Alberto Manzaneque Garcia
 *
 */
public class Message {
	
	private final static int MAX_MESSAGE_LENGTH = 4 + 9 + 16384; // piece of 2^14 bytes
	
	public static enum Type {
		CHOKE(0), UNCHOKE(1), INTERESTED(2), NOT_INTERESTED(3), HAVE(4),
		BITFIELD(5), REQUEST(6), PIECE(7), CANCEL(8), HANDSHAKE(-1), KEEP_ALIVE(-2);
		
		private byte id;
		private Type(int id) {
			this.id = (byte)id;
		}
		public byte getId() { return id; }
	}

	protected Type type;
	protected ByteBuffer buffer;
	private boolean valid;
	private int length;
	
	/**
	 * 
	 */
	public Message() {
		this.type = null; // still undefined
		buffer = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
		buffer.mark();
		valid = false;
		length = -1;
	}
	
	public Type getType() {
		return type;
	}
	
	public boolean isValid() {
		return valid;
	}
	
	public void setHandshakeMode() throws MalformedMessageException {
		if(length != -1) throw new MalformedMessageException("Can not change to handshake mode when it has started to read");
		type = Type.HANDSHAKE;
		length = 49 + 19; // fixed pstr
		buffer.limit(length);
	}
	
	public void createFromChannel(ByteChannel channel) throws IOException, MalformedMessageException {
		if(isValid()) throw new MalformedMessageException("The whole message is already read");
		if(length == -1) {
			buffer.limit(4);
			// FIXME hacer algo mas ademas de tirar excepcion??
			if(channel.read(buffer) == -1) throw new IOException("EOF reached");
			if(buffer.remaining() == 0) {
				length = buffer.getInt(0)+4;
				buffer.limit(length);
			}
		}
		if(length != -1 && buffer.remaining()>0) {
			if(channel.read(buffer) == -1) throw new IOException("EOF reached");
		}
		if(buffer.remaining() == 0) {
			if(type == null) {
				if(length==4) type = Type.KEEP_ALIVE;
				else type = decodeMessageType(buffer.get(4));
			}
			if(type == null) throw new MalformedMessageException("Unrecognised message type");
			valid = true;
			buffer.flip(); // prepare to write the message
		}
	}
	
	public void createFromPending(PendingMessage pending) {
		reset();
		buffer.clear();
		buffer.mark();
		pending.writeToBuffer(buffer);
		buffer.flip();
		type = pending.getType();
		valid = true;
		length = buffer.limit();
	}
	
	public int writeToChannel(ByteChannel channel) throws MalformedMessageException, IOException {
		if(!isValid()) throw new MalformedMessageException("Message is not valid");
		channel.write(buffer);
		return buffer.remaining();
	}
	
	public int remainingBytes() {
		return buffer.remaining();
	}
	
	public void reset() {
		length = -1;
		type = null;
		valid = false;
		buffer.position(0).limit(0);
	}
	
	public ByteBuffer getData() {
		return buffer;
	}
	
	private static Type decodeMessageType(byte type) {
		if(type == Type.CHOKE.getId()) {
			return Type.CHOKE;
		} else if(type == Type.UNCHOKE.getId()) {
			return Type.UNCHOKE;
		} else if(type == Type.INTERESTED.getId()) {
			return Type.INTERESTED;
		} else if(type == Type.NOT_INTERESTED.getId()) {
			return Type.NOT_INTERESTED;
		} else if(type == Type.HAVE.getId()) {
			return Type.HAVE;
		} else if(type == Type.BITFIELD.getId()) {
			return Type.BITFIELD;
		} else if(type == Type.REQUEST.getId()) {
			return Type.REQUEST;
		} else if(type == Type.PIECE.getId()) {
			return Type.PIECE;
		} else if(type == Type.CANCEL.getId()) {
			return Type.CANCEL;
		} else if(type == Type.HANDSHAKE.getId()) {
			return Type.HANDSHAKE;
		} else {
			return null;
		}
	}
	
	public static int parseHave(Message msg) {
		return msg.getData().getInt(5);
	}
	
	public static int[] parseRequest(Message msg) {
		int[] result = new int[3];
		result[0] = msg.getData().getInt(5);
		result[1] = msg.getData().getInt(9);
		result[2] = msg.getData().getInt(13);
		return result;
	}
	
	public static int[] parseCancel(Message msg) {
		return parseRequest(msg);
	}
	
	public static byte[][] parseHandshake(Message msg) {
		byte[][] result = new byte[2][20];
		msg.getData().position(28);
		msg.getData().get(result[0], 0, 20);
		msg.getData().position(48);
		msg.getData().get(result[1], 0, 20);
		return result;
	}
	
	public static BitField parseBitField(Message msg, int length) {
	    byte[] bitfield = new byte[msg.length-5];
	    msg.getData().get(bitfield);
	    try {
            return new BitField(length, bitfield);
        } catch (IllegalArgumentException e) {
            throw new TorrentProtocolException("Expected bitfield length doesn't match the one received", e);
        }
	}

}
