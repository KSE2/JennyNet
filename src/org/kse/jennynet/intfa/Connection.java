/*  File: Connection.java
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.kse.jennynet.core.ConnectionMonitor;
import org.kse.jennynet.exception.ClosedConnectionException;
import org.kse.jennynet.exception.FileInTransmissionException;
import org.kse.jennynet.exception.ListOverflowException;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.exception.UnconnectedException;
import org.kse.jennynet.exception.UnregisteredObjectException;

/**
 * Set of methods that characterise, build and use the features of a 
 * <i>JennyNet</i> Internet connection. A connection can transfer objects of
 * registered classes and any file which can be opened for reading in the local
 * file-system. In order to transmit objects, the layer requires a {@code 
 * Serialization} to be active for sending and receiving. {@code Serialization} 
 * objects represent serialisation methods and are used to register classes 
 * for transmission. Objects of unregistered classes cannot be transmitted. 
 * Default serialisations are active on new {@code Connection}s. They can be 
 * used as individual registration sets for the specific connection. See 
 * {@code JennyNet} and {@code Server} classes for global (generic) 
 * registrations.
 * 
 * <p><b>Channels and Send-Priorities</b>
 * <br>Sending occurs within multiple channels which are characterised
 * by the cardinal entity-type (FILE, OBJECT) and the SEND-PRIORITIES. As 
 * there are 5 priority values and 2 cardinal types, there are 10 transmission
 * channels in the <i>JennyNet</i> transport layer. 
 * Each channel owns the property to replicate the sequence
 * of send-orders at the reception side. The time-relation of
 * the channels to each other is by principle undefined; the ordering of
 * send-priorities is a mere hint for the transport layer which is not 
 * guaranteed to be realised. Object transmissions are generally preferred to
 * file-transmissions. i.e. all channels of file-transmission rank lower than
 * the channels of object transmissions. 
 * 
 * <p><b>Operation States</b>
 * <br>A {@code Connection} always is in one of the following operation states:
 * UNCONNECTED, CONNECTED, SHUTDOWN and CLOSED. The transition of states is
 * uni-directional and non-cyclic; it starts with UNCONNECTED (where 
 * parameter setting, connecting and closing operations can take place), moves
 * to CONNECTED (where all communication operations with the remote partner 
 * are performed) and finally ends in CLOSED (where no operations are possible). 
 * SHUTDOWN is a short-lived state which is assumed on both sides after a 
 * <i>close</i> command has been given on any side, and in which only reception
 * and abortion events still take place. In case of a fatal error of the 
 * connection or if <i>hard-close</i> is called, SHUTDOWN is skipped and the 
 * resulting state is CLOSED.
 * 
 * <p><b>Listeners or Poll-Services</b>
 * <br>A {@code Connection} needs to have a listener of type {@code 
 * ConnectionListener} or a {@code  ConnectionPollService} in order to receive
 * results from the communication with the remote station and to receive events
 * concerning the stability of the connection. Incoming and completed file 
 * transmissions are also indicated here.
 */
public interface Connection {

    /** A base division of algorithms in the <i>JennyNet</i> communication 
     * layer, expressed as CLIENT and SERVER.
    */
    public enum LayerCategory {
	      CLIENT, SERVER; 
    }
   
   /** Operation state of a {@code Connection}: UNCONNECTED, CONNECTED, 
    * SHUTDOWN, CLOSED.
    * <p>At any time a {@code Connection} possesses exactly one value of
    * {@code ConnectionState} as attribute. It cannot change to a state of 
    * lower ranking.
	 */
	public enum ConnectionState {
		UNCONNECTED, CONNECTED, SHUTDOWN, CLOSED;
	}

   /** Returns a structure containing the set of parameters currently operative 
    * for this connection. 
    * <p>The returned instance of {@code ConnectionParameters} can be used to
    * modify operational values of this connection where this is enabled.
    *  
    * @return <code>ConnectionParameters</code>
    */
   ConnectionParameters getParameters();

