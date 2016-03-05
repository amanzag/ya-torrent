/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.messages.MalformedMessageException;
import es.amanzag.yatorrent.protocol.messages.MessageReader;
import es.amanzag.yatorrent.protocol.messages.MessageWriter;
import es.amanzag.yatorrent.protocol.messages.RawMessage;
import es.amanzag.yatorrent.util.ConfigManager;

/**
 * @author Alberto Manzaneque
 *
 */
public class PeerConnection implements PeerMessageProducer {
	
	private static Logger logger = LoggerFactory.getLogger(PeerConnection.class);
	
	private Peer peer;
	private boolean amInterested, amChoking, peerInterested, peerChoking;
	private SocketChannel channel;
	private boolean handshakeSent, handshakeReceived;
	private List<PeerMessageAdapter> listeners;
	private MessageReader messageReader;
	private MessageWriter messageWriter;
	private Optional<TorrentMetadata> torrentMetadata;
	private Optional<BitField> bitField;
	
	/**
	 * Sometimes we don't know the infoHash and torrent until we receive the handshake, for instance,
	 * when we're receiving a connection. That's why we need this constructor.
	 */
	public PeerConnection(Peer peer, SocketChannel channel) {
		this.peer = peer;
		this.channel = channel;
		amInterested = peerInterested = false;
		amChoking = peerChoking = true;
		handshakeSent = handshakeReceived = false;
		listeners = new ArrayList<PeerMessageAdapter>(2);
		addMessageListener(new MessageProcessor());
		messageReader = new MessageReader();
		messageWriter = new MessageWriter();
		try {
			messageReader.setHandshakeMode();
		} catch (MalformedMessageException e) {
			e.printStackTrace();
		}
		torrentMetadata = Optional.empty();
		bitField = Optional.empty();
	}
	
	public PeerConnection(Peer peer, SocketChannel channel, TorrentMetadata torrentMetadata) {
		this(peer, channel);
		this.torrentMetadata = Optional.of(torrentMetadata);
		this.bitField = Optional.of(new BitField(torrentMetadata.getPieceHashes().size()));
	}

	public SocketChannel getChannel() {
		return channel;
	}
	
	public void doRead() throws MalformedMessageException, IOException {
		messageReader.readFromChannel(channel).ifPresent(msg -> {
		    onMessageReceived(msg);
		    messageReader.reset();
		    if(!handshakeReceived) messageReader.setHandshakeMode();
		});;
	}
	
	/**
	 * @return true if there is nothing pending to write
	 * @throws MalformedMessageException
	 * @throws IOException
	 */
	public boolean doWrite() throws MalformedMessageException, IOException {
	    boolean remaining = false;
		if(messageWriter.isBusy()) {
			remaining = !messageWriter.writeToChannel(channel);
			if(!remaining) {
			    logger.debug("Message sent to peer {}", peer);
			}
		}
		return !remaining;
	}
	
	protected void onMessageReceived(RawMessage msg) {
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
				adapter.onHave(RawMessage.parseHave(msg));
			}
			break;
		case BITFIELD:
			BitField receivedBitField = RawMessage.parseBitField(msg, bitField.get().getSize());
			for (PeerMessageAdapter adapter : listeners) {
			    adapter.onBitfield(receivedBitField);
			}
			break;
		case REQUEST: {
			int[] params = RawMessage.parseRequest(msg);
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onRequest(params[0], params[1], params[2]);
			}
			break; 
		}
		case PIECE:
			// TODO
			break;
		case CANCEL: {
			int[] params = RawMessage.parseCancel(msg);
			for (PeerMessageAdapter adapter : listeners) {
				adapter.onCancel(params[0], params[1], params[2]);
			}
			break; 
		}
		case HANDSHAKE: {
			byte[][] params = RawMessage.parseHandshake(msg);
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
		public void onHave(int chunkIndex) {
		    bitField.orElseThrow(() -> new IllegalStateException()).setPresent(chunkIndex, true);
		}
		
		@Override
		public void onHandshake(byte[] infoHash, byte[] peerId) {
		    if(torrentMetadata.isPresent() && !Arrays.equals(infoHash, torrentMetadata.get().getInfoHash())) {
		        throw new TorrentProtocolException("info_hash received in the handshake doesn't correspond to the torrent file");
		    };
			handshakeReceived = true;
			peer.setId(peerId);
			logger.debug("Handshake received from peer "+peer);
		}
		
		@Override
		public void onBitfield(BitField receivedBitField) {
		    logger.debug("Bitfield received from peer {}", peer);
		    if(!handshakeReceived) {
		        throw new TorrentProtocolException("no handshake received before bitfield");
		    }
		    bitField.get().add(receivedBitField);
		}
		
	}
	
	public Peer getPeer() {
		return peer;
	}
	
	public BitField getBitField() {
        return bitField.orElseThrow(() -> new IllegalStateException("Peer is not linked to any torrent yet"));
    }
	
	public void sendHandshake() {
	    messageWriter.send(RawMessage.createHandshake(
	            torrentMetadata.get().getInfoHash(), 
	            ConfigManager.getClientId().getBytes()));
	    logger.debug("Handshake queued to be sent to peer {}", peer);
	}
	
	public void sendBitField(BitField localBitField) {
	    messageWriter.send(RawMessage.createBitField(localBitField));
	    logger.debug("Bitfield queued to be sent to peer {}", peer);
	}
	
	public boolean isAmInterested() {
        return amInterested;
    }
	
	public boolean isPeerInterested() {
        return peerInterested;
    }
	
	public void setAmInterested(boolean amInterested) {
	    if(amInterested && !this.amInterested) {
	        messageWriter.send(RawMessage.createInterested());
	        logger.debug("Sending Interested message to peer {}", peer);
	    } else if (!amInterested && this.amInterested) {
	        messageWriter.send(RawMessage.createNotInterested());
	        logger.debug("Sending NotInterested message to peer {}", peer);
	    }
        this.amInterested = amInterested;
    }
	
}
