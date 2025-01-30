/*  File: Server.java
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerListener;
import org.kse.jennynet.intfa.ServerSignalMethod;
import org.kse.jennynet.util.ArraySet;

/**
 *  <p>A {@code Server} object can be allocated, either bound to a local 
 *  socket port or unbound. However, a server must be bound before it can be 
 *  started. In the client-server architecture, which is enabled by 
 *  <i>JennyNet</i>, the server receives incoming calls from clients and
 *  either rejects them or makes them available as {@code Connection} 
 *  objects to the user application. In order to deal with this functionality
 *  an application adds a {@code ServerListener} to the server (which is not
 *  to be confused with the {@code ConnectionListener}).
 *  <p><code>Server</code> accepts any number of server event listeners 
 *  (interface <code>ServerListener</code>). Furthermore, it holds an unlimited
 *  list of connections (interface <code>Connection</code>), which may 
 *  originate from this server or from any other source. Connections issued 
 *  from this server are of type <code>ServerConnection</code> and automatically
 *  inserted into the connection list, other connections may be added by the 
 *  user. This connection list is a service to 
 *  the user and not required for the functioning of this class or of rendered
 *  connections. However, the list can only contain connections which are  
 *  in the CONNECTED or SHUTDOWN operation state. Connections which get 
 *  closed while element of the list are automatically removed.
 *  
 *  <p>Server event <code>connectionAvailable()</code> is issued in a thread
 *  owned by the {@code Server} instance and appears asynchronously. All other
 *  server events appear synchronous on the user's calling thread.
 *  
 *  @see ServerListener
 *  @see ServerConnection
 *  @see Connection
 *  
 */
public class Server implements IServer {
   
   protected static boolean debug = JennyNet.debug;
   
   private static final int SOCKET_BACKLOG = 10;
   private static Timer timer = new Timer();
   private static int nextTransActionNumber = 1;
   
   private static synchronized int nextTransactionNumber () {
      return nextTransActionNumber++;
   }
   
   /** Queue with new connections which were accepted by the socket but not yet
    * by the user. Only valid if SignalMethod == ACCEPT. */
   private BlockingQueue<ServerConnection> incoming;
   /** Set of all operative connections of this server (remove on close). */
   private Hashtable<UUID, ServerConnection> connectionMap = new Hashtable<UUID, ServerConnection>();
   /** The set of listeners to this server. */
   private Set<ServerListener> listeners = new ArraySet<ServerListener>();
   private ArraySet<ConnectionListener> conListeners;
   
   private OurClientListener ourClientListener = new OurClientListener();
   private ConnectionParameters parameters = JennyNet.getConnectionParameters();
   private ServerSocket serverSocket;
   private AcceptThread acceptThread;
   private ServerSignalMethod signalMethod = ServerSignalMethod.LISTENER;
   private int queueCapacity = JennyNet.DEFAULT_QUEUE_CAPACITY;
   private int acceptThreadPriority = Thread.MAX_PRIORITY -2;
   private boolean tempoPrimacy;
   private boolean closed;

   private String serverName;


   /** Creates an unbound server.
    * 
    * @throws IOException - IO error when opening the socket
    */
   public Server () throws IOException {
      init(null);
   }
   
   /** Creates a server with the given socket address. An address of null 
    * creates a socket with the wildcard IP-address on any free port.
    * 
    * @param address SocketAddress server address or null for
    *                any free port
    * @throws IOException
    */
   public Server (SocketAddress address) throws IOException {
      if (address == null) {
         address = new InetSocketAddress(0);
      }
      init(address);
   }
   
   /** 
    * Creates a server bound to the specified port on the wildcard IP-address.
    * A port of 0 creates a socket on any free port.
    *  
    * @param port int port number of the server address (0..65535);
    *        0 for any free port 
    * @throws IOException 
    */
   public Server (int port) throws IOException {
      SocketAddress addr = new InetSocketAddress(port);
      init(addr);
   }
   
