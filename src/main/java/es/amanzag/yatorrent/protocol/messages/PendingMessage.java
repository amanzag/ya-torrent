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
	
	private Message.Type type;
	
	protected PendingMessage(Message.Type type) {
		this.type = type;
	}

	public Message.Type getType() {
		return type;
	}
	
	public abstract void writeToBuffer(ByteBuffer buffer);
}
