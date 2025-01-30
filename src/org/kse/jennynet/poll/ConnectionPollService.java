/*  File: ConnectionPollService.java
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

package org.kse.jennynet.poll;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;

/** This class makes a {@code ConnectionListener} superfluous for applications
 * which prefer polling from a {@code Connection} actively instead of digesting
 * events thrown in an alien thread. The application is reasonably simple:
 * create an instance with a {@code Connection} as argument and this service
 * is ready for polling and questioning. Care has to be taken that the
 * service instance gets closed if events are no further collected.
 * 
 * <p>This service has a limited capacity for buffering events which can be set 
 * by parameter to a fixed value. If this capacity is reached, any new events
 * offered by the connection can result in the blocking of the connection's
 * operations until the queue is relieved through polling.   
 */
public class ConnectionPollService {

	/** The minimum capacity of a connection-poll-service. */
	public static final int MIN_QUEUE_CAPACITY = 100;
	/** The maximum capacity of a connection-poll-service. */
	public static final int MAX_QUEUE_CAPACITY = 1000;
	
	private ArrayBlockingQueue<ConnectionEvent> queue; 
	private Connection connection;
	private ConListener listener = new ConListener();
	private Thread putThread, takeThread; 
	private int capacity;
	private boolean closed;
	
	/** Creates a new poll-service listening to the given connection and
	 * buffering events up to the given fixed capacity. The capacity has a
	 * minimum of MIN_QUEUE_CAPACITY and a maximum of MAX_QUEUE_CAPACITY.
	 * The argument gets automatically corrected. Argument of zero invokes
	 * the value from the connection's parameter "object-queue-capacity".
	 *  
	 * @param connection {@code Connection}
	 * @param capacity int queue capacity
	 */
	public ConnectionPollService (Connection connection, int capacity) {
		Objects.requireNonNull(connection);
		if (capacity <= 0) {
			capacity = connection.getParameters().getObjectQueueCapacity();
		}
		
		// correct capacity value to limits
		capacity = Math.max(MIN_QUEUE_CAPACITY, Math.min(MAX_QUEUE_CAPACITY, capacity));
		
		queue  = new ArrayBlockingQueue<ConnectionEvent>(capacity, true);
		this.connection = connection;
		this.capacity = capacity;
		connection.addListener(listener);
	}

	/** Creates a new poll-service listening to the given connection and
	 * buffering events up to the capacity which is defined in connection
	 * parameter "object-queue-capacity". This argument is corrected to bound 
	 * within MIN_QUEUE_CAPACITY .. MAX_QUEUE_CAPACITY.
	 *  
	 * @param connection {@code Connection}
	 */
	public ConnectionPollService (Connection connection) {
		this(connection, 0);
	}
	
	/** The connection to which this service is associated.
	 * 
	 * @return {@code Connection}
	 */
	public Connection getConnection() {return connection;}

	private void putToQueue (ConnectionEvent unit) {
		if (closed) return;
		try {
			putThread = Thread.currentThread();
			queue.put(unit);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			putThread = null;
		}
	}
	
	/** Returns the next available connection event if available 
	 * or null after the given amount of time or if the service is closed. 
	 *  
	 * @param timeout long milliseconds wait time
	 * @return <code>ConnectionEvent</code> or null
	 * @throws InterruptedException if the thread is interrupted while waiting 
	 */
	public synchronized ConnectionEvent poll (long timeout) throws InterruptedException {
		if (closed) return null;
		takeThread = Thread.currentThread();
		try {
			ConnectionEvent event = queue.poll(timeout, TimeUnit.MILLISECONDS);
			return event;
		} finally {
			takeThread = null;
		}
	}

