package org.kse.jennynet.exception;

/** A {@code Connection} has been expected to be in CONNECTED state but
 * failed.
 */
public class UnconnectedException extends JennyNetException {

	public UnconnectedException() {
	}

	public UnconnectedException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnconnectedException(String message) {
		super(message);
	}

	public UnconnectedException(Throwable cause) {
		super(cause);
	}

}
