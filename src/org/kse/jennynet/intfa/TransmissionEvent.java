/*  File: TransmissionEvent.java
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

package org.kse.jennynet.intfa;

import java.io.File;

/** Interface for a file transmission event issued by a <code>Connection</code>
 * to its listeners. Transmission events appear within the 
 * <code>ConnectionListener</code> description or can be
 * requested from an enclosing {@code ConnectionEvent}.
 * 
 * <p>Transmission events are exclusively concerned with FILE-TRANSFERS. They
 * have a type property defined by {@code TransmissionEventType}.
 * File transfers can be identified by their ID numbers (long integer) which 
 * reside within the name-space of the originating connection's OBJECT-IDs. 
 * As objects can be sent from either side of a connection, there naturally 
 * exist separate name spaces for OUTGOING and INCOMING file transfers.
 */

public interface TransmissionEvent {

   /** The connection which issued this event.
    * 
    * @return  <code>Connection</code> source of this event
    */
   Connection getConnection();

   /** The type of this event. Semantics see class description! 
    * 
    * @return {@code TransmissionEventType}
    */
   TransmissionEventType getType();

   /** Time the transmission was active in milliseconds.
    *  
    * @return long milliseconds
    */
   long getDuration();

   /** The amount of file data which has actually been exchanged
    * with remote station. This may be smaller than the "expected length"
    * for abortion events.
    * 
    * @return long transmitted data length (bytes)
    */
   long getTransmissionLength();

   /** The total size of the file to be transfered.
    * This is known with events FILE_SENDING, FILE_INCOMING, FILE_RECEIVED,
    * FILE_ABORTED.
    * 
    * @return long transmission file length in bytes
    */
   long getExpectedLength();

   /** The "remote path" (destination) information for the transmission.
    * This names the location where the sender intends to store the transfered
    * file at the receiver. 
    * <p>This is by definition a relative filepath which the receiver system 
    * makes absolute against connection parameter FILE_ROOT_PATH. 
    * If FILE_ROOT_PATH is undefined the transmission doesn't take place.
    * 
    * @return String file path or null
    */
   String getPath();

   /** Returns the file received or the file which buffers streaming data 
    * (TEMP-file) on the incoming side, depending on the state of the 
    * transmission. On the outgoing side it names the source file. 
    * 
    * @return File received/receiving file or null
    */
   File getFile();

   /** Code to detail error conditions when this event took place.
    *  
    * @return int 
    */
   int getInfo();
   
   /** Returns the transmission's priority channel.
    * 
    * @return {@code SendPriority}
    */
   SendPriority getPriority ();

   /** Returns the file transfer identifier which is at the same time an object
    * identifier. (A file is seen as a special object by the layer.) 
    * Incoming and outgoing transmission objects relate to different name 
    * spaces. The name always relates to the object name space of the 
    * originator, which is the sender.
    * 
    * @return long ID of the file transfer 
    */
   long getObjectID();

   /** Returns the ID of the server transaction which has caused this event
    * or zero if no transaction is involved.
    *   
    * @return int transaction-ID
    */
   int getTransaction ();
   
   /** Returns the communication direction that this event implies.
    * This aids in discriminating events which occur on both sides of the
    * connection.
    * 
    * @return {@code ComDirection}
    */
   ComDirection getDirection ();
   
   /** If an error exception is known for the cause of a transfer abortion,
    * it is shown here. Otherwise this method returns null.
    * 
    * @return Throwable abort error or null
    */
   Throwable getException();

}