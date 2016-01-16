/**
 * 
 */
package es.amanzag.yatorrent.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author Alberto Manzaneque
 *
 */
public class LogFormatter extends Formatter {

	private final DateFormat dateFormatter = new SimpleDateFormat();
	
	/* (non-Javadoc)
	 * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
	 */
	@Override
	public String format(LogRecord rec) {
		return "[ "+dateFormatter.format(new Date(rec.getMillis()))+" ] "+rec.getMessage()+'\n';
	}

}
