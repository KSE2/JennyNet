/*
*  File: JennyNet.java
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
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.kse.jennynet.core.ConnectionImpl.OutputProcessor;
import org.kse.jennynet.exception.ConnectionRejectedException;
import org.kse.jennynet.exception.ConnectionTimeoutException;
import org.kse.jennynet.exception.JennyNetHandshakeException;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IClient;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.util.ArraySet;
import org.kse.jennynet.util.SchedulableTimerTask;
import org.kse.jennynet.util.Util;

/** Class for global structures, operations and settings of the <i>JennyNet</i>
 * network communication service. <i>JennyNet</i> is a transport layer resting
 * on a TCP/IP channel which enables comfortable transmissions of objects
 * and files from the file-system in a a client-server or peer-to-peer 
 * host architecture. The services of this layer go beyond mere
 * transport of objects to time-based control and analysis of the behaviour
 * of the channel. The layer has some global properties which are controlled
 * in this class. Otherwise, its potential is expressed in the methods of
 * the {@code Connection} interface. Connections are the atomic units with 
 * which an application communicates to use this layer's services. 
 * 
 * <p><b>Connection-Parameter Default</b>
 * <br>This class contains the default and boundary values for the elements of
 * the {@code ConnectionParameter} class. A global instance of
 * connection-parameters is available, either as copy or as reference for
 * modifications. These parameters function as the default option settings for
 * each new {@code Client} connection instantiated in this layer. Furthermore,
 * new instances of {@code Server} adopt these parameters as their generic
 * default.
 * 
 * <p><b>Serialisations</b>
 * <br>Currently two methods for serialising objects are available: the default
 * Java-serialisation (0) and the Kryo-serialisation (1). The default method
 * can be set in this interface and will be stored in the global connection
 * parameters. Default serialisation instances for all methods are available 
 * and can be used to implement class registrations on a generic level.
 * Each connection can assume its own set of registrations, but the global
 * defaults are copied when a connection is created (client and server).
 * Availability of certain methods can rest on the binding of external classes
 * (so e.g. for the Kryo serialisation). If the classes are not available, the
 * corresponding default serialisations assume the value null. 
 * 
 * <p><b>Global Object Lists</b>
 * <br>Global lists of clients (connections) and servers are available which 
 * are administered automatically. Moreover a list of all active connections 
 * in this layer (client and server combined) can be obtained.
 * 
 * <p><b>Global Thread Settings</b>
 * <br>JennyNet uses global (static) threads to send objects off network
 * sockets and to deliver received objects to the user. The run-priorities of
 * these threads can be set and read here.
 * 
 * <p><b>Output Blockage Control</b>
 * <br>An automated control of output-thread blockage can be activated (by
 * default inactive). The object output-thread can become blocked in the 
 * context of user-owned algorithms (connection-listeners). If blockage-control
 * detects such a situation, the global output-thread is replaced by an 
 * individual thread for the affected connection. This sets the global thread
 * free for other connections to operate again.
 *  
 * <p><b>Layer Shutdown</b>
 * <br>With a central command ({@code shutdownAndWait()}) all registered
 * connections and servers can be shut down and closed.
 * 
 * <p><b>Application Setup</b>
 * <br>To start a <i>JennyNet</i> application this global class should be 
 * addressed first. All methods starting with "set" should be checked whether
 * they require to be called. After this the needed {@code Serialization}
 * instances can be addressed to register the required classes for 
 * transmitting objects in a {@code Connection}. Second and equally important
 * is the setup of the default connection-parameters.
 */

public class JennyNet {

   public static boolean debug = false;
   public static final int KILO = 1024;
   public static final int MEGA = 1024 * 1024;
   public static final int GIGA = 1024 * MEGA;
   public static final int SECOND = 1000;
   public static final int MINUTE = 60 * SECOND;
   public static final int HOUR = 60 * MINUTE;
   public static final int DAY = 24 * HOUR;