   private void init (SocketAddress address) throws IOException {
      serverSocket = new ServerSocket();
      serverSocket.setReuseAddress(true);
      
      if (address != null) {
         bind(address);
      }

      // add server to global set
      JennyNet.addServerToGlobalSet(this);
   }
   
   @Override
   public void bind (int port) throws IOException {
      SocketAddress addr = new InetSocketAddress(port);
      serverSocket.bind(addr, SOCKET_BACKLOG);
   }

   @Override
   public void bind (SocketAddress address) throws IOException {
      serverSocket.bind(address, SOCKET_BACKLOG);
   }

   @Override
   public ServerConnection accept (int timeout) throws InterruptedException {
      if (!isAlive())
         throw new IllegalStateException("Server is not operational! (not started or dead)");
      if (signalMethod != ServerSignalMethod.ACCEPT)
         throw new IllegalStateException("Server is not in ACCEPT modus (SignalMethod)");

      // wait for a connection becoming available in INCOMING queue
      // while dropping any closed connection (may arise from timeout condition)
      ServerConnection connection;
      do {
         connection = timeout == 0 ? incoming.take() : 
            incoming.poll(timeout, TimeUnit.MILLISECONDS);
         
      } while (connection.isClosed());
      return connection;
   }

   @Override
   public void start() {
      if (closed) 
         throw new IllegalStateException("server is closed");
      if (!isBound()) 
         throw new IllegalStateException("server is not bound to an address");
      
      if (signalMethod == ServerSignalMethod.ACCEPT) {
         incoming = new LinkedBlockingQueue<ServerConnection>(queueCapacity);
      }
      acceptThread = new AcceptThread();
      acceptThread.start();
      Thread.yield();
   }

   @Override
   public void close() {
      // break if server is closed
      if (closed) return;
      
      // mark instance closed
      closed = true;
	  if (debug) {
		  prot(" closing server");
	  }
      

      // remove server from global set
      JennyNet.removeServerFromGlobalSet(this);
      
      // close the socket if server is not started 
      // (important step! this unbinds the port resource from the socket)
      if (acceptThread == null) {
         try {
            serverSocket.close();
         } catch (IOException e) {
            e.printStackTrace();
         }

      // shutdown daemon if server has been started
      // (this also closes the server-socket)   
      } else {
         // terminate the ACCEPT thread (daemon)
         acceptThread.terminate();
         if (incoming != null) {
            incoming.clear();
         }
   
         // issue CLOSED event to server listeners
         fireServerEvent(0, null);
      }
   }

   /** Prints the given protocol text to the console.
    * 
    * @param text String 
    */
   protected void prot (String text) {
	   InetSocketAddress adr = getSocketAddress(); 
	   System.out.println("(Server " + (adr == null ? "?" : adr.getPort()) + ") " + text);
   }
   
   @Override
   public ConnectionParameters getParameters() {
      return parameters;
   }

   @Override
   public void setParameters(ConnectionParameters par) {
	  Objects.requireNonNull(par);
      parameters = (ConnectionParameters)par.clone();
   }

   @Override
   public void finalize () {
      close();
   }
   
   @Override
   public boolean isAlive() {
      return !serverSocket.isClosed() & 
             (acceptThread != null && acceptThread.isAlive());
   }
   
   @Override
   public boolean isClosed () {
      return closed;
   }

   @Override
   public void closeAllConnections (String reason) {
	  if (debug) {
		  prot(" closing all connections");
	  }
      for (ServerConnection con : getConnections()) {
         try { 
            con.shutdownClose(reason); 
         } catch (Throwable e) {
            prot("(close-all-connections) *** FAILED CLOSING CONNECTION: " + con);
            e.printStackTrace();
         }
      }
   }

   @Override
   public void closeAndWait (long time) throws InterruptedException {
	   close();
	   closeAllConnections();
	   waitForAllClosed(time);
   }

   @Override
   public void waitForAllClosed (long time) throws InterruptedException {
	   if (debug) {
		   prot("-- waiting for all closed");
	   }
	   long mark = System.currentTimeMillis();
       for (Connection con : getConnections()) {
    	   long diff = System.currentTimeMillis() - mark;
    	   long arg = time == 0 ? 0 : Math.max(10, time - diff);
   		   con.waitForClosed(arg);
       }
   }

