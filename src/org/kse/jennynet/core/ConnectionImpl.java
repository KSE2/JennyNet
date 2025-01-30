
/*  File: ConnectionImpl.java
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.kse.jennynet.core.JennyNet.ThreadUsage;
import org.kse.jennynet.exception.ClosedConnectionException;
import org.kse.jennynet.exception.ConnectionTimeoutException;
import org.kse.jennynet.exception.FileInTransmissionException;
import org.kse.jennynet.exception.ListOverflowException;
import org.kse.jennynet.exception.RemoteTransferBreakException;
import org.kse.jennynet.exception.SerialisationException;
import org.kse.jennynet.exception.SerialisationOversizedException;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.exception.UnconnectedException;
import org.kse.jennynet.exception.UnregisteredObjectException;
import org.kse.jennynet.exception.UserBreakException;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.intfa.TransmissionEventType;
import org.kse.jennynet.util.ArraySet;
import org.kse.jennynet.util.IO_Manager;
import org.kse.jennynet.util.MutableBoolean;
import org.kse.jennynet.util.SchedulableTimerTask;
import org.kse.jennynet.util.Util;

/** Implementation of the JennyNet <code>Connection</code> interface, building 
 * the common part of both <code>Client</code> and <code>ServerConnection</code>.
 */
class ConnectionImpl implements Connection {
   
   protected static final boolean debug = JennyNet.debug;
   
   /** Internal static Timer for time-control tasks. */
   private static Timer timer = new Timer();

   /** singleton static parcel sending queue and processor */
   static CoreSend coreSendClient, coreSendServer;

   /** singleton static user-object queue and delivery processor */
   static OutputProcessor staticOutputClient, staticOutputServer;
   

   // parametric
   private UUID uuid = UUID.randomUUID();
   private byte[] shortId = Util.makeShortId(uuid);
   private String connectionName;
   private OurParameters parameters;

   // objects
   private Set<ConnectionListener> listeners = new ArraySet<>();
   private Serialization[][] serials;
   private boolean[] serialAvail;
   private Serialization sendSerialisation;
   private Serialization receiveSerialisation;
   private Properties properties;
   private Socket socket;
   private OutputStream socketOutput;
   private InputStream socketInput;
   private Map<Long, Long> pingSentMap; // maps ping-id -> time sent
   private Map<Object, SendFileOrder> fileSenderMap; 
   private Map<Long, FileAgglomeration> fileReceptorMap; 
   private Map<Long, ObjectAgglomeration> objectReceptorMap;

   // processors
   /** user object serialisation and sending processor */
   private InputProcessor inputProcessor;
   private SendFileProcessor sendFileProcessor;
   /** basic network reception, signal digestion and object de-serialisation processor */
   private ReceiveProcessor receiveProcessor;
   
   // data queues sending
   private PriorityBlockingQueue<ObjectSendSeparation> inputQueue;
   private PriorityBlockingQueue<SendFileOrder> fileSendQueue;
   
   // queues and processors receiving
   /** generalised event delivery queue and processor */
   private OutputProcessor outputProcessor;
   
   // locks
   private Object waitForDisconnectLock = new Object();
   private Object waitForClosedLock = new Object();
   private Object sendAccessSync = new Object();
   /** SEND-LOCK and boolean value object; state TRUE means the lock is closed. */
   private MutableBoolean sendLock = new MutableBoolean(false);
   
   // operational
   private ConnectionState operationState = ConnectionState.UNCONNECTED;
   private LayerCategory layerCat;
   AliveSignalTimerTask aliveSignalTask;
   AliveReceptionControlTask aliveTimeoutTask;
   private ControlEndOfShutdownTask ctrlShutdownTask;
   private CheckIdleTimerTask checkIdleTask;
   private ErrorObject localCloseError;

   private AtomicLong objectSerialCounter = new AtomicLong();
   private AtomicLong currentSendLoad = new AtomicLong();
   private AtomicLong outputObjectCounter = new AtomicLong();
   private AtomicLong exchangedDataVolume = new AtomicLong();
   private AtomicLong transmittedVolume = new AtomicLong();
   private long receiveObjectCounter;
   private long pingSerialCounter;
   private long sendLoadLimit;
   private long lastSendTime;
   private long lastSendScheduleTime;
   private long lastReceiveTime;
   private long lastPingSendTime;
   private long sendObjectCounter;
   private int lastPingValue;
   private int transmitSpeed = -1;
   private int sendFileCounter, receiveFileCounter;
   private int outgoingTestError, incomingTestError;
   
   protected boolean fixedTransmissionSpeed;
   private boolean sendingOff;
   private boolean isCheckIdleState;
   private boolean suppressConfirm;	// testing tool
   private boolean objectsAllSent, filesAllSent, remoteAllSent;
   private boolean allSentSignalSent;
   private boolean closed;
   private boolean connected;
   private boolean isIdle;

   
   public ConnectionImpl (LayerCategory category) {
	   Objects.requireNonNull(category);
	   layerCat = category;
	   parameters = new OurParameters();
	   
	   // verify CoreSend existence
	   ensureCoreSend();
	   
	   // instantiate serialisation devices
	   serials = new Serialization[JennyNet.MAX_SERIAL_DEVICE][2];
	   serialAvail = new boolean[] {true, true, true};
	   installSerialisationMethod(0);
	   
	   // this connection's default serialisation devices
	   int method = parameters.getSerialisationMethod();
	   sendSerialisation = getSendSerialization(method);
	   receiveSerialisation = getReceiveSerialization(method);
	   
	   // activate the global connection parameter set 
	   try {
		   setParameters(JennyNet.getConnectionParameters());
	   } catch (IOException e) {
		   e.printStackTrace();
	   }
	   
   }

   /** Creates a new set of serialisation devices for the given serialisation
    * method and installs it into operative slots.
    *   
    * @param method int serialisation method
    * @return boolean true = success, false = method unavailable
    * @throws IllegalArgumentException if method is undefined
    */
   private boolean installSerialisationMethod (int method) {
	   try {
		   // check availability (TEST operation)
		   if (!serialAvail[method]) {
			   throw new SerialisationUnavailableException("availability denied for method " + method);
		   }
		   
		   Serialization ser = JennyNet.getDefaultSerialisation(method);
		   serials[method][0] = ser.copy();
		   serials[method][1] = ser.copy();
		   return true;
		   
	   } catch (ArrayIndexOutOfBoundsException e) {
		   throw new IllegalArgumentException("undefined method: " + method);
	   } catch (SerialisationUnavailableException e) {
		   serials[method][0] = null;
		   serials[method][1] = null;
		   return false;
	   }
   }
   
   /** Creates the required static {@code CoreSend} instance if it is not yet 
    * created or not alive.
    */
   private void ensureCoreSend () {
	   if (layerCat == LayerCategory.CLIENT) {
		   if (coreSendClient == null || !coreSendClient.isAlive()) {
			   coreSendClient = new CoreSend(layerCat);
		   }
	   } else {
		   if (coreSendServer == null || !coreSendServer.isAlive()) {
			   coreSendServer = new CoreSend(layerCat);
		   }
	   }
   }
   
   private CoreSend getCoreSend () {
	   return layerCat == LayerCategory.CLIENT ? coreSendClient : coreSendServer;
   }
   
	@Override
	public ConnectionState getOperationState() {
		return operationState;
	}
	
	/** Sets a new operation state and dispatches the corresponding event to
	 * connection listeners. The new state may not be lower in ranking than
	 * the existing. Does nothing if the given state is equal to the existing. 
	 * <p>In case of CLOSED being the new state, this method waits a maximum
	 * time of 1000 ms until the corresponding connection-event is delivered 
	 * or the output-thread is blocking.  
	 * 
	 * @param state {@code ConnectionState}
	 * @param error {@code ErrorObject}
	 */
	@SuppressWarnings("incomplete-switch")
	protected synchronized void setOperationState (ConnectionState state, ErrorObject error) {
		Objects.requireNonNull(state);
		if (state == operationState) return;
		if (state.ordinal() < operationState.ordinal())
			throw new IllegalStateException("illegal backstep of operation state: " + state);
		
		ConnectionEventType eventType = null;
		if (error != null && error.message == null) {
			if (error.info == 1) {
				error.message = "local server shutdown";
			} else if (error.info == 0) {
				error.message = "local connection shutdown";
			}
		}

		// ante-event dispatching
		switch (state) {
		case CONNECTED:
			operationState = state;
			connected();
			eventType = ConnectionEventType.CONNECTED;
			break;
		case SHUTDOWN:
			operationState = state;
			connectionShutdown(error);
			eventType = ConnectionEventType.SHUTDOWN;
			break;
		case CLOSED:
			connectionClosing(error);
			eventType = ConnectionEventType.CLOSED;
			break;
		}

	    fireConnectionEvent(eventType, error);
	    
	    // we wait for CLOSED event being delivered as this occurs on another thread
	    // if we don't do that, 'waitForClosed()' called externally does not guarantee
	    // the CLOSED event being delivered, which may lead to confusion
	    // this does not happen if the output-processor is in blocking state 
	    if (state == ConnectionState.CLOSED) {
	    	try {
				waitForClosed(1000);
			} catch (InterruptedException e) {
			}
	    }
	    
		operationState = state;
	}

    @Override
    public ConnectionParameters getParameters() {
       return parameters;
    }

	@Override
	public ConnectionMonitor getMonitor() {
		ConnectionMonitor m = new ConnectionMonitor();
		m.category = layerCat;
		m.trajectory = toString();
		m.operationState = getOperationState();
		m.serialMethod = getParameters().getSerialisationMethod();
		m.currentSendLoad = currentSendLoad.get();
		m.parcelsScheduled = getCoreSend().size();
		m.exchangedVolume = transmittedVolume.get();
		m.lastReceiveTime = lastReceiveTime;
		m.lastSendTime = lastSendTime;
		m.objectsIncoming = (objectReceptorMap == null ? 0 : objectReceptorMap.size()) + 
				            outputProcessor.getConSize(this);
		m.objectsOutgoing = inputQueue == null ? 0 : inputQueue.size();
		m.objectsReceived = receiveObjectCounter;
		m.objectsSent = sendObjectCounter;
		m.filesSent = sendFileCounter;
		m.filesReceived = receiveFileCounter;
		m.filesIncoming = fileReceptorMap == null ? 0 : fileReceptorMap.size();
		m.filesOutgoing = fileSendQueue == null ? 0 : fileSendQueue.size();
		m.transmitSpeed = transmitSpeed;
		m.lastPingValue = lastPingValue;
		m.aliveSendPeriod = aliveSignalTask == null ? 0 : aliveSignalTask.period;
		m.aliveTimeout = aliveTimeoutTask == null ? 0 : aliveTimeoutTask.tolerance;
		m.isIdle = isIdle;
		m.connected = connected;
		m.closed = closed;
		m.transmitting = isTransmitting();
		m.idleThreshold = checkIdleTask == null ? 0 : checkIdleTask.getThreshold();
		m.idleCheckPeriod = checkIdleTask == null ? 0 : checkIdleTask.getPeriod();
		
		return m;
	}

   @Override
   public void setParameters (ConnectionParameters par) throws IOException {
	  Objects.requireNonNull(par);
      parameters.takeOver(par);
   }

   @Override
   public Serialization getSendSerialization (int method) {
	   Serialization ser;
	   try {
		   ser =  serials[method][1];
		   if (ser == null) {
			   if (!installSerialisationMethod(method)) {
				   throw new SerialisationUnavailableException("serialisation unavailable: method " + method);
			   }
			   ser =  serials[method][1];
		   }
	   } catch (ArrayIndexOutOfBoundsException e) {
		   throw new IllegalArgumentException("undefined method: " + method);
	   }
	   return ser;
   }

   @Override
   public Serialization getReceiveSerialization (int method) {
	   Serialization ser;
	   try {
		   ser = serials[method][0];
		   if (ser == null) {
			   if (!installSerialisationMethod(method)) {
				   throw new SerialisationUnavailableException("serialisation unavailable: method " + method);
			   }
			   ser = serials[method][0];
		   }
	   } catch (ArrayIndexOutOfBoundsException e) {
		   throw new IllegalArgumentException("undefined method: " + method);
	   }
	   return ser;
   }

   @Override
   public Serialization getSendSerialization () {
	   if (sendSerialisation == null) {
		   int method = getParameters().getSerialisationMethod();
		   throw new SerialisationUnavailableException("serialisation unavailable: method " + method);
	   }
	   return sendSerialisation;
   }

   @Override
   public Serialization getReceiveSerialization () {
	   if (receiveSerialisation == null) {
		   int method = getParameters().getSerialisationMethod();
		   throw new SerialisationUnavailableException("serialisation unavailable: method " + method);
	   }
	   return receiveSerialisation;
   }

   /** Returns the send-serialisation for the given method if it can be
    * found, otherwise throws an exception.
    * 
    * @param method int serialisation method
    * @return {@code Serialization}
    * @throws IllegalArgumentException if method is undefined
    * @throws SerialisationUnavailableException if the method is not supported
    */
   protected Serialization obtainSendSerialisation (int method) {
	   Serialization ser = sendSerialisation;
   	   if (ser == null || ser.getMethodID() != method) {
     	  ser = getSendSerialization(method);
       }
	   return ser;
   }
   
   /** Returns the receive-serialisation for the given method if it can be
    * found, otherwise throws an exception.
    * 
    * @param method int serialisation method
    * @return {@code Serialization}
    * @throws IllegalArgumentException if method is undefined
    * @throws SerialisationUnavailableException if the method is not supported
    */
   protected Serialization obtainReceiveSerialisation (int method) {
	   Serialization ser = receiveSerialisation;
   	   if (ser == null || ser.getMethodID() != method) {
     	  ser = getReceiveSerialization(method);
       }
	   return ser;
   }
   
   /** Decrements the value of current send-load by the given number
    * of bytes. This unlocks any waiting threads on the SEND-LOCK 
    * if send-load falls below the limit as a result of this operation.
    * 
    * @param dataSize long bytes unloaded
    * @throws IllegalArgumentException if argument is negative
    */
   private void decrementSendLoad (long dataSize) {
	  if (dataSize < 0)
		 throw new IllegalArgumentException("illegal negative data size");
	   
	  long value = currentSendLoad.addAndGet(-dataSize);
	  if (value < sendLoadLimit) {
		  notifySendLock();
	  }
   }

   /** Increments the value of current send-load by the given number
    * of bytes.
    * 
    * @param dataSize long
    * @throws IllegalArgumentException if argument is negative
    */
   private void incrementSendLoad (long dataSize) {
	   if (dataSize < 0)
		   throw new IllegalArgumentException("illegal negative data size");
	   currentSendLoad.addAndGet(dataSize);
   }
   
   @Override
   public UUID getUUID() {return uuid;}

   @Override
   public void setUUID (UUID uuid) {
	   if (uuid == null)
		   throw new NullPointerException("uuid is null"); 
	   this.uuid = uuid;
	   this.shortId = Util.makeShortId(uuid);
   }
	   
   @Override
   public int hashCode () {
      InetSocketAddress s1 = getLocalAddress(); 
      InetSocketAddress s2 = getRemoteAddress(); 
      int h = s1 == null ? 2 : s1.hashCode();
      int j = s2 == null ? 1 : s2.hashCode();
      return h ^ j;
   }

   /** Whether both parameters share the same value, including possibly null. 
    * 
    * @param o1 Object
    * @param o2 Object
    * @return boolean true if (o1 == null & o2 == null) | o1.equals(o2)
    */
   private boolean sameValues (Object o1, Object o2) {
      return o1 == o2 || (o1 == null & o2 == null) || 
         ((o1 != null & o2 != null) && o1.equals(o2));   
   }
   
   @Override
   public boolean equals (Object obj) {
      if (obj == null || !(obj instanceof Connection))
         return false;
      
      InetSocketAddress thisLocal = getLocalAddress(); 
      InetSocketAddress thisRemote = getRemoteAddress(); 
      Connection con = (Connection)obj;
      InetSocketAddress conLocal = con.getLocalAddress(); 
      InetSocketAddress conRemote = con.getRemoteAddress();
      
      return sameValues(thisLocal, conLocal) && sameValues(thisRemote, conRemote);
   }

   @Override
   public String toString () {
      InetSocketAddress local = getLocalAddress(); 
      InetSocketAddress remote = getRemoteAddress(); 
      
      String localAddr = local == null ? "null" : local.toString();
      String remoteAddr = remote == null ? "null" : remote.toString();
      String name = getName() == null ? "" : getName().concat(" = ");
      return name + localAddr + " --> " + remoteAddr;
   }

   @Override
   protected void finalize () throws Throwable {
      close(null, 99);
   }

   @Override
   public boolean isConnected() {
      return connected;
   }

   @Override
   public boolean isClosed() {
      return closed;
   }

	@Override
	public boolean isGlobalOutput() {
		return outputProcessor == staticOutputClient || outputProcessor == staticOutputServer;
	}

   @Override
   public boolean isTransmitting() {
	  long transmitTime = getLastTransmitTime();
      int delta = (int)(System.currentTimeMillis() - transmitTime);
      boolean result = isConnected() && transmitTime > 0 ? delta < 4000 : false; 
      return result;
   }
   