   /** Enum for an application type: 'global' or 'individual'. */
   public static enum ThreadUsage {GLOBAL, INDIVIDUAL}
   
   // markers for version 1.0.0
   public static final String VERSION = "1.0.0";
   public static final int PARCEL_MARKER = 0xe40dd5a8;
   static final byte[] LAYER_HANDSHAKE_SERVER = Util.hexToBytes("3E18D1CCE6A81066714874963526AD21");
   static final byte[] LAYER_HANDSHAKE_CLIENT = Util.hexToBytes("3E18D1CCE6A810667148749635F9310A");
   static final byte[] CONNECTION_CONFIRM = Util.hexToBytes("AFAA5BE270CEFBCD69BAE19D10412F09");
   
   /** Buffer size for file IO streams. */
   public static final int STREAM_BUFFER_SIZE = 64 * KILO;
   public static final int DEFAULT_QUEUE_CAPACITY = 200;
   public static final int DEFAULT_PARCEL_QUEUE_CAPACITY = 600;

   /** Maximum number of serialisation devices */
   public static final int MAX_SERIAL_DEVICE = 3;
   public static final int DEFAULT_SERIALISATION_METHOD = 0; // JAVA
   /** Maximum buffer size for object serialisation. */
   public static final int DEFAULT_MAX_SERIALISE_SIZE = 100 * MEGA;
   public static final int MIN_MAX_SERIALISE_SIZE = 10 * KILO; 
   public static final int MAX_QUEUE_CAPACITY = 10000; 
   public static final int MAX_CON_SENDLOAD = 10 * MEGA; 
   public static final int MIN_MAX_CON_SENDLOAD = 16 * KILO; 
   public static final int MAX_TRANSMISSION_PARCEL_SIZE = 256 * KILO; 
   public static final int MIN_TRANSMISSION_PARCEL_SIZE = 1024; 
   public static final int MIN_ALIVE_PERIOD = 5000; 
   public static final int MIN_CONFIRM_TIMEOUT = 1000; 
   public static final int MIN_IDLE_CHECK_PERIOD = 5000; 
   public static final int MAX_ALIVE_PERIOD = 10 * MINUTE; 
   public static final int MIN_DELIVER_TOLERANCE = 1000; 
   public static final int DEFAULT_BASE_PRIORITY = Thread.NORM_PRIORITY;
   public static final int DEFAULT_TRANSMIT_PRIORITY = Thread.MAX_PRIORITY - 2;
   public static final int DEFAULT_TRANSMISSION_PARCEL_SIZE = 64 * KILO;
   public static final int DEFAULT_ALIVE_PERIOD = 0;
   public static final int DEFAULT_DELIVER_TOLERANCE = 10000      ; 
   public static final int DEFAULT_CONFIRM_TIMEOUT = 30000; 
   public static final int DEFAULT_IDLE_CHECK_PERIOD = 60000; 
   public static final int DEFAULT_TRANSMISSION_TEMPO = -1;
   public static final ThreadUsage DEFAULT_THREAD_USAGE = ThreadUsage.GLOBAL;

   // global structures
   /** Occupied file registry to protect from file output overrun events. */
   private static Timer timer; 

   private static Vector<IClient> globalClientList;
   private static Vector<IServer> globalServerList;
   private static Serialization[] globalSerials = new Serialization[MAX_SERIAL_DEVICE]; 
   private static Charset codingCharset;
   private static File tempDir;

   /** The layer's default connection parameters. These values will be
    * active when a new {@code Connection} is instantiated. */
   private static ConnectionParameters parameters;
   private static int outputThreadPriority;
   private static int sendThreadPriority;
   
   static {
	   reset();
   }
   
