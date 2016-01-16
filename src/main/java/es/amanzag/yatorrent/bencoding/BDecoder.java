/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Alberto Manzaneque
 *
 */
public class BDecoder {
	
	private InputStream in;
	
	public BDecoder(InputStream input) {
		this.in = input;
	}
	
	public BElement decodeNext() throws IOException, BEncodingException {
		int tempByte = in.read();
		switch (tempByte) {
		case 'd':
			return decodeDictionary();
		case 'i':
			return decodeInteger();
		case 'l':
			return decodeList();
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
			return decodeString(Integer.parseInt(buf.toString()));
			
		case 'e':
			return null;

		default:
			throw new BEncodingException("Unexpected character");
		}
	}
	
	protected BDictionary decodeDictionary() throws IOException, BEncodingException {
		BDictionary res = new BDictionary();
		BElement value = null;
		
		BElement key = decodeNext();
		
		if(key == null)
			throw new BEncodingException("Unexpected end of dictionary");
		
		if(!(key instanceof BString))
			throw new BEncodingException("Unexpected element");
		
		value = decodeNext();
		if(value == null)
			throw new BEncodingException("Unexpected end of dictionary");
		res.put((BString)key, value);
		
		
		while(key != null) {
			key = decodeNext();
			if(key !=null) {
				if(!(key instanceof BString))
					throw new BEncodingException("Unexpected element: "+key);
				value = decodeNext();
				if(value == null)
					throw new BEncodingException("Unexpected end of dictionary");
				res.put((BString)key, value);
			}

		}
		//System.out.println(res);
		return res;
	}
	
	protected BString decodeString(int length) throws IOException, BEncodingException {
		byte[] res = new byte[length];
		for(int i=0; i<length; i++) {
			res[i] = (byte)in.read();
		}
//		System.out.println(new BString(res));
		return new BString(res);
	}
	
	protected BInteger decodeInteger() throws BEncodingException, IOException {
		char tempByte = (char)in.read();
		StringBuffer buf = new StringBuffer(10);
		while(tempByte != 'e') {
			if(!Character.isDigit(tempByte))
				throw new BEncodingException("Unexpected character");
			buf.append(tempByte);
			tempByte = (char)in.read();
		}
		
//		System.out.println(Integer.parseInt(buf.toString()));
		return new BInteger(Long.parseLong(buf.toString()));
	}

	
	protected BList decodeList() throws IOException, BEncodingException {
		BList res = new BList();
		BElement el = decodeNext();
		while (el != null) {
			res.add(el);
			el = decodeNext();
		}
//		System.out.println(res);
		return res;
	}
	
}
