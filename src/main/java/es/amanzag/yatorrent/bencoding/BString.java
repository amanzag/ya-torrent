/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

import java.util.Arrays;

/**
 * @author Alberto Manzaneque
 *
 */
public class BString implements BElement, Comparable<BString> {

	private byte[] value;
	
	public BString(String value) {
		if(value==null) throw new NullPointerException();
		this.value = value.getBytes();
	}
	
	public BString(byte[] value) {
		if(value==null) throw new NullPointerException();
		this.value = value;
	}
	
	public String getValue() {
		return new String(value);
	}
	
	public byte[] getBytes() {
		return value;
	}
	
	@Override
	public boolean equals(Object arg0) {
		return ((arg0 instanceof BString)
				&& Arrays.equals(value, ((BString)arg0).value));
	}
	
	@Override
	public String toString() {
		return new String(value);
	}

	public int compareTo(BString arg0) {
		return new String(value).compareTo(arg0.getValue());
	}
}
