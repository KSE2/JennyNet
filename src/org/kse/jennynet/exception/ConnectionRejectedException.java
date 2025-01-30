package org.kse.jennynet.exception;

import java.net.SocketException;

/** Thrown to indicate that the remote JennyNet layer 
 * rejects the connection attempt, possibly giving further detail about
 * the cause in a text argument. This is a subclass of java.net.SocketException.
 */

public class ConnectionRejectedException extends SocketException {

   public ConnectionRejectedException() {
   }

   public ConnectionRejectedException (String msg) {
      super(msg);
   }

}
