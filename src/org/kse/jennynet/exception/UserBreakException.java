package org.kse.jennynet.exception;

/** The user has broken a sending operation at the connection interface.
 */
public class UserBreakException extends JennyNetException {

	public UserBreakException() {
	}

	public UserBreakException(String message, Throwable cause) {
		super(message, cause);
	}

	public UserBreakException(String message) {
		super(message);
	}

	public UserBreakException(Throwable cause) {
		super(cause);
	}

}
