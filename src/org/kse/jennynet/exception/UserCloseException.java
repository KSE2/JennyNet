package org.kse.jennynet.exception;

/** The user has closed the connection at its interface or in a collective
 * order.
 */
public class UserCloseException extends JennyNetException {

	public UserCloseException() {
	}

	public UserCloseException(String message, Throwable cause) {
		super(message, cause);
	}

	public UserCloseException(String message) {
		super(message);
	}

	public UserCloseException(Throwable cause) {
		super(cause);
	}

}
