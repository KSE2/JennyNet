package org.kse.jennynet.exception;

/** A transmission operation has been stopped by the remote side of the
 * connection.
 */
public class RemoteTransferBreakException extends JennyNetException {

   public RemoteTransferBreakException() {
   }

   public RemoteTransferBreakException(String message, Throwable cause) {
      super(message, cause);
   }

   public RemoteTransferBreakException(String message) {
      super(message);
   }

   public RemoteTransferBreakException(Throwable cause) {
      super(cause);
   }

}
