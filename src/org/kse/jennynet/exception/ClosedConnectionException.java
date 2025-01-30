package org.kse.jennynet.exception;


public class ClosedConnectionException extends JennyNetException {

   public ClosedConnectionException() {
   }

   public ClosedConnectionException(String message, Throwable cause) {
      super(message, cause);
   }

   public ClosedConnectionException(String message) {
      super(message);
   }

   public ClosedConnectionException(Throwable cause) {
      super(cause);
   }

}