   /** Returns the time-point when the most recent socket communication
    * took place (incoming or outgoing).
    *  
    * @return long time in milliseconds
    */
   protected long getLastTransmitTime() {
      return Math.max(lastSendTime, lastReceiveTime);
   }

	@Override
	public int getLastPingTime() {return lastPingValue;}

   @Override
   public long sendObject (Object object, int method, SendPriority priority) {
      checkConnected();
      checkObjectRegisteredForSending(object, method);
      
      long objNr = -1; 
      if (inputProcessor != null && !inputProcessor.isTerminated()) {
          // throw exception if input queue is at maximum
    	  int topSize = getParameters().getObjectQueueCapacity();
    	  if (inputQueue.size() >= topSize) {
    		  throw new ListOverflowException("input queue is full, caps = " + topSize);
    	  }

    	  // assign object number and add to input queue
    	  objNr = getNextObjectNr();
    	  inputQueue.add(new ObjectSendSeparation(object, objNr, method, priority));
    	  if (debug) {
    		  prot("xx adding new user send-object (" + objNr + ") to send-queue (size " 
    	           + inputQueue.size() + "), rem " + getRemoteAddress());
    	  }
      }
      return objNr;
   }

   /** Checks whether this connection is in one of the operation states
    * CONNECTED or SHUTDOWN and throws an exception if this is not the case. 
    * 
    * @throws ClosedConnectionException in operation state CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   private void checkConnectedShutdown () {
      if (closed) {
         throw new ClosedConnectionException();
      }
      if (!isConnected()) {
          throw new UnconnectedException("socket unconnected");
      }
   }

   /** Checks whether this connection is in the operation state
    * CONNECTED and throws an exception if this is not the case. 
    * 
    * @throws ClosedConnectionException in operation state SHUTDOWN or CLOSED 
    * @throws UnconnectedException in operation state UNCONNECTED
    */
   private void checkConnected () {
	  switch (operationState) {
	  case CLOSED:	  
	  case SHUTDOWN:  throw new ClosedConnectionException();
	  case UNCONNECTED: throw new UnconnectedException("socket unconnected");
	  default:
	  }
   }

   @Override
   public long sendFile (File file, String remotePath, SendPriority priority, 
		                    int transaction) throws IOException {
      checkConnected();

      // test space in order-list
      if (fileSendQueue.size()+1 > getParameters().getObjectQueueCapacity()) {
          throw new ListOverflowException("send-file queue overflow");
      }
      
      // place file-send order in order queue
	  synchronized (fileSendQueue) {
		  SendFileOrder order = new SendFileOrder(file, remotePath, priority, transaction);
		  fileSendQueue.add(order);
		  fileSenderMap.put(order.fileID, order);
		  fileSenderMap.put(order.remotePath, order);
    	  if (debug) {
    		  prot("xx adding new user send-file (" + order.fileID + ") to send-queue (size " +
    	           fileSendQueue.size() + "), rem " + getRemoteAddress());
    	  }
		  
		  // start new instance of SendFileProcessor (if not available)
		  if (sendFileProcessor == null) {
			 sendFileProcessor = new SendFileProcessor();
	      }
	      return order.fileID;
	  }
   }
   
   /** Sends a signal to remote. This queues the signal object for sending
    * into <i>coreSend</i> iff the socket is connected. This 
    * operates without checking or setting of current send-load because 
    * signals are the most preferred objects and always enabled for sending.
    * 
    * @param signal <code>Signal</code>
    */
   protected void sendSignal (Signal signal) {
	  // don't send if socket down
	  if (!connected) return;
	   
      if (debug) {
    	  prot("-- (send-signal) (obj " + signal.getObjectID() + ") " + signal.getSigType() +
    			  ", info " + signal.getInfo() + " to " + getRemoteAddress());
          String text = signal.getText();
    	  if (text != null) {
    		  prot("   Text = ".concat(text));
    	  }
      }

      getCoreSend().put(signal);
      return;
   }

   /** Puts a transmit-parcel into coreSend processor. This method updates and
    * deals with the current send-load of this connection, handles baud-delay
    * (TEMPO) and enters WAIT-state if the conditions require it. This method 
    * may block for a lengthy time. If the connection associated with the 
    * parcel is closed, the parcel will not be posted. The method is
    * thread-safe.
    *  
    * @param parcel {@code TransmissionParcel}
    * @throws InterruptedException
    */
   protected void queueParcelForSending (TransmissionParcel parcel) throws InterruptedException {
	   synchronized (sendAccessSync) {
		  // don't send if socket down
		  if (!connected) return;

		   // ensure starting value for lastSendScheduleTime
		   if (lastSendScheduleTime == 0) {
			   lastSendScheduleTime = System.currentTimeMillis();
		   }
		   
		   // baud delay solution in relation to last-send-schedule-time
		   delay_baud(parcel, "queueParcelForSending");
		
		   try {
			   // queue parcel into core-send (unchecked)
			   incrementSendLoad(parcel.getSerialisedLength());
			   getCoreSend().put(parcel);
			   lastSendScheduleTime = System.currentTimeMillis();
			   
			   // send-queue blocking behaviour dependent on unsent data load
			   // explain: we don't have input blocking in InputProcessor (queue)
			   // and we may have endless amounts of parcels coming from FileSendProcessor
			   checkAndPerformSendWait();
			   
		   } catch (ClosedConnectionException e) {
		   }
	   }
   }

   /** Sends the ALL-SENT signal to remote iff the conditions are met. 
    * Queues this signal into coreSend, and sets the 'allSentSignalSent' marker. 
    * After this signal will be sent off in coreSend, a task is performed which
    * performs control-end-of-shutdown. 
    * <p>NOTE: This method does not wait and the time it takes until the 
    * signal is actually sent in <i>coreSend</i> is indeterminable. For this 
    * reason the {@code ControlEndOfShutdownTask} is glued to the signal-parcel
    * and activated after sending on the socket.
    */
   private synchronized void sendAllSentSignal () {
	  // don't send if socket down or already sent or conditions not met
	  if (connected & !allSentSignalSent & objectsAllSent & filesAllSent) {
		  TransmissionParcel signal = new TransmissionParcel(this, 1);
		  ctrlShutdownTask = new ControlEndOfShutdownTask();
		  signal.setTimerTask(ctrlShutdownTask);
	      if (debug) {
	    	  prot("-- (send-all-sent-signal)  sending FINAL signal to " + getRemoteAddress());
	      }
	
    	  getCoreSend().add(signal);
    	  incrementSendLoad(signal.getSerialisedLength());
    	  allSentSignalSent = true;
	  }
   }
   
   /** Sets LAST-SEND-TIME to the current time.
    */
   private void setLastSendTime () {
	   lastSendTime = System.currentTimeMillis();
   }
   
   /** Calculates and sets the sendload-limit for this connection.
    * 
    * @param par {@code ConnectionParameters}
    */
   private void setSendLoadLimit (ConnectionParameters par) {
      long h = (long)par.getParcelQueueCapacity() * par.getTransmissionParcelSize() / 2;
      sendLoadLimit = Math.min(Math.max(h, JennyNet.MIN_MAX_CON_SENDLOAD), JennyNet.MAX_CON_SENDLOAD);
   }
   
   /** Notifies the SEND-LOCK to release any waiting threads. This performs 
    * conditional to the current state of sendLock, which is a MutableBoolean.
    */
   private void notifySendLock () {
	   //  There is a tiny false operation chance as the value checking
	   //  occurs outside of the synchronised block, but this is seen harmless as 
	   //  the worst case is a slipped through data parcel for sending. The locking
	   //  system corrects itself through normal send operation.
	   
	  if (sendLock.getValue()) {
		  synchronized(sendLock) {
			 sendLock.notifyAll();
			 sendLock.setValue(false);
		  }
	  }
   }

   /** Performs waiting on the send-lock if the conditions require it and
    * until the conditions are met to release it. This is not secured against
    * sporadic interrupts. Does nothing if the conditions are not met.
    * <p>Waiting is entered either when sending is switched OFF (via TEMPO)
    * or the current send-load for this connection is exceeding the limit. 
    * The release of the lock is triggered by coreSend (after send-load 
    * is low enough) or via setTempo() when sending is switched ON.
    * 
    * @throws InterruptedException
    */
   private void checkAndPerformSendWait () throws InterruptedException {
	  do {
		 if (currentSendLoad.get() < sendLoadLimit && !sendingOff) {
			break;
		 }
		 synchronized(sendLock) {
			 if (debug) {
				 prot("-- (check-And-Perform-SendWait) \"" + Thread.currentThread().getName() + "\": entering WAIT STATE -- sending blocked");
			 }
			 sendLock.setValue(true);
			 sendLock.wait();
		 }
	  } while (true);
   }
   
   /** Inserts the specified delivery object into the outgoing queue. 
   * <p>If 'restricted' is true, this method may block until space is 
   * made available in the queue. In this case the queue is limited by 
   * <code>getParameters().getObjectQueueCapacity()</code>. If the argument
   * is false the queue is unbounded.
   * 
   * @param object <code>UserObject</code>
   * @param restricted boolean true = wait for space, false = never wait
   */
   private void putObjectToReceiveQueue (DeliveryObject object, boolean restricted) {
	   if (restricted) {
		   // naive blocking behaviour depending on output object counter
		   int topSize = getParameters().getObjectQueueCapacity();
		   do {
			   if (outputObjectCounter.get() < topSize) {
				   break;
			   }
			   Util.sleep(20);
		   } while (true);
	   }

		// put object into outgoing sorted queue
		Thread.interrupted();
		outputProcessor.put(object);
   }

   /** Waits until no more delivery-objects (output-events) are present in 
    * the output-processor for this connection or the output-thread is blocking
    * or the calling thread is interrupted.
    * 
    * @return boolean true if and only if the method was terminated
    *         without thread interruption, false = thread interrupted
    */
   private boolean waitForObjectsDelivered () {
	   if (debug) {
		   prot("-- waiting for objects delivered");
	   }
	   do {
		   if (outputObjectCounter.get() <= 0 || outputProcessor.isBlocking()) {
			   return true;
		   }
		   if (!Util.sleep(30)) return false;
	   } while (true);
   }
   
   @Override
   public long sendPing() {
	  checkConnected();
      long time = System.currentTimeMillis();
	  if (pingSentMap == null || time - lastPingSendTime < 5000) return -1;
	  
      long pingId = ++pingSerialCounter;
      pingSentMap.put(pingId, time);
      sendSignal( Signal.newPingSignal(this, pingId) );
      lastPingSendTime = time;
      return pingId;
   }

   
   @Override
   public void setTempo (int baud) {
	  if (baud < -1)
		  throw new IllegalArgumentException("illegal baud value: " + baud);
	  checkConnectedShutdown();

      if (debug) {
    	  String hstr = baud > -1 ? ("-- setting TEMPO to " + baud + " bytes/sec")
    	    	  : "-- setting TEMPO to NO LIMIT";
    	  prot(hstr + ", " + getRemoteAddress());
      }

	  // adjust connection settings
	  transmitSpeed = baud;
      if (inputProcessor != null) {
    	  inputProcessor.setSending(baud != 0);
      }
	  
	  // signal remote about current baud setting
      Signal tempo = Signal.newTempoSignal(this, baud);
      sendSignal(tempo);
   }

   /** Throws an exception if the given object is not of a class that
    *  is registered for transmission for the given method.
    * 
    * @param object Object testable object
    * @param method int serialisation method
    * @throws UnregisteredObjectException if parameter object is not eligible
    *         for transmission
    * @throws SerialisationUnavailableException
    * @throws IllegalArgumentException if method is undefined
    */
   private void checkObjectRegisteredForSending (Object object, int method) {
	  Objects.requireNonNull(object);
	  Serialization ser = obtainSendSerialisation(method);
      if (!ser.isRegisteredClass(object.getClass()))
          throw new UnregisteredObjectException(object.getClass().toString());
   }

   @Override
   public long sendData(byte[] buffer, int start, int length, SendPriority priority) {
      JennyNetByteBuffer buf = new JennyNetByteBuffer(buffer, start, length);
      return sendObject(buf, priority);
   }

   @Override
   public boolean breakTransfer(long objectID, ComDirection direction, String text) {
	  boolean ok = false;
      if (direction == ComDirection.INCOMING) {
         ok = breakIncomingTransfer(objectID, text);
      } else {
         ok = breakOutgoingTransfer(objectID, text);
      }
      return ok;
   }

   @Override
   public boolean  breakTransfer(long objectID, ComDirection direction) {
	   return breakTransfer(objectID, direction, null);
   }

   /** Breaks an incoming file transfer from remote.
    * 
    * @param objectID long file identifier
    * @param text String commentary on the event
    * @return boolean true = break performed, false = break not performed
    */
   protected boolean breakIncomingTransfer (long objectID, String text) {
      checkConnectedShutdown();
      boolean ok = false;
      
      // find an incoming file transmission
      FileAgglomeration fileRec = fileReceptorMap.get(objectID);
      if (fileRec != null) {
         fileRec.dropTransfer(108, 3, new UserBreakException(text));
         ok = true;
      }
      return ok;
   }
   
   /** Breaks an outgoing object or file transfer to remote.
    * 
    * @param objectID long object or file identifier
    * @param text String commentary on the event
    * @return boolean true = break performed, false = break not performed
    */
   protected boolean breakOutgoingTransfer (long objectID, String text) {
      checkConnectedShutdown();
      boolean ok = false;
      
      // find an outgoing file transmission
      SendFileOrder fileOrder = fileSenderMap.get(objectID);
      if (fileOrder != null) {
    	  fileOrder.breakTransfer(105, 4, new UserBreakException(text));
          ok = true;

      // find an outgoing object transfer 
      } else {
    	  ObjectSendSeparation sendSep = findObjectSendOrder(objectID);
    	  if (sendSep != null) {
			  if (text == null) text = "object terminated by user";
		      sendSep.breakTransfer(201, 4, new ErrorObject(201, text));
	          ok = true;
    	  }
      }
      return ok;
   }
   
   /** Searches for an object sending order of the given identifier in the 
    * input-queue.
    * 
    * @param objectID long object identifier
    * @return {@code ObjectSendSeparation} or null if not found
    */
   private ObjectSendSeparation findObjectSendOrder (long objectID) {
 	  Object[] arr = inputQueue.toArray();
 	  for (Object o : arr) {
 		  ObjectSendSeparation sep = (ObjectSendSeparation) o;
 		  if (sep.getObjectNr() == objectID) {
 			  return sep;
 		  }
 	  }
 	  return null;
   }
   
   /**
    * Schedules a new task to realise sending of ALIVE signals to remote
    * with the given period. A previously scheduled tasks is cancelled. 
    * A period value of 0 cancels ALIVE sending. An ALIVE_CONFIRM signal
    * (w/ corrected argument) is sent to remote after the task has been
    * established.
    *  
    * <p>The given value is corrected to a number in the range
    * MIN_ALIVE_PERIOD .. MAX_ALIVE_PERIOD.
    * 
    * @param alivePeriod int signalling period in milliseconds
    */
   protected void setAlivePeriod (int alivePeriod) {
	  checkConnectedShutdown();
	  alivePeriod = correctedAlivePeriod(alivePeriod);
	  if (debug) {
         prot("-- setting up ALIVE signal sending, period = " + alivePeriod);
	  }

	  // create the ALIVE signal sending task (replace an old one)
      AliveSignalTimerTask.createNew(this, alivePeriod);
      
      // send an ALIVE_CONFIRM signal to remote
	  Signal signal = Signal.newAliveConfirmSignal(this, alivePeriod);
	  sendSignal(signal);
   }
   
   /** Sends an ALIVE-REQUEST signal to remote station in order to receive 
    * periodic ALIVE signals from it.
    * 
    * @param period int ALIVE cycle length in milliseconds 
    */
   protected void sendAliveRequest (int period) {
	   Signal signal = Signal.newAliveRequestSignal(this, correctedAlivePeriod(period));
	   sendSignal(signal);
   }
   
   /** Returns the correct ALIVE-period value for the given input value.
    * Upper and lower limits are tested according to constants from
    * {@code JennyNet} class.
    * 
    * @param period int ALIVE period suggested
    * @return int ALIVE period corrected
    */
   private int correctedAlivePeriod (int period) {
	   if (period != 0) {
		  // correction of activation argument
		  period = Math.min(JennyNet.MAX_ALIVE_PERIOD, Math.max(JennyNet.MIN_ALIVE_PERIOD, period));
	   }
	   return period;
   }
   
