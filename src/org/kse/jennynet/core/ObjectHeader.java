/*  File: ObjectHeader.java
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.kse.jennynet.intfa.SendPriority;

/**
 * Object Header Data is available on the first parcel
 * received for a transmitted object. Likewise, it has
 * to be given to outgoing when the object is split into
 * parcels for sending. Package internal only.
 *
 * <p>The serialisation size of this class is minimum 23 bytes,
 * plus what may be necessary for the optional PATH information.
 * The CRC32 value is only passed through and is related to the described
 * object.
 */

class ObjectHeader {
   
   private long objectID;
   private int method; 
   private long objectSize; // object serialisation size
   private long nrParcels;
   private SendPriority priority; // channel of transmission
   private String path; // DESTINATION for file objects
   private byte[] serialisedPath; // DESTINATION transmit format (same information as path) 
   private int crc32; // optional object serialisation CRC

   /** Creates a new object-header for the given object-ID and serialisation
    * method.
    *  
    * @param objectID long object identifier
    */
   public ObjectHeader (long objectID) {
      this.objectID = objectID;
   }

   public long getObjectID() {
      return objectID;
   }

   /** The path information associated with the transmission object.
    * Required for file objects.
    * 
    * @return String
    */
   public String getPath() {
      return path;
   }

   public int getSerialisationMethod() {
      return method;
   }

   /** Returns the object serialisation length.
    * 
    * @return long
    */
   public long getTransmissionSize() {
      return objectSize;
   }

   /** The number of parcels required to perform the transmission.
    *  
    * @return long 
    */
   public long getNumberOfParcels() {
      return nrParcels;
   }
   
   public SendPriority getPriority () {
	   return priority;
   }

   public void setPriority (SendPriority p) {
	   priority = p;
   }
   
   public void writeObject (DataOutputStream output) throws IOException {
      DataOutputStream out = output;
      
      out.write(method);
      out.write(priority.ordinal());
      out.writeLong(objectSize);
      out.writeLong(nrParcels);
      out.writeInt(crc32);
      
      // write path string if available
      if ( path != null) {
         out.writeShort(serialisedPath.length);
         out.write(serialisedPath);
      } else {
         out.writeShort(0);
      }
   }
   
   /** Returns the length required to write this header to serialisation.
    * 
    * @return int length in bytes
    */
   public int getHeaderLength () {
      return 24 + (path != null ? serialisedPath.length : 0);
   }
   
   public void readObject (DataInputStream input) throws IOException {
      DataInputStream in = input;
      
      method = in.read();
      priority = SendPriority.valueOf(in.read());
      objectSize = in.readLong();
      nrParcels = in.readLong();
      crc32 = in.readInt();
      
      // read path string if available
      int len = in.readShort();
      if ( len > 0) {
         serialisedPath = new byte[len];
         in.readFully(serialisedPath);
         path = new String(serialisedPath, JennyNet.getCodingCharset());
      } else {
         path = null;
      }
   }

   /** Header data soundness. 
    * 
    * @return boolean true = header ok
    */
   public boolean verify() {
      return objectID > 0 & objectSize > -1 & method > -1 & nrParcels > 0; 
   }

   /** Sets the object serialisation length or file length.
    * 
    * @param length long
    */
   public void setTransmissionSize (long length) {
	   if (length < 0) 
		   throw new IllegalArgumentException();
      objectSize = length;
   }

   public void setNrOfParcels (long nrOfParcels) {
	   if (nrOfParcels < 0) 
		   throw new IllegalArgumentException();
      nrParcels = nrOfParcels;
   }

   public void setSerialisationMethod (int method) {
      this.method = method;
   }

   /** Sets the PATH information for the transmission object. The length of
    * the path is limited to 0xFFFF. The path is required for file
    * transmissions only.
    * 
    * @param path String
    * @throws IllegalArgumentException
    */
   public void setPath (String path) {
      if (path != null && path.length() > 0xFFFF) 
         throw new IllegalArgumentException("PATH too long!");
      
      this.path = path;
      if ( path != null) {
         serialisedPath = path.getBytes(JennyNet.getCodingCharset());
      } else {
         serialisedPath = null;
      }
   }

	public int getCrc32 () {
		return crc32;
	}
	
	public void setCrc32 (int crc32) {
		this.crc32 = crc32;
	}
   
   
}
