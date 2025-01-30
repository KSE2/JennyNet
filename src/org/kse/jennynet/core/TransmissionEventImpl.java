/*  File: TransmissionEventImpl.java
* 
*  Project JennyNet
*  @author Wolfgang Keller
*  
*  Copyright (c) 2025 by Wolfgang Keller, Munich, Germany
* 
This program is not public domain software but copyright protected to the 
author(s) stated above. However, you can use, redistribute and/or modify it 
under the terms of the The GNU General Public License (GPL) as published by
the Free Software Foundation, version 3.0 of the License.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the License along with this program; if not,
write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, 
Boston, MA 02111-1307, USA, or go to http://www.gnu.org/copyleft/gpl.html.
*/

package org.kse.jennynet.core;

import java.io.File;
import java.util.EventObject;
import java.util.Objects;

import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.intfa.TransmissionEventType;

class TransmissionEventImpl extends EventObject implements TransmissionEvent {
   private TransmissionEventType type;
   private ComDirection direction;
   private SendPriority priority;
   private long objectID;
   private int info;
   private int transaction;
   private Throwable exception;
   private String path;
   private File file;
   
   private long duration;
   private long transmittedLength, expectedLength;
   
   /** Creates a transmission event with error information. 
    * File and path are null.
    * 
    * @param connection Connection
    * @param type TransmissionEventType
    * @param direction {@code ComDirection}
    * @param priority {@code SendPriority} 
    * @param objectID long file identifier
    * @param info int error code 
    * @param e {@code Throwable} exception related to error or null
    */
   public TransmissionEventImpl (
         Connection connection, 
         TransmissionEventType type, 
         ComDirection direction,
         SendPriority priority,
         long objectID,
         int info, 
         Throwable e 
         ) {
      
      super(connection);
      Objects.requireNonNull(type, "type is null");
      Objects.requireNonNull(direction, "direction is null");
      Objects.requireNonNull(priority, "priotity is null");
      
      this.type = type;
      this.direction = direction;
      this.priority = priority;
      this.setObjectID(objectID);
      this.setInfo(info);
      this.setException(e);
   }

   /** Creates a transmission event with essential settings.
    * 
    * @param connection Connection
    * @param type TransmissionEventType
    * @param direction {@code ComDirection}
    * @param priority {@code SendPriority} 
    * @param objectID long file identifier
    */
   public TransmissionEventImpl (
         Connection connection, 
         TransmissionEventType type, 
         ComDirection direction,
         SendPriority priority,
         long objectID
         ) {
      this(connection, type, direction, priority, objectID, 0, null);
   }

   /** Creates a transmission event including the file and path 
    * references. Error info is zero and throwable null.
    * 
    * @param connection Connection
    * @param type TransmissionEventType
    * @param direction {@code ComDirection}
    * @param priority {@code SendPriority} 
    * @param objectID long file identifier
    * @param file File reception file
    * @param pathInfo String (may be null)
    */
   public TransmissionEventImpl(
         ConnectionImpl connection,
         TransmissionEventType type, 
         ComDirection direction,
         SendPriority priority,
         long objectID,
         File file, 
         String pathInfo
         ) {
      this(connection, type, direction, priority, objectID);
      Objects.requireNonNull(file, "file is null");

      setFile(file);
      setPath(pathInfo);
   }

   protected void setObjectID (long objectID) {
      if (objectID < 1)
         throw new IllegalArgumentException("illegal object number: " + objectID);
      
      this.objectID = objectID; 
   }

   @Override
   public Connection getConnection () {
      return (Connection)source;
   }
   
   @Override
   public TransmissionEventType getType () {
      return type;
   }

   @Override
   public long getDuration() {
      return duration;
   }

   /** Sets the duration of the transmission.
    * 
    * @param duration long milliseconds
    */
   protected void setDuration (long duration) {
      this.duration = duration;
   }

   @Override
   public long getTransmissionLength () {
      return transmittedLength;
   }

   /** Sets the actually transmitted data length.
    * 
    * @param transmittedLength long data length (bytes)
    */
   protected void setTransmissionLength (long transmittedLength) {
      this.transmittedLength = transmittedLength;
   }

   @Override
   public long getExpectedLength() {
      return expectedLength;
   }

   /** Sets the total size of the file to be transfered.
    * 
    * @param length long transmission file length
    */
   protected void setExpectedLength (long length) {
      this.expectedLength = length;
   }

   @Override
   public String getPath () {
      return path;
   }

   /** Sets the "remote path" information for the transmission.
    * 
    * @param path String file path (may be null)
    */
   protected void setPath (String path) {
      this.path = path;
   }
   
   /** Sets the File information on this event.
    * 
    * @param f File current reception file
    */
   protected void setFile (File f) {
      this.file = f;
   }
   
   @Override
   public File getFile () {
      return file;
   }

   @Override
   public int getInfo() {
      return info;
   }

   @Override
   public long getObjectID() {
      return objectID;
   }

   @Override
   public Throwable getException() {
      return exception;
   }

   protected void setException(Throwable exception) {
      this.exception = exception;
   }

   protected void setInfo (int info) {
      this.info = info;
   }

   @Override
   public ComDirection getDirection () {
	   return direction;
   }

	@Override
	public int getTransaction () {
		return transaction;
	}

	protected void setTransaction (int transaction) {
		this.transaction = transaction;
	}

	@Override
	public String toString() {
		String hstr = "TRANS-EVT, " + direction + ": " + type + " ID " + objectID + ", info " + info;
		if (file != null) {
			hstr += "\n       file = " + file.getAbsolutePath() + ", path = " + path;
		}
		return hstr;
	}

	@Override
	public SendPriority getPriority() {
		return priority;
	}

}