   /** Sets whether this connection performs a period based checking of
    * data volume exchanged with remote station (IDLE/BUSY state).
    * The PERIOD of checking is set in connection parameters.
    * <p>Switch TRUE is suppressed (no-op) if connection is not connected.
    *  
    * @param v boolean true == check idle state, false == don't check idle state
    * @param period int for v == true, the check period in milliseconds
    */
   protected void setCheckIdleState (boolean v, int period) {
	   if (period < 0)
		   throw new IllegalArgumentException("illegal period value");
       isCheckIdleState = v;
       
      // switch ON
      if (v) {
         if (!isConnected()) return;

         CheckIdleTimerTask oldTask = checkIdleTask;
//         int period = getParameters().getIdleCheckPeriod();

         // cancel an existing task if PERIOD differs with request
         if (checkIdleTask != null && checkIdleTask.getPeriod() != period) {
            checkIdleTask.cancel();
            checkIdleTask = null;
         }
         
         // create new check-idle-task if not running
         if (checkIdleTask == null) {
            checkIdleTask = oldTask != null ? new CheckIdleTimerTask(period, 
            		        oldTask.lastCheckTime, oldTask.volumeMarker)
            		        : new CheckIdleTimerTask(period);
         }
         
      // switch OFF   
      } else {
         // cancel a running check-idle-task
         if (checkIdleTask != null) {
            checkIdleTask.cancel();
            checkIdleTask = null;
            isIdle = false;
         }
      }
   }
   
   
   /** Setting for testing only. Receiving file transmissions remain unconfirmed
    * and don't complete with reception events.
    *  
    * @param v boolean
    */
   void setSuppressFileConfirm (boolean v) {
	   suppressConfirm = v;
   }
   
   /** Sets the availability of a serialisation method regardless of its
    * implementation. If value <b>false</b> is set, the implementation reacts
    * as if the method were not implemented.
    * 
    * @param method int serialisation method
    * @param v boolean true = available, false = not available
    */
   void setSerialMethodAvailability (int method, boolean v) {
	   serialAvail[method] = v;
   }
   
   /** Testing method: whether the CONFIRM signal sending is suppressed.
    * @return boolean true = suppressed signal
    */
   boolean isConfirmSuppressed () {
	   return suppressConfirm;
   }
   
   /** Cancels the tasks of the ALIVE system which have been activated.
    */
   void cancelAliveSystem () {
      if (aliveSignalTask != null) {
          aliveSignalTask.cancel();
      }
      if (aliveTimeoutTask != null) {
          aliveTimeoutTask.cancel();
      }
   }
   
   /** Setting for testing only. For a given parcel-number above zero an 
    * IOException is thrown during processing of a file-transmission. Zero
    * switches off.
    * 
    * @param direction {@code ComDirection}
    * @param parcelNr int parcel serial number of error occurrence
    */
   void setProcessingTestError (ComDirection direction, int parcelNr) {
	   if (direction == ComDirection.INCOMING) {
		   incomingTestError = parcelNr;
	   } else {
		   outgoingTestError = parcelNr;
	   }
   }

   OutputProcessor getOutputProcessor () {return outputProcessor;}
   
   /** Returns for testing purposes the parcel-nr on which an internal
    * processing failure is thrown.
    * 
    * @param direction {@code ComDirection}
    * @return int parcel serial number of error occurrence
    */
   int getProcessingTestError (ComDirection direction) {
	   return direction == ComDirection.INCOMING ? incomingTestError : outgoingTestError;
   }
   
   /** Removes the file-agglomeration with the give identifier from the 
    * file-receptor-map. Test function.
    * 
    * @param fileID long 
    */
   void removeFileReceptor(long fileID) {
	   if (fileReceptorMap != null) {
		   fileReceptorMap.remove(fileID);
	   }
   }

   @Override
   public boolean isIdle() {return isIdle;}

   @Override
   public Properties getProperties () {
      if (properties == null) {
         properties = new Properties();
      }
      return properties;
   }

   /** Initialises all operations of this connection with new subsystem instances.
    * Any still lingering data elements are discarded and name counters start from zero.
    * 
    * @param socket Socket (requires to be bound and connected)
    * @throws IOException
    * @throws IllegalStateException if socket is not bound or not connected
    * @throws ClosedConnectionException if connection is closed      
    */
   @SuppressWarnings("hiding")
   protected void start (Socket socket) throws IOException {
      if (isClosed())
          throw new ClosedConnectionException(toString());
      if (!socket.isConnected()) 
         throw new IllegalStateException("socket not connected!"); 
      if (!socket.isBound()) 
         throw new IllegalStateException("socket not bound to local address!"); 
      
      // socket initialisation
      this.socket = socket;
      connected = true;
      filesAllSent = true;
      ConnectionParameters par = getParameters();
      int bufferSize = par.getTransmissionParcelSize() + 100;
      socketOutput = new BufferedOutputStream(socket.getOutputStream(), bufferSize);
      socketInput = socket.getInputStream();

      // data inits
      objectSerialCounter.set(0);;
      pingSerialCounter = 0;
      exchangedDataVolume.set(0);
      transmittedVolume.set(0);
      currentSendLoad.set(0);
      setSendLoadLimit(par);
      
      // create hashtables and services
      fileSenderMap = new Hashtable<Object, SendFileOrder>(); 
      fileReceptorMap = new Hashtable<Long, FileAgglomeration>(); 
      objectReceptorMap = new Hashtable<Long, ObjectAgglomeration>(); 
      pingSentMap = new Hashtable<Long, Long>();

      // create data queues
      inputQueue = new PriorityBlockingQueue<ObjectSendSeparation>();
      fileSendQueue = new PriorityBlockingQueue<SendFileOrder>();
      receiveProcessor = new ReceiveProcessor();
      receiveProcessor.start();
      
      // request ALIVE sending from remote if defined
      int period = par.getAlivePeriod();
      if (period > 0) {
    	  sendAliveRequest(period);
      }
      
      // create IDLE state checking task (if requested)
      setCheckIdleState(par.getIdleThreshold() > 0, par.getIdleCheckPeriod());
      
      // set connection TEMPO (informs remote about a non-default speed)
      if (par.getTransmissionSpeed() > -1) {
    	  setTempo(par.getTransmissionSpeed());
      }
      
      // create and start data processors
      inputProcessor = new InputProcessor();
      inputProcessor.start();
   }
   
   /** Method called internally when this client has been marked as CONNECTED
    * and before the event is issued to connection listeners.
    * <p>The method of this class does nothing.
    */
   protected void connected () {
   }
   
   /** Called internally when this connection has been marked as CLOSED and 
    * before the event is issued to connection listeners.
    * <p>The method of class {@code ConnectionImpl} does nothing.
    * 
    * @param error ErrorObject error associated, may be null
    */
   protected void connectionClosing (ErrorObject error) {
   }
   
   /** Called internally when this connection has been marked as SHUTDOWN and 
    * is in the process of shutdown. At this point the socket may still be 
    * operative and will eventually die.
    * <p>The method of class {@code ConnectionImpl} does nothing.
    * 
    * @param error ErrorObject error associated, may be null
    */
   protected void connectionShutdown (ErrorObject error) {
   }
   
   @Override
   public void close (String reason) {
      if (closed) return;
      if (debug) {
    	  prot("xxx USER closes connection " + this + (reason == null ? "" : (", " + reason)));
      }
      
      String text = reason == null ? "closed by user" : reason;
      closeShutdown(new ErrorObject(0, text));
   }
      
   /** Leads to SHUTDOWN state with the given Throwable and info as
    * sources for the error-object passed. Does nothing if already closed.
    * 
    * @param ex {@code Throwable}
    * @param info int error code
    */
   protected void close (Throwable ex, int info) {
	   if (closed) return;
	   closeShutdown(new ErrorObject(info, ex));
   }
   
   /** Puts this connection into SHUTDOWN state, in which all remaining
    * send-orders will still be performed, with an optional error condition. 
    * Does nothing if SHUTDOWN state is already set.
    * 
    * @param error {@code ErrorObject}
    */
   protected void closeShutdown (ErrorObject error) {
	  Objects.requireNonNull(error);
	  if (closed | operationState == ConnectionState.SHUTDOWN) return;
	  localCloseError = error;

	  // easy closure if unconnected
	  if (operationState == ConnectionState.UNCONNECTED) {
		  closed = true;
		  setOperationState(ConnectionState.CLOSED, error);
		  return;
	  }
	  
      if (debug) {
    	  prot("-- (close-shutdown) SHUTDOWN : closing connection w/ error " 
    			  + error.info + ", msg=" + error.message + ", " + this);
      }
      
	  // close input-processor to prevent new sending objects
      if (inputProcessor != null) {
    	  inputProcessor.shutdown(error.info > 3);
      }
      
	  // conditional: send shutdown signal to remote
	  if (!(error instanceof RemoteShutdownMsg)) {
		  Signal s = Signal.newShutdownSignal(this, error.info, error.message);
		  sendSignal(s);
	  }
	  
      // set new state: SHUTDOWN and terminate input-processor
      setOperationState(ConnectionState.SHUTDOWN, error);
   }

   /** Closes this connection's send and receive capabilities and issues
    * a CLOSED event to connection-listeners. This also closes the socket.
    * <p>This method waits until all events in the output-queue have been
    * delivered before the CLOSED state is assumed. It waits for a limited
    * time for the CLOSED event to be delivered.
    * 
    * @param error {@code ErrorObject} to be presented in connection event
    * @param signalRemote boolean whether remote shall be sent CLOSED signal 
    */
   private void closeTerminal (ErrorObject error, boolean signalRemote) {
	  Objects.requireNonNull(error);
      if (closed) return;
	  int errInfo = error.info;
	  String errMsg = error.message;

      // closes connection against further user input
      closed = true;
      if (debug) {
    	  prot("-- (close-terminal) enter closing w/ " + error + this);
      }
      
	  // easy closure if unconnected
	  if (operationState == ConnectionState.UNCONNECTED) {
		  setOperationState(ConnectionState.CLOSED, error);
		  return;
	  }
	  
      // terminate ALIVE structures
      cancelAliveSystem();
      
      // terminate IDLE control
      setCheckIdleState(false, 0);
      
      // inform sub-classes about closing
      connectionClosing(error);
      
	   // shutdown object sending processor
	   if (inputProcessor != null && inputProcessor.isAlive()) {
           inputProcessor.terminate();
       }
	   
	   // shutdown file-send-processor, if active
	   if (sendFileProcessor != null) {
		   sendFileProcessor.terminate();
	   }
	   
       // terminate socket-incoming data chain threads
       if (receiveProcessor != null && receiveProcessor.isAlive()) {
          receiveProcessor.terminate();
       }

       // wait for threads to terminate
       try {
    	   if (inputProcessor != null) {
    		   inputProcessor.join();
//    		   prot("-- inputProcessor joined");
    	   }
    	   if (sendFileProcessor != null) {
    		   sendFileProcessor.join();
//    		   prot("-- sendFileProcessor joined");
    	   }
       } catch (InterruptedException e) {
       }
       
       // report broken sending objects (creates events)
       if (inputQueue != null) {
    	  for (ObjectSendSeparation sender : getSendingObjects()) {
    		  sender.breakTransfer(error.info == 10 ? 205 : 203, 4, error);
    	  }
    	  inputQueue.clear();
       }
      
      // terminate file receptor threads (creates events)
      if (fileReceptorMap != null) {
    	  for (FileAgglomeration agg : getFileReceptors()) {
    		  agg.dropTransfer(114, 5, null);
    	  }
      }
      
      // terminate file sending orders (creates events)
      if (fileSendQueue != null) {
    	  for (SendFileOrder order : getSendFileOrders()) {
    		  order.breakTransfer(113, 6, null);
    	  }
      }
      
	   // wait until dispatched events are delivered by the output-processor
	   waitForObjectsDelivered();
	   if (debug) {
		   prot("-- (close-terminal) all objects delivered");
	   }
	   
      // send CLOSED signal to remote
      if (signalRemote) {
		  Signal close = Signal.newClosedSignal(ConnectionImpl.this, errInfo, errMsg);
		  sendSignal(close);
		  Util.sleep(30);
      }

//	  if (debug) {
//		  prot("-- (close-terminal) closing socket to " + getRemoteAddress());
//	  }
	  closeSocket(error);

	  if (debug) {
		  prot("-- (close-terminal) dispatching CLOSED event");
	  }
	  setOperationState(ConnectionState.CLOSED, error);

	  // terminate an individual output-processor
      if (outputProcessor != staticOutputClient && outputProcessor != staticOutputServer) {
   	      if (debug) {
		     prot("-- (close-terminal) terminating individual output-processor");
	      }
      	  outputProcessor.terminate();
      }
   } // closeTerminal
   
   /** Returns the list of user supplied objects waiting to be sent to remote.
    * (This extracts the input-queue.)
    * 
    * @return {@code ObjectSendSeparation[]}
    */
	private ObjectSendSeparation[] getSendingObjects() {
		return inputQueue.toArray(new ObjectSendSeparation[inputQueue.size()]);
    }

	/** Controls the conditions for ending SHUTDOWN operation state and 
	 * migrate to CLOSED state. Induces closing of the connection (socket) if 
	 * the conditions are met. 
	 */
	private  void controlEndOfShutdown() {
		if (objectsAllSent & filesAllSent & remoteAllSent) {
			if (debug) {
				prot("-- (control-end-of-shutdown) detected END-OF-SHUTDOWN, closing connection to " + getRemoteAddress());
			}
			closeTerminal(localCloseError, false);
		}
	}

   /** Closes the network socket associated with this connection. 
	* Optional error information can be given for reporting (debug) reasons. 
	* Does nothing if this connection is already disconnected.
    *  
    * @param error {@code ErrorObject} error info block; may be null
    */
   private void closeSocket (ErrorObject error) {
	  if (!connected) return;
      connected = false;
      
      try {
          if (socket != null && !socket.isClosed()) {
        	 // close the network socket
             socketOutput.close();
             socketInput.close();
             socket.close();

             // report
             if (debug) {
                 String message = error == null ? null : error.message;
                 int info = error == null ? 0 : error.info;
	             prot("---- (closeSocket) Connection Socket closed --- " 
	            		 + getLocalAddress() + "  --->  " + getRemoteAddress() );
	             if (message != null || info != 0) {
	            	 prot("        error: code " + info + ", msg: " + message);
	             }
             }
          }
          
          // release any waiting threads
          synchronized (waitForDisconnectLock) {
         	 waitForDisconnectLock.notifyAll();
          }
          
       } catch (IOException e) {
          e.printStackTrace();
       }
   }
   
   /** Returns the set of {@code SendFileOrder} instances currently stored
    * for sending a file transmission. This is enabled for undisturbed
    * traversal.
    * 
    * @return {@code Set<SendFileOrder>}
    */
   protected Set<SendFileOrder> getSendFileOrders () {
	   if (fileSenderMap == null) return new ArraySet<>();
	   Collection<SendFileOrder> values = fileSenderMap.values();
	   CopyOnWriteArraySet<SendFileOrder> as = new CopyOnWriteArraySet<>(values);
	   return as;
   }
   
   /** Returns the set of {@code FileAgglomeration} instances currently active
    * for receiving file transmissions.
    * 
    * @return Set&lt;SendFileProcessor&gt;
    */
   protected Set<FileAgglomeration> getFileReceptors () {
	   if (fileReceptorMap == null) return null;
	   return new CopyOnWriteArraySet<>(fileReceptorMap.values());
   }
   
   @Override
   public void addListener (ConnectionListener listener) {
      if (listener != null) {
         synchronized (listeners) {
            listeners.add(listener);
         }
      }
   }

   @Override
   public void removeListener (ConnectionListener listener) {
      if (listener != null) {
         synchronized (listeners) {
            listeners.remove(listener);
         }
      }
   }
   
	@Override
	public Set<ConnectionListener> getListeners() {
		return new ArraySet<>(getConListeners());
	}

   /** Returns a copy of the current list of listeners on this connection.
    * Modifications on the returned list do not strike through to
    * the connection's operative list!
    *  
    * @return array of <code>ConnectionListener</code>
    */
   protected ConnectionListener[] getConListeners () {
      synchronized (listeners) {
         ConnectionListener[] array = new ConnectionListener[listeners.size()];
         listeners.toArray(array);
         return array;
      }
   }
   
   /** Fires the given file-transmission event to all connection-listeners.
    * 
    * @param tmEvent {@code TransmissionEvent}
    */
   protected void fireTransmissionEvent (TransmissionEvent tmEvent) {
	  Objects.requireNonNull(tmEvent);
	  
	  if (tmEvent.getType() == TransmissionEventType.FILE_RECEIVED) {
		  receiveFileCounter++;
	  }
	  
	  ConnectionEvent event = new ConnectionEvent(tmEvent);
	  DeliveryObject object = new DeliveryObject(event, SendPriority.NORMAL);
      putObjectToReceiveQueue(object, true);
   }

   /** Fires an object-aborted event to all connection-listeners.
    * 
    * @param object {@code Object}, may be null
    * @param objectNr long object identifier
    * @param info int error code
    * @param msg String error description or null
    */
   protected void fireObjectAbortedEvent (Object object, long objectNr, int info, String msg) {
	  ConnectionEvent event = new ConnectionEvent(this, null, ConnectionEventType.ABORTED, 
			  	object, objectNr, info, msg);
	  DeliveryObject deliver = new DeliveryObject(event, SendPriority.NORMAL);
      putObjectToReceiveQueue(deliver, false);
   }

