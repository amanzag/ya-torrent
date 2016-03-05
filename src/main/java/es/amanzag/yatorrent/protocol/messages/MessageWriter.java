package es.amanzag.yatorrent.protocol.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageWriter {
    
    private final static Logger logger = LoggerFactory.getLogger(MessageWriter.class);
    
    private LinkedList<RawMessage> messageQueue;
    private RawMessage currentSending;
    
    public MessageWriter() {
        this.messageQueue = new LinkedList<>();
    }
    
    /**
     * @return true if there is nothing pending to be sent, false if there is
     * @throws IOException
     */
    public boolean writeToChannel(ByteChannel channel) throws IOException {
        while(isBusy()) {
            ByteBuffer buffer = currentSending.getRawData();
            channel.write(buffer);
            if(buffer.hasRemaining()) {
                return false;
            } else {
                logger.debug("{} sent", currentSending.getType());
                currentSending = null;
                if(!messageQueue.isEmpty()) {
                    currentSending = messageQueue.removeFirst();
                }
            }
        }
        return true;
    }
    
    public void send(RawMessage msg) {
        if(isBusy()) {
            messageQueue.add(msg);
        } else {
            currentSending = msg;
        }
    }
    
    public boolean isBusy() {
        return currentSending != null || !messageQueue.isEmpty();
    }
    
}
