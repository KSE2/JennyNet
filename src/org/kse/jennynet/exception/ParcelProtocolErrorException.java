package org.kse.jennynet.exception;

public class ParcelProtocolErrorException extends JennyNetException {

   public ParcelProtocolErrorException() {
   }

   public ParcelProtocolErrorException(String message, Throwable cause) {
      super(message, cause);
   }

   public ParcelProtocolErrorException(String message) {
      super(message);
   }

   public ParcelProtocolErrorException(Throwable cause) {
      super(cause);
   }

}