   @Override
   public int sendObjectToAll (Object object) {
      return sendObjectToAll(object, SendPriority.NORMAL);
   }
   
   @Override
   public int sendObjectToAll (Object object, SendPriority priority) {
      return sendObjectToAllExcept(null, object, priority);
   }

   @Override
   public int sendObjectToAllExcept (UUID id, Object object, SendPriority priority) {
	  Objects.requireNonNull(object, "object is null");
	  Objects.requireNonNull(priority, "priority is null");
      TransmissionErrorCollector collector = null;
      int transActionId = nextTransactionNumber();
      if (object instanceof byte[]) {
    	  object = new JennyNetByteBuffer((byte[])object);
      }
      
      for (Connection con : getConnections()) {
         try { 
            if (con.isConnected() && (id == null || !id.equals(con.getUUID()))) { 
               con.sendObject(object, priority); 
            }
         } catch (Throwable e) {
            if (collector == null) {
               collector = new TransmissionErrorCollector(transActionId);
            }
            collector.addError(con, e);
         }
      }
      
      // report any occurred error conditions
      if (collector != null) {
         collector.reportErrors();
      }
      return transActionId;
   }

   @Override
   public int sendFileToAll (File file, String pathInfo, SendPriority priority) {
	   return sendFileToAllExcept(null, file, pathInfo, priority);
   }

   @Override
   public int sendFileToAllExcept (UUID id, File file, String pathInfo, SendPriority priority) {
      TransmissionErrorCollector collector = null;
      int transActionId = nextTransactionNumber();
      
      for (Connection con : getConnections()) {
         try { 
            if (con.isConnected() && (id == null || !id.equals(con.getUUID()))) { 
               con.sendFile(file, pathInfo, priority, transActionId); 
            }
         } catch (Throwable e) {
            if (collector == null) {
               collector = new TransmissionErrorCollector(transActionId);
            }
            collector.addError(con, e);
         }
      }
      
      // report any occurred error conditions
      if (collector != null) {
         collector.reportErrors();
      }
      return transActionId;
   }

   @Override
   public int sendPingToAll() {
      TransmissionErrorCollector collector = null;
      int transActionId = nextTransactionNumber();
      
      for (Connection con : getConnections()) {
         try { 
            if (con.isConnected()) { 
               con.sendPing(); 
            }
         } catch (Throwable e) {
            if (collector == null) {
               collector = new TransmissionErrorCollector(transActionId);
            }
            collector.addError(con, e);
         }
      }
      // report any occurred error conditions
      if (collector != null) {
         collector.reportErrors();
      }
      return transActionId;
   }

   @Override
   public int sendTempoToAll (int baud) {
      TransmissionErrorCollector collector = null;
      int transActionId = nextTransactionNumber();
      for (Connection con : getConnections()) {
         try { 
            if (con.isConnected()) { 
               con.setTempo(baud); 
            }
         } catch (Throwable e) {
            if (collector == null) {
               collector = new TransmissionErrorCollector(transActionId);
            }
            collector.addError(con, e);
         }
      }
      // report any occurred error conditions
      if (collector != null) {
         collector.reportErrors();
      }
      return transActionId;
   }

   @Override
   public ServerConnection getConnection (UUID uuid) {
      return connectionMap.get(uuid);
   }

   @Override
   public ServerConnection getConnection (InetSocketAddress addr) {
	  synchronized (connectionMap) {
		  for (ServerConnection con : connectionMap.values()) {
	         if (con.getRemoteAddress().equals(addr)) return con;
		  }
	  }
	  return null;
   }
   
   @Override
   public ServerConnection[] getConnections() {
	  synchronized (connectionMap) {
	      ServerConnection[] a = new ServerConnection[connectionMap.size()];
	      int i = 0;
	      for (Enumeration<ServerConnection> en = connectionMap.elements();
	           en.hasMoreElements(); i++) {
	         a[i] = en.nextElement();
	      }
      return a;
	  }
   }

