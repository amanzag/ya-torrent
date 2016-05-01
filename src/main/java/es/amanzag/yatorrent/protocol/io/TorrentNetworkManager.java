package es.amanzag.yatorrent.protocol.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.Peer;
import es.amanzag.yatorrent.protocol.PeerConnection;
import es.amanzag.yatorrent.protocol.PeerConnectionListener;
import es.amanzag.yatorrent.protocol.PeerConnectionProducer;
import es.amanzag.yatorrent.protocol.PeerMessageListener;
import es.amanzag.yatorrent.storage.TorrentStorage;

public class TorrentNetworkManager implements PeerConnectionProducer {
    
    private final static Logger logger = LoggerFactory.getLogger(TorrentNetworkManager.class);

    private Selector sockSelector;
    private TorrentMetadata metadata;
    private TorrentStorage storage;
    private List<PeerConnectionListener> listeners;
    
    public TorrentNetworkManager(TorrentMetadata metadata, TorrentStorage storage) {
        sockSelector = null;
        this.metadata = metadata;
        this.storage = storage;
        this.listeners = new ArrayList<>();
    }
    
    public void start() throws IOException {
        sockSelector = Selector.open();
    }
    
    public void stop() throws IOException {
        sockSelector.close();
    }
    
    public void connect(Peer peer) throws IOException {
        SocketChannel sock = SocketChannel.open();
        sock.configureBlocking(false);
        sock.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()));
        sock.register(sockSelector, SelectionKey.OP_CONNECT, peer);
    }
    
    public void processSocketEvents() {
        try {
            if(sockSelector.select(5000)>0) {
                Set<SelectionKey> keys = sockSelector.selectedKeys();
                Iterator<SelectionKey> i = keys.iterator();
                while(i.hasNext()) {
                    SelectionKey key = i.next();
                    i.remove();
                    
                    if(key.isConnectable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        Peer peer = (Peer) key.attachment();
                        try {
                            if(channel.isConnectionPending()) {
                                channel.finishConnect();
                                PeerConnection conn = new PeerConnection(peer, channel, storage, metadata);
                                key.attach(conn);
                                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                                logger.debug("Connected to new peer: "+peer);
                                for (PeerConnectionListener listener : listeners) {
                                    listener.onNewConnection(conn);
                                }
                                conn.addMessageListener(new PeerMessageListener() {
                                    @Override
                                    public void onDisconnect() {
                                        SelectionKey sk = conn.getChannel().keyFor(sockSelector);
                                        if(sk != null) {
                                            sk.cancel();
                                        }
                                    }
                                });
                            }
                        } catch (IOException e) {
                            logger.debug("Can not connect to peer "+peer+". "+e.getMessage());
                            key.attach(null);
                            key.cancel();
                            for (PeerConnectionListener peerConnectionListener : listeners) {
                                peerConnectionListener.onConnectionFailed(peer);
                            }
                        } catch (Exception e) {
                            logger.error("Error establishing connection to "+peer, e);
                            key.attach(null);
                            key.cancel();
                        }
                    } 
                    if (key.isValid() && key.isWritable()) {
                        PeerConnection conn = (PeerConnection) key.attachment();
                        try {
                            conn.doWrite();
                            if(!conn.isWriting()) {
                                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            }
                        } catch (Exception e) {
                            logger.debug("Error sending message to "+conn.getPeer()+". Closing connection: "+e.getMessage());
                            conn.kill();
                        }
                    } 
                    if (key.isValid() && key.isReadable()) {
                        PeerConnection conn = (PeerConnection) key.attachment();
                        try {
                            if(!conn.getChannel().isOpen()) {
                                logger.debug("Socket closed. Connection with "+conn.getPeer()+" dropped");
                                conn.kill();
                                key.cancel();
                            } else {
                                conn.doRead();
                            }
                        } catch (ConnectionClosedException e) {
                            logger.info("Peer {} closed the connection", conn.getPeer());
                            conn.kill();
                        } catch (Exception e) {
                            logger.error("Error reading from socket ("+e.getMessage()+"). Closing connection with "+conn.getPeer(), e);
                            conn.kill();
                        }
                    }
                }
            }
            // after processing all events, maybe some connections are now interested in writing, so we have to enable it
            for (SelectionKey key : sockSelector.keys()) {
                if (key.isValid() && key.attachment() instanceof PeerConnection) {
                    PeerConnection pc = (PeerConnection) key.attachment();
                    boolean writeInterest = (key.interestOps() & SelectionKey.OP_WRITE) > 0;
                    if(!writeInterest && pc != null && pc.isWriting()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Unhandled exception", e);
        }
    }

    @Override
    public void addPeerConnectionListener(PeerConnectionListener listener) {
        listeners.add(listener);
    }
    
}