   /** Fires an event of the given connection administration type to 
    * all connection-listeners.
    * 
    * @param type {@code ConnectionEventType} type of event issued
    * @param cause {@code ErrorObject} object containing information about 
    *              the cause of event, may be null
    */
   protected void fireConnectionEvent (ConnectionEventType type, ErrorObject cause) {
	  Objects.requireNonNull(type, "type is null");
	  if (type != ConnectionEventType.CONNECTED && type != ConnectionEventType.CLOSED 
		  && type != ConnectionEventType.IDLE && type != ConnectionEventType.SHUTDOWN) 
		  throw new IllegalArgumentException("illegal event type");
      
      int info = cause == null ? 0 : cause.info;
      String message = cause == null ? null : cause.message;
	  ConnectionEvent event = new ConnectionEvent(this, type, info, message);
	  DeliveryObject deliver = new DeliveryObject(event, SendPriority.NORMAL);
      putObjectToReceiveQueue(deliver, false);
   }

   /** Prints the given protocol text to the console.
    * 
    * @param text String 
    */
   protected void prot (String text) {
	   prot(this, text);
   }
   
   /** Prints the given protocol text to the console.
    * 
    * @param con {@code Connection} source connection
    * @param text String 
    */
   protected static void prot (Connection con, String text) {
	   InetSocketAddress adr = con.getLocalAddress(); 
	   System.out.println("(" + (adr == null ? "?" : adr.getPort()) + ") " + text);
   }
   
   @Override
   public InetSocketAddress getRemoteAddress() {
      return socket == null ? null :
         (InetSocketAddress)socket.getRemoteSocketAddress();
   }

   @Override
   public InetSocketAddress getLocalAddress() {
      return socket == null ? null :
         (InetSocketAddress)socket.getLocalSocketAddress();
   }

   @Override
   public void setName(String name) {
      connectionName = name;
   }

   @Override
   public String getName() {
      return connectionName;
   }

   @Override
   public LayerCategory getCategory () {
      return layerCat;
   }

   @Override
   public byte[] getShortId() {
      return shortId;
   }

	@Override
	public int getTransmissionSpeed() {
		return transmitSpeed;
	}
     
   /** Adds the given value to the exchanged data volume counter and performs
    * associated control tasks if required.
    * 
    * @param length int increment value
    */
   private void addToExchangedVolume (int length) {
	   if (length > 0) {
		   exchangedDataVolume.addAndGet(length);
		   if (isIdle && checkIdleTask != null) {
			   checkIdleTask.run();
		   }
	   }
   }

   /** Performs BAUD related sleep time of the calling thread if a transmit
    * speed (TEMPO) is set. 
    * 
    * @param parcel <code>TransmissionParcel</code>
    * @param markTime long time value when transmission of parcel started (ms)
    * @param function String algorithmic location (debug report)
    */
   private void delay_baud (TransmissionParcel parcel, String function) {
	  long speed = getTransmissionSpeed();
      if (speed > 0) {
    	  long markTime = lastSendScheduleTime;
    	  long shallLast = (long)parcel.getSerialisedLength() * 1000 / speed;
    	  long hasTaken = System.currentTimeMillis() - markTime;
    	  int delay = (int)(shallLast - hasTaken);
// prot("(delay_baud) mark=" + markTime + ", shall=" +shallLast + ", taken=" + hasTaken + ", delay=" + delay);    	  
    	  if (delay > 0) {
              if (debug) {
            	  prot("--- (" + function + ") : BAUD delay performing " 
            		  + delay + " ms sleep, " + "consumed " + hasTaken 
            		  + ", obj " + parcel.getObjectID());
              }
    		  Util.sleep(delay);
    	  }
      }
   }
   
   /** Returns the next object serial number for sending.
    * The number may not re-occur for this connection.
    * Numbers are starting from 1;
    * 
    * @return long serial number
    */
   protected long getNextObjectNr() {
      return objectSerialCounter.incrementAndGet();
   }

   /** Sets the member value 'objectSerialCounter' to v minus 1.
    * 
    * @param v int
    */
   protected void setNextObjectNr (long v) {
	   if (v <= objectSerialCounter.get()) 
		   throw new IllegalArgumentException("illegal value: " + v);
	   objectSerialCounter.set(v-1);
   }
   
   protected Socket getSocket () {
      return socket;
   }
   
   /** Returns the static {@code Timer} instance used by this class.
    *    
    * @return Timer
    */
   protected static Timer getTimer () {return timer;}
   
	@Override
	public void waitForDisconnect (long time) throws InterruptedException {
		synchronized (waitForDisconnectLock) {
			if (connected) {
				long mark = System.currentTimeMillis();
				waitForDisconnectLock.wait(time);
				
				// perform hard closure if time exceeded
				long elapsed = System.currentTimeMillis() - mark;
				if (time > 0 && elapsed >= time) {
					closeTerminal(new ErrorObject(8, "connection shutdown timeout"), true);
				}
			}
		}
	}

	@Override
	public void waitForClosed (long time) throws InterruptedException {
		synchronized (waitForClosedLock) {
			if (operationState != ConnectionState.CLOSED && !outputProcessor.isBlocking()) {
				if (debug) {
					prot("-- enter waiting for CLOSED");
				}
				long mark = System.currentTimeMillis();
				waitForClosedLock.wait(time);
				
				// perform hard closure if time exceeded
				long elapsed = System.currentTimeMillis() - mark;
				if (time > 0 && elapsed >= time) {
					closeTerminal(new ErrorObject(8, "connection closing timeout"), true);
				}
			}
		}
	}

	@Override
	public void closeHard () {
		closeTerminal(new ErrorObject(10, "connection closed hardly"), true);
	}
	
// --------------- inner classes ----------------   
   
   /** This thread performs serialisation of user input objects, segments
    * the serialisation into sending parcels and puts them into the 
    * core-send parcel queue. If 'terminate()' is called, operations cease
    * immediately but the input-queue is left uncleared. If 'shutdown()' is 
    * called, operations continue until the input-queue is empty or an 
    * error condition is set.
    */
   private class InputProcessor extends Thread {
      volatile boolean shutdown, terminate, errorCondition;
      
      InputProcessor () {
         super("Input Processor ".concat(String.valueOf(getLocalAddress())));
         setPriority(parameters.getBaseThreadPriority());
         setSending(getParameters().getTransmissionSpeed() != 0);
      }
      
      @Override
      public void run() {
         
         while (!terminate) {
        	// check operation status
        	boolean operating = !shutdown || (!inputQueue.isEmpty() && !errorCondition);
        	if (!operating) {
       	 		if (debug) {
       	 			prot("-- (InputProcessor) FINISH OPERATIONS");
       	 		}
        		break;
        	}
            	
            try {
           	 	// enter waiting state if SENDING OFF
       		 	// we send only SIGNAL parcels in sending-off state
           	 	if (sendingOff) {
           	 		if (debug) {
           	 			prot("-- INPUT-PROCESSOR: sending is OFF");
           	 		}
           	 		// checking and performing send-locked state
                    checkAndPerformSendWait();
           	 	}
                sendingOff = getTransmissionSpeed() == 0;
                
               // get next send-object from input-queue and process for next parcel
               // (this can block until a send-object is available)
               // we choose this two-step access method to allow for resorting of objects
               // during sending (in particular relevant for TEMPO delayed connections)
               // (unfortunately Java queues won't allow for waiting peek operations)
               ObjectSendSeparation separation = inputQueue.peek();
               if (separation == null) {
            	   separation = inputQueue.take();
            	   inputQueue.put(separation);
               }
               
               // get next send-parcel from send-object and push into core-sending
               // (pushing can block until send load has decreased under the connection's limit)
               // remove the send-object from input-queue if all parcels are 
               // transferred to core-send
               TransmissionParcel parcel = separation.getNextParcel();
               if (parcel == null) {
            	   inputQueue.remove(separation);
            	   sendObjectCounter++;
            	   if (inputQueue.isEmpty() && fileSendQueue.isEmpty()) {
            		   lastSendScheduleTime = 0;
            	   }
               } else {
            	   queueParcelForSending(parcel);
               }
        	   
            } catch (InterruptedException e) {
            	interrupted();
            	
            } catch (Throwable e) {
            	e.printStackTrace();
            	close(e, 5);
            }
         } // while
         
         objectsAllSent = true;
         
         // control end of SHUTDOWN state
         // send ALL_SENT signal to remote if no other activity remaining
         if (shutdown) {
       		 sendAllSentSignal();
         }
	     if (debug) {
		    prot("-- InputProcessor terminated, rem "  + getRemoteAddress());
		 }
      } // run
      
      /** Sets the cardinal send control (on/off state). If sending is off
       * no send-parcels get queued for sending.
       * 
       * @param doSend boolean true == send data, false == wait state
       */
      public void setSending (boolean doSend) {
    	  if (sendingOff != doSend) return;
    	  if (debug) {
    		  prot("-- (InputProcessor) set SENDING to '" + doSend +"' : " + ConnectionImpl.this);
    	  }
    	  
    	  sendingOff = !doSend;
    	  if (doSend) {
    		  notifySendLock();
    	  } else {
    		  interrupt();
    	  }
      }

      /** Initiates shutdown on this thread while continuing to send queued
       * objects conditionally. Thread terminates when input queue is empty 
       * or an error-condition is entered (parameter).
       *  
       * @param error boolean true = local error condition, false = otherwise
       */
      public void shutdown (boolean error) {
         if (debug) {
        	 prot("-- (InputProcessor) enter shutdown w/ error = " + error);
         }
    	 errorCondition = error;
         shutdown = true;
         interrupt();
      }

      /** Terminates operations of this thread unconditionally and 
       * immediate. The input data queue is cleared. 
       */
      public void terminate () {
          if (debug) {
         	 String hstr = isAlive() ? "terminate called" : " already DEAD";
         	 prot("--- (InputProcessor) " + hstr + ", rem " + getRemoteAddress());
          }
    	  terminate = true;
    	  interrupt();
      }
      
      /** Whether this thread has been terminated. Thread may continue
       * operations in termination state until its input queue is empty. 
       * 
       * @return boolean
       */
      public boolean isTerminated () {
    	  return shutdown | terminate;
      }
   } // InputProcessor
   
   
   /** Handles delivery of output objects (de-serialised net-received objects)
    * to the application.
    */
   static class OutputProcessor extends PriorityBlockingQueue<DeliveryObject> {
	  final LayerCategory layer;
	  final Thread output; 
	  final String name;
	  ConnectionImpl connection;
	  long objectCounter;
	  long deliverMarkTm;
	  boolean isStatic;
      volatile boolean terminated;

      /** Creates a new output-processor and starts its thread.
       * 
       * @param name String processor name 
       * @param category {@code LayerCategory} processor's belonging
       * @param priority int thread priority 
       * @param staticUsage boolean whether instance is for static use 
       */
      public OutputProcessor (String name, LayerCategory category, int priority, boolean staticUsage) {
    	 super(32);
    	 Objects.requireNonNull(category, "category is null");
    	 this.name = name;
    	 layer = category;
    	 isStatic = staticUsage;
    	 if (debug) {
    		 System.out.println("-- JENNY-NET OUTPUT INIT (" + category + ", " 
    				 + (staticUsage ? "static" : "specific") + "), priority = " + priority);
    	 }
    	 
    	 // create thread
         output = new Thread("JennyNet Output Processor: " + name) {
        	 
         @Override
         public void run() {
            
             while (true) {
               	 // decide on whether to continue operations
               	 boolean operating = !terminated || !isEmpty();
               	 if (!operating) break;
               	 
                 try {
                    // read from object-output-queue
                    DeliveryObject deliverObj = take();
                    // get associated connection 
                	ConnectionImpl con = deliverObj.getConnection();

                	try {
                   		deliverEvent(deliverObj);
	                   	 
                    } catch (Throwable e) {
                  	  	con.prot("***  OUTPUT-PROCESSOR (" + layer + ", " + (staticUsage ? "static" : "specific")
                  	  			+ "): UNCAUGHT APPLICATION EXCEPTION  *** for " 
                   				+ con + "\n     " + e + "\n");
                        e.printStackTrace();
                         
                    } finally {
                    	// decrement object counter of connection
                    	con.outputObjectCounter.decrementAndGet();
                    }
                      
                   } catch (InterruptedException e) {
                   		interrupted();
                   }
             }
                
             clear();
             if (debug) {
            	 System.out.println("-- TERMINATING JENNY-NET OutputProcessor: " + name);
             }
         }  // run
         };
         
         setThreadPriority(priority);
         if (staticUsage) {
        	 output.setDaemon(true);
         }
         output.start();
      }
      
      /** Whether this queue is capable of delivering objects to listeners,
       * i.e. the internal thread is alive.
       * 
       * @return boolean true == is alive
       */
      public boolean isAlive () {
    	  return output.isAlive();
      }

      /** Whether this processor has been initialised as static-usage
       * (i.e. is the global output-processor).
       * 
       * @return boolean true = static usage, false = individual usage
       */
      public boolean isStatic () {return isStatic;}
      
      /** In case of an ongoing (unfinished) event delivery this value 
       * indicates the delay which has taken place so far. If there is no
       * delivery ongoing, zero is returned.
       * 
       * @return long delay in milliseconds
       */
      public long getDeliveryDelay () {
    	  return deliverMarkTm == 0 ? 0 : System.currentTimeMillis() - deliverMarkTm;
      }
      
      /** Whether this processor is overdue blocking in an event delivery 
       * attempt. Overdue blocking occurs if the user routine of a connection
       * listener does not return for an amount of time which is defined in 
       * connection parameter "deliver-tolerance".
       *    
       * @return boolean true = thread is blocked in delivery
       */
      public boolean isBlocking () {
    	  ConnectionImpl con = connection;
    	  long mark = deliverMarkTm;
    	  if (mark > 0 && con != null) {
    		  long delay = getDeliveryDelay();
    		  int tolerance = con.getParameters().getDeliverTolerance();
    		  boolean resolve = delay > tolerance;
    		  if (debug) {
    			  con.prot("-- (output-processor.isBlocking) " + resolve + ", mark = " + mark + ", delay = " 
    					  + delay + ", tolerance = " + tolerance);
    		  }
    		  return resolve;
    	  }
    	  return false;
      }
      
      public void terminate() {
         terminated = true;
         output.interrupt();
      }

	  public void setThreadPriority(int p) {
		 output.setPriority(p);
	  }
	  
	  /** Removes all output-events for the given connection from this 
	   * processor and put them into the 'other' processor.
	   * 
	   * @param con {@code ConnectionImpl} connection of events
	   * @param other {@code OutputProcessor} target processor
	   * @return int number of elements moved
	   */
	  public int drainConEventsTo (ConnectionImpl con, OutputProcessor other) {
		  Object[] arr = toArray();
    	  int count = 0;
    	  for (Object o : arr) {
    		  DeliveryObject obj = (DeliveryObject) o;
    		  if (obj.getConnection() == con) {
    			  count++;
    			  other.put(obj);
    			  remove(obj);
    		  }
    	  }
    	  return count;
	  }
	  
      @Override
      public synchronized void put (DeliveryObject obj) {
    	 if (!output.isAlive())
    		 throw new IllegalStateException("output processor unavailable");
    	 
    	 obj.setDeliverNr(objectCounter++);
 		 obj.getConnection().outputObjectCounter.incrementAndGet();
		 super.put(obj);
	  }

      /** Returns the number of delivery objects which belong to the given 
       * connection.
       * 
       * @param con {@code Connection} 
       * @return int number of objects
       */
      public int getConSize (Connection con) {
    	  Object[] arr = toArray();
    	  int count = 0;
    	  for (Object o : arr) {
    		  if (((DeliveryObject) o).getConnection() == con) {
    			  count++;
    		  }
    	  }
    	  return count;
      }
      
      private void releaseWaitForClosed (ConnectionImpl con) {
		 // unlock threads waiting for CLOSED
		 synchronized (con.waitForClosedLock) {
			if (debug) {
				con.prot("-- releasing wait-for-CLOSED");
			}
		 	 con.waitForClosedLock.notifyAll();
		 }
      }
      
      private void deliverEvent (DeliveryObject deliver) {
    	  Objects.requireNonNull(deliver);

    	  ConnectionImpl con = deliver.getConnection();
    	  ConnectionEvent evt = deliver.getConnectionEvent();
    	  ConnectionEventType eventType = evt.getType();
    	  Object object = evt.getObject();
	      int info = evt.getInfo();
	      long objectNr = evt.getObjectNr();
	      String msg = evt.getText();
	      String subType = eventType != ConnectionEventType.TRANS_EVT ? "" : 
	    	  ("." + evt.getTransmissionEvent().getType());
	      if (debug) {
	    	  con.prot("(OutputProcessor) delivering " + eventType + subType + ", objNr " + objectNr 
	    			  + ", info " + info + ", msg = " + msg + ", rem " + con.getRemoteAddress());
          	  if (evt.getType() == ConnectionEventType.OBJECT && object == null) {
        		 con.prot("(OutputProcessor) *** WARNING: delivery object is null, ID = " 
        					+ objectNr + ", rem " + con.getRemoteAddress());
        	  }
	      }
    	  
          // dispatch event to registered listeners
	      try {
	    	 deliverMarkTm = System.currentTimeMillis();
	    	 connection = con;
		     ConnectionListener[] array = con.getConListeners();
	         for (ConnectionListener i : array) {
		         switch (eventType) {
		         case CONNECTED:
		            i.connected(con);
		            break;
		         case CLOSED:
		            i.closed(con, info, msg);
		            break;
		         case SHUTDOWN:
		            i.shutdown(con, info, msg);
		            break;
		         case IDLE:
	                i.idleChanged(con, con.isIdle(), info);
		            break;
				case ABORTED:
					i.objectAborted(con, objectNr, object, info, msg);
					break;
				case OBJECT:
					i.objectReceived(con, deliver.getPriority(), objectNr, object);
					break;
				case PING_ECHO:
					i.pingEchoReceived((PingEcho)object);
					break;
				case TRANS_EVT:
					i.transmissionEventOccurred(evt.getTransmissionEvent());
					break;
		        default:
		            throw new UnsupportedOperationException("not ready for event type: ".concat(eventType.toString()));
		         }
             }

	   	  // recover from user thrown exception
	 	  } catch (Throwable e) {
			  e.printStackTrace();
			  
		  } finally {
			  deliverMarkTm = 0;
			  connection = null;
			  if (eventType == ConnectionEventType.CLOSED) {
				  releaseWaitForClosed(con);
			  }
		  }
      }