   @Override
   public List<ServerConnection> getConnectionList() {
	  synchronized (connectionMap) {
	      List<ServerConnection> list = new ArrayList<>(connectionMap.size());
	      for (Enumeration<ServerConnection> en = connectionMap.elements(); en.hasMoreElements();) {
	         list.add(en.nextElement());
	      }
	      return list;
	  }
   }

   @Override
   public void removeConnection (ServerConnection connection) {
      if (connection == null) return;
      
      // fire event if connection was present before removing
      if (connectionMap.remove(connection.getUUID()) != null) {
         connection.removeListener(ourClientListener);
         fireServerEvent(3, connection);
      }
   }
   
   @Override
   public boolean addConnection (ServerConnection connection) {
      if (connection == null || connection.isClosed())
         return false;

      // add our listener
      connection.addListener(ourClientListener);
      
      // add user's connection-listeners
      if (conListeners != null) {
   	     synchronized (conListeners) {
   	    	for (ConnectionListener li : conListeners) {
    		   connection.addListener(li);
   	    	}
   	     }
      }
      
      // put connection into map
      Connection oldEntry = connectionMap.put(connection.getUUID(), connection);
      
      // fire removal event if connection with UUID was present
      if (oldEntry != null) {
         fireServerEvent(3, oldEntry);
      }
      
      // fire insertion event
      fireServerEvent(2, connection);
      return true;
   }
   
   @Override
   public void addListener (ServerListener listener) {
      if (listener == null)
         throw new NullPointerException();
      
      synchronized (listeners) {
         listeners.add(listener);
      }
   }

   @Override
   public void addConnectionListener (ConnectionListener listener) {
	   Objects.requireNonNull(listener);
	   if (conListeners == null) {
		   conListeners = new ArraySet<>();
	   }
	   synchronized (conListeners) {
		   conListeners.add(listener);
	   }
   }
   
   @Override
   public void removeConnectionListener (ConnectionListener listener) {
	   if (conListeners != null && listener != null) {
		   synchronized (conListeners) {
			   conListeners.remove(listener);
		   }
	   }
   }
   
   @Override
   @SuppressWarnings("unchecked")
   public Set<ConnectionListener> getConnectionListeners () {
	   if (conListeners == null) {
		   return new ArraySet<>();
	   }
	   synchronized (conListeners) {
		   return (Set<ConnectionListener>) conListeners.clone();
	   }
   }
   
   @Override
   public void removeListener (ServerListener listener) {
      if (listener != null) {
         synchronized (listeners) {
            listeners.remove(listener);
         }
      }
   }

   @Override
   public void setSignalMethod (ServerSignalMethod method) {
	  Objects.requireNonNull(method);
      if (acceptThread != null)
         throw new IllegalStateException("server has been started");

      signalMethod = method;
   }

   
   @Override
   public ServerSignalMethod getSignalMethod() {
      return signalMethod;
   }

   @Override
   public void setAcceptQueueCapacity (int capacity) {
      if (acceptThread != null)
         throw new IllegalStateException("server has been started");
      
      queueCapacity = Math.max(capacity, 1);
   }

   @Override
   public int getAcceptQueueCapacity() {
      return queueCapacity;
   }

   @Override
   public void setTempoPrimacy (boolean prime) {
	  tempoPrimacy = prime;
      for (ServerConnection con : getConnections()) {
          try { 
             if (con.isConnected()) { 
                con.setTempoFixed(prime); 
             }
          } catch (Throwable e) {
        	  e.printStackTrace();
          }
       }
   }

   @Override
   public boolean getTempoPrimacy() {
	   return tempoPrimacy;
   }

   @Override
   public ServerListener[] getListeners () {
      ServerListener[] array;
      synchronized (listeners) {
          array = new ServerListener[listeners.size()];
          listeners.toArray(array);
      }
      return array;
   }
   