   /** Copies the given sets of parameters to become instructive for this 
    * connection. This method is only supported in state UNCONNECTED, 
    * otherwise an exception is thrown.
    *  
    * @param parameters <code>ConnectionParameters</code>
    * @throws IllegalStateException if connection is connected
    * @throws IOException if some directory setting doesn't work
    */
   void setParameters (ConnectionParameters parameters) throws IOException;

   /** Returns the serialisation device for the outgoing stream 
    * of this connection and the given serialisation method. Modifications 
    * performed on the returned device remain local to this connection.
    * Each method has its own set of registered classes.
    * <p><small>NOTE: Modifications performed on the global serialisation
    * instance ({@code JennyNet}) do not strike through to already instantiated
    * {@code Connections}. </small>
    *  
    * @param method int serialisation method number (0 = Java, 1 = Kryo, 
    *                   2 = custom)
    * @return <code>Serialization</code>
    * @throws IllegalArgumentException if method is undefined
    * @throws SerialisationUnavailableException if the method is not supported
    */
   Serialization getSendSerialization (int method);

   /** Returns the serialisation device for the incoming stream 
    * of this connection and the given serialisation method. Modifications 
    * performed on the returned device remain local to this connection.
    * Each method has its own set of registered classes.
    * <p><small>NOTE: Modifications performed on the global serialisation
    * instance ({@code JennyNet}) do not strike through to already instantiated
    * {@code Connections}. </small>
    *  
    * @param method int serialisation method number (0 = Java, 1 = Kryo,
    *                   2 = custom)
    * @return <code>Serialization</code>
    * @throws IllegalArgumentException if method is undefined
    * @throws SerialisationUnavailableException if the method is not supported
    */
   Serialization getReceiveSerialization (int method);

   /** Returns the serialisation device for the outgoing stream of this 
    * connection and the current parameter method setting. Modifications 
    * performed on the returned device remain local to this connection.
    * <p><small>NOTE: Modifications performed on the global serialisation
    * instance ({@code JennyNet}) do not strike through to already instantiated
    * {@code Connections}. </small>
    *  
    * @return <code>Serialization</code>
    * @throws SerialisationUnavailableException if the default method
    *         (parameters) is not supported
    */
   Serialization getSendSerialization ();

   /** Returns the serialisation device for the incoming stream of this 
    * connection and the current parameter method setting. Modifications 
    * performed on the returned device remain local to this connection.
    * <p><small>NOTE: Modifications performed on the global serialisation
    * instance ({@code JennyNet}) do not strike through to already instantiated
    * {@code Connections}. </small>
    *  
    * @return <code>Serialization</code>
    * @throws SerialisationUnavailableException if the default method
    *         (parameters) is not supported
    */
   Serialization getReceiveSerialization ();
   
   /** Returns the unique ID of this connection. The value is automatically
    * created together with the instance of <code>Connection</code>. It may
    * also be set by the application.
    *  
    * @return <code>UUID</code> technical connection identifier
    */
   UUID getUUID ();

   /** Sets the UUID identifier for this connection. Care has to be taken
    * about the circumstance when this method is called as this instance
    * may lose its membership in hash-tables and environments. As for 
    * JennyNet handling, UUID of a <code>Connection</code> owned/rendered
    * by a <code>Server</code> should not be altered or otherwise be removed
    * and re-added into the server's connection list explicitly.
    *  
    * @param uuid <code>UUID</code>
    */
   void setUUID (UUID uuid);

   /** Returns 4 bytes of a short identifier value for this connection
    * based on its UUID value.
    * 
    * @return byte[] (4 bytes)
    */
   byte[] getShortId ();
   
   /** Returns the IP-address and port number of the remote end of this
    *  connection, or null if this is undefined.
    *  
    *  @return InetSocketAddress remote address or null
    */
   InetSocketAddress getRemoteAddress();

