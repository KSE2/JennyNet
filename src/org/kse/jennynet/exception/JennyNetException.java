
package org.kse.jennynet.exception;

public class JennyNetException extends RuntimeException {
	public JennyNetException () {
		super();
	}

	public JennyNetException (String message, Throwable cause) {
		super(message, cause);
	}

	public JennyNetException (String message) {
		super(message);
	}

	public JennyNetException (Throwable cause) {
		super(cause);
	}
}
