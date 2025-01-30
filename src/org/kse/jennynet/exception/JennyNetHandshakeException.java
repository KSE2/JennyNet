package org.kse.jennynet.exception;

import java.net.SocketException;

/** Thrown to indicate that the remote end does not comply
 * with the JENNYNET handshake protocol. The remote listener may not be 
 * a JennyNet software layer or its software version is not compatible.  
 * <p>This is a subclass of java.net.SocketException.
 */

public class JennyNetHandshakeException extends SocketException {

   private static final long serialVersionUID = -6670055890266924934L;

   public JennyNetHandshakeException() {
   }

   public JennyNetHandshakeException(String msg) {
      super(msg);
   }

}
