/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Alberto Manzaneque
 *
 */
public class BEncoder {

	private OutputStream out;
	
	private static String charset = "ISO-8859-1";
	
	public BEncoder(OutputStream output) {
		this.out = output;
	}
	
	public void encode(BString s) throws IOException {
		String length = String.valueOf(s.getBytes().length);
		out.write(length.getBytes(charset));
		out.write(':');
		out.write(s.getBytes());
	}
	
	public void encode(BInteger i) throws IOException {
		out.write("i".getBytes(charset));
		out.write(String.valueOf(i.getValue()).getBytes(charset));
		out.write("e".getBytes(charset));
	}
	
	public void encode(BDictionary d) throws IOException {
		out.write("d".getBytes(charset));
		for (BString key : d.keySet()) {
			encode(key);
			encode(d.get(key));
		}
		out.write("e".getBytes(charset));
	}
	
	public void encode(BList l) throws IOException {
		out.write("l".getBytes(charset));
		for(BElement element : l) {
			encode(element);
		}
		out.write("e".getBytes(charset));		
	}
	
	public void encode(BElement e) throws IOException {
		if(e instanceof BString) {
			encode((BString)e);
		} else if (e instanceof BInteger) {
			encode((BInteger)e);
		} else if (e instanceof BDictionary) {
			encode((BDictionary)e);
		} else if (e instanceof BList) {
			encode((BList)e);
		}
	}

}
