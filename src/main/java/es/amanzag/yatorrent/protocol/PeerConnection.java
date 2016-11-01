/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.io.MessageReader;
import es.amanzag.yatorrent.protocol.io.MessageWriter;
import es.amanzag.yatorrent.protocol.messages.MalformedMessageException;
import es.amanzag.yatorrent.protocol.messages.RawMessage;
import es.amanzag.yatorrent.storage.Piece;
import es.amanzag.yatorrent.storage.TorrentStorage;
import es.amanzag.yatorrent.storage.TorrentStorageException;
import es.amanzag.yatorrent.util.ConfigManager;

/**
 * @author Alberto Manzaneque
 *
 */
public class PeerConnection implements PeerMessageProducer {
	
    private final static int MAX_BLOCK_QUEUE_SIZE = 5;
    private final static int MAX_BLOCK_REQUEST = 16 * 1024;
    private final static int BLOCK_SIZE = MAX_BLOCK_REQUEST;
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
	private Optional<DownloadStatus> downloadStatus;
	private Optional<TorrentStorage> storage;
	private boolean seeder;
	
	private LinkedList<BlockRequest> requestsQueue;
	private final static int MAX_REQUEST_QUEUE_SIZE = 10;
	
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
		downloadStatus = Optional.empty();
		requestsQueue = new LinkedList<>();
		seeder = false;
	}
	
	public PeerConnection(Peer peer, SocketChannel channel, TorrentStorage storage, TorrentMetadata torrentMetadata) {
		this(peer, channel);
		this.torrentMetadata = Optional.of(torrentMetadata);
		this.bitField = Optional.of(new BitField(torrentMetadata.getPieceHashes().size()));
		this.storage = Optional.of(storage);
	}

	public SocketChannel getChannel() {
		return channel;
	}
	
	public int doRead() throws MalformedMessageException, IOException {
		messageReader.readFromChannel(channel).ifPresent(msg -> {
		    onMessageReceived(msg);
		    messageReader.reset();
		    if(!handshakeReceived) {
                messageReader.setHandshakeMode();
            }
		});
		return messageReader.getBytesRead();
	}
	
	/**
	 * @return true if there is nothing pending to write
	 * @throws MalformedMessageException
	 * @throws IOException
	 */
	public void doWrite() throws MalformedMessageException, IOException {
		if(messageWriter.isBusy()) {
			messageWriter.writeToChannel(channel);
		}
		if(!messageWriter.isBusy()) {
		    fulfilNextUploadRequest();
		}
	}
	
	public boolean isWriting() {
	    return messageWriter.isBusy();
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
	    logger.debug("Closing peer {}", this);
		try {
			channel.close();
		} catch (IOException e) {
			logger.debug("Error when trying to close connecion with "+this+". "+e.getMessage());
		}
		if(isDownloading()) {
		    downloadStatus.get().piece.unlock();
		    downloadStatus = Optional.empty();
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
			logger.debug("Peer "+PeerConnection.this+" has choked");
		}
		
		@Override
		public void onUnchoke() {
			peerChoking = false;
			logger.debug("Peer "+PeerConnection.this+" has unchoked");
		}
		
		@Override
		public void onInterested() {
			peerInterested = true;
			logger.debug("Peer "+PeerConnection.this+" is interested");
		}
		
		@Override
		public void onNotInterested() {
			peerInterested = false;
			logger.debug("Peer "+PeerConnection.this+" is no longer interested");
		}
		
		@Override
		public void onHave(int chunkIndex) {
		    bitField.orElseThrow(() -> new IllegalStateException()).setPresent(chunkIndex, true);
		    seeder = !bitField.get().hasBitsUnset();
		    logger.debug("Have {} received from peer {}", chunkIndex, PeerConnection.this);
		}
		
		@Override
		public void onHandshake(byte[] infoHash, byte[] peerId) {
		    if(torrentMetadata.isPresent() && !Arrays.equals(infoHash, torrentMetadata.get().getInfoHash())) {
		        throw new TorrentProtocolException("info_hash received in the handshake doesn't correspond to the torrent file");
		    };
			handshakeReceived = true;
			peer.setId(peerId);
			logger.debug("Handshake received from peer {}", PeerConnection.this);
		}
		
		@Override
		public void onBitfield(BitField receivedBitField) {
		    if (logger.isDebugEnabled()) {
		        String type = receivedBitField.hasBitsUnset() ? "LEECHER" : "SEEDER";
		        logger.debug("Bitfield received from peer {} ({})", PeerConnection.this, type);
		    }
		    if(!handshakeReceived) {
		        throw new TorrentProtocolException("no handshake received before bitfield");
		    }
		    bitField.get().add(receivedBitField);
		    seeder = !bitField.get().hasBitsUnset();
		}
		
		@Override
		public void onBlock(int index, int offset, ByteBuffer data) {
		    if (!downloadStatus.isPresent()) {
		        logger.error("Got a block that wasn't expecting. Closing connection with peer {}", PeerConnection.this);
		        kill();
		    }
		    Piece piece = downloadStatus.get().piece;
		    if(piece.getIndex() != index) {
		        logger.error("Got block of a different piece than expected");
		        kill();
		    } else {
		        logger.debug("Received block [index={}, offset={}, length={}] from peer {}",
		                index, offset, data.remaining(), PeerConnection.this);
		        try {
		            piece.write(offset, data);
		            if(piece.isComplete()) {
		                logger.debug("Finished downloading piece {}", index);
		                downloadStatus = Optional.empty();
		                piece.unlock();
		                notifyMessageListeners(listener -> listener.onPiece(piece.getIndex()));
		            } else {
		                downloadStatus.ifPresent(status -> status.queueSize--);
		                scheduleMoreBlockRequests();
		            }
		        } catch (IOException e) {
		            logger.warn("Error writing to file", e);
		        } catch (TorrentStorageException e) {
		            logger.error("Error storing piece", e);
		            kill();
		        }
		    }
		}
		
		@Override
		public void onRequest(int pieceIndex, int offset, int length) {
		    if(requestsQueue.size() >= MAX_REQUEST_QUEUE_SIZE) {
		        logger.info("Peer {} tried to queue too many requests. Disconnecting", PeerConnection.this);
		        kill();
		    } else if (length > MAX_BLOCK_REQUEST) {
		        logger.info("Peer {} requested a block too big ({} bytes). Disconnecting", PeerConnection.this, length);
		        kill();
		    } else if (!storage.isPresent()) {
		        logger.info("Received a request from a peer that isn't fully initialized ({}). Disconnecting", PeerConnection.this);
		        kill();
		    } else {
		        try {
		            Piece piece = storage.get().piece(pieceIndex);
		            if (!piece.isComplete()) {
		                logger.info("Peer {} requested a piece that isn't complete yet ({}). Disconnecting", PeerConnection.this, pieceIndex);
		                kill();
		            } else if (offset + length > piece.getLength()) {
		                logger.info("Peer {} requested a block that isn't within the bounds of the piece. Disconnecting", PeerConnection.this);
		                kill();
		            } else {
		                requestsQueue.addFirst(new BlockRequest(pieceIndex, offset, length));
		                if(!messageWriter.isBusy()) {
		                    fulfilNextUploadRequest();
		                }
		            }
		        } catch (IndexOutOfBoundsException e) {
		            logger.info("Peer {} requested a piece that doesn't exist ({}). Disconnecting", PeerConnection.this, pieceIndex);
		            kill();
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
	    logger.debug("Handshake queued to be sent to peer {}", this);
	}
	
	public void sendBitField(BitField localBitField) {
	    messageWriter.send(RawMessage.createBitField(localBitField));
	    logger.debug("Bitfield queued to be sent to peer {}", this);
	}
	
	public void sendHave(int pieceIndex) {
	    messageWriter.send(RawMessage.createHave(pieceIndex));
	    logger.debug("Have {} queued to be sent to peer {}", pieceIndex, this);
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
	        logger.debug("Sending Interested message to peer {}", this);
	    } else if (!amInterested && this.amInterested) {
	        messageWriter.send(RawMessage.createNotInterested());
	        logger.debug("Sending NotInterested message to peer {}", this);
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
	        logger.debug("Sending Choke message to peer {}", this);
	    } else if(!amChoking && this.amChoking) {
	        messageWriter.send(RawMessage.createUnchoke());
	        logger.debug("Sending Unchoke message to peer {}", this);
	    }
        this.amChoking = amChoking;
    }
	
	private void requestBlock(int pieceIndex, int offset, int length) {
	    messageWriter.send(RawMessage.createRequest(pieceIndex, offset, length));
	    logger.debug("Sending Request message [index={}, offset={}, length={}] to peer {}", 
	            pieceIndex, offset, length, this);
	}
	
	public void download(Piece piece) {
	    logger.debug("Starting download of piece {} from peer {}", piece.getIndex(), this);
	    if (isDownloading()) {
	        throw new IllegalStateException("Already downloading a piece, can't start downloading another one");
	    }
	    piece.lock();
	    downloadStatus = Optional.of(new DownloadStatus(piece, piece.getCompletion()-1));
	    scheduleMoreBlockRequests();
	}
	
	private void scheduleMoreBlockRequests() {
        DownloadStatus status = downloadStatus.get();
        while(status.queueSize < MAX_BLOCK_QUEUE_SIZE && 
                status.lastRequestedByte < status.piece.getLength()-1) {
            requestBlock(
                    status.piece.getIndex(), 
                    status.lastRequestedByte+1, 
                    Math.min(BLOCK_SIZE, status.piece.getLength() - (status.lastRequestedByte+1))
                    );
            status.lastRequestedByte += BLOCK_SIZE;
            status.queueSize++;
        }
	}
	
	public boolean isDownloading() {
	    return downloadStatus.isPresent();
	}
	
	private void fulfilNextUploadRequest() {
	    if (requestsQueue.isEmpty() || !storage.isPresent()) {
	        return;
	    }
	    BlockRequest block = requestsQueue.removeLast();
	    try {
            messageWriter.send(RawMessage.createPiece(block, storage.get().piece(block.pieceIndex)));
        } catch (IOException e) {
            logger.error("Error reading piece from disk. Closing connection with peer.", e);
            kill();
        }
	}
	
	@Override
	public String toString() {
	    return peer.toString() + ", " + (seeder ? "SEEDER" : "LEECHER");
	}
	
	private final static class DownloadStatus {
	    final Piece piece;
	    int queueSize;
	    int lastRequestedByte;
	    public DownloadStatus(Piece piece, int lastRequestedByte) {
            this.piece = piece;
            this.lastRequestedByte = lastRequestedByte;
            queueSize = 0;
        }
	}
	
}