   /** Returns the IP-address and port number of the local end 
    *  of this connection, or null if this is undefined.
    *  
    *  @return InetSocketAddress local address or null
    */
   InetSocketAddress getLocalAddress();

   /** Whether this Connection is connected to the remote end 
    * via its socket (whether socket is connected and not closed). This is
    * expected to be the case in operation states CONNECTED or SHUTDOWN. 
    * Note that a connection can become disconnected at any time.
    * A disconnection is indicated as event to connection listeners.
    * 
    * @return boolean "connected" status
    */
   boolean isConnected();

   /** Whether this Connection is closed (operation state CLOSED). 
    * A closed connection cannot be reused. Closure is indicated to 
    * connection listeners after this final state has been reached.
    * 
    * @return boolean "closed" status
    */
   boolean isClosed();

   /** Whether this connection is currently engaged in data transmission
    * (reading or writing) over the net. The fall down of this flag has a
    * latency of 4 seconds.
    * 
    * @return boolean transmission status
    */
   boolean isTransmitting ();
   
   /** Whether object and event delivery is performed on a global (static) 
    * thread. If <b>false</b> object and event delivery performs on a thread 
    * specific to this connection (which adds storage and execution demand to 
    * the layer). The status may change caused by the layer if delivery has
    * been blocking for a lengthy time and blocking control has been activated
    * in the {@code JennyNet} class.
    * <p><small>NOTE: Object and event delivery occurs sequentially for a 
    * single connection. On connections with specific output threads, delivery
    * can perform parallel with other connections. In this case the application
    * is responsible for avoiding damaging overrun conditions.</small>
    * 
    * @return boolean true = static thread delivery, false = specific thread
    *                 delivery
    */
   boolean isGlobalOutput ();

   /** Whether this connection operates below the defined IDLE threshold.
    * If no IDLE threshold has been defined, <i>false</i> is always returned.
    * <p><small>The IDLE threshold can be set up at the connection parameters.
    * Per package default idle-control is switched off.</small>     
    *  
    * @return boolean false == inactive control or above or equal IDLE 
    *                 threshold (BUSY); 
    *                 true == below IDLE threshold (IDLE)
    */
   boolean isIdle();
   
   /** Returns the operation state of this connection.
    * 
    * @return {@code ConnectionState}
    */
   ConnectionState getOperationState ();
   
   /** Returns the duration value in milliseconds of the last ping echo
    * received or zero if no echo was ever received.
    * 
    * @return int milliseconds
    */
   int getLastPingTime ();
   
