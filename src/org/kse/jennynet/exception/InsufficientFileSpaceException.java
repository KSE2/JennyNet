package org.kse.jennynet.exception;

import java.io.IOException;

/** A file transmission cannot be saved to persistent device at the receiver 
 * because the free space on the device is insufficient.
 */

public class InsufficientFileSpaceException extends IOException {

   public InsufficientFileSpaceException() {
   }

   public InsufficientFileSpaceException(String message, Throwable cause) {
      super(message, cause);
   }

   public InsufficientFileSpaceException(String message) {
      super(message);
   }

   public InsufficientFileSpaceException(Throwable cause) {
      super(cause);
   }

}
