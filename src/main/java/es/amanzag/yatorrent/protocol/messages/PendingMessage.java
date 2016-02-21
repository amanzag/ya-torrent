/**
 * 
 */
package es.amanzag.yatorrent.protocol.messages;

import java.nio.ByteBuffer;

/**
 * @author Alberto Manzaneque
 *
 */
public abstract class PendingMessage {
	
	private RawMessage.Type type;
	
	protected PendingMessage(RawMessage.Type type) {
		this.type = type;
	}

	public RawMessage.Type getType() {
		return type;
	}
	
	public abstract void writeToBuffer(ByteBuffer buffer);
}