   /** Sends the given serialisable Object over the network in the NORMAL
    * transmission priority. The returned identifier is reflected as 
    * object-ID in related transmission events. This method returns quickly
    * or fails with exception. 
    * <p><small>NOTE: Actual sending is not performed on the calling thread.
    * For objects which cannot be instantly transmitted, a limited buffer list 
    * takes them up. In order to be serialisable, the object's class has to be 
    * registered for transmission at this connection's send-serialisation 
    * device otherwise an exception is thrown.</small>
    * 
    * @param object Object serialisable object to be sent
    * @return long object identifier
    * @throws UnregisteredObjectException if object class is not 
    *         registered for transmission
    * @throws ListOverflowException if the send queue was full
    * @throws SerialisationUnavailableException if the send-serialisation
    * 		  is unavailable
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   default long sendObject (Object object) {
	   return sendObject(object, SendPriority.NORMAL);
   }

   /** Sends the given serialisable Object over the network in the given
    * transmission priority. The returned identifier is reflected as 
    * object-ID in related transmission events. This method returns quickly
    * or fails with exception.
    * <p>Each priority value effectively constitutes a separate transmission 
    * channel and the serial order of posted objects only is guaranteed on the
    * reception side within the same priority channel. 
    * <p><small>Actual sending is not performed on the calling thread. For
    * objects which cannot be instantly transmitted, a limited buffer list 
    * takes them up. In order to be serialisable, the object's class has to be 
    * registered for transmission at this connection's send-serialisation 
    * device otherwise an exception is thrown.</small>
    * 
    * @param object Object serialisable object to be sent
    * @param priority <code>SendPriority</code> transmission priority
    * @return long object identifier
    * @throws UnregisteredObjectException if object is not 
    *         registered for transmission
    * @throws ListOverflowException if the send queue was full
    * @throws SerialisationUnavailableException if the send-serialisation
    * 		  is unavailable
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   default long sendObject (Object object, SendPriority priority) {
	   int method = getParameters().getSerialisationMethod();
	   return sendObject(object, method, priority);
   }

   /** Sends the given serialisable Object over the network under the given
    * transmission priority and serialisation method. The returned
    * identifier is reflected as object-ID in related transmission events. 
    * This method returns quickly or fails with exception.
    * <p>Each priority value effectively constitutes a separate transmission 
    * channel and the serial order of posted objects only is guaranteed on the
    * reception side within the same priority channel. 
    * <p><small>Actual sending is not performed on the calling thread. For
    * objects which cannot be instantly transmitted, a limited buffer list 
    * takes them up. In order to be serialisable, the object's class has to be 
    * registered for transmission at this connection's send-serialisation 
    * device otherwise an exception is thrown.</small>
    * 
    * @param object Object serialisable object to be sent
    * @param method int serialisation method (0 = Java, 1 = Kryo, 
    *                   2 = custom)
    * @param priority <code>SendPriority</code> transmission priority
    * @return long object identifier
    * @throws IllegalArgumentException if method is undefined
    * @throws UnregisteredObjectException if object is not 
    *         registered for transmission
    * @throws ListOverflowException if the send queue was full
    * @throws SerialisationUnavailableException if the send-serialisation
    * 		  is unavailable
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   long sendObject (Object object, int method, SendPriority priority);

   /** Transfers a file to the remote station. Files can be scheduled
    * with any length (Long.MAX_VALUE). The returned file identifier can be
    * referenced in subsequent commands concerning the transmission
    * and is reflected as object-ID in related transmission events.
    * The send-priority used in this method is <i>Normal</i>. 
    * This method returns quickly or fails with exception.
    * <p><small>Files are always transmitted in a lower preference than 
    * objects. In consequence the transmission of files is not guaranteed to
    * occur within a time. The remote station may reject or be not prepared 
    * to receive transmissions or a specific filepath. More details on 
    * handling transmissions is available in the manual page. 
    * </small>
    * 
    * @param file <code>File</code> the file to be sent
    * @param remotePath String target path information for the remote station
    * @return long file identifier
    * @throws FileNotFoundException if the file cannot be found or read
    * @throws FileInTransmissionException if the remote-path is already in 
    *         transmission
    * @throws IllegalArgumentException if remote-path is empty
    * @throws ListOverflowException if the sender list was full
    * @throws UnsupportedOperationException if file-sending is switched off
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    * @throws IOException 
    */
   default long sendFile (File file, String remotePath) throws IOException {
	   return sendFile(file, remotePath, SendPriority.NORMAL, 0);
   }

   /** Transfers a file to the remote station in a specific priority channel.
    * Files can be scheduled with any length (Long.MAX_VALUE). The returned 
    * file identifier can be referenced in subsequent commands concerning the
    * posted transmission and is also reflected as object-ID in related 
    * transmission events.
    * This method returns quickly or fails with exception.
    * <p><small>Files are always transmitted in a lower preference than 
    * objects. In consequence the transmission of files is not guaranteed to
    * occur within a time. The remote station may reject or be not prepared 
    * to receive transmissions or a specific filepath. More details on 
    * handling transmissions is available in the manual page. 
    * </small>
    * 
    * @param file <code>File</code> the file to be sent
    * @param remotePath String target path information for the remote station
    * @param priority <code>SendPriority</code> priority
    * @return long file identifier
    * @throws FileNotFoundException if the file cannot be found or read
    * @throws FileInTransmissionException if the remote-path is already in 
    *         transmission
    * @throws IllegalArgumentException if remote-path is empty
    * @throws ListOverflowException if the sender list was full
    * @throws UnsupportedOperationException if file-sending is switched off
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    * @throws IOException 
    */
   default long sendFile (File file, String remotePath, SendPriority priority) 
		      throws IOException {
	   return sendFile(file, remotePath, priority, 0);
   }

