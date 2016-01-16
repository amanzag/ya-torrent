/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

import java.util.Comparator;
import java.util.TreeMap;

/**
 * @author Alberto Manzaneque
 *
 */
public class BDictionary extends TreeMap<BString, BElement> implements BElement {
	
	public BDictionary() {
		super(new Comparator<BString>() {
			public int compare(BString arg0, BString arg1) {
				return arg0.compareTo(arg1);
			}
		});
	}

}