   /** Initialises all internal structures so that the static instance
    * assumes a state as freshly instantiated.
    */
   public static void reset () {
      // create the coding charset (does not affect object serialisation methods)
      try { codingCharset = Charset.forName("UTF-8"); 
      } catch (UnsupportedCharsetException e) {
         codingCharset = Charset.defaultCharset();
      }
      
      // initial structures
      globalClientList = new Vector<>(16, 32);
      globalServerList = new Vector<>(16, 32);
      if (timer != null) {
    	  timer.cancel();
    	  timer = null;
      }
      
      // initial values
      outputThreadPriority = Thread.NORM_PRIORITY + 1;
      sendThreadPriority = Thread.MAX_PRIORITY - 2;
      tempDir = new File(System.getProperty("java.io.tmpdir"));
      
      // default initialised objects
      parameters = new ConnectionParametersImpl();

   	  globalSerials[0] = createSerialisation(0);
   	  globalSerials[2] = null;
      try {
    	  globalSerials[1] = createSerialisation(1);
      } catch (SerialisationUnavailableException e) {
    	  parameters.setSerialisationMethod(0);
      }
   }
   
   /** If this is set <b>true</b>, a periodic control for event delivery
    * blocking in user routines is performed for all registered connections.
    * The default setting is OFF (false).
    * <p>NOTE: If this is set <b>true</b>, in consequence of a blocking
    * condition the affected connection receives its own delivery thread
    * (the blocking continues)
    * <p>NOTE: This method should ideally be called after any modifications to
    * the global set of connection-parameters have been performed. It can be 
    * called multiple times.
    *  
    * @param v boolean true = control is ON, false = control is OFF
    */
   public static void setConnectionBlockingControl (boolean v) {
	   if (v & timer == null) {
		  timer = new Timer(true);
		  
		  // start  the connection control task
		  SchedulableTimerTask task = new ControlBlockingOutputTask();
		  task.schedule(timer);
	   } else if (!v & timer != null) {
		   timer.cancel();
		   timer = null;
	   }
   }

   /** Whether periodic control for blocking conditions in connections is
    * active.
    * 
    * @return boolean true = control is ON, false = control is OFF
    */
   public static boolean isConBlockingControlled () {
	   return timer != null;
   }
   
   /** Shuts down the <i>JennyNet</i> network layer with all communicating 
    * elements and waits for the given amount of time until connections 
    * terminate naturally.
    * 
    * @param time long maximum wait time; 0 for unlimited
    * @throws InterruptedException 
    */
   public static void shutdownAndWait (long time) throws InterruptedException {
	   Thread.interrupted(); 

	   // close all clients
	   List<IClient> clients = getGlobalClientSet();
	   for (IClient cl : clients) {
		   cl.close();
	   }

	   // wait for shutdown of clients
	   for (IClient cl : clients) {
		   cl.waitForClosed(time);
	   }
	   
	   List<IServer> servers = getGlobalServerSet();

	   // shut down servers by waiting for termination on each
	   for (IServer sv : servers) {
		   sv.closeAndWait(time);
	   }
	   
	   // timer shutdown
	   setConnectionBlockingControl(false);
   }
   
   /** Returns the thread priority of the layer's socket sending service threads.
    * Defaults to Thread.MAX_PRIORITY - 2.
    *  
    *  @return int thread priority
    */
   public static int getSendThreadPriority() {return sendThreadPriority;}

   /** Sets the thread priority of the layer's socket sending service threads.
    * Defaults to Thread.MAX_PRIORITY - 2.
    *  
    * @param p int thread priority
    */
   public static void setSendThreadPriority (int p) {
      sendThreadPriority = Math.min(Math.max(p, Thread.MIN_PRIORITY), Thread.MAX_PRIORITY);

      if (ConnectionImpl.coreSendClient != null) {
    	  setThreadPriority(ConnectionImpl.coreSendClient.send, sendThreadPriority);
      }
      if (ConnectionImpl.coreSendServer != null) {
    	  setThreadPriority(ConnectionImpl.coreSendServer.send, sendThreadPriority);
      }
   }
   
