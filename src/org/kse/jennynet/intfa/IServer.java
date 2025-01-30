/*  File: IServer.java
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.kse.jennynet.core.DefaultServerListener;

/**
 * Describes the behaviour and features of a server object.
 * A server can be created with instances of class <code>Server</code>.
 * A server must be bound before it can be started. It has 
 * to be explicitly started with the <code>start()</code> method to become 
 * operational. Closing a server means to discontinue the service and release
 * socket resources. Closing the server, however, does not close rendered 
 * connections.
 * 
 * <p>A server object has a set of parameters associated. These
 * parameters are basically connection parameters which become the default
 * parameters of new connections rendered by the server. Some of the parameters
 * may also become instrumental for server operations, like e.g. the 
 * CONFIRM_TIMEOUT parameter for the time the server waits before it closes
 * down connection requests which are not dealt with by the application.
 * 
 * <p>A server object holds memory of the connections it has rendered. This 
 * is called the "connection registry" and consists of an array of open 
 * connections which the application can obtain at the interface. It is kept
 * solely for the convenience of the user, connections and server being 
 * completely separate functional entities. Note in particular that the closing
 * of a server object does not invalidate or close listed connections. 
 * If connections have to be closed along with the server, method <code>
 * closeAllConnections</code> can be called. The connection registry holds only
 * open connections while closing a connection removes them from the registry.
 * 
 * <p>This class is event dispatching. In the usual manner listeners can be 
 * added to Server instances. The listener has to comply with a set of methods
 * and is informed most importantly about new connections being made available
 * through the server (in signal method LISTENER). The listener interface is 
 * {@link ServerListener}; Class {@link DefaultServerListener} can aid 
 * programming of instant listener subclasses.   
 * 
 * <p>A server can be run in one of two connection signalling modes: LISTENER
 * and ACCEPT. The default method is LISTENER. LISTENER means that new 
 * connections are made available to the application through event dispatching
 * (in a thread owned by the server).
 * ACCEPT follows a different policy by letting applications poll new 
 * connections from a queue in a blocking method. Both methods are exclusive.
 * 
 * <p>Last but not least a server can function as a multiplexer for sending
 * objects, files or pings to all connections contained in the connection 
 * registry. Furthermore, all these connections can be closed with a single 
 * method.
 * 
 */

public interface IServer {

   /** Binds the server to a port. The IP-address is
    * the <i>wildcard</i> (0.0.0.0). A port number of zero leads
    * to a system created ephemeral port number.
    * 
    * @param port int port-number (0..65535) to define 
    *             the address of this server
    * @throws IOException if the server could not be bound or is 
    *         already bound
    * @throws IllegalArgumentException if port is out of range        
    */
   void bind (int port) throws IOException;

   /** Binds the server to a given local socket address (IP-address and 
    * port number). An address of null leads to a system created ephemeral
    * port number and the <i>wildcard</i> IP-address (0.0.0.0) to bind the 
    * socket.
    * 
    * @param address <code>SocketAddress</code>
    * @throws IOException if the server could not be bound or is 
    *         already bound
    */
   void bind (SocketAddress address) throws IOException;

   /** Whether this server is successfully bound to an address.
    * 
    * @return boolean true == bound to server-address
    */
   boolean isBound ();
   
   /** Returns IP address and port of this server's 
    *  socket or null if this socket is unbound.
    *  
    *  @return InetSocketAddress server socket address
    */
   InetSocketAddress getSocketAddress();

   /** Returns the set of connection-parameters which functions as default
    * for new connections accepted by this this server. The returned value can
    * be modified to form this server-specific but connection-generic set. 
    * The initial value of this set is taken from the global default parameters
    * in class {@code JennyNet} when a server is instantiated.
    * <p><small>NOTE: The server may take reference to some of the generic 
    * parameters for its operations, e.g. for the CONFIRM_TIMEOUT value.</small>
    * 
    * @return <code>ConnectionParameters</code>
    */
   ConnectionParameters getParameters ();

