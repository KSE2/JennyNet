
package org.kse.jennynet.exception;

/** 
 */

public class TimeoutException extends RuntimeException {
	public TimeoutException () {
		super();
	}

	public TimeoutException (String message, Throwable cause) {
		super(message, cause);
	}

	public TimeoutException (String message) {
		super(message);
	}

	public TimeoutException (Throwable cause) {
		super(cause);
	}
}
