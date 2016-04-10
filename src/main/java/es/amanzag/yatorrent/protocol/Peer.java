/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.google.common.base.Objects;

import es.amanzag.yatorrent.util.Util;

/**
 * @author Alberto Manzaneque
 *
 */
public class Peer {
    
    private final static Duration RECONNECT_INTERVAL = Duration.ofSeconds(60);
	
	private String address;
	private int port;
	private byte[] id;
	private int score;
	private Instant lastDisconnection;
	
	/** Constructs a Peer without the info_hash. It must be read from the handshake
	 * or set by the method setInfoHash
	 */
	public Peer(String address, int port) {
	    requireNonNull(address);
		this.address = address;
		this.port = port;
		this.id = null;
		score = 0;
		lastDisconnection = Instant.now().minus(RECONNECT_INTERVAL); // to make sure that the first time we try to connect
	}
	
	@Override
	public boolean equals(Object obj) {
		Peer comp = (Peer) obj;
		return address.equals(comp.address) && port == comp.port ;
	}
	
	@Override
	public int hashCode() {
	    return Objects.hashCode(address, port);
	}
	
	@Override
	public String toString() {
		return "IP: "+address+", port: "+port+", id: " +
		        Optional.ofNullable(id).map(Util::bytesToHex).orElse("null");
	}

	public String getAddress() {
		return address;
	}

	public byte[] getId() {
		return id;
	}

	public int getPort() {
		return port;
	}
	
	public void setId(byte[] id) {
		this.id = id;
	}	
	
	public void recordConnection() {
	    score++;
	}

	public void recordDisconnection() {
	    score--;
	    lastDisconnection = Instant.now();
	}
	
	public boolean shouldForget() {
	    return score < -2;
	}
	
	public boolean shouldTryConnection() {
	    return Instant.now().minus(RECONNECT_INTERVAL).compareTo(lastDisconnection) > 0;
	}
}
