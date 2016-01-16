/**
 * 
 */
package es.amanzag.yatorrent.protocol;



/**
 * @author Alberto Manzaneque
 *
 */
public class Peer {
	
	private String address;
	private int port;
	private byte[] id;
	
	
//	public Peer(String address, int port, byte[] infoHash) {
//		this(address, port);
//	}
	
	/** Constructs a Peer without the info_hash. It must be read from the handshake
	 * or set by the method setInfoHash
	 */
	public Peer(String address, int port) {
		if(address == null) throw new NullPointerException();
		this.address = address;
		this.port = port;
		this.id = null;
	}
	
	@Override
	public boolean equals(Object obj) {
		Peer comp = (Peer) obj;
		return address.equals(comp.address) && port == comp.port ;
	}
	
	@Override
	public String toString() {
		return "IP: "+address+", port: "+port+", id: "+id;
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

}
