package org.kse.jennynet.exception;

/** Thrown to indicate that the result of an object's serialisation assumes
 * a size which is larger than what has been set as limit. The limit can be
 * set in {@code ConnectionParameters} instances, for a single connection
 * or on a global level in the {@code JennyNet} class.
 *  
 */
public class SerialisationOversizedException extends JennyNetException {

	public SerialisationOversizedException() {
	}

	public SerialisationOversizedException(String message, Throwable cause) {
		super(message, cause);
	}

	public SerialisationOversizedException(String message) {
		super(message);
	}

	public SerialisationOversizedException(Throwable cause) {
		super(cause);
	}

}
