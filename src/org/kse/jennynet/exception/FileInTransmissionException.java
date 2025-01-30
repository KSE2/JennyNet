package org.kse.jennynet.exception;

import java.io.IOException;

/** A file is addressed for writing or transmission which is already marked 
 * as being currently written or already in transmission. For the transmission
 * part the critical datum is not the source-file but the target filepath. 
 */
public class FileInTransmissionException extends IOException {

   public FileInTransmissionException() {
   }

   public FileInTransmissionException(String message, Throwable cause) {
      super(message, cause);
   }

   public FileInTransmissionException(String message) {
      super(message);
   }

   public FileInTransmissionException(Throwable cause) {
      super(cause);
   }

}
