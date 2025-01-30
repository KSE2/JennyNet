package org.kse.jennynet.exception;

/** Thrown when an object was scheduled for sending with a type which is not
 * registered for transmission at the {@code Serialization} device.
 */
public class UnregisteredObjectException extends JennyNetException {

   public UnregisteredObjectException() {
   }

   public UnregisteredObjectException(String message, Throwable cause) {
      super(message, cause);
   }

   public UnregisteredObjectException(String message) {
      super(message);
   }

   public UnregisteredObjectException(Throwable cause) {
      super(cause);
   }

}