   /** Returns the thread priority of the layer's output service threads.
    * This includes threads which deliver received objects and events to the 
    * application. Defaults to Thread.NORM_PRIORITY + 1.
    *  
    *  @return int thread priority
    */
   public static int getOutputThreadPriority() {return outputThreadPriority;}

   /** Sets the thread priority of the layer's output service threads.
    * This includes threads which deliver received objects and events to the 
    * application. Defaults to Thread.NORM_PRIORITY + 1.
    *  
    * @param p int thread priority
    */
   public static void setOutputThreadPriority (int p) {
      outputThreadPriority = Math.min(Math.max(p, Thread.MIN_PRIORITY), Thread.MAX_PRIORITY);
      
      if (ConnectionImpl.staticOutputClient != null) {
    	  setThreadPriority(ConnectionImpl.staticOutputClient.output, outputThreadPriority);
      }
      if (ConnectionImpl.staticOutputServer != null) {
    	  setThreadPriority(ConnectionImpl.staticOutputServer.output, outputThreadPriority);
      }
   }
   
   private static void setThreadPriority (Thread thread, int priority) {
	   if (thread != null) {
		   thread.setPriority(priority);
	   }
   }
   
   /** Returns the global {@code Serialization} object for the given 
    * serialisation method or throws an exception if this method is not 
    * supported. The 
    * returned object will be copied into new {@code Connection} instances 
    * (client or server) which have opted for this method in their parameters. 
    * <p><small>Registrations of serialisable classes can be performed on the
    * returned global object and become available in new connections.
 	* The registrations here will not become active for already existing 
    * connections.</small> 
    *
    * @param method int serialisation method-code (0 = Java, 1 = Kryo, 
    *                   2 = custom)
    * @return <code>Serialization</code>
    * @throws IllegalArgumentException if method is invalid
    * @throws SerialisationUnavailableException if the requested method is not
    *         implemented
    */
   public static Serialization getDefaultSerialisation (int method) {
	  if (method < 0 | method >= MAX_SERIAL_DEVICE)
		  throw new IllegalArgumentException("illegal method value: " + method);
	  
	  Serialization ser = globalSerials[method];
	  if (ser == null) {
		  throw new SerialisationUnavailableException("not implemmented method: " + method);
	  }
      return ser;
   }

   /** Assigns the given serialisation to method 2. 
    * <p>Method 2 is the slot for custom serialisations. By default this slot
    * is void and its use leads to object sending abortions or exceptions 
    * thrown. 
    * 
    * @param ser {@code Serialization} or null to discard
    */
   public static void setCustomSerialisation (Serialization ser) {
	   globalSerials[2] = ser;
	   if (ser != null) {
		   initDefaultSerialisation(ser);
	   }
   }
   
   /** Sets the charset used for layer internal use.
    * 
    * @return Charset text coding charset
    */
   public static Charset getCodingCharset() {return codingCharset;}

   /** Sets the charset used for layer internal text encoding. The value
    * has to be set equal in sending and receiving stations.
    * (Does not affect object serialisation methods!)
    * 
    * @param charset Charset
    */
   public static void setCodingCharset (Charset charset) {
	  Objects.requireNonNull(charset);
      codingCharset = charset;
   }
   
   /** Returns the code number of the default serialisation method 
    * applied by this layer.
    * 
    * @return int method code
    */
   public static int getDefaultSerialisationMethod () {
      return parameters == null ? DEFAULT_SERIALISATION_METHOD
            : parameters.getSerialisationMethod();
   }

