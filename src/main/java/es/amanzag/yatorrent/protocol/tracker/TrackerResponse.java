/**
 * 
 */
package es.amanzag.yatorrent.protocol.tracker;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import es.amanzag.yatorrent.bencoding.BDecoder;
import es.amanzag.yatorrent.bencoding.BEncodingException;
import es.amanzag.yatorrent.protocol.Peer;

/**
 * @author Alberto Manzaneque
 *
 */
public class TrackerResponse {
	
	private String warningMessage;
	private Integer interval;
	private Integer minInterval;
	private String trackerId;
	private Integer complete;
	private Integer incomplete;
	private List<Peer> peers;
	
	protected TrackerResponse() {
		peers = new ArrayList<Peer>();
	}
	
	public static TrackerResponse createFromStream(InputStream in) throws TrackerProtocolException {
		TrackerResponse response = null;
		try {
			response = new TrackerResponse();
			Map<String, Object> root = new BDecoder(in).decodeNext();
			
			String failureReason = (String) root.get("failure reason");
			if(failureReason != null) {
				throw new TrackerProtocolException("the tracker returned an error: "+failureReason);
			}
			
			response.warningMessage = (String) root.get("warning message");
			
			response.interval = (Integer) root.get("interval");
			if(response.interval == null) {
                throw new TrackerProtocolException("interval not present in tracker response");
            }

			response.minInterval = (Integer) root.get("min interval");
			response.trackerId = (String) root.get("tracker id");
			response.complete = (Integer) root.get("complete");
			response.incomplete = (Integer) root.get("incomplete");
			
			Object peers = root.get("peers");
			
			if(peers instanceof List) { // normal mode
				for (Map<String, Object> dict : (List<Map<String, Object>>)peers) {
					String ip = (String) dict.get("ip");
					Integer port = (Integer) dict.get("port");
					if(ip == null || port == null) {
						throw new TrackerProtocolException("malformed peer list");
					}
					String peerId = (String) dict.get("peer id");
					Peer peer = null;
					peer = new Peer(ip, port);
					if(peerId == null) {
						peer.setId(BDecoder.toBytes(peerId));
					}
					response.peers.add(peer);
				}
			} else if (peers instanceof String) { // compact mode
				byte[] buf = BDecoder.toBytes(((String) peers));
				if(buf.length % 6 != 0) {
                    throw new TrackerProtocolException("malformed peer list");
                }
				for(int i=0; i<buf.length; i+=6) {
					StringBuffer ip = new StringBuffer(15);
					for(int j=0; j<4; j++) {
						int part = 0x000000FF & buf[i+j];
						ip.append(part);
						if(j != 3) ip.append(".");
					}
					int firstByte = 0x000000FF & (int)buf[i+4];
					int secondByte = 0x000000FF & (int)buf[i+5];
					int port = firstByte << 8 | secondByte & 0x0000FFFF;
					response.peers.add(new Peer(ip.toString(), port));
				}
				
			} else {
				throw new TrackerProtocolException("invalid peers field in tracker response");
			}
			
		} catch (IOException e) {
			throw new TrackerProtocolException(e.getMessage(), e);
		} catch (BEncodingException e) {
			throw new TrackerProtocolException("tracker response is not well encoded. "+e.getMessage(), e );
		} catch (ClassCastException e) {
			throw new TrackerProtocolException("field not expected in tracker response", e);
		}
		return response;
	}

	public long getComplete() {
		return complete;
	}

	public long getIncomplete() {
		return incomplete;
	}

	public long getInterval() {
		return interval;
	}

	public long getMinInterval() {
		return minInterval;
	}

	public List<Peer> getPeers() {
		return peers;
	}

	public String getTrackerId() {
		return trackerId;
	}

	public String getWarningMessage() {
		return warningMessage;
	}

}