	/** Returns the next connection event, waiting if necessary until one 
	 * becomes available, or null if the service is closed  
	 *  
	 * @return <code>ConnectionEvent</code> or null if the service is closed
	 * @throws InterruptedException if the thread is interrupted while waiting 
	 */
	public synchronized ConnectionEvent take () throws InterruptedException {
		if (closed) return null;
		takeThread = Thread.currentThread();
		try {
			ConnectionEvent event = queue.take();
			return event;
		} finally {
			takeThread = null;
		}
	}

	/** Returns the number of events which are waiting to be polled.
	 * 
	 * @return int
	 */
	public int available () {return closed ? 0 : queue.size();}

	/** Returns the storage capacity of this polling service in number of
	 * events.
	 * 
	 * @return int capacity
	 */
	public int getCapacity () {return capacity;}

	/** Returns true iff there are no events waiting to be polled.
	 * 
	 * @return boolean
	 */
	public boolean isEmpty () {return closed ? true : queue.isEmpty();}
	
	/** Whether this service actively listens to the associated connection.
	 * 
	 * @return boolean true = listening, false = not listening
	 */
	public boolean isListening () {return listener != null;}
	
	/** Whether this service is closed.
	 * 
	 * @return boolean true = closed, false = not closed
	 */
	public boolean isClosed () {return closed;}
	
	/** Stops this poll-service's listening for connection events.
	 * After this method is called, queued events are still available for
	 * polling but no new events will be stored.
	 */
	public void stopListening () {
		if (listener != null) {
			connection.removeListener(listener);
			listener = null;
		}
		if (putThread != null) {
			putThread.interrupt();
			putThread = null;
		}
	}
	
	/** Stops this poll-service completely.
	 * After this method is called, no more events can be polled (i.e. the
	 * queue is empty) and this service does not listen any more to the
	 * connection.
	 * <p>Threads that may be currently blocked while waiting for a queue
	 * state are interrupted. 
	 */
	public void close () {
		if (!closed) {
			closed = true;
			stopListening();
			if (takeThread != null) {
				takeThread.interrupt();
			}
			queue.clear();
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}

	private class ConListener implements ConnectionListener {
		@Override
		public void connected (Connection con) {
			ConnectionEvent event = new ConnectionEvent(con, ConnectionEventType.CONNECTED);
			putToQueue(event);
		}
	
		@Override
		public void closed(Connection con, int cause, String message) {
			ConnectionEvent event = new ConnectionEvent(con, ConnectionEventType.CLOSED, cause, message);
			putToQueue(event);
		}
	
		@Override
		public void shutdown(Connection con, int cause, String message) {
			ConnectionEvent event = new ConnectionEvent(con, ConnectionEventType.SHUTDOWN, cause, message);
			putToQueue(event);
		}

		@Override
		public void idleChanged(Connection con, boolean idle, int exchange) {
			ConnectionEvent event = new ConnectionEvent(con, ConnectionEventType.IDLE, 
					idle ? 1 : 0, "exchange per min: ".concat(String.valueOf(exchange)));
			putToQueue(event);
		}
	
		@Override
		public void objectReceived (Connection con, SendPriority priority, long objectNr, Object object) {
			ConnectionEvent event = new ConnectionEvent(con, priority, object, objectNr);
			putToQueue(event);
		}
	
		@Override
		public void transmissionEventOccurred (TransmissionEvent transEvt) {
			ConnectionEvent event = new ConnectionEvent(transEvt);
			putToQueue(event);
		}
	
		@Override
		public void pingEchoReceived (PingEcho pingEcho) {
			ConnectionEvent event = new ConnectionEvent(pingEcho.getConnection(), null, ConnectionEventType.PING_ECHO, 
					pingEcho, pingEcho.pingId(), pingEcho.duration(), null);
			putToQueue(event);
		}

		@Override
		public void objectAborted (Connection con, long objectNr, Object object, int info, String msg) {
			ConnectionEvent event = new ConnectionEvent(con, null, ConnectionEventType.ABORTED, 
					object, objectNr, info, msg);
			putToQueue(event);
		}
	}

	
}
