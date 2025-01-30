/*  File: TransmissionEventType.java
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

/**  Type division for the file-transmission events ({@code TransmissionEvent}).
*/

public enum TransmissionEventType {
	
	  /** An outgoing file transfer has started transmitting on the network.
	   * If the traffic on the connection is high and many files scheduled
	   * for sending, this event can be delayed.
       * With getFile() the data source file, with getPath() the destination 
       * filepath information (relative path) can be obtained. 
	   */
	  FILE_SENDING,
	  
	  /**  A new incoming file transfer is indicated for the receiver. With 
	   * getPath() of the event the intended relative destination filepath can
	   * be obtained. With getFile() the temporary file is named which is 
	   * buffering streamed data.
	   */
	  FILE_INCOMING,

	  /** A file transfer has been aborted. This event is indicated at both 
	   * sides of the transfer. Details on the cause of the abortion are 
	   * indicated with the <i>getInfo()</i> value. (See manual for a legend.) 
	   * With <i>getPath()</i> of the event the intended relative destination 
	   * filepath can be obtained. With <i>getFile()</i> the file is named 
	   * which has been buffering streamed data (incoming) or which is the 
	   * data source (outgoing).
	   */
	  FILE_ABORTED,

	  /** Indicates to the receiver the success of a file transfer. 
      * With <i>getFile()</i> of the event the final location of the file can
      * be obtained, with <i>getPath()</i> the destination filepath information
      * as set by the sender.
      */
      FILE_RECEIVED,
      
	  /** Indicates to the sender the success of a file transfer.
       * With <i>getFile()</i> the transmitted data file, with <i>getPath()</i>
       * the destination filepath can be obtained as set by the sender. 
       */
      FILE_CONFIRMED,
      
   }