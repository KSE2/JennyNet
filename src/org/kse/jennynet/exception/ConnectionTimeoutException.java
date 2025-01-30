package org.kse.jennynet.exception;

import java.net.SocketException;

/** Thrown to indicate that waiting for a CONNECTION_CONFIRM signal
 * from the remote end has failed due to timeout.
 * This is a subclass of java.net.SocketException.
 */

public class ConnectionTimeoutException extends SocketException {

   public ConnectionTimeoutException() {
   }

   public ConnectionTimeoutException(String msg) {
      super(msg);
   }

}
