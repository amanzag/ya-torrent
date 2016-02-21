/**
 * 
 */
package es.amanzag.yatorrent.protocol.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Optional;

/**
 * @author Alberto Manzaneque Garcia
 *
 */
public class MessageReader {
	
	private final static int MAX_MESSAGE_LENGTH = 4 + 9 + 16384; // piece of 2^14 bytes
	
	protected RawMessage.Type type;
	protected ByteBuffer buffer;
	private boolean valid;
	private int length;
	
	public MessageReader() {
		this.type = null; // still undefined
		buffer = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
		buffer.mark();
		valid = false;
		length = -1;
	}
	
	public boolean isValid() {
		return valid;
	}
	
	public void setHandshakeMode() throws MalformedMessageException {
		if(length != -1) throw new MalformedMessageException("Can not change to handshake mode when it has started to read");
		type = RawMessage.Type.HANDSHAKE;
		length = 49 + 19; // fixed pstr
		buffer.limit(length);
	}
	
	public Optional<RawMessage> readFromChannel(ByteChannel channel) throws IOException, MalformedMessageException {
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
			if(channel.read(buffer) == -1) {
                throw new IOException("EOF reached");
            }
		}
		if(buffer.remaining() == 0) {
			if(type == null) {
				if(length==4) {
                    type = RawMessage.Type.KEEP_ALIVE;
                } else {
                    type = RawMessage.decodeMessageType(buffer.get(4));
                }
			}
			if(type == null) {
                throw new MalformedMessageException("Unrecognised message type");
            }
			valid = true;
			buffer.flip(); // prepare to read the message
		}
		if(valid) {
		    return Optional.of(new RawMessage(type, length, buffer));
		}
		return Optional.empty();
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
	
}