      public void interrupt() {
    	  deliverMarkTm = 0;
    	  output.interrupt();
      }
   }
   
   /** Structure to hold information about a send-file order. Orders can be
    * queued in a sorted fashion as the structure is comparable. Orders can be
    * kept until they are transformed into {@code SendFileProcessor} to 
    * actually perform the sending. 
    */
   private class SendFileOrder implements Comparable<SendFileOrder> {
	   private long fileID;
	   /** the source data file of the transfer */
	   private File file;
	   /** input-stream of file */
       private InputStream fileIn;
	   private SendPriority priority;
	   private Object lock = new Object();
	   /** the destination (relative) path for the receiver */
	   private String remotePath;
	   private long fileLength;
	   private long insertTime;
       private long duration;
	   private int nrOfParcels;
	   private int parcelsSent;
	   private int parcelBufferSize;
	   private long transmittedLength;
	   /** optional transaction code (e.g. server multiplexor action) */
	   private int transaction;
	   /** whether transmission is not finished */
	   private boolean ongoing;
	   private boolean isReserved;
	   
	   /** Creates a new order to transmit a file. A new object-ID is 
	    * associated with the order as file-ID if all conditions are met.
	    * The input file is tested for existence and availability to read.
	    * 
	    * @param file File input file
	    * @param remotePath String destination path information (target name)
	    * @param priority {@code SendPriority}
	    * @param transaction int optional transaction ID 
        * @throws FileInTransmissionException if there exists a transmission 
        *         for the given destination path
        * @throws FileNotFoundException if the file is not found or cannot
        *         be opened
	    * @throws IOException 
	    */
	   public SendFileOrder (File file, String remotePath, 
			                 SendPriority priority, int transaction) throws IOException {
		   Objects.requireNonNull(file, "file is null");
		   Objects.requireNonNull(remotePath, "remotePath is null");
		   Objects.requireNonNull(priority, "priority is null");
		   if (remotePath.isEmpty())
			   throw new IllegalArgumentException("remote-path is empty");
		   if (transaction < 0)
			   throw new IllegalArgumentException("transaction is negative");
		   
		   this.file = file.getCanonicalFile();
		   this.remotePath = remotePath;
		   this.priority = priority;
		   this.transaction = transaction;
		   init();
	   }

	   /** Calculates order data and tests them semantically deep.
	    * File must exist and remote-path not impose a duplicate.
	    *   
        * @throws FileInTransmissionException 
        * @throws FileNotFoundException
	    * @throws IOException
	    */
	   private void init() throws IOException {
		  // test file existence and availability
          InputStream input = new FileInputStream(file);
          input.close();
          
          // check if file-target is not already in transmission
          if (fileSenderMap.containsKey(remotePath)) {
             throw new FileInTransmissionException();
          }
	         
		  fileLength = file.length();
		  fileID = getNextObjectNr();
	      parcelBufferSize = parameters.getTransmissionParcelSize();
	      long h = fileLength / parcelBufferSize;
	      if (fileLength % parcelBufferSize > 0) h++;
	      if (h > Integer.MAX_VALUE) {
	    	  throw new IllegalStateException("illegal number of parcels for file-order: " + h);
	      }
	      nrOfParcels = Math.max(1, (int) h);
		  insertTime = System.currentTimeMillis();
	      ongoing = true;
	      
	      if (debug) {
	      	 prot("--- created SendFileOrder, file-ID " + fileID + ", length " + fileLength 
	      			 + ", parcels " + nrOfParcels + ", remote [" + remotePath + "]");
	      	 prot("    source = " + file.getAbsolutePath());
	      }
	   }

	   /** Opens the source file and performs IO-reservation.
	    * 
	    * @throws FileInTransmissionException if the file is blocked in IO
	    * @throws IOException
	    */
	   public void startSending () throws IOException {
      	  if (!IO_Manager.get().enterActiveFile(file, ComDirection.INCOMING)) {
    		 throw new FileInTransmissionException("blocked IO for reading: " + file);
    	  }
      	  isReserved = true;
          fileIn = new BufferedInputStream(new FileInputStream(file), JennyNet.STREAM_BUFFER_SIZE);
	   }
	   
	   @Override
	   /** The lower value is the higher priority in a priority queue.
	    */
	   public int compareTo (SendFileOrder o) {
		   Objects.requireNonNull(o);
		   
		   // first rank: priority
		   int po = priority.compareTo(o.priority);
		   if (po != 0) return -po;
		   
		   // second rank: file-ID (lower value first)
		   return fileID < o.fileID ? -1 : fileID > o.fileID ? 1 : 0;
	   }

	   /** Duration of the transmit since order insertion in milliseconds.
        *     
        * @return long time value 
        */
       public long getTransmitTime() {
          return ongoing ? System.currentTimeMillis() - insertTime : duration;
       }

	   @Override
	   public boolean equals (Object o) {
		   if (o == null || !(o instanceof SendFileOrder)) return false;
		   return ((SendFileOrder)o).fileID == fileID;
	   }

	   @Override
	   public int hashCode() {return (int) fileID;}

	/** Terminates this file transmission and issues a FILE_ABORTED event
	   *  stating the given event-info and optionally a causing exception.
	   * Sends a a BREAK signal w/ 'signalInfo' value to remote station if 
	   * and only if 'signalInfo' is not zero.
	   *
	   * @param eventInfo int
	   * @param signalInfo int signal subtype, if != 0 sends a BREAK signal 
	   *        of this type to remote
	   * @param e Exception error message
	   * @throws IOException 
	   */
	  public void breakTransfer (int eventInfo, int signalInfo, Exception e) {
	     if ( !ongoing ) return;
	     cancelTransfer();
	
	     if (debug) { 
	   	     prot("--- (SendFileOrder) dropping outgoing file transfer ID " + fileID);
		     prot("    signal to remote: BREAK " + signalInfo);
	         prot("    FILE_ABORTED event " + eventInfo);
	     }
	
	     // send a TRANSFER BREAK signal to remote
	     if (signalInfo != 0) {
	        String text = e == null ? null : e.toString();
	        sendSignal(Signal.newBreakSignal(ConnectionImpl.this, fileID, signalInfo, text));
	     }
	
	     // issue ABORTED event to user
	     if (eventInfo != 0) {
	        // inform the user (remote abortion event)
	        TransmissionEventImpl event = new TransmissionEventImpl(
	        	 ConnectionImpl.this,
	             TransmissionEventType.FILE_ABORTED, ComDirection.OUTGOING,
	             priority, fileID, eventInfo, e);
	        event.setDuration(getTransmitTime());
	        event.setPath(remotePath);
	        event.setFile(file);
	        event.setTransmissionLength(transmittedLength);
	        event.setExpectedLength(fileLength);
	        event.setTransaction(transaction);
	        fireTransmissionEvent(event);
	     }
	  }

	private synchronized void cancelTransfer () {
	     if (debug) { 
	    	 prot("--- (SendFileOrder) closing transfer: ID " + fileID + ", remote = " + remotePath
	    			 + ", " + ConnectionImpl.this.getRemoteAddress());
	     }
	     
	     // de-register this transmission thread
	     duration = getTransmitTime();
	     fileSenderMap.remove(fileID);
	     fileSenderMap.remove(remotePath);
	     
	     // close the file source and cancel IO-reservation
	     closeFile();

         // release a waiting condition on this order (wait-lock)
    	 synchronized (lock) {
    	     ongoing = false;
    		 lock.notifyAll();
    	 }
	  }

	 /**  If this send-order is ongoing, closes the file resources and
	  * removes this order from the send-order-queue.
	  */
	 public void closeFile ()  {
		 if (!ongoing) return;

		 // remove from order-queue
	     synchronized (fileSendQueue) {
	    	 fileSendQueue.remove(this);
	     }
	     
	     try {
	    	 if (fileIn != null) {
	    		 fileIn.close();  
	    		 if (debug) {
	    			 prot("	   source file closed: " + file);
	    		 }
	    	 }
		     if (isReserved) {
		    	 IO_Manager.get().removeActiveFile(file, ComDirection.INCOMING);
		    	 isReserved = false;
		     }
	     } catch (IOException e) {
	        e.printStackTrace();
	     }
	 }
	
	/** Upon reception of a CONFIRM or a FAIL signal from remote. 
	   * 
	   * @param signalInfo int 0 = success, 1 = remote file assignment error, 
	   *                   3 = remote processing error 
	   * @param error Exception error condition, may be null
	   * @throws IOException 
	   */
	  public void finishTransfer (int signalInfo, Exception error) {
	     if ( !ongoing ) return;
	     cancelTransfer();
	     
	     boolean success = signalInfo == 0;
	     if (success) {
	    	 sendFileCounter++;
	     }
	     if (debug) {
	    	 prot("--- (SendFileOrder) finishing outgoing transfer ID " + fileID  
	    			 + ", success=" + success);
	     }
	     
	     // EVENT dispatch: inform user (file transfer event)
	     TransmissionEventType type = success ? TransmissionEventType.FILE_CONFIRMED 
	           : TransmissionEventType.FILE_ABORTED;
	     TransmissionEventImpl event = new TransmissionEventImpl(
	           ConnectionImpl.this, type, ComDirection.OUTGOING, priority, fileID);
	     event.setInfo(signalInfo == 3 ? 109 : signalInfo == 1 ? 101 : 0);
	     event.setException(error);
	     event.setDuration(getTransmitTime());
	     event.setTransmissionLength(transmittedLength);
	     event.setPath(remotePath);
	     event.setFile(file);
	     event.setTransaction(transaction);
	     fireTransmissionEvent(event);
	  }

	 /** Terminates this file transmission for a timeout event,
	   * sends a FAIL signal to remote and issues a FILE_ABORTED event to local.
	   * 
	   * @param sec int timeout time in seconds
	 * @throws IOException 
	   */
	  public void timeoutTransfer (int sec) {
	     if (!ongoing) return;
	     
	     // stop transfer
	     cancelTransfer();
	
	     if (debug) {
	    	 prot("--- (SendFileOrder) CONFIRM-TIMEOUT on outgoing transfer ID " + fileID);
	     }
	     
	     // signal FAILURE to remote
	     sendSignal(Signal.newFailSignal(ConnectionImpl.this, fileID, 2, "timeout = " + sec + " sec"));
	
	     // EVENT dispatch: inform user (timeout abortion event)
	     TransmissionEventImpl event = new TransmissionEventImpl (ConnectionImpl.this, 
	    		 TransmissionEventType.FILE_ABORTED, ComDirection.OUTGOING, 
	    		 priority, fileID, 103, null);
	     event.setDuration(getTransmitTime());
	     event.setPath(remotePath);
	     event.setFile(file);
	     event.setTransmissionLength(transmittedLength);
	     event.setExpectedLength(fileLength);
	     event.setTransaction(transaction);
	     fireTransmissionEvent(event);
	  }
	  
	  /** The calling thread waits until this order is finished through the 
	   * <i>cancelTransfer()</i> method. This is the case when the transfer
	   * receives a confirming or breaking condition through respective 
	   * member methods.
	   * 
	   * @throws InterruptedException
	   */
	  public void waitForTermination () throws InterruptedException {
		  synchronized (lock) {
			  if (!ongoing) return;
			  if (debug) {
				  prot("-- entering a WAIT condition on send-file-order " + fileID);
			  }
			  lock.wait();
			  if (debug) {
				  prot("-- leaving a WAIT condition on send-file-order " + fileID);
			  }
		  }
	  }
   }
   
   /** Thread to conduct file sending over the network. Operates as long as 
    * there is at least one entry in the 'fileOutQueue' (list of SendFileOrder)
    * and terminates when the queue becomes empty. The 'fileOutQueue' is
    * filled by the file-send commands.
    * 
    */
   private class SendFileProcessor extends Thread {
	   
      private volatile boolean terminate;
      private SendFileOrder lastOrder;

      /** Creates a new file send processor (Thread) for a given file sending
       * order.
       * 
       * @param sendOrder {@code SendFileOrder}sending order
       * @throws FileInTransmissionException if there is already a transmission for that file
       * @throws FileNotFoundException if opening the file was impossible
       * @throws IOException
       */
      public SendFileProcessor () throws IOException {
         super("Send File Processor ".concat(String.valueOf(getLocalAddress())));
         init();
         start();
      }
      
      private void init () throws FileNotFoundException {
    	  Objects.requireNonNull(fileSenderMap);
    	  Objects.requireNonNull(fileSendQueue);
          setPriority(Math.max(parameters.getBaseThreadPriority()-2, Thread.MIN_PRIORITY));
    	  filesAllSent = false;

         if (debug) {
        	 prot("--- starting new FileSendProcessor");
         }
      }
      
      @Override
      public void run() {
    	  SendFileOrder order = null;
    	  
          while (!terminate) {
          try {
        	 // adopt connection setting for thread priority
	         lastOrder = order;
	         
	         // peek from send-file-order queue
	         synchronized (fileSendQueue) {
	        	 order = fileSendQueue.peek();
	         }

	         // stop running if no order in queue
	         if (order == null) {
	        	 terminate();
	        	 break;
	         }
	         
	         if (debug && lastOrder != order) {
	        	 prot("--  (FileSendProcessor) aquired another send-order: " + order.fileID 
	        			 + ", remote = " + order.remotePath + ", " + getRemoteAddress());
	         }
	         
	         // open source input-stream if required
	         if (order.fileIn == null) {
	        	 order.startSending();
	         }
	        	 
	         // prepare order sending data
	         byte[] buffer = new byte[order.parcelBufferSize];
	         int parcelNr = order.parcelsSent;
	         long fileID = order.fileID;
	
	         // inform user about commencing the transmission (event dispatch)
	         if (parcelNr == 0) {
		         TransmissionEventType type = TransmissionEventType.FILE_SENDING; 
		         TransmissionEventImpl event = new TransmissionEventImpl(
		                 ConnectionImpl.this, type, ComDirection.OUTGOING, order.priority, order.fileID);
		         event.setPath(order.remotePath);
		         event.setFile(order.file);
		         event.setExpectedLength(order.fileLength);
		         event.setTransaction(order.transaction);
		         fireTransmissionEvent(event);
	         }
	         
	         // post up to three parcels
	         for (int loop = 0; loop < 3 & !terminate; loop++) {
	        	// order may have been closed externally
	        	if (!order.ongoing) break; 
	        	
	           // read from file (blocking) 
	           int readLen = order.fileIn.read(buffer);
	           if (parcelNr > 0 & readLen == -1) {
	        	   // finish operation (file-sent)
	               if (debug) {
	               	 prot("--- (FileSendProcessor) all parcels queued for send-order " 
	               			 	+ order.fileID + ", remote = " + order.remotePath);
	               }
	        	   order.closeFile();
	               break;
	           }
	           
	           // construct next parcel
	           TransmissionParcel parcel = new TransmissionParcel(
	        		 ConnectionImpl.this,
	                 order.fileID, parcelNr, buffer, 0, Math.max(readLen, 0));
	           parcel.setChannel(TransmissionChannel.FILE);
	           parcel.setPriority(order.priority);
	           if (debug) {
	        	   prot("--- created FILE PARCEL: file-ID " + fileID + ", ser " + parcelNr);
	           }
	           
	           // construct an object header in parcel number 0
	           if (parcelNr == 0) {
	              ObjectHeader header = parcel.getObjectHeader();
	              header.setTransmissionSize(order.fileLength);
	              header.setPath(order.remotePath);
	              header.setNrOfParcels(order.nrOfParcels);
	              header.setPriority(order.priority);
	              header.setCrc32(Util.CRC32_of(order.file));
	           }
	
	           // testing function: failure on parcel-nr
	           else if (parcelNr == getProcessingTestError(ComDirection.OUTGOING)) {
	         	  throw new IOException("TESTING IO-Error (file-sending)"); 
	           }
	           
	           parcelNr++;
	           boolean isLastParcel = parcelNr == order.nrOfParcels;
	           
	           // add a timer-task for TRANSFER CONFIRM or abortion on last parcel
	           // this will be scheduled after the parcel is sent on the socket
	           if (isLastParcel) {
	        	  int nrOfGiga = (int) (order.transmittedLength / JennyNet.GIGA);
	              AbortFileTimeoutTask timeoutTask = new AbortFileTimeoutTask(
	                  fileID, parameters.getConfirmTimeout() + nrOfGiga * 15000);
	              parcel.setTimerTask(timeoutTask);
	           }
	
	           // queue file parcel for sending (blocking)
	           queueParcelForSending(parcel);
	           order.transmittedLength += parcel.getLength();
	           order.parcelsSent++;
	           
	           // during SHUTDOWN state we delay the last send-order until reception of CONFIRM
	       	   if (isLastParcel && operationState == ConnectionState.SHUTDOWN 
	       		   && fileSendQueue.size() == 1) {
				  order.waitForTermination();
	       	   }
	         } // for
	         
         } catch (InterruptedException e) {
         } catch (Exception e) {
        	 e.printStackTrace();
        	 if (order != null) {
        		order.breakTransfer(111, 2, e);
        	 }
         } 
         } // while

   	     if (inputQueue.isEmpty()) {
   	   	     lastSendScheduleTime = 0;
   	     }

         // SHUTDOWN state controls, conditional send signal to remote
         // and migration to CLOSED
   		 filesAllSent = true;
     	 if (operationState == ConnectionState.SHUTDOWN) {
     		sendAllSentSignal();
    	 }
      
      } // run