   /** Sets the code number of the default serialisation method of this layer.
    * Typically this is performed before any network-active objects are
    * instantiated. New instances of {@code Client} or {@code Server} will 
    * assume the given method as their initial setting. The setting on 
    * individual connections can be modified in their parameters. Already 
    * existing connections will not be affected by a global default method 
    * change. Also it must be noted that new server-connections take
    * reference to their {@code Server} for initial parameters.
    * 
    * @param method int method code (0 = Java, 1 = Kryo, 2 = custom)
    * @return boolean true = method has changed, false = method is unchanged
    * @throws IllegalArgumentException if the given method is undefined
    * @throws SerialisationUnavailableException if the method is not supported
    */
   public static boolean setDefaultSerialisationMethod (int method) {
	   Objects.requireNonNull(parameters, "global parameter set is null");
	   getDefaultSerialisation(method);
	   return parameters.setSerialisationMethod(method);
   }
   
   /** Creates a new {@code Serialization} instance of the given type (method)
    * and initialises it with the <i>JennyNet</i> standard class registrations.
    * The code legend is: 0 = Java, 1 = Kryo.
    * Note that any additional registrations performed by the application on
    * other serialisation instances are not available on the new instance.
    * <p>Due to the class binding situation of the application the chosen
    * method may not be supported by JennyNet. In this case the
    * {@code SerialisationUnavailableException} is thrown.
    *  
    * @param method int serialisation type (0..1)
    * @return {@code Serialization}
    * @throws IllegalArgumentException if method is invalid
    * @throws SerialisationUnavailableException if method is unsupported
    */
   public static Serialization createSerialisation (int method) 
		   	throws SerialisationUnavailableException {
	   if (method < 0 | method > 1)
		   throw new IllegalArgumentException("illegal method value: " + method);
	   Serialization ser = null;
//	   System.out.println("CREATING SERIALISATION: " + method);
	   try {
		   switch (method) {
		   case 0:	ser = new JavaSerialisation(); break;
		   case 1:	ser = new KryoSerialisation(); break;
		   }
		   initDefaultSerialisation(ser);
	   } catch (NoClassDefFoundError e) {
//		   System.err.println("*** SERIALISATION NOT FOUND: " + method);
		   throw new SerialisationUnavailableException(e);
	   }
	   return ser;
   }
   
   /** Performs some standard class registrations on the given serialization
    * object.
    * <p>Registered are byte[], String, Character, Boolean, Byte, Short,
    * Integer, Long, Float, Double, JennyNetByteBuffer.
    * 
    * @param ser {@code Serialization}
    */
   public static void initDefaultSerialisation (Serialization ser) {
	  try {
		  ser.registerClass(byte[].class);
	      ser.registerClass(String.class);      
	      ser.registerClass(Character.class);      
	      ser.registerClass(Boolean.class);      
	      ser.registerClass(Byte.class);      
	      ser.registerClass(Short.class);      
	      ser.registerClass(Integer.class);      
	      ser.registerClass(Long.class);      
	      ser.registerClass(Float.class);      
	      ser.registerClass(Double.class);      
	      ser.registerClass(JennyNetByteBuffer.class);      
	  } catch (NotSerializableException e) {
		  System.err.println("*** JENNY-NET-WARNING! Unable to register default class in Serialization " + ser); 
		  System.err.println("    " + e.getMessage());
	  }
   }

   /** Returns the layer's TEMP directory setting.
    * 
    * @return File directory
    */
   public static File getTempDirectory() {return tempDir;}

   /** Sets the directory for the layer's default TEMP directory.
    * 
    * @param dir File TEMP directory
    * @throws IllegalArgumentException if parameter is not a directory
    * @throws IOException if the path cannot get verified (canonical name)
    */
   public static void setTempDirectory (File dir) throws IOException {
	   Objects.requireNonNull(dir);
	   if (!dir.isDirectory())
		   throw new IllegalArgumentException("not a directory: " + dir.getAbsolutePath());
	   
	   tempDir = dir.getCanonicalFile();
   }

