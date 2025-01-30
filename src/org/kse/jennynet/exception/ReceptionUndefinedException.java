package org.kse.jennynet.exception;

import java.io.IOException;

/** The receiving layer is not prepared to receive an object or a file.
 */
public class ReceptionUndefinedException extends IOException {

	public ReceptionUndefinedException() {
		super();
	}

	public ReceptionUndefinedException(String message, Throwable cause) {
		super(message, cause);
	}

	public ReceptionUndefinedException(String message) {
		super(message);
	}

	public ReceptionUndefinedException(Throwable cause) {
		super(cause);
	}

	
}
