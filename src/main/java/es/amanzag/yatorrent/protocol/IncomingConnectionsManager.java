/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.protocol.messages.MalformedMessageException;

/**
 * @author Alberto Manzaneque
 *
 */
public class IncomingConnectionsManager extends Thread implements IncomingConnectionEventProducer {
	
	private static Logger logger = LoggerFactory.getLogger(TorrentDownloadManager.class);
	
	private ServerSocketChannel serverSock;
	private int port;
	private Vector<IncomingConnectionListener> listeners;
	private PeerConnection conn;
	
	public IncomingConnectionsManager(int port) {
		this.port = port;
		listeners = new Vector<IncomingConnectionListener>();
	}
	
	@Override
	public void run() {
		logger.info("Listening at port "+port);
		try {
			serverSock = ServerSocketChannel.open();
			serverSock.configureBlocking(true);
			serverSock.socket().bind(new InetSocketAddress(port));
			
			while(true) {
				try {
					SocketChannel clientSock = serverSock.accept();
					logger.debug("TCP connection received from "+clientSock.socket().getInetAddress());
					Peer client = new Peer(clientSock.socket().getInetAddress().getHostAddress(),
							clientSock.socket().getPort());
					conn = new PeerConnection(client, clientSock);
					conn.addMessageListener(new PeerMessageAdapter() {
						public void onHandshake(byte[] infoHash, byte[] peerId) {
							for (IncomingConnectionListener torrent : listeners) {
								if(Arrays.equals(infoHash, torrent.getInfoHash())) {
									torrent.incomingConnectionReceived(conn);
									break;
								}
							}
						}
					});
					// TODO make all this stuff asynchronous in order to implement a timeout
					conn.doRead();
				} catch (MalformedMessageException e) {
					logger.debug("Connection from "+conn.getPeer()+" rejected. "+e.getMessage());
					conn.kill();
				} catch (IOException e) {
					
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		
	}

	@Override
	public void addIncomingConnectionListener(IncomingConnectionListener listener) {
		listeners.add(listener);
	}
	

}
