package org.kse.jennynet.exception;

/** Thrown to indicate that a serialisation device ({@code Serialization})
 * which was referenced or named does not exist. One of the reasons may be
 * that its implementation classes are not installed. 
 */
public class SerialisationUnavailableException extends JennyNetException {

	public SerialisationUnavailableException() {
	}

	public SerialisationUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public SerialisationUnavailableException(String message) {
		super(message);
	}

	public SerialisationUnavailableException(Throwable cause) {
		super(cause);
	}

}
