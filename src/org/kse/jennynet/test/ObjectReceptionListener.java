/*  File: ObjectReceptionListener.java
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

package org.kse.jennynet.test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.core.JennyNetByteBuffer;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.Util;

/** A <code>ConnectionListener</code> to receive 
 * <code>JennyNetByteBuffer</code> and return them via an output method
 * as list of byte arrays. There is an optional lock as parameter which
 * gets notified with each reception event.
 */
public class ObjectReceptionListener extends DefaultConnectionListener {

	private Station station = Station.SERVER;
	PrintStream out;
	
	private List<byte[]> received = new ArrayList<byte[]>();
	private List<ConnectionEvent> events = new Vector<>();
	private Object lock;
	private int unlockThreshold;

	/** Creates a new reception listener with an optional lock
	 * to be notified.
	 *  
	 * @param lock Object lock to be notified, may be null
	 * @param unlockSize int size of object list which triggers 
	 * 		  lock release 
	 * @param station Station client or server
	 */
	public ObjectReceptionListener (Object lock, int unlockSize, Station station) {
		this.lock = lock;
		unlockThreshold = unlockSize;
		this.station = station;
		out = station == Station.CLIENT ? System.err : System.out;	
	}
	
	/** Creates a new reception listener without lock reference.
	 * 
	 * @param station Station client or server 
	 */
	public ObjectReceptionListener (Station station) {
		this.station = station;
		out = station == Station.CLIENT ? System.err : System.out;	
	}
	
	@Override
	public synchronized void objectReceived(Connection con, SendPriority priority, long objNr, Object obj) {
		out.println("*** OBJECT RECEIVED: ID " + objNr + ", prio = " + priority + ", class = " + obj.getClass());
		
		events.add(new ConnectionEvent(con, priority, obj, objNr));
		
		if (obj instanceof JennyNetByteBuffer) {
			received.add(((JennyNetByteBuffer)obj).getData());
			
			if (lock != null && received.size() == unlockThreshold) {
				synchronized(lock) {
					lock.notify();
				}
			}
		}
	}

	@Override
	public synchronized void connected (Connection con) {
		events.add(new ConnectionEvent(con, ConnectionEventType.CONNECTED));
		out.println("*** CONNECTED event: con = " + con);
	}

	@Override
	public synchronized void closed (Connection con, int cause, String message) {
		events.add(new ConnectionEvent(con, ConnectionEventType.CLOSED, cause, message));
		out.println("*** CLOSED event: con = " + con + ", cause = " + cause + ", msg = " + message);
	}

	@Override
	public synchronized void shutdown (Connection con, int cause, String message) {
		events.add(new ConnectionEvent(con, ConnectionEventType.SHUTDOWN, cause, message));
		out.println("*** SHUTDOWN event: con = " + con + ", cause = " + cause + ", msg = " + message);
	}

	@Override
	public synchronized void idleChanged (Connection con, boolean idle, int exchange) {
		ConnectionEvent event = new ConnectionEvent(con, ConnectionEventType.IDLE, 
				idle ? 1 : 0, "exchange per min: ".concat(String.valueOf(exchange)));
		events.add(event);
		out.println("*** IDLE event: con = " + con + ", state = " + (idle ? "IDLE" : "BUSY")
				+ ", exchange = " + exchange);
	}

	@Override
	public synchronized void pingEchoReceived (PingEcho pingEcho) {
		events.add(new ConnectionEvent(pingEcho.getConnection(), null, ConnectionEventType.PING_ECHO, pingEcho, 
				pingEcho.pingId(), pingEcho.duration(), null));
		out.println("*** PING-ECHO event: con = " + pingEcho.getConnection() + ", ID = " + pingEcho.pingId() 
				+ ", duration = " + pingEcho.duration());
	}

	@Override
	public synchronized void transmissionEventOccurred(TransmissionEvent event) {
		events.add(new ConnectionEvent(event));
		out.println("*** TRANSMISSION event: con = " + event.getConnection() + ", info = " 
				+ event.getInfo() + ", msg = " + event.getType());
	}

	@Override
	public synchronized void objectAborted (Connection con, long objectNr, Object object, int info, String msg) {
		String className = object == null ? "unknown" : object.getClass().toString();
		out.println("*** OBJECT ABORTED: ID " + objectNr + ", class = " + className
					+ ", info = " + info + ", msg = " + msg);
		events.add(new ConnectionEvent(con, null, ConnectionEventType.ABORTED, object, objectNr, info, msg));
	}

	/** Returns the list of events which occurred on this listener.
	 * 
	 * @return {@code List<TransmissionEvent>}
	 */
	public List<ConnectionEvent> getEvents () {
		return new ArrayList<ConnectionEvent>(events);
	}
	
	/** Returns a subset of connection-events of the given type 
	 * occurring on this listener, in the order of their appearance.
	 * 
	 * @param type {@code ConnectionEvent} selection event-type
	 * @return {@code List<ConnectionEvent>}
	 */
	public List<ConnectionEvent> getEvents (ConnectionEventType type) {
		List<ConnectionEvent> list = new ArrayList<>();
		for (ConnectionEvent evt : getEvents()) {
			if (evt.getType() == type) {
				list.add(evt);
			}
		}
		return list;
	}
	
	/** Whether one of the contains data blocks renders the given CRC32
	 *  value.
	 *  
	 * @param crc int search CRC
	 * @return boolean true == crc contained
	 */
	public synchronized boolean containsBlockCrc (int crc) {
		for (byte[] blk : received) {
			if (Util.CRC32_of(blk) == crc)
				return true;
		}
		return false;
	}

	/** A list of received objects.
	 * 
	 * @return {@code List<byte[]>}
	 */
	public List<byte[]> getReceived () {
		return received;
	}
	
	public synchronized void reportEvents () {
		out.println("# EVENT-LIST for " + station);
		for (ConnectionEvent evt : events) {
			out.println("   " + evt);
		}
	}
	
	/** The size of the number of objects received.
	 * 
	 * @return int
	 */
	public int getSize () {
		return received.size();
	}
	
	/** Clears events and objects received and sets a new value for
	 * unlock-threshold. 
	 * 
	 * @param unlockSize int
	 */
	public void reset (int unlockSize) {
		reset();
		unlockThreshold = unlockSize;
	}

	/** Clears events and objects received.
	 */
	public void reset () {
		received.clear();
		events.clear();
	}
}