   /** Fire a server event to all listeners.
    * <br>(For event==1, a <code>ServerConnection</code> has to be
    * given as parameter.
    * Throwables from listeners are caught, neutralised and print-stack-traced.
    * 
    * @param event int event code: 0=server closed; 1=connection available;
    *              2=connection added to registry; 3=connection removed from registry;
    * @param con {@code Connection}
    */
   protected void fireServerEvent (int event, Connection con) {
      // we cycle all listeners on a copy of the listener list
      for (ServerListener i : getListeners()) {
         try {
            switch (event) {
            case 0: i.serverClosed(this); break;
            case 1: i.connectionAvailable(this, (ServerConnection)con); break;
            case 2: i.connectionAdded(this, con); break;
            case 3: i.connectionRemoved(this, con); break;
            }
         } catch (Throwable e) {
            e.printStackTrace();
         }
      }
   }

   protected void fireTransactionError (Connection connection, Throwable e, int transActionId) {
      // we cycle all listeners on a copy of the listener list
      for (ServerListener i : getListeners()) {
         try {
            i.errorOccurred(this, connection, transActionId, e);
         } catch (Throwable e1) {
            e1.printStackTrace();
         }
      }
   }

   @Override
   public InetSocketAddress getSocketAddress() {
      return (InetSocketAddress)serverSocket.getLocalSocketAddress();
   }

   protected ServerSocket getSocket () {
      return serverSocket;
   }

   /** Returns the {@code Timer} instance used by this class.
    *    
    * @return Timer
    */
   protected static Timer getTimer () {
      return timer;
   }
   
//   /** Sets the {@code Timer} instance for this class.
//    * 
//    * @param timer {@code Timer}
//    */
//   protected static void setTimer (Timer timer) {
//	   Objects.requireNonNull(timer);
//	   Server.timer = timer;
//   }
   
   @Override
   public boolean isBound () {
      return serverSocket.isBound();
   }

   @Override
   public void setThreadPriority (int threadPriority) {
      if (threadPriority < Thread.MIN_PRIORITY | threadPriority > Thread.MAX_PRIORITY)
         throw new IllegalArgumentException("illegal thread priority value");
      
      acceptThreadPriority = threadPriority;
      if (acceptThread != null) {
         acceptThread.setPriority(threadPriority);
         acceptThreadPriority = acceptThread.getPriority();
      }
   }

   @Override
   public int getThreadPriority () {
      return acceptThreadPriority;
   }

   @Override
   public void setName (String name) {
      serverName = name;
   }

   @Override
   public String getName () {
      return serverName;
   }

// ----------- INNER CLASSES  ------------   
   
   /** Daemon thread dealing with accepting new incoming connections
    *  for the server and dispatching them to user application.
    *  This thread will close the serverSocket when terminated.
    */
   private class AcceptThread extends Thread {
      private boolean terminate;
      
   AcceptThread () {
      super("JN-SERVER Connection Acception");
      setDaemon(true);
      setPriority(getThreadPriority());
      setThreadPriority(getPriority());
   }

   @Override
   public void run() {
      int errorCount = 0;
      
      if (debug) {
    	  System.out.println("++ JENNYNET " + JennyNet.VERSION + " SERVER THREAD started accepting at : " + 
    			  serverSocket.getLocalSocketAddress() );
      }
         
      while (!terminate) {
         try {
            // accept a connection and initialise
            Socket socket = serverSocket.accept();

            // verify network layer
            int time = getParameters().getConfirmTimeout() / 2;
            boolean verified = JennyNet.verifyNetworkLayer(0, socket, timer, time);
            if (!verified) continue;
            
            // once nature is verified, create the server connection (unstarted)
            ServerConnectionImpl connection = new ServerConnectionImpl(Server.this, socket);
            connection.setParameters(getParameters());
            connection.setTempoFixed(tempoPrimacy);
            connection.addListener(ourClientListener);
            
            // create and schedule timer task to shutdown socket in case
            // application doesn't decide on acceptance
            timer.schedule(new SocketShutdownTask(connection), 
                  getParameters().getConfirmTimeout());

            // signal connection event to user (various methods)
            if (signalMethod == ServerSignalMethod.LISTENER) {
               // issue connection available event (LISTENER method)
               fireServerEvent(1, connection);
            } else {
               // put connection into incoming connections queue (ACCEPT method)
               // immediately close connection if list is full
               if (!incoming.offer(connection)) {
                  connection.close();
               }
            }
         
         } catch (SocketException e) {
            if (!terminate) {
               e.printStackTrace();
            }
         } catch (Throwable e) {
            e.printStackTrace();
            if (++errorCount > 10) {
               System.err.println("+++ JENNY SERVER THREAD HALTED, Error-Overflow +++: " + 
                     serverSocket.getLocalSocketAddress() );
               terminate();
            }
         }
     } // while
      if (debug) {
    	  System.out.println("++ JENNY SERVER THREAD ----- TERMINATED! : " + 
    			  serverSocket.getLocalSocketAddress());
      }
   }