   /** Verifies the JennyNet network layer on the remote end of the connection.
    * Blocks for a maximum of ? milliseconds to read data from remote.
    * The socket must be connected. If false is returned or an IO exception is
    * thrown, the socket gets closed.
    *
    * @param agent int controlling agent: 0 = server, 1 = client
    * @param socket Socket connected socket
    * @param timer Timer the timer thread to use for the timer task
    * @param time int milliseconds to wait for a remote signal

    * @return boolean true == JennyNet confirmed, false == invalid endpoint or timeout
    * @throws IllegalArgumentException if socket is unconnected
    * @throws IOException 
    */
   @SuppressWarnings("hiding")
   static boolean verifyNetworkLayer (int agent, final Socket socket, Timer timer, int time) 
         throws IOException {
      // check for conditions
      if (!socket.isConnected())
         throw new IllegalArgumentException("socket is unconnected!");
      
      // write our handshake to remote
      byte[] sendHandshake = agent == 0 ? JennyNet.LAYER_HANDSHAKE_SERVER : 
                                          JennyNet.LAYER_HANDSHAKE_CLIENT;
      byte[] receiveHandshake = agent == 0 ? JennyNet.LAYER_HANDSHAKE_CLIENT : 
                                          JennyNet.LAYER_HANDSHAKE_SERVER;
      socket.getOutputStream().write(sendHandshake);
      
      try {
         // file in for the socket shutdown timer
         // which covers the case that remote doesn't send enough bytes
         TimerTask task = new TimerTask() {

            @Override
            public void run() {
               try {
                  socket.close();
                  if (debug) {
                	  System.out.println("----- TIMEOUT on socket listening (SERVER : VERIFY NETWORK LAYER)");
                	  System.out.println("      REMOTE = " + socket.getRemoteSocketAddress());
                  }
               } catch (IOException e) {
                  e.printStackTrace();
               }
            }
         };
         timer.schedule(task, time);
         
         // try read remote handshake
         byte[] handshake = new byte[16];
         new DataInputStream(socket.getInputStream()).readFully(handshake);
         task.cancel();

         // test and verify remote handshake
         return Util.equalArrays(handshake, receiveHandshake);
         
      } catch (SocketException e) {
         // this is a typical timeout response
         e.printStackTrace();
         socket.close();
         return false;
      } catch (EOFException e) {
         // this is a remote closure response
         e.printStackTrace();
         socket.close();
         return false;
      } catch (IOException e) {
         socket.close();
         throw e;
      }
   }

   /** Returns a clone of the global default set of {@code Connection} 
    * parameters. This set may have been specialised by modifications through
    * the application. 
    * <p>This set becomes directly active on {@code Client} connections, 
    * furthermore it becomes the default set for new {@code Server} instances.
    * {@code ServerConnection} instances take reference to their server for
    * the default set of parameters.
    * 
    * @return <code>ConnectionParameters</code>
    */
   public static ConnectionParameters getConnectionParameters () {
      return (ConnectionParameters)parameters.clone();
   }

   /** Returns a {@code ConnectionParameter} parameter set with standard
    * default values. The values of this set are <i>JennyNet</i> constants 
    * and thus not modified by application action. The returned set is a new
    * instance and free to use by the application.
    * 
    * @return <code>ConnectionParameters</code>
    */
   public static ConnectionParameters getStandardParameters () {
	   return new ConnectionParametersImpl();
   }

   /** Returns the global default {@code ConnectionParameter} instance in 
    * direct reference. 
    * <p>The returned set of parameters can be used to modify the values into
    * a configuration which automatically will become active in new 
    * {@code Client} connections and {@code Server} instances as default 
    * setting. 
    * 
    * @return <code>ConnectionParameters</code>
    */
   public static ConnectionParameters getDefaultParameters () {return parameters;}

   /** Returns a list of currently active servers in the <i>JennyNet</i> layer.
    * This copy-list can be modified without consequences.
    * 
    * @return {@code List<IServer>}
    */
   public static List<IServer> getGlobalServerSet () {
	   return new ArrayList<IServer>(globalServerList);
   }
   
