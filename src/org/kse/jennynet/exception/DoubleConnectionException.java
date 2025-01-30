package org.kse.jennynet.exception;


public class DoubleConnectionException extends JennyNetException {

   public DoubleConnectionException() {
   }

   public DoubleConnectionException(String message, Throwable cause) {
      super(message, cause);
   }

   public DoubleConnectionException(String message) {
      super(message);
   }

   public DoubleConnectionException(Throwable cause) {
      super(cause);
   }

}
