package org.kse.jennynet.exception;

import java.io.IOException;

/** Exception thrown after an attempt to serialise or deserialise
 * an object failed. The 'info' information disclosed the cause of the error.
 * 
 * <p><b>Meaning of 'info'</b>
 * <br>1    unregistered object-class
 * <br>2    serialisation size overflow
 * <br>3    object not serialisable
 * <br>4    object class faulty or not available
 * <br>5    reading error
 * <br>6    writing error
 * <br>7    data integrity error
 * <br>8    generic error
 */
public class SerialisationException extends IOException {

	private int info;
	
	public SerialisationException() {
	}

	public SerialisationException(int info, String message) {
		super(message);
		this.info = info;
	}

	public SerialisationException(int info, Throwable cause) {
		super(cause);
		this.info = info;
	}

	public SerialisationException(int info, String message, Throwable cause) {
		super(message, cause);
		this.info = info;
	}

	public int getInfo () {return info;}
	
	public void setInfo (int info) {
		this.info = info;
	}
	
}
