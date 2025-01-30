/*  File: ObjectAgglomeration.java
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

import org.kse.jennynet.exception.SerialisationException;
import org.kse.jennynet.exception.SerialisationOversizedException;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;

/** Structure which assembles incoming data parcels to build up an object 
 * serialisation, which is automatically de-serialised after the last parcel 
 * has been received.
 */
class ObjectAgglomeration {

   private ConnectionImpl connection;
   private Serialization serialisation;
   private SendPriority priority;
   private long objectID;
   private int serialMethod = -1;
   private int serialSize, bufferPos;
   private int numberOfParcels;
   private Object object;
   private byte[] byteStore;
   
   private int nextParcelNr;
   
   public ObjectAgglomeration (ConnectionImpl connection, long objectID, SendPriority priority) {
      this.connection = connection;
      this.objectID = objectID;
      this.priority = priority;
   }

   /** Whether the object is complete, de-serialised and ready to be collected.
    * 
    * @return boolean
    */
   public boolean objectReady () {
      return object != null;
   }
   
   /** Returns the de-serialised transmission object if available, null
    * otherwise.
    * 
    * @return Object object or null if unavailable
    */
   public Object getObject () {
      return object;
   }
   
   /** Returns the serialisation method identifier taken from the first
    * received object parcel or -1 if there was no parcel received.
    * 
    * @return int serialisation method number or -1
    */
   public int getSerialMethod () {
	   return serialMethod;
   }
   
   /** Returns the priority class by which the contained object was
    * or is being sent.
    * 
    * @return {@code SendPriority}
    */
   public SendPriority getPriority () {
	   return priority;
   }
   
   /** Digest a single data parcel into the agglomeration. If the given parcel
    * is the last of the sequence, the resulting object is de-serialised and
    * becomes ready for collection. 
    * 
    * @param parcel <code>TransmissionParcel</code>
    * @throws IllegalStateException if parcel is malformed, out of sequence, 
    *         or object serialisation size overflows maximum
    * @throws SerialisationException if de-serialising fails
    * @throws SerialisationUnavailableException if reception has no 
    *         serialisation for the requested method
    */
   public void digestParcel (TransmissionParcel parcel) throws SerialisationException {
      // verify fitting
      if (parcel.getChannel() != TransmissionChannel.OBJECT) 
         throw new IllegalArgumentException("illegal parcel channel; must be OBJECT");
         
      if (parcel.getObjectID() != objectID)
         throw new IllegalStateException("mismatching object-ID in agglomeration parcel");

      if (parcel.getParcelSequencelNr() != nextParcelNr) {
         String hstr = objectReady() ? " (object completed)" : "";
         throw new IllegalStateException("PARCEL SERIAL NUMBER out of sequence (object agglomeration); " +
         		" expected: " + nextParcelNr + hstr + ", received: " + parcel.getParcelSequencelNr());
      }

      // initialise on parcel number 0 (HEADER PARCEL)
      if (parcel.getParcelSequencelNr() == 0) {
         ObjectHeader header = parcel.getObjectHeader();
         numberOfParcels = (int) header.getNumberOfParcels();
         serialSize = (int) header.getTransmissionSize();

         // check correctness of indicated object data size 
         if (numberOfParcels < 0 | serialSize < 0) {
        	 throw new IllegalStateException("negative parcel amount or data length detected");
         }
         
         // individualise receive-serialisations
         serialMethod = header.getSerialisationMethod();
         serialisation = connection.obtainReceiveSerialisation(serialMethod);
         
         // check feasibility of serialisation buffer length 
         if (serialSize > connection.getParameters().getMaxSerialisationSize()) {
            throw new SerialisationOversizedException("received oversized object serialisation: ID=" + objectID +
                  ", serial-size=" + serialSize);
         }

         // create the serialisation storage field
         byteStore = new byte[serialSize];
      }
      
      // add parcel data to byte stream
      try {
         System.arraycopy(parcel.getData(), 0, byteStore, bufferPos, parcel.getLength());
         bufferPos += parcel.getLength();
      } catch (Throwable e) {
         e.printStackTrace();
         throw new IllegalStateException("unable to store parcel BYTE BUFFER in object agglomeration; current size == " 
                     + bufferPos);
      }

      // if last parcel arrived, perform object de-serialisation
      if (nextParcelNr+1 == numberOfParcels) {
         object = serialisation.deserialiseObject(byteStore);
      } else {
         nextParcelNr++;
      }
      
   }
}