   /** Sets the set of connection parameters which is by default assigned
    * to incoming connections on this server.
    * 
    * @param parameters <code>ConnectionParameters</code>
    */ 
   void setParameters (ConnectionParameters parameters);
   
   /** Sets the priority value for this server's daemon thread dealing with
    * accepting connections from the Internet and dispatching server events. 
    * Defaults to Thread.MAX_PRIORITY - 2.
    * 
    * @param threadPriority int new thread priority
    * @throws IllegalArgumentException if threadPriority is out of range
    */
   void setThreadPriority (int threadPriority);
   
   /** Returns the priority value for this server's daemon thread dealing 
    * with accepting connections and dispatching server events. Defaults 
    * to Thread.MAX_PRIORITY - 2.
    * 
    * @return int threadPriority 
    */
   int getThreadPriority ();

   /** Sets any name for this server. This is for convenience of 
    * application use.
    * 
    * @param name String server name (may be null)
    */
   void setName (String name);

   /** Returns the text name given to this server by method <code>
    * setName(String)</code>.
    * 
    * @return String or null if undefined
    */
   String getName ();
   
   /** Sets the method by which incoming connections are signalled to the
    * user application. By default new connections are indicated via
    * event dispatcher to server-listeners. It can alternatively be set to 
    * make connections available at the <code>accept()</code> polling method
    * instead (in which case events are not issued). 
    * <p>This setting must be performed while the server is not started, 
    * otherwise an exception is thrown.
    *  
    * @param method <code>IServer.SignalMethod</code>
    * @throws IllegalStateException if server has been started
    */
   void setSignalMethod (ServerSignalMethod method);

   /** Returns this server's signalling method for incoming new
    * connections. Defaults to "LISTENER".
    * 
    * @return <code>ServerSignalMethod</code>
    */
   ServerSignalMethod getSignalMethod ();

   /** Sets the queue capacity for incoming server connections.
    * The queue capacity is only relevant for this server's signalling
    * method "ACCEPT".
    * <p>This setting must be performed while the server has not started, 
    * otherwise an exception is thrown.
    * 
    * @param capacity int accept queue capacity
    * @throws IllegalStateException if server has been started
    */
   void setAcceptQueueCapacity (int capacity);
   
   /** Sets whether this server owns the primacy to set TEMPO values
    * (transmission speed) for connections. If primacy is switched on,
    * clients are not allowed to set TEMPO on the connection. By default
    * this setting is <b>false</b>.
    * 
    * @param prime boolean true == server primacy, false == no primacy 
    *              (default)
    */
   void setTempoPrimacy (boolean prime);
   
   /** Whether this server owns the primacy to set TEMPO
    * (transmission speed) for connections. If primacy is switched on,
    * clients cannot set TEMPO on the connection.
    * 
    * @return boolean true == server primacy, false == no primacy (default)
    */
   boolean getTempoPrimacy ();
   
   /** Returns the queue capacity for incoming server connections.
    * Default value is <code>JennyNet.DEFAULT_QUEUE_CAPACITY</code>.
    * 
    * @return int accept queue capacity
    */
   int getAcceptQueueCapacity ();
   
   /** Returns a new connection that has reached at the port to 
    * which this server is bound. This method blocks until a connection is 
    * available, the calling thread has been interrupted or a given timeout 
    * expired. This method only works if this server's <code>SignalMethod
    * </code> is set to "Accept", otherwise an exception is thrown.
    * <p>The returned {@code ServerConnection} has to be started or rejected
    * within short time, otherwise it gets automatically closed for timeout
    * reason.
    * 
    * <p><small><u>Note:</u> The server holds a buffer queue for incoming
    * connections with limited capacity. If that capacity is exceeded, new
    * connections are rejected at the socket without informing the application.
    * </small> 
    * 
    * @param timeout int time in milliseconds to wait before this method
    *        returns from blocking if no connection arrives; a value 0 is
    *        interpreted as limitless.
    * @return <code>ServerConnection</code> or null if no connection becomes 
    *         available within the given time
    * @throws InterruptedException if the calling thread has been interrupted
    * @throws IllegalStateException if this server is not ALIVE or signalling
    *         method is not set to ACCEPT
    * @see setAcceptQueueCapacity        
    */        
   ServerConnection accept (int timeout) throws InterruptedException;

