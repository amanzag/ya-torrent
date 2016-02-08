/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

/**
 * @author Alberto Manzaneque
 *
 */
public class BDecoder {
	
    private final static Charset charset = Charsets.ISO_8859_1;
    
	private InputStream in;
	
	public BDecoder(InputStream input) {
		this.in = input;
	}
	
	public static byte[] toBytes(String s) {
	    return s.getBytes(charset);
	}
	
	@SuppressWarnings("unchecked")
    public <T> T decodeNext() throws IOException {
		int tempByte = in.read();
		try {
		    switch (tempByte) {
		    case 'd':
		        return (T) decodeDictionary();
		    case 'i':
		        return (T) decodeInteger();
		    case 'l':
		        return (T) decodeList();
		    case '0':
		    case '1':
		    case '2':
		    case '3':
		    case '4':
		    case '5':
		    case '6':
		    case '7':
		    case '8':
		    case '9':
		        StringBuffer buf = new StringBuffer(10);
		        while(tempByte != ':') {
		            if(!Character.isDigit(tempByte))
		                throw new BEncodingException("Unexpected character");
		            buf.append((char)tempByte);
		            tempByte = (char)in.read();
		        }
		        return (T) decodeString(Integer.parseInt(buf.toString()));
		        
		    case 'e':
		        return null;
		        
		    default:
		        throw new BEncodingException("Unexpected character");
		    }
		    
		} catch (ClassCastException e) {
		    throw new BEncodingException("Unexpected type. " + e.getMessage(), e);
		}
	}
	
	protected Map<String, ?> decodeDictionary() throws IOException {
	    ImmutableMap.Builder<String, Object> res = ImmutableMap.builder();
		Object value = null;
		
		String key = decodeNext();
		
		if(key == null)
			throw new BEncodingException("Unexpected end of dictionary");
		
		value = decodeNext();
		if(value == null)
			throw new BEncodingException("Unexpected end of dictionary");
		res.put(key, value);
		
		
		while(key != null) {
			key = decodeNext();
			if(key !=null) {
				value = decodeNext();
				if(value == null)
					throw new BEncodingException("Unexpected end of dictionary");
				res.put(key, value);
			}

		}
		return res.build();
	}
	
	protected String decodeString(int length) throws IOException {
		byte[] res = new byte[length];
		for(int i=0; i<length; i++) {
			res[i] = (byte)in.read();
		}
		return new String(res, charset);
	}
	
	protected Integer decodeInteger() throws IOException {
		char tempByte = (char)in.read();
		StringBuffer buf = new StringBuffer(10);
		while(tempByte != 'e') {
			if(!Character.isDigit(tempByte))
				throw new BEncodingException("Unexpected character");
			buf.append(tempByte);
			tempByte = (char)in.read();
		}
		
		return Integer.valueOf(buf.toString());
	}

	
	protected List<?> decodeList() throws IOException {
		List<Object> res =  new ArrayList<>();
		Object el = decodeNext();
		while (el != null) {
			res.add(el);
			el = decodeNext();
		}
		return res;
	}
	
}