      /** Terminates this file transmission without stating a cause.
       */
      public void terminate () {
    	  if (!terminate) {
	    	  terminate = true;
	    	  sendFileProcessor = null;
	    	  interrupt();
	          if (debug) {
	         	 prot("--- (terminate) stop running FileSendProcessor");
	          }
    	  }
      }

      @Override
      protected void finalize() throws Throwable {
    	  terminate();
    	  super.finalize();
      }
   }
   
   
   /** CoreSend is a global queue for send-parcels for all connections with an
    * adjunct processing thread. The type is <code>PriorityBlockingQueue</code>
    * of <code>TransmissionParcel</code>. The digesting end automatically sends 
    * parcels over the net sockets which belong to the issuing connections
    * and are available as data element in the parcels. The processing occurs 
    * as a daemon thread which runs until CoreSend is terminated. Currently 
    * termination does not take place.
    * <p>If sending of a parcel causes an error, the associated <code>Connection
    * </code> is closed by the processor stating the error. 
    *
    * Currently this terminates if an IO error occurs on the socket.
    */
   static final class CoreSend extends PriorityBlockingQueue<TransmissionParcel> {
	  final LayerCategory category;
      final Thread send;
	  volatile boolean terminate;
      
      public CoreSend (LayerCategory category) {
         super(32);
         Objects.requireNonNull(category);
         this.category = category;
         if (debug) {
        	 System.out.println("-- JENNY-NET STATIC CORESEND INIT (" + category + "), priority = " 
        			 + JennyNet.getSendThreadPriority());
         }
         send = new Thread("JennyNet Static CoreSend") {
        	 
            @Override
            public synchronized void run() {
               ConnectionImpl con = null;
               
               while (true) {
            	  // determine whether thread has to stop 
            	  boolean working = !terminate || !isEmpty();
            	  if (!working) break;
                	  
                  try {
                     // take next parcel from send-queue
                     TransmissionParcel parcel = take();
                     con = parcel.getConnection();
                     TransmissionChannel channel = parcel.getChannel();
                     
                     // ignore parcels of inactive connections
                     // or cancelled file transfers
                     if (!con.isConnected() ||
                    	 (channel == TransmissionChannel.FILE &&
                    	 con.fileSenderMap.get(parcel.getObjectID()) == null)) {
                    	 if (debug) {
                    		 prot(con, "-- (CoreSend) dropped a SENDER parcel, " + parcel.getChannel() + ", obj "  
                    				 + parcel.getObjectID() + ", ser " + parcel.getParcelSequencelNr()
                    				 + ", " + ", tar " + con.getRemoteAddress().getPort());
                    	 }
                    	 if (!parcel.isSignal()) {
                             con.decrementSendLoad(parcel.getSerialisedLength());
                    	 }
                    	 continue;
                     }

                   	 // send parcel over network socket
                   	 writeToSocket(parcel);
                     
                     // schedule a timer-task that may be defined on the parcel
                     SchedulableTimerTask task = parcel.getTimerTask();
                     if (task != null) {
                        task.schedule(timer);
                     }
                     
                  } catch (InterruptedException e) {
             		 interrupted();

                  } catch (Throwable e) {
                	  if (debug) {
                		  e.printStackTrace();
                	  }
                      if (con != null) {
                    	  con.closeTerminal(new ErrorObject(5, e), true);
                      }
                  } 
               }  // while

               // clear input queue when terminating
               clear();
               if (debug) {
            	   System.out.println("-- TERMINATING JENNY-NET STATIC CoreSend Processor");
               }
            } // run
         };
         
         // classify and start the thread
         send.setPriority(JennyNet.getSendThreadPriority());
         send.setDaemon(true);
         send.start();
      } // CoreSend
      
    /** Inserts the specified data parcel into this priority queue. 
    * This method will not block.
    * 
    * @param parcel <code>TransmissionParcel</code>
    * @throws IllegalStateException if this thread has terminated
    * @throws ClosedConnectionException if the parcel's connection has closed
    */
    @Override
	public void put (TransmissionParcel parcel) {
    	if (terminate) 
    		throw new IllegalStateException("send-thread is terminated");
    	
    	// verify parcel associated connection
    	ConnectionImpl con = parcel.getConnection();
    	if (!con.isConnected()) 
    		throw new ClosedConnectionException("disconnected in parcel sending");
    	
		// put parcel into sorting queue
		super.put(parcel);
		
		if (debug) {
			prot(con, "-- (CoreSend) putting PARCEL w/ prio " 
		            + parcel.getPriority().ordinal() + ", " + parcel.getPriority()
		            + ", " + parcel.getChannel() + " (" + parcel.getObjectID() + ", " + parcel.getParcelSequencelNr() 
		            + "), tar " + con.getRemoteAddress().getPort());
			prot(con, "              queue-size = " + size());
		}
	}

    /** Writes a transmission parcel to its associated network socket.
     * This method blocks until all data of the argument has been written
     * to the output stream.
     * 
     * @param parcel {@code TransmissionParcel}
     * @throws IOException
     */
    protected void writeToSocket (TransmissionParcel parcel) throws IOException {
        ConnectionImpl con = parcel.getConnection();
        if (debug) {
           prot(con, "--- (CORE-SEND) writing parcel to socket: " + ", " + parcel.getChannel() 
                + " (" + parcel.getObjectID() + ", " + parcel.getParcelSequencelNr() 
           		+ "), rem " + con.getRemoteAddress());
           if (parcel.getParcelSequencelNr() == 0) {
        	   parcel.report(1, System.out);
           }
        }
        
        // write to TCP socket
        parcel.writeObject(con.socketOutput);
        con.socketOutput.flush();
        con.setLastSendTime();

        // update connection's exchanged volume counter 
        con.transmittedVolume.addAndGet(parcel.getSerialisedLength());
        if (!parcel.isSignal()) {
     	    con.addToExchangedVolume(parcel.getLength());
            con.decrementSendLoad(parcel.getSerialisedLength());
        }
     }
    
      /** Triggers termination of the sending thread. The internal thread
       * stays alive until all queued parcels underwent a send attempt,
       * i.e. the queue becomes empty.
       */
      public void terminate () {
         terminate = true;
         send.interrupt();
      }

      @Override
      protected void finalize() throws Throwable {
         terminate();
         super.finalize();
      }

//      /** Sets the priority of the internal sending thread.
//       * 
//       * @param p int thread priority
//       */
//      public void setThreadPriority (int p) {
//         send.setPriority(p);
//      }
      
      /** Whether this sending instance is capable of sending parcels,
       * i.e. the internal thread is alive.
       * 
       * @return boolean true == sending is alive
       */
      public boolean isAlive () {
    	  return send.isAlive();
      }
   }  // CoreSend
   
   
   /** A BlockingQueue that contains transmission parcels for the OBJECT 
    * CHANNEL, received from the network socket (remote JennyNet layer) and 
    * also works as the core RECEIVE PROCESSOR. 
    * <p>Received SIGNALs are immediately digested here, FILE CHANNEL parcels  
    * are distributed to the corresponding file agglomeration objects, which 
    * are processors themselves. OBJECT parcels are just stored into this queue.
    */
   private class ReceiveProcessor  extends Thread {
      volatile boolean terminated;
      
      public ReceiveProcessor () {
    	  super("ReceiveProcessor ".concat(String.valueOf(getLocalAddress())));
	      setPriority(parameters.getTransmitThreadPriority());
      }
      
      @Override
      public void run() {

    	 while (!terminated) {
            try {
               // read next incoming parcel from remote (blocking)
               TransmissionParcel parcel = readParcelFromSocket();
               if (terminated) break;
        	   lastReceiveTime = System.currentTimeMillis();

               // branch parcel path into SIGNAL, FILE and OBJECT digestion
               switch (parcel.getChannel()) {
               case SIGNAL: 
                  signalReceiveDigestion(parcel);
               break;
               case OBJECT: 
	              // sum up exchanged data for IDLE state control (if opted)
            	  parcelReceiveDigestion (parcel);
               break;
               case FILE: 
                  // sum up exchanged data for IDLE state control (if opted)
                  fileReceiveDigestion(parcel);
               break;
               case FINAL:
            	   if (parcel.getParcelSequencelNr() == 1) {
	            	 // signal ALL-SENT from remote
                  	 remoteAllSent = true;
                  	 if (ctrlShutdownTask != null && ctrlShutdownTask.hasRun) {
                  		 controlEndOfShutdown();
                  	 }
            	   }
               break;
               default: throw new IllegalStateException("SOCKET-RECEIVE: unknown parcel channel");
               }
            
             } catch (InterruptedException e) {
        	   interrupted();
        	   
            } catch (SocketException | EOFException e) {
               if (debug) {
            	   prot("-- (ReceiveProcessor) fatal exception: " + e + ", " + ConnectionImpl.this);
               }
               if (operationState == ConnectionState.CONNECTED) {
                  closeTerminal(new ErrorObject(6, e), false);
               } else if (!remoteAllSent) {
            	  prot("XXX BAD SOCKET EXCEPTION " + ConnectionImpl.this);
            	  e.printStackTrace();
            	  ErrorObject error = new ErrorObject(4, e); 
                  closeTerminal(error, false);
               }
               break;
               
            } catch (Throwable e) {
               if (debug) {
            	   prot("-- (ReceiveProcessor) Throwable: " + e + ", " + ConnectionImpl.this);
               }
               e.printStackTrace();

               if (operationState == ConnectionState.CONNECTED) {
                  close(e, 6);
               } else {
            	  closeTerminal(new ErrorObject(5, e), false);
            	  break;
               }
            }
         } // while
         
         // clear any remaining in object reception-map
         objectReceptorMap.clear();
	     if (debug) {
	        prot("-- ReceiveProcessor terminated, rem "  + getRemoteAddress());
	     }
      } // run
      
      private void fileReceiveDigestion (TransmissionParcel parcel) throws InterruptedException {
         
         // try find the specific receptor queue to take the parcel
         long fileID = parcel.getObjectID();
         FileAgglomeration fileQueue = fileReceptorMap.get(fileID);

         // create and memorise a new file receptor if none exists
         if (fileQueue == null) {
            
            // drop parcels of aborted  objects 
        	// we expect serial 0 for initial parcel
            if (parcel.getParcelSequencelNr() > 0) {
            	if (debug) {
            		prot("-- (ReceiveProcessor) FILE RECEIVE dropping out-of-sync parcel (ID=" + 
            				parcel.getObjectID() + ", serial=" + 
            				parcel.getParcelSequencelNr() + ") rem= " +
            				parcel.getConnection().getRemoteAddress().getPort());
            	}
                return;
            }
            
            // insert new receptor into receptor map 
            // happens only with header != null
            try {
               fileQueue = new FileAgglomeration(ConnectionImpl.this, fileID); 
               fileReceptorMap.put(fileID, fileQueue);
            } catch (Exception e) {
               // RETURN SIGNAL: incoming file cannot be received (some error)
               sendSignal(Signal.newBreakSignal(ConnectionImpl.this, fileID, 1, e.toString()));
               return;
            }
         }
         
         // just put the parcel in queue, they do the rest!
         fileQueue.put(parcel);
         if (debug) {
        	 prot("--$ (FileAgglomeration) queue-size = " + fileQueue.size());
         }
      }

      private void parcelReceiveDigestion (TransmissionParcel parcel) throws SerialisationException {
          long objectNr = parcel.getObjectID();
          int sequenceNr = parcel.getParcelSequencelNr();

          // look for the relevant parcel agglomeration from registry
          ObjectAgglomeration agglom = objectReceptorMap.get(objectNr);                 
          boolean isNewObject = agglom == null;
     	  String fromStr = ", rem " + getRemoteAddress();
          
          // if agglomeration not found, create a new one
          if (isNewObject) {
        	 // control for lost object parcels
        	  if (sequenceNr != 0) {
                  if (debug) {
                 	 prot("--- (ReceiveProcessor) dropping orphan parcel for unknown OBJECT: " 
                 			 + objectNr + ", " + sequenceNr + fromStr);
                  }
                  return;
        	  }
        	 
        	 // create a new object-agglomeration (parcel reception tool)
             agglom = new ObjectAgglomeration(ConnectionImpl.this, objectNr, parcel.getPriority());
          }
          
          try {
	          // let agglomeration digest received parcel
	          agglom.digestParcel(parcel);
	          
	          // if object is finished
	          if (agglom.objectReady()) {
	        	 // create user object from agglomeration
	        	 UserObject object = new UserObject(agglom.getObject(), objectNr, agglom.getPriority());
	        	 
	        	 // put user object into output queue 
	        	 receiveObjectCounter++;
	             putObjectToReceiveQueue(object, true);
	             if (debug) {
	            	 prot("--- (ReceiveProcessor) received OBJECT handed to output queue: " + objectNr + fromStr);
	             }
	
	             // remove finished agglomeration from registry
	             if (!isNewObject) {
	                objectReceptorMap.remove(objectNr);
	                if (debug) {
	                	prot("--- (ReceiveProcessor) OBJECT agglomeration de-registered: " + objectNr + fromStr);
	                }
	             }
	          }
	          
	          // insert new agglomeration into registry
	          else if (isNewObject) {
	             objectReceptorMap.put(objectNr, agglom);
	             if (debug) {
	            	 prot("--- (ReceiveProcessor) NEW OBJECT agglomeration registered: " + objectNr + fromStr);
	             }
	          }
	          
          } catch (SerialisationUnavailableException e) {
         	  // no reception serialisation defined
              if (debug) {
               	 prot("--- (ReceiveProcessor) dropping OBJECT parcel, no receive-serialisation for method " 
               			 + agglom.getSerialMethod() + ", (" + objectNr + ", " + sequenceNr + ") " + fromStr);
              }
           	  // reaction to NO-RECEPTION-DEFINED: send FAIL 6 signal to remote 
           	  sendSignal(Signal.newFailSignal(ConnectionImpl.this, objectNr, 6, "reception undefined"));
        	  
          } catch (SerialisationException e) {
        	  // reaction to deserialisation error : send FAIL 5 signal to remote 
        	  sendSignal(Signal.newFailSignal(ConnectionImpl.this, objectNr, 5, e.toString()));
          }
	 } // parcelReceiveDigestion