   /** Starts operating this server.
    * 
    * @throws IllegalStateException if this server is not bound or closed  
    */
   void start();

   /** Whether this server instance is capable of receiving new connections
    * from the Internet.
    * 
    * @return boolean true == server is alive
    */
   boolean isAlive ();
   
   /** Closes this server's port activity. A closed server
    * cannot be re-opened, instead a new server instance has to be created. 
    * This server's connections remain alive and the connection registry
    * untouched. The close method can be called at any time, including before
    * the server is started.
    */
   void close();

   /** Whether this server has been closed.
    * 
    * @return boolean true == server is closed
    */
   boolean isClosed ();
   
   /** Closes all registered connections and finally removes them from the 
    * connection registry. Note that this method only initiates SHUTDOWN
    * of all connections. It may not be assumed that connections are CLOSED
    * after this method returns. 
    */
   default void closeAllConnections () {closeAllConnections(null);}
   
   /** Closes all registered connections and finally removes them from the 
    * connection registry. Note that this method only initiates SHUTDOWN
    * of all connections. It may not be assumed that connections are CLOSED
    * after this method returns.
    * 
    * @param reason String optional reason for closure, may be null
    */
   void closeAllConnections (String reason);
   
   /** Closes this server, all registered connections and waits until all 
    * connections are CLOSED or the given time is spent.
    * <p>This method returns when all connections currently in the registry 
    * assumed the CLOSED state or the given time has passed or when the calling
    * thread is interrupted.
    * Any connections still not closed at a timeout will be hard-closed 
    * (socket closure) with an error code. 
    *
    * @param time long time to wait for connection closure (milliseconds),
    * 				 0 for unlimited
    * @throws InterruptedException
    */
   void closeAndWait (long time) throws InterruptedException;

   /** Waits until there are no more connections in the registry, the given
    * time has passed or the calling thread is interrupted.
    * 
    * @param time long time to wait (milliseconds), 0 for unlimited
    * @throws InterruptedException
    */
   void waitForAllClosed (long time) throws InterruptedException;
   
   /** Sends an object to all connected clients with "Normal" (medium) 
    * send priority.
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related error events to server-listeners.
    * 
    * @param object Object serialisable object (type must be registered
    *        for serialisation)
    * @return int transaction id number
    */
   int sendObjectToAll (Object object);

   /** Sends an object to all connected clients with a given send priority.
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related error events to server-listeners.
    * 
    * @param object Object serialisable object (type must be registered
    *        for serialisation)
    * @param priority <code>SendPriority</code> transmission priority
    * @return int transaction id number
    */
   int sendObjectToAll (Object object, SendPriority priority);

   /** Sends an object to all connected clients except the one given as
    * argument (if not null).
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related error events to server-listeners.
    *
    * @param id UUID of the connection exempted as target; may be null
    * @param object Object serialisable object (type must be registered)
    * @param priority <code>SendPriority</code> transmission priority
    * @return int transaction id number
    */
   int sendObjectToAllExcept (UUID id, Object object, SendPriority priority);
   
   /** Sends a file to all connected clients. The sending may fail
    * for a client if it is not ready to accept the transmission. 
    * The failure for one does not influence the sending to other
    * clients. Failures are indicated by issuing an error event to 
    * connection listeners.
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related error events to server-listeners.
    * 
    * @param file <code>File</code> file to transmit
    * @param pathInfo String intended file path at the remote station
    *                 (see documentation for operation contract)
    * @param priority {@code SendPriority} 
    * @return int transaction id number
    */
   int sendFileToAll (File file, String pathInfo, SendPriority priority);