   /** Transfers a file to the remote station in a specific priority channel
    * and with a transaction code. The transaction code will be indicated in
    * the delivery event. Files can be scheduled
    * with any length (Long.MAX_VALUE). The returned file identifier can be
    * referenced in subsequent commands concerning the posted transmission
    * and is also reflected as object-ID in related transmission events.
    * This method returns quickly or fails with exception.
    * <p><small>Files are always transmitted in a lower preference than 
    * objects. In consequence the transmission of files is not guaranteed to
    * occur within a time. The remote station may reject or be not prepared 
    * to receive transmissions or a specific filepath. More details on 
    * handling transmissions is available in the manual page. 
    * </small>
    * 
    * @param file <code>File</code> the file to be sent
    * @param remotePath String target path information for the remote station
    * @param priority <code>SendPriority</code> priority
    * @param transaction int a reference for transaction compounds (optional)
    * @return long file identifier
    * @throws FileNotFoundException if the file cannot be found or read
    * @throws FileInTransmissionException if the remote-path is already in 
    *         transmission
    * @throws IllegalArgumentException if remote-path is empty
    * @throws ListOverflowException if the sender list was full
    * @throws UnsupportedOperationException if file-sending is switched off
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    * @throws IOException 
    */
   long sendFile (File file, String remotePath, SendPriority priority, 
		      int transaction) throws IOException;
   
   /** Sends the given block of byte data over the network.
    * <p>Class <i>{@code JennyNetByteBuffer}</i> is used to represent 
    * the given block at the remote station as incoming object.    
    * 
    * @param buffer byte[] data buffer
    * @param start int buffer offset of data to be sent
    * @param length int length of data to be sent
    * @param priority <code>SendPriority</code> transmission priority
    * @return long object identifier
    * @throws IllegalArgumentException if data addressing is wrong
    * @throws ListOverflowException if the send queue was full
    * @throws SerialisationUnavailableException if the send-serialisation
    * 		  is unavailable
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */ 
   long sendData (byte[] buffer, int start, int length, SendPriority priority);
   
   /** Sends the given block of byte data over the network.
    * <p>Class <i>{@code JennyNetByteBuffer}</i> is used to represent 
    * the given block at the remote station as incoming object.    
    * 
    * @param buffer byte[] data buffer
    * @param priority <code>SendPriority</code> transmission priority
    * @return long object identifier
    * @throws IllegalArgumentException if data addressing is wrong
    * @throws ListOverflowException if the send queue was full
    * @throws SerialisationUnavailableException if the send-serialisation
    * 		  is unavailable
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */ 
   default long sendData (byte[] buffer, SendPriority priority) {
	   return sendData(buffer, 0, buffer.length, priority);
   }

