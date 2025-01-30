/*  File: ConnectionListener.java
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


/** Interface for application algorithms that listen to events of a 
 * <i>JennyNet</i> <code>Connection</code>. A <code>ConnectionListener</code> 
 * has to be registered at one or more <code>Connection</code> instances to 
 * receive its events. The listener consist of a set of methods, each 
 * representing an event type. The methods identify the issuing connection.
 * If events are required to become objects, the application can create them
 * via instantiating class {@code ConnectionEvent}.
 * 
 * <p><b>Organisation</b>
 * <p>As basic rule, event dispatching for a connection always occurs 
 * sequential in a <i>JennyNet</i> own thread. If there are multiple 
 * connections in a layer (e.g. a server) the default behaviour is that the 
 * events of ALL connections occur sequential in a singular thread. This 
 * behaviour can be changed for particular connections by setting their 
 * parameter <i>DeliveryThreadUsage</i> to INDIVIDUAL (by default GLOBAL). In
 * this case the specific connection has its own delivery thread and there are
 * more than one delivery threads active at the same time. This circumstance 
 * may also exist after a blocking error condition occurred in one of the 
 * listener methods (application algorithm) and the control circuit for
 * delivery blocking has been activated in 
 * {@code JennyNet.setConnectionBlockingControl()}. 
 * 
 * <p><b>Time Management</b>
 * <p>Event execution in the application should not take long amounts of time 
 * and operations that can block must be avoided. In consequence of blocking 
 * execution in the listener, the output thread for the affected connection
 * may change and parallel event dispatching ensues on a new thread. 
 * To avoid blocking, event digestion should be transferred to user owned 
 * threads as soon as possible.
 * 
 * <p><b>Alternative</b>
 * <p>An alternative way to receive connection events is by polling from a
 * {@code ConnectionPollService}. In this case the application never deals 
 * with <i>JennyNet</i> owned threads.
 * 
 *  @see Connection
 */

public interface ConnectionListener {

  /** Called when the remote end has been connected on the layer level
   * (operation state CONNECTED). 
   * <p>This event will occur before any objects are received or can be sent. 
   * The event implies that all controls for establishing a connection on the
   * remote side have been passed.   
   * 
   * @param connection <code>Connection</code> source connection
   */
   void connected (Connection connection);

   /** Called when the IDLE state of the connection changes (falls below or 
    * rises above the IDLE THRESHOLD). This event can only appear if an 
    * IDLE-THRESHOLD has been set up in connection parameters.
    * 
    * @param connection <code>Connection</code> source connection
    * @param idle boolean true == state IDLE, false == state BUSY
    * @param exchange int measured bytes per minute in control period
    */ 
   void idleChanged (Connection connection, boolean idle, int exchange);

   /** Called when the remote end is no longer connected (operation state
    * CLOSED). In the regular case the CLOSED state follows the SHUTDOWN state
    * and leaves a clean transmission queue. In case of an error condition 
    * (e.g. a network breakdown) or hard closure by the user, ongoing
    * file or object transmissions can be broken (which is indicated via 
    * separate events before this CLOSED event).
    * <p>This always indicates that the connection has gone out of use, neither 
    * outgoing messages nor incoming events will further occur. 
    * A closed connection cannot be reused.
    * 
    * @param connection <code>Connection</code> source connection
    * @param cause int code for cause of disconnection if available
    * @param message String message about cause of disconnection if available
    */
   void closed (Connection connection, int cause, String message);

   /** Called when a regular <i>close()</i> method has been called on any side 
    * of the connection (operation state SHUTDOWN). In case of some internal
    * errors, the SHUTDOWN state can be triggered by the layer itself. 
    * <p>In the SHUTDOWN state new send orders are prohibited but
    * incoming communication still takes place and file-transfers can be
    * cancelled. After all outstanding transmissions are finished, the 
    * connection enters CLOSED state.
    * 
    * @param connection <code>Connection</code>  source connection
    * @param cause int error-code for the cause of closing
    * @param message String message about cause of closing if available
    */
   void shutdown (Connection connection, int cause, String message);

   /** Called when an application object has been received from the remote end
    *  of the connection. The given object is available here in same data 
    *  state and with the layer created identifier as was sent on the 
    *  remote end (remote name space).
    *  
    * @param connection <code>Connection</code> source connection
    * @param priority {@code SendPriority}
    * @param objectNr long identifier of received object (remote name space)
    * @param object Object received object
    */   
   void objectReceived (Connection connection, SendPriority priority, 
		   			    long objectNr, Object object);

   /** Called when an application object which was sent on the local connection
    * interface could not be transmitted due to socket failure, connection 
    * shutdown or a serialisation problem. The argument object, if available,
    * is the original as given by the user and with the identifier as returned
    * by the sending method.
    *  
    * @param connection <code>Connection</code> source connection
    * @param objectNr long identifier of failed object (local name space)
    * @param object Object sending object or null if unavailable
    * @param info int error code
    * @param msg String error comment, may be null 
    */   
   void objectAborted (Connection connection, long objectNr, Object object, 
		   		       int info, String msg);

   /** Called when a PING-ECHO was received from the remote station. The
    * remote station sends a ping-echo as a reaction to the sending of a
    * PING signal from the connection interface. 
    * 
    * @param pingEcho {@code PingEcho}
    * @see PingEcho
    */
   void pingEchoReceived (PingEcho pingEcho);

   /** Called when an event occurred concerning a file-transmission of the 
    * source connection, incoming or outgoing. More detail on the event types
    * can be found in the interface description of type <code>TransmissionEvent
    * </code>.
    * 
    * <p><small>A received file is indicated with <code>event.getType() == 
    * TransmissionEventType.FILETRANSFER_RECEIVED</code>.
    * The received file is available at <code>event.getFile()</code>.
    * </small>
    *  
    * @param event File <code>TransmissionEvent</code>
    * @see TransmissionEvent
    */
   void transmissionEventOccurred (TransmissionEvent event);
   
}