   /** Sends a file to all connected clients except the one
    * given as argument (if not null). The sending may fail for a client if
    * it is not ready to accept the transmission. The failure for one
    * does not influence the sending to other clients. Failures are
    * indicated by issuing an error event to connection listeners.
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related events.
    * 
    * @param id UUID of the connection exempted as target; may be null
    * @param file File file to transmit
    * @param pathInfo String intended file path at the remote station
    *                 (see documentation for operation contract)
    * @param priority {@code SendPriority} 
    * @return int transaction id number
    */
   int sendFileToAllExcept (UUID id, File file, String pathInfo, SendPriority priority);

   /** Sends a PING signal to all connected clients. Corresponding 
    * PING-ECHOs will be reported as events to connection listeners.
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related events.
    *  
    * @return int transaction id number
    */
   int sendPingToAll ();

   /** Sends a maximum speed setting to all active connections.
    * <p>Returns a positive transaction number which is used only once and 
    * indicated in related events.
    * 
    * @param baud int BAUD transmission speed in bytes per second
    * @return int transaction id number
    */
   int sendTempoToAll (int baud);

   /** Adds a server-listener to this server.
    * 
    * @param listener <code>IServerListener</code>
    */
   void addListener (ServerListener listener);

   /** Removes the given server-listener from this server.
    * 
    * @param listener <code>IServerListener</code> (may be null)
    */
   void removeListener (ServerListener listener);

   /** Returns the list of registered {@code ServerListener} instances.
    * It is save to modify the list.
    * 
    * @return {@code ServerListener[]}
    */
   ServerListener[] getListeners ();
   
   /** Returns the connection with the given UUID identifier or null
    * if this connection is not listed in the registry. The returned 
    * connection can be unconnected but not closed. 
    * 
    * @param uuid UUID connection name 
    * @return <code>ServerConnection</code> or null
    */
   ServerConnection getConnection (UUID uuid);
   
   /** Returns the connection with the given Internet socket address of the
    * remote host or null if such a connection is not in the registry.
    * The returned connection could be unconnected but not closed. 
    * 
    * @param addr {@code InetSocketAddress} Internet address of remote end
    * @return <code>ServerConnection</code> or null
    */
   ServerConnection getConnection (InetSocketAddress addr);
   
   /** Returns an array with all listed open connections.
    * The array returned is a copy and may be modified by the application.
    * 
    * @return {@code ServerConnection[]} 
    */
   ServerConnection[] getConnections ();

   /** Returns a {@code List} of all open server-connections.
    * The list structure can be modified without consequences for the server.
    * 
    * @return {@code List<ServerConnection>} 
    */
   List<ServerConnection> getConnectionList ();

   /** Removes the given connection from the registry of this server. This
    * does not close the connection. Tolerates <b>null</b> for no-operation.
    * 
    * @param connection <code>ServerConnection</code> (may be null)
    */
   void removeConnection (ServerConnection connection);

   /** Adds a connection to the registry of this server.
    * Adding may fail silently if the given connection is closed or null. 
    * A connection contained in the registry prior to this call with the same
    * UUID value will be replaced with the argument connection.
    * <p><small>This method tolerates <b>null</b> for no-operation.</small>
    * 
    * @param connection <code>Connection</code> (may be null)
    * @return boolean true == connection is in registry, false otherwise
    */
   boolean addConnection(ServerConnection connection);

   /** Adds a connection-listener to this server which will be added to
    * every accepted new or added connection. This will not add the listener
    * to already listed connections.
    * 
    * @param listener {@code ConnectionListener}
    */
   void addConnectionListener(ConnectionListener listener);

   /** Removes the given auto-addable connection-listener from this server. 
    * This does not remove the listener from existing connection. 
    * 
    * @param listener {@code ConnectionListener}
    */
   void removeConnectionListener(ConnectionListener listener);

   /** Returns the set of auto-addable connection-listeners which were
    * registered in this server. The set structure is a clone.
    *  
    * @return {@code Set<ConnectionListener>}
    */
   Set<ConnectionListener> getConnectionListeners ();

}