   /** Sends a PING to the remote station. The corresponding PING-ECHO 
    * will be indicated as PING-ECHO event to connection listeners.
    * <p>One PING sending is permitted every 5 seconds. Additional 
    * send attempts are not performed and prompted by return value -1.  
    * 
    * @return long PING identifier number (PINGs have their own name space),
    *              -1 if sending of the PING was suppressed
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   long sendPing ();

   /** Attempts to set the transmission TEMPO on both ends of this connection
    * to match the given Baud rate, where one symbol is defined as one byte
    * (bytes per second). 
    * This is useful to slow down the transmission rate of a connection. A
    * value of zero blocks all active sending orders until a higher value is 
    * set. The special value -1 indicates "no limitation". 
    * <p><small>Setting the TEMPO is by default unrestricted for both ends
    * of the connection, however the server can be set to become the exclusive
    * master of the TEMPO setting. In this case attempts to set the speed by
    * the client side are ineffective.</small>
    * 
    * @param baud int speed in bytes-per-second or -1 for no limit
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   void setTempo (int baud);
   
   /** Fatally terminates an incomplete file transmission, incoming or 
    * outgoing, identified by its object-ID. The layer discriminates incoming
    * and outgoing name spaces for objects (including files). The 
    * name space is expressed here with the <i>direction</i> parameter. Does 
    * nothing if there is no transmission found for the given identifier.
    * <p><small>The practical value of this method lies in rejecting an 
    * incoming file transmission which was indicated in a connection
    * event, or an outgoing transmission if for some reason it has become 
    * obsolete. Outgoing transmissions are identified with the return value
    * of a <code>sendFile()</code> method. 
    * The breaking of a transmissions which has completed is meaningless and
    * silently ignored.</small>
    * 
    * @param objectID long identifier for a file transmission
    * @param direction {@code ComDirection} incoming or outgoing name space
    * @return boolean true = transmission found, false = transmission not found
    */
   boolean breakTransfer (long objectID, ComDirection direction);
   
   /** Fatally terminates an incomplete file transmission, incoming or 
    * outgoing, identified by its object-ID, while stating a text for the 
    * reason or cause of the break. The layer discriminates incoming
    * and outgoing name spaces for objects (including files). The name space 
    * is expressed here with the <i>direction</i> parameter. Does 
    * nothing if there is no transmission found for the given identifier.
    * <p><small>The practical value of this method lies in rejecting an 
    * incoming file transmission which was indicated in a connection
    * event, or an outgoing transmission if for some reason it has become 
    * obsolete. Outgoing transmissions are identified with the return value
    * of a <code>sendFile()</code> method. 
    * The breaking of a transmissions which has completed is meaningless and
    * silently ignored.</small>
    * 
    * @param objectID long identifier number for transmitted file
    * @param direction {@code ComDirection} incoming or outgoing name space
    * @param text String optional cause information for remote
    * @return boolean true = transmission found, false = transmission not found
    */
   boolean breakTransfer (long objectID, ComDirection direction, String text);
   
   /** Initiates the shutdown of this connection with the terminal goal to
    * close it permanently. This issues the SHUTDOWN event to connection 
    * listeners (operation state SHUTDOWN) and the parallel shutdown of this 
    * connection on the remote station.
    * <p>In the SHUTDOWN state ongoing transmissions continue until finished
    * and the connection terminally enters CLOSED state. After calling this 
    * method no new transmission orders can be given.  
    */
   default void close() {close((String)null);}


   /** Initiates the shutdown of this connection with the terminal goal to
    * close it permanently. Optionally, a text for the reason of the closure 
    * can be transmitted. This issues the SHUTDOWN event to connection 
    * listeners (operation state SHUTDOWN) and the parallel shutdown of this 
    * connection on the remote station.
    * <p>In the SHUTDOWN state ongoing transmissions continue until finished
    * and the connection terminally enters CLOSED state. After calling this 
    * method no new transmission orders can be given.  
    * 
    * @param reason String optional text, may be null
    */
   void close (String reason);

   /** Closes this connection immediately by closing the network socket.
    * If not necessitated by special circumstance, the regular {@code close()} 
    * method should be preferred. Loss of data which has been ordered to be 
    * sent can occur. 
    */
   void closeHard ();
   
   /** Closes this connection and waits until this connection reaches the 
    * CLOSED state, an optional time limit has passed or the calling thread is
    * interrupted. If time limit was exceeded, a hard closure of the connection
    * with an error code is performed.
    * 
    * @param time long time to wait for CLOSED (milliseconds), 0 for unlimited
    * @throws InterruptedException
    */
   default void closeAndWait (long time) throws InterruptedException {
	   close();
	   waitForClosed(time);
   }

