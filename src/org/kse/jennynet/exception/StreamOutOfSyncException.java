package org.kse.jennynet.exception;

public class StreamOutOfSyncException extends JennyNetException {

   public StreamOutOfSyncException() {
   }

   public StreamOutOfSyncException(String message, Throwable cause) {
      super(message, cause);
   }

   public StreamOutOfSyncException(String message) {
      super(message);
   }

   public StreamOutOfSyncException(Throwable cause) {
      super(cause);
   }

}
