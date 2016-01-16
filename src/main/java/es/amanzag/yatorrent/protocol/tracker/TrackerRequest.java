/**
 * 
 */
package es.amanzag.yatorrent.protocol.tracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * @author Alberto Manzaneque
 *
 */
public class TrackerRequest {
	
	public enum Params{
		info_hash, peer_id, port, uploaded, downloaded, left, 
		compact, event, ip, numwant, key, trackerid 
	}
	
	private URL url;
	private byte[] infoHash;
	private String peerId;
	private Integer port;
	private Long uploaded;
	private Long downloaded;
	private Long left;
	private Boolean compact;
	private String event;
	private String ip;
	private Integer numwant;
	private String key;
	private String trackerid;
	
	public TrackerRequest(URL url) throws MalformedURLException {
		if(!url.getProtocol().equals("http"))
			throw new MalformedURLException("Unsupported protocol "+url.getProtocol());
		this.url = url;
	}
	
	public URL getRequestURL() throws TrackerProtocolException {
		StringBuffer req = new StringBuffer(url.toExternalForm());
		req.append('?');
		try {
			addParameter(req, "info_hash", (infoHash == null ? null : URLEncoder.encode(new String(infoHash,"ISO-8859-1"), "ISO-8859-1")), true);
			addParameter(req, "peer_id", peerId, true);
			addParameter(req, "port", port, true);
			addParameter(req, "uploaded", uploaded, true);
			addParameter(req, "downloaded", downloaded, true);
			addParameter(req, "left", left, true);
			addParameter(req, "compact", compact ? "1": "0", true);
			addParameter(req, "event", event, false);
			addParameter(req, "ip", ip, false);
			addParameter(req, "numwant", numwant, false);
			addParameter(req, "key", key, false);
			addParameter(req, "trackerid", trackerid, false);
			
			req.deleteCharAt(req.length()-1); // delete the final &
			return new URL(req.toString());
		} catch (MalformedURLException e) {
			throw new TrackerProtocolException("malformed url "+url, e);
		} catch (UnsupportedEncodingException e) {
			throw new TrackerProtocolException("unsupported URL encoding", e);
		} 
	}
	
	private static void addParameter(StringBuffer str, String name, Object value, boolean mandatory) throws TrackerProtocolException {
		if(value != null) {
			str.append(name);
			str.append('=');
			str.append(value);
			str.append('&');
		} else if (mandatory) {
			throw new TrackerProtocolException("parameter "+name+" is mandatory");
		}
	}
	
	public InputStream make() throws IOException, TrackerProtocolException {
		HttpURLConnection conn = (HttpURLConnection) getRequestURL().openConnection();
		conn.connect();
		int response = conn.getResponseCode();
		if(response != HttpURLConnection.HTTP_ACCEPTED && response != HttpURLConnection.HTTP_OK) {
			throw new TrackerProtocolException(conn.getResponseMessage());
		}
		return conn.getInputStream();
	}

	public boolean isCompactAllowed() {
		return compact;
	}

	public void setCompactAllowed(boolean compact) {
		this.compact = compact;
	}

	public long getDownloaded() {
		return downloaded;
	}

	public void setDownloaded(long downloaded) {
		this.downloaded = downloaded;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public byte[] getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(byte[] infoHash) {
		this.infoHash = infoHash;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public long getLeft() {
		return left;
	}

	public void setLeft(long left) {
		this.left = left;
	}

	public int getNumwant() {
		return numwant;
	}

	public void setNumwant(int numwant) {
		this.numwant = numwant;
	}

	public String getPeerId() {
		return peerId;
	}

	public void setPeerId(String peerId) {
		this.peerId = peerId;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getTrackerid() {
		return trackerid;
	}

	public void setTrackerid(String trackerid) {
		this.trackerid = trackerid;
	}

	public long getUploaded() {
		return uploaded;
	}

	public void setUploaded(long uploaded) {
		this.uploaded = uploaded;
	}
	
}
