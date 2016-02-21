package es.amanzag.yatorrent.protocol.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public class MessageWriter {
    
    private ByteBuffer buffer;
    private boolean done;
    
    public MessageWriter() {
        reset();
    }
    
    /**
     * @return true if a message was sent, false if it didn't finish
     * @throws IOException
     */
    public boolean writeToChannel(ByteChannel channel) throws IOException {
        if(done) {
            throw new MalformedMessageException("Message is already sent");
        }
        channel.write(buffer);
        if(buffer.hasRemaining()) {
            return false;
        } else {
            reset();
            return true;
        }
    }
    
    public void send(RawMessage msg) {
        if(isValid()) {
            // TODO should messages be queued here?
            throw new IllegalStateException();
        }
        buffer = msg.getRawData();
    }
    
    private void reset() {
        buffer = null;
        done = false;
    }
    
    public boolean isValid() {
        return buffer != null;
    }

}