   /** Returns the number of currently active servers in the JennyNet layer.
    * 
    * @return int number of active servers
    */
   public static int getNrOfServers () {
	   return globalServerList.size();
   }
   
   /** Adds a server to the layer's global server set. Double entry of 
    * the same object is silently prevented.
    *  
    * @param server <code>IServer</code>
    */
   protected static void addServerToGlobalSet (IServer server) {
	   if (!globalServerList.contains(server)) {
		   globalServerList.add(server);
	   }
   }

   /** Removes a server from the global server set.
    * 
    * @param server <code>IServer</code>
    */
   protected static void removeServerFromGlobalSet (IServer server) {
	   globalServerList.remove(server);
   }
   
   /** Returns all connections which are known to JennyNet globally.
    * This includes all clients and all connections from servers.
    * The set can be modified without consequences.
    * 
    * @return {@code Set<Connection>}
    */
   public static Set<Connection> getAllConnections () {
	   ArraySet<Connection> set = new ArraySet<Connection>();
	   // collect all Client instances
	   for (IClient cl : getGlobalClientSet()) {
		   set.add(cl);
	   }
	   
	   // collect all connections from all servers
	   for (IServer sv : getGlobalServerSet()) {
		   set.addAll(sv.getConnectionList());
	   }
	   return set;
   }
   
   /** Returns a list of currently active clients in the <i>JennyNet</i> layer.
    * This copy-list can be modified without consequences.
    * 
    * @return <code>List&lt;IClient&gt;</code>
    */
   public static List<IClient> getGlobalClientSet () {
	   return new ArrayList<IClient>(globalClientList);
   }
   
   /** Returns the number of currently active clients in the JennyNet layer.
    * 
    * @return int number of active clients
    */
   public static int getNrOfClients () {
	   return globalClientList.size();
   }
   
   /** Adds a client to the layer's global client set. Double entry of 
    * the same object is silently prevented.
    *  
    * @param client <code>IClient</code>
    */
   protected static void addClientToGlobalSet (IClient client) {
	   if (!globalClientList.contains(client)) {
		   globalClientList.add(client);
	   }
   }

   /** Removes a client from the global client set.
    * 
    * @param client <code>IClient</code>
    * @return boolean true = client was contained, false = set unchanged
    */
   protected static boolean removeClientFromGlobalSet (IClient client) {
	   return globalClientList.remove(client);
   }
   
