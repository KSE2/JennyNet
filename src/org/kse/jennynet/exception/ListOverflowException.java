package org.kse.jennynet.exception;

/** A buffering list for objects to be operated has reached the end of its
 * capacity and an insertion attempt had to be denied.
 */
public class ListOverflowException extends JennyNetException {

	public ListOverflowException() {
	}

	public ListOverflowException(String message, Throwable cause) {
		super(message, cause);
	}

	public ListOverflowException(String message) {
		super(message);
	}

	public ListOverflowException(Throwable cause) {
		super(cause);
	}

}
