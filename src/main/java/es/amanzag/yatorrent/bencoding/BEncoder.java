/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static es.amanzag.yatorrent.bencoding.BDecoder.toBytes;

/**
 * @author Alberto Manzaneque
 *
 */
public class BEncoder {

	private OutputStream out;
	
	public BEncoder(OutputStream output) {
		this.out = output;
	}
	
	public void encode(String s) throws IOException {
		String length = String.valueOf(toBytes(s).length);
		out.write(toBytes(length));
		out.write(':');
		out.write(toBytes(s));
	}
	
	public void encode(Integer i) throws IOException {
		out.write(toBytes("i"));
		out.write(toBytes(String.valueOf(i)));
		out.write(toBytes("e"));
	}
	
	public void encode(Map<String, Object> d) throws IOException {
		out.write(toBytes("d"));
		for (String key : d.keySet()) {
			encode(key);
			encode(d.get(key));
		}
		out.write(toBytes("e"));
	}
	
	public void encode(List<Object> l) throws IOException {
		out.write(toBytes("l"));
		for(Object element : l) {
			encode(element);
		}
		out.write(toBytes("e"));		
	}
	
	@SuppressWarnings("unchecked")
    public void encode(Object e) throws IOException {
		if(e instanceof String) {
			encode((String)e);
		} else if (e instanceof Integer) {
			encode((Integer)e);
		} else if (e instanceof Map) {
			encode((Map<String, Object>)e);
		} else if (e instanceof List) {
			encode((List<Object>)e);
		}
	}

}