   /** Waits the given time for a CONNECTION_VERIFIED signal received from the
    * remote endpoint. This should only take place after <i>verifyNetworkLayer()</i> has been
    * passed positively. Method throws exceptions to indicate various failure conditions.
    * The socket must be connected. If a timeout, rejection or IO exception is thrown, the socket 
    * gets closed.
    * 
    * @param socket Socket connected socket
    * @param timer Timer the timer thread to use for the timer task
    * @param time int milliseconds to wait for a remote signal
    * @return int ALIVE period as requested by remote
    * 
    * @throws JennyNetHandshakeException if remote sent a false signal (out of protocol)
    * @throws ConnectionRejectedException if remote JennyNet layer refused the connection
    * @throws ConnectionTimeoutException if waiting for connection signal expired or time 
    *              parameter is below 1
    * @throws IOException
    * @throws IllegalArgumentException if socket is unconnected
    */
   static int waitForConnection (final Socket socket, Timer timer, int time) throws IOException {
      // check for conditions
      if (!socket.isConnected())
         throw new IllegalArgumentException("socket is unconnected!");
      if (time < 1) 
         throw new ConnectionTimeoutException("illegal time value");
      
      // file in for the socket shutdown timer
      // which covers the case that remote doesn't send enough bytes
      class WaitTimerTask extends TimerTask {
         boolean expired = false;

         @Override
         public void run() {
            try {
               expired = true;
               socket.close();
               if (debug) {
            	   System.out.println("----- TIMEOUT on socket listening (SERVER : VERIFY CONNECTION STATUS)");
            	   System.out.println("      REMOTE = " + socket.getRemoteSocketAddress());
               }
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
         
         public boolean hasExpired () {
            return expired;
         }
      };
      WaitTimerTask task = new WaitTimerTask();
      timer.schedule(task, time);
         
      try {
         // try read remote connection confirm signal
         byte[] remoteSignal = new byte[20];
         new DataInputStream(socket.getInputStream()).readFully(remoteSignal);
         task.cancel();
   
         // test and verify remote handshake
         byte[] verifySignal = Arrays.copyOf(remoteSignal, 16);
         if ( !Util.equalArrays(verifySignal, JennyNet.CONNECTION_CONFIRM) )
            throw new JennyNetHandshakeException("false signal on WAIT_FOR_CONNECTION_CONFIRM");
         
         // extract ALIVE-PERIOD request from remote
         int alivePeriod = Util.readInt(remoteSignal, 16);
         return alivePeriod;
         
      } catch (SocketException e) {
         socket.close();

         // this is the timeout response (triggered by the timer task)
         if (task.hasExpired()) {
            throw new ConnectionTimeoutException("waiting expired for " + time + " milliseconds") ;
         }
         // this is some other SocketException   
		 throw e;
		 
      } catch (EOFException e) {
         // this is a remote closure response
         socket.close();
         throw new ConnectionRejectedException();
         
      } catch (IOException e) {
         // this is a remote closure response
         socket.close();
         throw e;
      }
   }

   /** Sends a CONNECTION_CONFIRM message to remote station including a
    * request for ALIVE signals, if any. This is part of the initial handshake
    * protocol during establishing a connection between client and server.
    *  
    * @param connection <code>Connection</code> sending connection
    * @throws IOException 
    */
   static void sendConnectionConfirm (Connection connection) throws IOException {
      byte[] signal = Arrays.copyOf(JennyNet.CONNECTION_CONFIRM, 20);
      Util.writeInt(signal, 16, connection.getParameters().getAlivePeriod());
      ((ConnectionImpl)connection).getSocket().getOutputStream().write(signal);
   }


//  ----------  INNER CLASSES  ---------------	
	
   private static class ControlBlockingOutputTask extends SchedulableTimerTask {
	   
	   /** Creates a new task.
	    */
	   public ControlBlockingOutputTask () {
		   super(5000, frequency(), "Control-Blocking-Output-Task");
	   }

	   @Override
	   public void run() {
		    if (debug) {
			   System.out.println("$$ running Control-Blocking-Output-Processor");
	        }
		   	for (int i = 0; i < 2; i++) {
		   		
			   OutputProcessor output = i == 0 ? ConnectionImpl.staticOutputClient : ConnectionImpl.staticOutputServer;
			   ConnectionImpl con = output.connection;
			   boolean blocking = output.isBlocking();
			   if (debug) {
				   boolean conOk = con != null;
				   System.out.println("$$ (control-blocking-output) con = " + conOk + ", output alive = " + output.isAlive() 
				   		    + ", blocking = " + blocking);
		       }
			   if (blocking && con != null && output.isAlive()) {
				   con.getParameters().setDeliveryThreadUsage(ThreadUsage.INDIVIDUAL);
				   int count = output.drainConEventsTo(con, con.getOutputProcessor());
				   output.interrupt();
				   if (debug) {
				      con.prot("--$$ (control-blocking): replaced global output to individual, " 
				    		  + count + " events moved, rem "+ con.getRemoteAddress());
				      con.prot("--$$ (control-blocking) output time-mark is " + output.deliverMarkTm);
				   }
			   }
		   }
	   } // run
	   
	   static int frequency () {
		   int tolerance = getDefaultParameters().getDeliverTolerance();
		   int res = Math.max(1000, tolerance / 4);
		   return res;
	   }
   }

}
