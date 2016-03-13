/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.messages.MalformedMessageException;
import es.amanzag.yatorrent.protocol.messages.MessageReader;
import es.amanzag.yatorrent.protocol.messages.MessageWriter;
import es.amanzag.yatorrent.protocol.messages.RawMessage;
import es.amanzag.yatorrent.storage.Piece;
import es.amanzag.yatorrent.storage.TorrentStorageException;
import es.amanzag.yatorrent.util.ConfigManager;

/**
 * @author Alberto Manzaneque
 *
 */
public class PeerConnection implements PeerMessageProducer {
	
    private final static int BLOCK_SIZE = 16 * 1024;
	private static Logger logger = LoggerFactory.getLogger(PeerConnection.class);
	
	private Peer peer;
	private boolean amInterested, amChoking, peerInterested, peerChoking;
	private SocketChannel channel;
	private boolean handshakeSent, handshakeReceived;
	private List<PeerMessageListener> listeners;
	private MessageReader messageReader;
	private MessageWriter messageWriter;
	private Optional<TorrentMetadata> torrentMetadata;
	private Optional<BitField> bitField;
	private Optional<Piece> pieceDownloading;
	
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
		listeners = new ArrayList<PeerMessageListener>(2);
		addMessageListener(new MessageProcessor());
		messageReader = new MessageReader();
		messageWriter = new MessageWriter();
		messageReader.setHandshakeMode();
		torrentMetadata = Optional.empty();
		bitField = Optional.empty();
		pieceDownloading = Optional.empty();
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
		    notifyMessageListeners(c -> c.onChoke());
			break;
		case UNCHOKE:
		    notifyMessageListeners(c -> c.onUnchoke());
			break;
		case INTERESTED:
		    notifyMessageListeners(c -> c.onInterested());
			break;
		case NOT_INTERESTED:
		    notifyMessageListeners(c -> c.onNotInterested());
			break;
		case HAVE:
		    notifyMessageListeners(c -> c.onHave(RawMessage.parseHave(msg)));
			break;
		case BITFIELD:
			BitField receivedBitField = RawMessage.parseBitField(msg, bitField.get().getSize());
			notifyMessageListeners(c -> c.onBitfield(receivedBitField));
			break;
		case REQUEST: {
			int[] params = RawMessage.parseRequest(msg);
			notifyMessageListeners(c -> c.onRequest(params[0], params[1], params[2]));
			break; 
		}
		case PIECE: {
		    Object[] params = RawMessage.parsePiece(msg);
		    notifyMessageListeners(c -> c.onBlock((Integer)params[0], (Integer)params[1], (ByteBuffer)params[2]));
		    break;
		}
		case CANCEL: {
			int[] params = RawMessage.parseCancel(msg);
			notifyMessageListeners(c -> c.onCancel(params[0], params[1], params[2]));
			break; 
		}
		case HANDSHAKE: {
			byte[][] params = RawMessage.parseHandshake(msg);
			notifyMessageListeners(c -> c.onHandshake(params[0], params[1]));
			break; 		
		}
		case KEEP_ALIVE:
		    notifyMessageListeners(c -> c.onKeepAlive());
			break; 
		}
	}
	
	public void kill() {
	    logger.debug("Closing peer {}", peer);
		try {
			channel.close();
		} catch (IOException e) {
			logger.debug("Error when trying to close connecion with "+peer+". "+e.getMessage());
		}
		if(isDownloading()) {
		    pieceDownloading.get().unlock();
		    pieceDownloading = Optional.empty();
		}
		notifyMessageListeners(listener -> listener.onDisconnect());
		listeners.clear();
	}

	@Override
	public void addMessageListener(PeerMessageListener listener) {
		if(!listeners.contains(listener)) {
            listeners.add(listener);
        }
	}

	@Override
	public void removeMessageListener(PeerMessageListener listener) {
		listeners.remove(listener);
	}
	
	private void notifyMessageListeners(Consumer<PeerMessageListener> c) {
	    new ArrayList<>(listeners).stream().forEach(c);
	}
	
	private class MessageProcessor implements PeerMessageListener {
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
		
		@Override
		public void onBlock(int index, int offset, ByteBuffer data) {
		    if (!pieceDownloading.isPresent()) {
		        logger.error("Got a block that wasn't expecting. Closing connection with peer {}", peer);
		        kill();
		    }
		    Piece piece = pieceDownloading.get();
		    if(piece.getIndex() != index) {
		        logger.error("Got block of a different piece than expected");
		        kill();
		    } else if(piece.getCompletion() != offset) {
		        logger.error("Piece {}. Got block starting in {} but was expecting {}",
		                index, offset, piece.getCompletion());
		        kill();
		    } else {
		        logger.debug("Received block [index={}, offset={}, length={}] from peer {}",
		                index, offset, data.remaining(), peer);
		        try {
		            piece.write(data);
		            if(piece.isComplete()) {
		                logger.debug("Finished downloading piece {}", index);
		                pieceDownloading = Optional.empty();
		                piece.unlock();
		                notifyMessageListeners(listener -> listener.onPiece(piece.getIndex()));
		            }
		        } catch (IOException e) {
		            logger.warn("Error writing to file", e);
		        } catch (TorrentStorageException e) {
		            logger.error("Error storing piece", e);
		        }
		    }
		}
	}
	
	public Peer getPeer() {
		return peer;
	}
	
	public BitField getBitField() {
        return bitField.orElseThrow(() -> new IllegalStateException("Peer is not linked to any torrent yet"));
    }
	
	public void sendHandshake() {
	    if (handshakeSent) {
	        throw new TorrentProtocolException("Trying to send a handshake but it was already sent");
	    }
	    messageWriter.send(RawMessage.createHandshake(
	            torrentMetadata.get().getInfoHash(), 
	            ConfigManager.getClientId().getBytes()));
	    handshakeSent = true;
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
	
	public boolean isAmChoking() {
        return amChoking;
    }
	
	public boolean isPeerChoking() {
        return peerChoking;
    }
	
	public void setAmChoking(boolean amChoking) {
	    if(amChoking && !this.amChoking) {
	        messageWriter.send(RawMessage.createChoke());
	        logger.debug("Sending Choke message to peer {}", peer);
	    } else if(!amChoking && this.amChoking) {
	        messageWriter.send(RawMessage.createUnchoke());
	        logger.debug("Sending Unchoke message to peer {}", peer);
	    }
        this.amChoking = amChoking;
    }
	
	private void requestBlock(int pieceIndex, int offset, int length) {
	    messageWriter.send(RawMessage.createRequest(pieceIndex, offset, length));
	    logger.debug("Sending Request message [index={}, offset={}, length={}] to peer {}", 
	            pieceIndex, offset, length, peer);
	}
	
	public void download(Piece piece) {
	    logger.debug("Starting download of piece {} from peer {}", piece.getIndex(), peer);
	    if (isDownloading()) {
	        throw new IllegalStateException("Already downloading a piece, can't start downloading another one");
	    }
	    piece.lock();
	    pieceDownloading = Optional.of(piece);
	    int tempCompletion = piece.getCompletion();
        while(tempCompletion < piece.getLength()) {
            requestBlock(piece.getIndex(), tempCompletion, Math.min(BLOCK_SIZE, piece.getLength()-tempCompletion));
            tempCompletion += BLOCK_SIZE;
        }
	}
	
	public boolean isDownloading() {
	    return pieceDownloading.isPresent();
	}
	
}
