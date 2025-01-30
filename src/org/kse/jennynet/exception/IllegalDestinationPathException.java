package org.kse.jennynet.exception;

import java.io.IOException;

public class IllegalDestinationPathException extends IOException {

	public IllegalDestinationPathException() {
		super();
	}

	public IllegalDestinationPathException(String message, Throwable cause) {
		super(message, cause);
	}

	public IllegalDestinationPathException(String message) {
		super(message);
	}

	public IllegalDestinationPathException(Throwable cause) {
		super(cause);
	}

}