   /** Waits until this connection receives regular socket closure, an 
    * optional time limit has passed or the calling thread is interrupted. 
 	* If time limit was exceeded, a hard closure of the connection with an 
 	* error code is performed.
 	* <p><small>This method has slightly different effects compared to
 	* <i>waitForClosed()</i>. For instance if applied to an UNCONNECTED
 	* instance, it does not turn its operation state to CLOSED but only 
 	* returns. It returns earlier to socket failure or closure.</small>
    * 
    * @param time long time to wait for disconnection (milliseconds), 
    * 				0 for unlimited
    * @throws InterruptedException
    */
   void waitForDisconnect (long time) throws InterruptedException;
   
   /** Waits until this connection reaches the CLOSED state, an 
    * optional time limit has passed or the calling thread is interrupted. 
 	* If time limit was exceeded, a hard closure of the connection with an 
 	* error code is performed.
    * 
    * @param time long time to wait for CLOSED (milliseconds), 
    * 				0 for unlimited
    * @throws InterruptedException
    */
   void waitForClosed (long time)  throws InterruptedException;
   
   /** Adds a listener to communication events of this connection. 
    * If the listener already exists, it is not added again.
    * <p><small>NOTE: <i>JennyNet</i> offers the no-operation <code>
    * DefaultConnectionsListener</code> class to ease programming of 
    * listeners.</small>. 
    * 
    * @param listener <code>ConnectionListener</code> event listener 
    *                 (may be null)
    */
   void addListener (ConnectionListener listener);

   /** Removes a listener to communication events of this connection. 
    * 
    * @param listener <code>ConnectionListener</code> event listener 
    *                 (may be null)
    */
   void removeListener (ConnectionListener listener);
   
   /** Returns the set of {@code ConnectionListener} which are currently
    * listening to events of this connection.
    * 
    * @return {@code Set<ConnectionListener>}
    */
   Set<ConnectionListener> getListeners ();

   /** Sets the human friendly name for this connection.
    *  
    * @param name String connection name
    */
   void setName (String name);

   /** Returns the human friendly name of this connection or null
    * if it is undefined.
    * 
    * @return String name of connection or null
    */
   String getName ();
   
   /** Returns a <code>Properties</code> instance owned by this connection
    * for user purposes. <i>JennyNet</i> does not set or use values of this 
    * structure.
    *  
    * @return <code>Properties</code>
    */
   Properties getProperties ();
   
   /** Returns a set of inspection values for this connection.
    * 
    * @return {@code ConnectionMonitor}
    */
   ConnectionMonitor getMonitor ();
   
   /** Returns the currently active transmission speed (TEMPO) of this 
    * connection expressed in bytes per second. A value zero indicates that 
    * transmission is blocked. This value reflects a setting, not a measurement.
    * 
    * @return int speed in bytes per second
    */
   int getTransmissionSpeed ();
   
   /** Two Connection instances are equal if they share identical values
    * on both their local and remote socket addresses. 
    * 
    * @param obj <code>Object</code>
    * @return boolean true if instances share same end-point addresses
    */
   @Override
   boolean equals (Object obj);
   
   /** An equals-compliant hashcode for this connection.
    * 
    * @return int instance hashcode
    */
   @Override
   int hashCode ();
   
   /** Returns the human readable name of this connection, if any, followed 
    * by a printout of 2 Internet socket addresses (IP-address and port number
    * ), leading with the local and trailing with the remote address, 
    * separated by an arrow.
    * 
    * @return String textual representation of this connection
    */
   @Override
   String toString();

   /** Returns the layer-category of this connection. Layer categories are
    * CLIENT or SERVER. 
    * 
    * @return {@code LayerCategory}
    */
   LayerCategory getCategory();

}