	private void signalReceiveDigestion (TransmissionParcel parcel) {
         
         // identify signal (analyse parcel)
         Signal signal = new Signal(parcel);
         SignalType type = signal.getSigType();
         int info = signal.getInfo();
         String msg = signal.getText();
         long objectID = parcel.getObjectID();
     	 if (debug) {
     		 prot("-- SIGNAL REC (ob " + objectID + "): " + type +
     				 " (i " + signal.getInfo() + ") from " + getRemoteAddress() + ", [" + getLocalAddress() + "]");
     	 }
         
         switch (type) {
         case ALIVE_REQUEST:
        	setAlivePeriod(info);
         break;
         
         case ALIVE_CONFIRM:
        	 // 'info' contains the ALIVE period which remote has scheduled
        	 // on zero an existing control task is removed
        	 int period = info/2; 
        	 int delta = Math.min(info*3/2 - period, 2*JennyNet.MINUTE); 
        	 AliveReceptionControlTask.createNew(ConnectionImpl.this, period, period+delta);
         break;
         
         case ALIVE:
        	 if (aliveTimeoutTask != null) {
        		 aliveTimeoutTask.pushConfirmedTime();
        	 }
         break;
         
         case PING:
            sendSignal(Signal.newEchoSignal(ConnectionImpl.this, objectID));
         break;
         
         case ECHO:
            try {
               // create and store a PING-ECHO instance 
               // by removing the stored ping-sent time information
               long timeSent = pingSentMap.remove(objectID);
               PingEcho pingEcho = PingEchoImpl.create(ConnectionImpl.this, objectID, 
                     timeSent, (int)(System.currentTimeMillis() - timeSent));
               lastPingValue = pingEcho.duration();
               DeliveryObject object = new DeliveryObject(pingEcho, pingEcho.pingId(), SendPriority.HIGH);
               putObjectToReceiveQueue(object, true);
               
            } catch (Exception e) {
               e.printStackTrace();
            }
         break;
         
         case BREAK:
            // whether a file receptor is concerned ..
        	boolean isIncomingFile = info == 6 || info == 4 || info == 2;
        	
        	// if incoming file is concerned ..
        	if (isIncomingFile) {
        		// drop transfer on the file-agglomeration
                if (debug) {
             	   prot("-- (signal digestion) dropping INCOMING FILE TRANSFER (BREAK) " + objectID);
                }
	            FileAgglomeration fileAgglom = fileReceptorMap.get(objectID);
	            if (fileAgglom != null) {
	               int eventInfo = info == 2 ? 112 : info == 4 ? 106 : 116;
	               fileAgglom.dropTransfer(eventInfo, 0, 
	                     new RemoteTransferBreakException(msg));
	            }
        	}
        	
            // if outgoing file is concerned ..
            else {
        	   // drop transfer on the send-file-processor
               if (debug) {
            	   prot("-- (signal digestion) dropping OUTGOING FILE TRANSFER (BREAK) " + objectID);
               }
               SendFileOrder fileSender = fileSenderMap.get(objectID);
               if (fileSender != null) {
                  int eventInfo = info == 1 ? 101 : info == 3 ? 107 : 115;
                  fileSender.breakTransfer(eventInfo, 0, 
                      new RemoteTransferBreakException(msg));
               } else {
            	  prot("   ERROR: send-file-processor not found!");
               }
            }
         break;
         
         case CONFIRM:
            // finish a file sender (OK)
        	SendFileOrder fileSender = fileSenderMap.get(objectID);
            if (fileSender != null) {
               fileSender.finishTransfer(0, null);
            }
         break;
         
         case FAIL:
        	switch (info) {
        	case 1:
        	case 3:
               // finish a file sender (Failure)
               fileSender = fileSenderMap.get(objectID);
               if (fileSender != null) {
            	  Exception e = msg == null ? null : new RemoteTransferBreakException(msg);
                  fileSender.finishTransfer(info, e);
               }
               break;
               
        	case 2:
               // finish a file receptor (Failure)
               FileAgglomeration fileQueue = fileReceptorMap.get(objectID);
               if (fileQueue != null) {
            	   fileQueue.dropTransfer(104, 0, null);
               }
               break;
               
        	case 4:
        	   // finish an object reception
        		if (objectReceptorMap.remove(objectID) != null && debug) {
        			prot("-- FAIL signal: removed OBJECT reception, ID = " + objectID + ", rem " + getRemoteAddress());
        		}
        		break;
        		
        	case 5:
        	case 6:
   		     	// issue REMOTE-ABORTED event to user
		   		fireObjectAbortedEvent(null, objectID, info == 5 ? 207 : 209, msg);
        	}
         break;
         
         case SHUTDOWN:
        	 if (operationState == ConnectionState.CONNECTED) {
        		 int i = info == 1 ? 3 : 2;
        		 String defaultText = info == 1 ? "remote server shutdown" : "remote connection shutdown";
        		 String text = msg == null ? defaultText : "(remote " + info + ") " + msg;
        		 closeShutdown(new RemoteShutdownMsg(i, text));
        	 }
         break;
         
         case CLOSED:
        	 if (isConnected()) {
        		 int i = info == 1 ? 3 : 2;
        		 String defaultText = info == 1 ? "remote server shutdown" : "remote connection closure";
        		 String text = msg == null ? defaultText : "(remote " + info + ") " + msg;
        		 closeTerminal(new ErrorObject(i, text), false);
        	 }
       	 break;
       	 
         case TEMPO:
        	if (getTransmissionSpeed() != info) {
	        	if (!fixedTransmissionSpeed) {
	        		// set the new local transmission speed after remote
	        		transmitSpeed = info;
	        		if (inputProcessor != null) {
	      	  			inputProcessor.setSending(info != 0);
	        		}
	            	if (debug) {
	      	  			prot("-- TEMPO SIGNAL received, new TRANSMIT BAUD is " + info);
	            	}
	        	} else {
	        		// ignore remote TEMPO setting due to local priority setting
	        		// reset remote speed
	            	if (debug) {
	      	  			prot("-- TEMPO SIGNAL received with Baud " + info + " --> ignored!");
	            	}
	      	  		setTempo(getTransmissionSpeed());
	        	}
        	}
         break;
         }
      } // signalReceiveDigestion

	   private TransmissionParcel readParcelFromSocket () throws IOException {
	       TransmissionParcel parcel = TransmissionParcel.readParcel(ConnectionImpl.this, socketInput);
	       if (!parcel.isSignal()) {
	    	   addToExchangedVolume(parcel.getLength());
	       }
	       transmittedVolume.addAndGet(parcel.getSerialisedLength());

	       if (debug) {
	           prot("--- (ReceiveProcessor) reading parcel from socket: " + ", " + parcel.getChannel() 
	           + " (" + parcel.getObjectID() + ", " + parcel.getParcelSequencelNr() 
	      		+ "), rem " + getRemoteAddress());
	           if (parcel.getParcelSequencelNr() == 0) {
	        	   parcel.report(0, System.out);
	           }
	       }
		   return parcel;
	   }
	   
      public void terminate () {
         if (debug) {
        	 String hstr = isAlive() ? "terminate called" : " already DEAD";
        	 prot("--- (ReceiveProcessor) " + hstr + ", rem " + getRemoteAddress());
         }
         terminated = true;
         interrupt();
      }
   } // ReceiveProcessor

   /** One-time task to control the termination of a file-send-order.
    * If at the time this task is running the file-sender-map contains the
    * given file-ID, then the corresponding file-send-order is aborted
    * with a timeout event. 
    */
   private class AbortFileTimeoutTask extends SchedulableTimerTask {
      private long fileId;
      private int out_time;
      
      /** A new file transfer time-control task.
       * 
       * @param fileId long transfer identifier
       * @param time int delay in milliseconds
       */
      public AbortFileTimeoutTask (long fileId, int time) {
         super(time, "AbortFileTimeoutTask, file=" + fileId);
         this.fileId = fileId;
         out_time = time / 1000;
      }
      
      @Override
      public void run() {
     	 // terminate if socket is closed
     	 if (!connected) return;
     	  
         // find outgoing file transmission and timeout
         SendFileOrder fileSender = fileSenderMap.get(fileId);
         if (fileSender != null) {
         	if (debug) {
               prot("CON-TIMER: Cancelling file transfer on missing CONFIRM: " 
                       + getRemoteAddress() + " FILE = " + fileId);
         	}

            fileSender.timeoutTransfer(out_time);
         }
      }
   }
   
   /** Parcel-bound task to control "end-of-shutdown" (termination of
    * operation state SHUTDOWN in favour of CLOSED) of this connection after 
    * the corresponding parcel has being sent to remote.
    */
   private class ControlEndOfShutdownTask extends SchedulableTimerTask {
	   boolean hasRun;
	   
	   /** Creates a new task.
	    */
	   public ControlEndOfShutdownTask () {
		   super(0, "Control-End-Of-Shutdown-Task");
	   }

	   @Override
	   public void run() {
		   if (debug) {
		      prot("-- running Control-End-Of-Shutdown-Task, tar " + getRemoteAddress());
		   }
	 	   Util.sleep(100);
		   controlEndOfShutdown();
		   hasRun = true;
	   }
   }
   
   /** The idle-timer-task runs periodically to investigate the value of the
    * IDLE state in this connection. The IDLE state 'IDLE' can be entered and
    * left depending on the volume of data exchanged (incoming or outgoing)
    * with the remote station since the last control.
    * This task issues a connection event whenever it finds that the IDLE state
    * has changed. The check period and the change threshold of volume can be 
    * set in connection parameters.
    */
   private class CheckIdleTimerTask extends TimerTask {
      private long volumeMarker;
      private long lastCheckTime;
      private final int period;
      
      /** Creates and schedules on <i>timer</i> a new idle-check-task.
       * 
       * @param period int milliseconds of check-period
       */
      public CheckIdleTimerTask (int period) {
         this.volumeMarker = exchangedDataVolume.get();
         this.period = Math.max(5000, period);
         lastCheckTime = System.currentTimeMillis();
         
         // log output
     	 if (debug) {
     		 prot("-- created new CHECK-IDLE-TIMER-TASK, period = "
     			 + period + ", marker = " + System.currentTimeMillis()
     			 + ", threshold = " + getThreshold()
     			 + ", rem " + getRemoteAddress());
     	 }

         // schedule the new task
         timer.schedule(this, period, period);
      }
      
      /** Creates and schedules on <i>timer</i> a new idle-check-task
       * with the give initial values for last-check-time and marked 
       * data volume.
       * 
       * @param period int milliseconds of check-period
       * @param checkTime long milliseconds
       * @param volume long data volume in bytes
       */
      public CheckIdleTimerTask (int period, long checkTime, long volume) {
    	  this(period);
    	  this.lastCheckTime = checkTime;
    	  this.volumeMarker = volume;
      }
      
      
      /** The check period in milliseconds.
       * 
       * @return int milliseconds
       */
      public int getPeriod () {return period;}
      
      public int getThreshold () {
    	  return getParameters().getIdleThreshold();
      }
      
      @Override
      public boolean cancel () {
         boolean ok = super.cancel();
         
     	 if (debug) {
            prot("--- canceled CHECK-IDLE-TIMER-TASK, marker = " 
                  + System.currentTimeMillis() + ", rem " + getRemoteAddress());
     	 }
         return ok;
      }

      /** Sets the connection's IDLE state and issues a connection event
       * in case the state has changed.
       * 
       * @param state boolean new IDLE state, true = IDLE, false = BUSY
       * @param exchangeRate int data exchange rate in bytes per minute
       */
      private synchronized void setIdleState (boolean state, int exchangeRate) {
    	  if (state != isIdle) {
              isIdle = state;
              String text = "new state " + (state ? "IDLE" : "BUSY");
              ErrorObject infoObj = new ErrorObject(Math.max(-1, exchangeRate), text);
              fireConnectionEvent(ConnectionEventType.IDLE, infoObj);
    	  }
      }

      @Override
      public void run () {
          // determine time spent in seconds since last investigation
          // we assume a minimum of 1
          long now = System.currentTimeMillis();
          long deltaMillis = now - lastCheckTime;
          long timeDelta = Math.max(1, deltaMillis / 1000);
          lastCheckTime = now;
          
         // determine data exchanged since last investigation (delta)
    	 long volume = exchangedDataVolume.get();
         long delta =  Math.max(0, volume - volumeMarker);
         volumeMarker = volume;
         if (debug) {
        	 prot("(CheckIdleTimerTask)     IDLE-CHECK: time-delta = " + timeDelta + ", volume-delta = " + delta);
         }
         
         if (isCheckIdleState) {
            // calculate the exchange in bytes-per-minute for the current check period
        	long exchange = delta * 60 / timeDelta;
            long threshold = getThreshold();
            if (debug) {
           	    prot("(CheckIdleTimerTask)     IDLE-CHECK: exchange-rate (baud) = " + exchange + ", threshold = " + threshold);
            }
   
            // set IDLE state, fire connection event if state has changed
            setIdleState(exchange < threshold, (int)exchange);
         }
      }
   }
   
   /** Periodic Timer Task to send ALIVE signals to the remote station.
     * The task cancels itself if the connection is no longer connected.
    * 
    */
   private static class AliveSignalTimerTask extends TimerTask {
      private ConnectionImpl connection;
      private int period;
      private long sendTime;
      
      /** Creates a new ALIVE SIGNAL timer task for periodic signalling
       * to the remote station of the given connection. A previously 
       * created task is cancelled. 
       * 
       * <p><small>If <code>period</code> is zero, a previous
       * task is cancelled but no new one is created.</small>
       * 
       * @param con Connection
       * @param period int ALIVE sending interval in milliseconds
       * @throws IllegalArgumentException if period is negative
       */
      public static void createNew (ConnectionImpl con, int period) {
    	 if (con.aliveSignalTask != null) {
    		 con.aliveSignalTask.cancel();
    	 }

    	 if (period > 0) {
             // create a new timer task
             con.aliveSignalTask = new AliveSignalTimerTask(con, period);
         } else {
        	 con.aliveSignalTask = null;
         }
      }
      
      /** A new timer task for periodic ALIVE signalling to remote
       * or controlling ALIVE timeout from remote (depending on execution
       * modus). Cancels a previously scheduled task of the same type.
       *  
       * @param con Connection
       * @param period int period in milliseconds (must be > 0)
       * @throws IllegalArgumentException if period is 0 or negative          
       */
      private AliveSignalTimerTask (ConnectionImpl con, int period) {
    	 Objects.requireNonNull(con);
         if (period <= 0) 
            throw new IllegalArgumentException("period <= 0");
         this.connection = con;
         this.period = period;

     	 if (debug) {
     		connection.prot("CREATE ALIVE-SEND-TIMER-TASK on : " + con + ", period=" + period);
     		connection.prot("             time marker = " + System.currentTimeMillis());
     	 }

         // schedule this task
         timer.schedule(this, period, period);
      }
      
    @Override
	public boolean cancel() {
       if (debug) {
     	 	connection.prot("-- cancelling ALIVE SENDING task");
       }
	   return super.cancel();
	}

      @Override
      public void run() {
    	 int delta = 0;
    	 try {
	    	 // cancel task if connection stopped
	         if (!connection.isConnected()) {
	        	cancel();
	            return;
	         }
	         
	         // send ALIVE signal and mark send-time
	    	 connection.sendSignal(Signal.newAliveSignal(connection));
	    	 if (debug) {
	    		delta = sendTime == 0 ? 0 : (int)(System.currentTimeMillis() - sendTime)/1000;
	    	 }
	    	 sendTime = System.currentTimeMillis();
	     	 if (debug) {
	     		connection.prot("-- (AliveSignalTimerTask) ALIVE sent to : " + connection.getRemoteAddress() + 
	     				 ", delta = " + delta + " sec");
	     	 }
    	 } catch (Throwable e) {
    		 e.printStackTrace();
    	 }
      }
   }

   /** Class to contain the local connection parameters and administer
    * local services on parameter value change.
    */
   private class OurParameters extends ConnectionParametersImpl {
      private String rejectMsg = "parameter may not change in operating connection";

      /** Creates a new OurParameters from the global set of JennyNet parameters.
       */
      OurParameters () {
    	  try {
			 takeOver(JennyNet.getConnectionParameters());
		  } catch (IOException e) {
			 e.printStackTrace();
		  }
      }
      
      @Override
      public boolean setSerialisationMethod (int method) {
    	  boolean changed = super.setSerialisationMethod(method);
    	  if (changed) {
    		  try {
    			  sendSerialisation = getSendSerialization(method);
    			  receiveSerialisation = getReceiveSerialization(method);
    			  prot("-- setting default SERIALISATION to method " + method);
    		  } catch (SerialisationUnavailableException e) {
    			  sendSerialisation = null;
    			  receiveSerialisation = null;
    			  prot("** FAILED to set default SERIALISATION to method " + method + ", now default is null");
    		  }
    	  }
    	  return changed;
      }

	/** Copies all values from the given parameter set to this
       * parameter set. This includes side effects to the enclosing 
       * connection.
       * 
       * @param p {@code ConnectionParameters}
       * @throws IllegalStateException if the connection is operative
       * @throws IOException
       */
      protected void takeOver (ConnectionParameters p) throws IOException {
    	 Objects.requireNonNull(p);
         if (getOperationState() != ConnectionState.UNCONNECTED) 
            throw new IllegalStateException(rejectMsg);

         setAlivePeriod(p.getAlivePeriod());
         setBaseThreadPriority(p.getBaseThreadPriority());
         setTransmitThreadPriority(p.getTransmitThreadPriority());
         setConfirmTimeout(p.getConfirmTimeout());
         setDeliveryThreadUsage(p.getDeliveryThreadUsage());
         setDeliverTolerance(p.getDeliverTolerance());
         setFileRootDir(p.getFileRootDir());
         setIdleCheckPeriod(p.getIdleCheckPeriod());
         setIdleThreshold(p.getIdleThreshold());
         setMaxSerialisationSize(p.getMaxSerialisationSize());
         setObjectQueueCapacity(p.getObjectQueueCapacity());
         setParcelQueueCapacity(p.getParcelQueueCapacity());
         setSerialisationMethod(p.getSerialisationMethod());
         setTransmissionParcelSize(p.getTransmissionParcelSize());
         setTransmissionSpeed(p.getTransmissionSpeed());
      }
      
      @Override
      public int setBaseThreadPriority (int p) {
         p = super.setBaseThreadPriority(p);
         
         // update our processors affected
         if (inputProcessor != null)  {
            inputProcessor.setPriority(p);
         }
         if (outputProcessor != null && !outputProcessor.isStatic()) {
             outputProcessor.setThreadPriority(Math.min(Thread.MAX_PRIORITY, p + 1));
         }
         SendFileProcessor sfp = sendFileProcessor;
         if (sfp != null) {
        	 sfp.setPriority(Math.max(p-2, Thread.MIN_PRIORITY));
         }
         return p;
      }