   /** Terminates the accept-thread (including a close on the
    * server-socket).
    */
   public void terminate() {
      terminate = true;
      try {
         serverSocket.close();
      } catch (IOException e) {
      }
      interrupt();
      yield();
   }
   } // AcceptThread

   /**
    * Listener to connections which were created by this server.
    * We are only interested in events which trigger insertion or removal
    * of these connections into our connection registry (connectionMap).
    * <p>Connections are inserted when they signal CONNECTED, they are
    * removed when they signal CLOSED.
    */
   private class OurClientListener extends DefaultConnectionListener {
	   
      @Override
      public void connected (Connection con) {
         // add a new connection to registry
         addConnection((ServerConnection)con);
      }

      @Override
      public void closed (Connection con, int info, String msg) {
         // remove a connection from registry
         removeConnection((ServerConnection)con);
      }
   } // ClientListener

   /**
    * TimerTask (scheduled at the server's Timer instance) for controlling
    * expiration of the socket open time for an incoming connection request.
    * The task closes the socket to the client if and only if the 
    * associated connection has not been started by the application.  
    */
   private class SocketShutdownTask extends TimerTask {
      private ServerConnection connection;
      
      public SocketShutdownTask (ServerConnection con) {
    	 Objects.requireNonNull(con);
         connection = con;
      }
      
      @Override
      public void run () {
         if (!connection.isConnected()) {
            try {
               connection.reject();
               if (debug) {
            	   prot("-- TIMEOUT ON PENDING ACCEPTION: closed server-connection: " + connection);
               }
               connection = null;
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
      }
      
   } // ConnectionShutdownTask

   /** This humble class collects error conditions coming from multiplex
    * actions on connections, mostly sending actions. It holds informations
    * on Connection and Throwable and offers feature to report them to event
    * listeners in one block. This allows us to pass over error conditions 
    * while servicing the complete list of connections in multiplexing and
    * issue error events only after all "sane" actions have been started off.
    */
   private class TransmissionErrorCollector {
      private List<TransmissionErrorEvent> list = new ArrayList<TransmissionErrorEvent>(); 
      private int transactionId;
      
      private class TransmissionErrorEvent {
         Connection connection;
         Throwable exception;
         
         TransmissionErrorEvent(Connection c, Throwable e) {
            if (c == null | e == null)
               throw new NullPointerException();
            connection = c;
            exception = e;
         }
      }

      /** Create a new transaction error collector.
       * 
       * @param transaction int identifier for multiplex transaction 
       */
      TransmissionErrorCollector (int transaction) {
         transactionId = transaction;
      }
      
      /** Adds an error condition for a specific connection.
       * 
       * @param connection Connection
       * @param e Throwable
       */
      void addError (Connection connection, Throwable e) {
         list.add(new TransmissionErrorEvent(connection, e));
      }
      
      /** Issues listed error events with the given transaction-ID reference.
       */
      void reportErrors () {
         for (TransmissionErrorEvent e : list) {
            fireTransactionError(e.connection, e.exception, transactionId);
         }
      }
   }

}
