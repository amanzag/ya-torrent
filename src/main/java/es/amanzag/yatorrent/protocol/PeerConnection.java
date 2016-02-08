/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.protocol.messages.MalformedMessageException;
import es.amanzag.yatorrent.protocol.messages.Message;
import es.amanzag.yatorrent.protocol.messages.PendingMessage;

/**
 * @author Alberto Manzaneque
 *
 */
public class PeerConnection implements PeerMessageProducer {
	
	private static Logger logger = LoggerFactory.getLogger(PeerConnection.class);
	
	private Peer peer;
	private boolean amInterested, amChoking, peerInterested, peerChoking;
	private byte[] infoHash;
	private SocketChannel channel;
	private boolean handshakeSent, handshakeReceived;
	private List<PeerMessageAdapter> listeners;
	private Message recv, send;
	private List<PendingMessage> pending;
	private PendingMessage currentSending;
	
	public PeerConnection(Peer peer, SocketChannel channel) {
		this.peer = peer;
		this.channel = channel;
		amInterested = peerInterested = false;
		amChoking = peerChoking = true;
		handshakeSent = handshakeReceived = false;
		listeners = new ArrayList<PeerMessageAdapter>(2);
		addMessageListener(new MessageProcessor());
		recv = new Message();
		send = new Message();
		pending = new ArrayList<PendingMessage>();
		currentSending = null;
		try {
			recv.setHandshakeMode();
		} catch (MalformedMessageException e) {
			e.printStackTrace();
		}
	}
	
	public PeerConnection(Peer peer, SocketChannel channel, byte[] infoHash) {
		this(peer, channel);
		this.infoHash = infoHash;
	}

	public SocketChannel getChannel() {
		return channel;
	}
	
	public void doRead() throws MalformedMessageException, IOException {
		recv.createFromChannel(channel);
		if(recv.isValid()) {
			onMessageReceived(recv);
			recv.reset();
			if(!handshakeReceived) recv.setHandshakeMode();
		}
	}
	
	/**
	 * @return true if there is still something to write
	 * @throws MalformedMessageException
	 * @throws IOException
	 */
	public boolean doWrite() throws MalformedMessageException, IOException {
		boolean somethingPending = false;
		if(send.isValid()) {
			int remaining = send.writeToChannel(channel);
			if(remaining == 0) {
				for (PeerMessageAdapter l : listeners) {
					l.onMessageSent(currentSending);
				}
				send.reset();
				currentSending = null;
				if(pending.size() > 0) {
					currentSending = pending.remove(0);
					send.createFromPending(currentSending);
					somethingPending = true;
				}
			} else {
				somethingPending = true;
			}
		}
		return somethingPending;
	}
	
	protected void onMessageReceived(Message msg) {
		switch(msg.getType()) {
		case CHOKE:
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onChoke();
			}
			break;
		case UNCHOKE:
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onUnchoke();
			}
			break;
		case INTERESTED:
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onInterested();
			}
			break;
		case NOT_INTERESTED:
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onNotInterested();
			}
			break;
		case HAVE:
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onHave(Message.parseHave(recv));
			}
			break;
		case BITFIELD:
			//TODO
			break;
		case REQUEST: {
			int[] params = Message.parseRequest(recv);
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onRequest(params[0], params[1], params[2]);
			}
			break; 
		}
		case PIECE:
			// TODO
			break;
		case CANCEL: {
			int[] params = Message.parseCancel(recv);
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onCancel(params[0], params[1], params[2]);
			}
			break; 
		}
		case HANDSHAKE: {
			byte[][] params = Message.parseHandshake(recv);
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onHandshake(params[0], params[1]);
			}
			break; 		
		}
		case KEEP_ALIVE:
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onKeepAlive();
			}
			break; 
		}
	}
	

	
	public void kill() {
		try {
			channel.close();
		} catch (IOException e) {
			logger.debug("Error when trying to close connecion with "+peer+". "+e.getMessage());
		}
	}

	@Override
	public void addMessageListener(PeerMessageAdapter listener) {
		if(!listeners.contains(listener))
			listeners.add(listener);
	}

	@Override
	public void removeMessageListener(PeerMessageAdapter listener) {
		listeners.remove(listener);
	}
	
	private class MessageProcessor extends PeerMessageAdapter {
		@Override
		public void onChoke() {
			peerChoking = true;
			logger.debug("Peer "+peer+" has chocked");
		}
		
		@Override
		public void onUnchoke() {
			peerChoking = false;
			logger.debug("Peer "+peer+" has unchocked");
		}
		
		@Override
		public void onInterested() {
			peerInterested = true;
			logger.debug("Peer "+peer+" is interested");
		}
		
		@Override
		public void onNotInterested() {
			peerInterested = false;
			logger.debug("Peer "+peer+" is no longer interested");
		}
		
		@Override
		public void onHandshake(byte[] infoHash, byte[] peerId) {
			handshakeReceived = true;
			PeerConnection.this.infoHash = infoHash;
			peer.setId(peerId);
			logger.debug("Handshake received from peer "+peer);
		}
		
		@Override
		public void onMessageSent(PendingMessage msg) {
			logger.debug(msg.getType()+" sent to peer "+peer);
		}
		
		
	}
	
	public Peer getPeer() {
		return peer;
	}
	
	public byte[] getInfoHash() {
		return infoHash;
	}
	
	public void enqueue(PendingMessage msg) {
		pending.add(msg);
		if(!send.isValid()) {
			PendingMessage tmp = pending.remove(0);
			send.createFromPending(tmp);
			currentSending = tmp;
		}
	}
	
}