      @Override
      public int setTransmitThreadPriority (int p) {
         p = super.setTransmitThreadPriority(p);
         if (receiveProcessor != null) {  
        	 receiveProcessor.setPriority(p);
         }
         return p;
      }

      @Override
	  public void setTransmissionSpeed (int tempo) {
	   	 if (getTransmissionSpeed() == tempo) return;
	     super.setTransmissionSpeed(tempo);
	     if (isConnected()) {
	    	 setTempo(tempo);
	     }
	  }

	  @Override
	  public int setTransmissionParcelSize (int size) {
		  size = super.setTransmissionParcelSize(size);
		  setSendLoadLimit(this);
		  return size;
	  }

      @Override
      public int setAlivePeriod (int period) {
         period = super.setAlivePeriod(period);
         if (isConnected()) {
        	 sendAliveRequest(period);
         }
         return period;
      }

      @Override
      public void setIdleThreshold (int idleThreshold) {
         super.setIdleThreshold(idleThreshold);
         setCheckIdleState(getIdleThreshold() > 0, getIdleCheckPeriod());
      }

      @Override
      public void setIdleCheckPeriod (int period) {
         super.setIdleCheckPeriod(period);
         setCheckIdleState(getIdleThreshold() > 0, getIdleCheckPeriod());
      }

  	  @Override
	  public void setDeliveryThreadUsage (ThreadUsage usage) {
  		 synchronized (this) {
	     super.setDeliveryThreadUsage(usage);
	  	 OutputProcessor oldProcessor = outputProcessor;
	  	 int staticPrio = JennyNet.getOutputThreadPriority();
	  	 int indivPrio = Math.min(Thread.MAX_PRIORITY, getBaseThreadPriority() + 1);

	  		
	     // Client thread
    	 if (layerCat == LayerCategory.CLIENT) {
		     // set to GLOBAL
		     if (usage == ThreadUsage.GLOBAL) {
		  	    // ensure static global output processor existence
		  	    if (staticOutputClient == null || !staticOutputClient.isAlive()) {
		  		   staticOutputClient = new OutputProcessor("GLOBAL OUTPUT (Client)", layerCat, staticPrio, true);
		  	    }
		  		   
		  	    // set this connection's processor to static processor
		  	    outputProcessor = staticOutputClient;
		
		  	    // terminate an orphan output processor
		  	    if (oldProcessor != null && oldProcessor != staticOutputClient) {
		  		   oldProcessor.terminate();
		  	    }
		  		   
		      // set to INDIVIDUAL
		     } else {
		  	     if (outputProcessor == null || !outputProcessor.isAlive() || outputProcessor == staticOutputClient) {
		  		    outputProcessor = new OutputProcessor("SPECIFIC OUTPUT (Client)", layerCat, indivPrio, false);
		  	    }
		  	 }

	     // Server thread
	     } else {
		     // set to GLOBAL
		     if (usage == ThreadUsage.GLOBAL) {
		  	    // ensure static global output processor existence
		  	    if (staticOutputServer == null || !staticOutputServer.isAlive()) {
		  	    	staticOutputServer = new OutputProcessor("GLOBAL OUTPUT (Server)", layerCat, staticPrio, true);
		  	    }
		  		   
		  	    // set this connection's processor to static processor
		  	    outputProcessor = staticOutputServer;
		
		  	    // terminate an orphan output processor
		  	    if (oldProcessor != null && oldProcessor != staticOutputServer) {
		  		   oldProcessor.terminate();
		  	    }
		  		   
		      // set to INDIVIDUAL
		     } else {
		  	     if (outputProcessor == null || !outputProcessor.isAlive() || outputProcessor == staticOutputServer) {
		  		    outputProcessor = new OutputProcessor("SPECIFIC OUTPUT (Server)", layerCat, indivPrio, false);
		  	    }
		  	 }
	     }
  		 }
	  } // setDeliveryThreadUsage
	
      // methods with restricted accessibility (must be unconnected)
      
      @Override
      public void setParcelQueueCapacity (int parcelQueueCapacity) {
         if (isConnected()) 
            throw new IllegalStateException(rejectMsg);
         super.setParcelQueueCapacity(parcelQueueCapacity);
      }

      @Override
      public void setObjectQueueCapacity (int objectQueueCapacity) {
         if (isConnected()) 
            throw new IllegalStateException(rejectMsg);
         super.setObjectQueueCapacity(objectQueueCapacity);
      }
   }
   
   // --------------- inner classes ----------------   
      
      /** Error object to contain an error code and an error message.
       */
      protected static class ErrorObject {
    	  String message;
    	  int info;
    	  
    	  ErrorObject (int info, String message) {
    		  this.info = info;
    		  this.message = message;
    	  }

		public ErrorObject (int info, Throwable ex) {
			this.info = info;
			if (ex != null) {
				message = ex.toString();
			}
		}

		@Override
		public String toString() {
			return "Error (" + info + ", msg=" + message + ")";
		}
      }
      
      private class RemoteShutdownMsg extends ErrorObject {

		RemoteShutdownMsg (int info, String message) {
			super(info, message);
		}
      }
      
      /** A class extending {@code UserObject} which performs object
       * serialisation and prepares transmission parcels. The parcels
       * are returned via a get-next iteration mechanism. The serialisation 
       * is performed lazily.
       */
      private class ObjectSendSeparation  extends UserObject  {
    	  TransmissionParcel[] parcelBundle;
    	  int serialMethod;
    	  int nextParcel;

    	  /** Create a send-separation for the given user object.
    	   * 
    	   * @param userObject Object user object
    	   * @param id long object identifier
    	   * @param method int serialisation method 
    	   * @param priority {@code SendPriority} send-channel
    	   * @throws IllegalArgumentException if method is invalid
    	   */
	  	  public ObjectSendSeparation (Object userObject, long id, int method, SendPriority priority) {
	   		  super(userObject, id, priority);
	   		  if (method < 0 | method >= JennyNet.MAX_SERIAL_DEVICE)
	   			  throw new IllegalArgumentException("illegal method number : " + method);
	   		  
	   		  serialMethod = method;
	   	  }
	
		/** Returns the {@code TransmissionParcel} which is next to be sent
	   	   * from this object separation or null if no more parcels are 
	   	   * available. This performs object serialisation on the first
	   	   * parcel requested and is used as a tool to iterate all sendable
	   	   * parcels of the object.
	   	   *  
	   	   * @return {@code TransmissionParcel}
		   * @throws SerialisationException 
	   	   */
	   	  public TransmissionParcel getNextParcel () throws SerialisationException {
	   		  if (parcelBundle == null) {
	   			  separateParcels();
	   		  }
	   		  return nextParcel == parcelBundle.length ? null : parcelBundle[nextParcel++];
	   	  }
	   	   
	   	  /** Divides the incorporated user object into sendable
	   	   *  transmission parcels. This performs object serialisation.
	   	   *
	   	   *  @throws SerialisationUnavailableException if there was no
	   	   *          valid serialisation device for the requested method
	   	   *  @throws SerialisationOversizedException if rendered data block
	   	   *          is too large
	   	   *  @throws SerialisationException if serialisation failed due to 
	   	   *          lack of registration or other error
	   	   */
	   	  private void separateParcels () throws SerialisationException {
	          // serialise the input object --> create a serial data object
	          byte[] serObj;
	          long objectNr = getObjectNr();
	          Serialization ser = obtainSendSerialisation(serialMethod);
//	          try {
//	        	  ser = getSendSerialization();
//	          } catch (SerialisationUnavailableException e) {
//	          } finally {
//	        	  if (ser == null || ser.getMethodID() != serialMethod) {
//	        		  ser = getSendSerialization(serialMethod);
//	        	  }
//	          }
	          
       	   	  serObj = ser.serialiseObject(getObject());
//	          } catch (Exception e) {
//	        	  throw new IllegalStateException("send serialisation error (" +
//	        		   getLocalAddress() + ") object-id " + objectNr, e);
//	          }
	           
	          // guard against serialisation size overflow (parameter setting)
	          if (serObj.length > parameters.getMaxSerialisationSize()) {
	        	  throw new SerialisationOversizedException("send serialisation overflow for object " + 
	        		   objectNr + ", size " + serObj.length);
	          }
	           
	          // split object serialisation into send parcels
	          parcelBundle = TransmissionParcel.createParcelArray(
	           		   ConnectionImpl.this, serObj, objectNr, serialMethod, getPriority(),
	                   parameters.getTransmissionParcelSize());
	   	  }

		 /** Terminates the transmission of this object-separation and issues 
		  * an ABORTED event to the local user
		  * stating the given event-info an optional message about the cause.
		  * Sends a a BREAK signal w/ 'signalInfo' value to remote station if 
		  * and only if 'signalInfo' is not zero.
		  *
		  * @param eventInfo int
		  * @param error {@code ErrorObject} cause information 
		  */
		  public void breakTransfer (int eventInfo, int signal, ErrorObject error) {
			 long objectID = getObjectNr();
		     String text = error == null ? null : error.message;
		     if (debug) { 
		   	     prot("--- (ObjectSendSeparation) dropping outgoing OBJECT transmission, ID " + objectID 
		   	    	  + ", event = " + eventInfo);
		     }
		
		     // remove separation from queue
		     if (!inputQueue.remove(this)) return;
		     
		     // send a signal to remote if opted
		     if (signal > 0) {
			     sendSignal(Signal.newFailSignal(ConnectionImpl.this, objectID, signal, text));
			     if (debug) {
				     prot("    signal to remote: FAIL " + signal + ", OBJECT-ABORTED event " + eventInfo);
			     }
		     }
		     
		     // issue ABORTED event to user
		     if (eventInfo != 0) {
		         // inform the user (remote abortion event)
		   		 fireObjectAbortedEvent(getObject(), getObjectNr(), eventInfo, text);
		     }
		  }
      }
      
    private class DeliveryObject implements Comparable<DeliveryObject> {
    	private final SendPriority priority;
    	private final long objectID;
    	private final ConnectionEvent event;
    	private long deliverNr;
    	
    	/** Constructor for a user object to be delivered with all required 
    	 * settings. This can also be used for delivery of {@code PingEcho}.
    	 * 
    	 * @param object Object user object
    	 * @param objectId long object identifier (> 0)
    	 * @param priority {@code SendPriority}
    	 */
    	public DeliveryObject (Object object, long objectId, SendPriority priority) {
   			Objects.requireNonNull(object, "object is null");
   			Objects.requireNonNull(priority, "priority is null");
       	  	if (objectId <= 0) 
       	  		throw new IllegalArgumentException("illegal object number (possibly integer overflow): " + objectId);

    		 objectID = objectId;
    		 deliverNr = objectId;
    		 ConnectionEventType type = object instanceof PingEcho ? ConnectionEventType.PING_ECHO : ConnectionEventType.OBJECT;
    		 this.event = new ConnectionEvent(ConnectionImpl.this, priority, type, object, objectId, 0, null);
    		 this.priority = priority;
    	}

    	/** Constructor for a connection event to be delivered with a 
    	 * priority.
    	 * 
    	 * @param event {@code ConnectionEvent} event to deliver
    	 * @param priority {@code SendPriority} deliver priority
    	 */
    	public DeliveryObject (ConnectionEvent event, SendPriority priority) {
   			Objects.requireNonNull(event, "event is null");
   			Objects.requireNonNull(priority, "priority is null");

    		 this.event = event;
    		 this.priority = priority;
    		 objectID = event.getObjectNr();
    		 deliverNr = event.getObjectNr();
    	}

		/** We compare natural for ordering a <code>PriorityQueue</code>,
		 * i.e. the lower element is the priorised element. 
		 */
		@Override
		public int compareTo (DeliveryObject obj) {
			// compare priority class
			if (priority.ordinal() < obj.priority.ordinal()) return  1;
			if (priority.ordinal() > obj.priority.ordinal()) return -1;

			// if undecided, compare delivery sequence number
			return deliverNr < obj.deliverNr ? -1 : 
				deliverNr > obj.deliverNr ?  1 : 0;
		}

  	    ConnectionEvent getConnectionEvent () {return event;}
  	    
  	    Object getObject () {
  	    	return event == null ? null : event.getObject();
  	    }

	    long getObjectNr () {return objectID;}

	    ConnectionImpl getConnection () {return ConnectionImpl.this;}
	    
	    SendPriority getPriority () {return priority;}

		public void setDeliverNr (long deliverNr) {
			this.deliverNr = deliverNr;
		}
		
    } // DeliveryObject
      
    /** A class representing a user-object which is made comparable according
      * to our send priority queue requirements. The object is assigned a
      * serial object number.
      */
    private class UserObject extends DeliveryObject  {
    	  
    	/** Constructor for any user object with all available settings.
    	 * 
    	 * @param con {@code ConnectionImpl}
    	 * @param object Object
    	 * @param id long object-ID
    	 * @param priority {@code SendPriority}
    	 */
    	public UserObject (Object object, long id, SendPriority priority) {
    		super(object, id, priority);
    	}

    	/** Constructor for a PingEcho object. Priority is 'High'.
    	 * 
    	 * @param con {@code ConnectionImpl}
    	 * @param echo {@code PingEcho}
    	 */
    	public UserObject (PingEcho echo) {
    		super(echo, echo.pingId(), SendPriority.HIGH);
    	}
    }

    /** Periodic task to control the reception of ALIVE signals from remote
     * and issue a regular connection close (error 9) if the missing tolerance
     * time is passed. Period and tolerance time can be set by parameters. 
     * The task cancels itself if the connection is no longer connected.
     */
	static class AliveReceptionControlTask extends TimerTask {
	      private ConnectionImpl connection;
	      private long confirmedTime;
	      private int countSignals;
	      private int tolerance;
	      
	      /** Creates a new ALIVE control timer task for periodic checking
	       * of ALIVE signals received from remote station. A previously 
	       * created task is cancelled. This assigns to a connection member
	       * variable ('aliveTimeoutTask').
	       * 
	       * <p><small>If <code>period</code> is zero, a previous
	       * task is cancelled but no new one is created.</small>
	       * 
	       * @param con Connection
	       * @param period int check period in milliseconds
	       * @param tolerance int period in milliseconds in which ALIVE must
	       *        occur 
	       * @throws IllegalArgumentException if period is negative
	       */
	      public static void createNew (ConnectionImpl con, int period, int tolerance) {
	    	 int oldSignals = 0;
	     	 if (con.aliveTimeoutTask != null) {
	    		 con.aliveTimeoutTask.cancel();
	    		 oldSignals = con.aliveTimeoutTask.countSignals;
	    	 }

	    	 if (period > 0) {
	             // create a new timer task
	             con.aliveTimeoutTask = new AliveReceptionControlTask(con, period, tolerance);
	             con.aliveTimeoutTask.countSignals += oldSignals;
	         } else {
	        	 con.aliveTimeoutTask = null;
	         }
	      }
	      
	      /** A new timer task for periodic ALIVE signal reception checking
	       * from remote (depending on execution
	       * modus). Cancels a previously scheduled task of the same type.
	       *  
	       * @param con Connection
	       * @param period int performance period in milliseconds (must be > 0)
	       * @param tolerance int time to allow signal missing
	       * @throws IllegalArgumentException if period is 0 or negative          
	       */
	      private AliveReceptionControlTask (ConnectionImpl con, int period, int tolerance) {
	    	 Objects.requireNonNull(con, "connection is null");
	         if (period <= 0) 
	            throw new IllegalArgumentException("period <= 0");
	         if (tolerance <= 0) 
		            throw new IllegalArgumentException("tolerance <= 0");
	         this.connection = con;
	         this.tolerance = tolerance;
	         confirmedTime = System.currentTimeMillis();
	
	         // log output
             if (debug) {
            	 connection.prot("CREATE ALIVE-TIMEOUT TASK for : " + con + ", period=" + period + ", tolerance=" + tolerance);
             }
             
	         // schedule the new task
	         timer.schedule(this, period, period);
	      }
	      
	      /** Pushes the current time as actual ALIVE-ECHO confirmed time.
	       */
	      public void pushConfirmedTime () {
	    	  confirmedTime = System.currentTimeMillis();
	    	  countSignals++;
	      }
	      
	      /** The number of ALIVE signals received from remote (which is the
	       * number of control events).
	       * 
	       * @return int
	       */
	      public int getNrSignals () {return countSignals;}
	      
	      @Override
		  public boolean cancel() {
             if (debug) {
           	 	connection.prot("-- cancelling ALIVE RECEPTION control task");
             }
			 return super.cancel();
		  }

		 @Override
	      public void run() {
	    	 try {
		    	 // cancel task if connection stopped
		         if (!connection.isConnected()) {
		        	cancel();
		            return;
		         }
	
		         // close connection if ALIVE signal from remote is missing over tolerance time
		         if (confirmedTime > 0) {
		        	 long delta = System.currentTimeMillis() - confirmedTime;
	                 if (debug) {
	                	 connection.prot("-- CHECKING ALIVE RECEPTION (control task), delta = " + delta/1000 + " sec");
	                 }
		        	 if (delta > tolerance - 200) {
		        		 connection.close(new ConnectionTimeoutException("ALIVE signal timeout"), 9);
		        	 }
		         }
	    	 } catch (Throwable e) {
	    		 e.printStackTrace();
	    	 }
	      }
	   }

}
