/*  File: FileReceptionListener.java
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

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.intfa.TransmissionEventType;

/** A <code>ConnectionListener</code> to receive file transmissions and 
 * transmission events and return them via an output methods.
 * There is a lock parameter which gets notified when enough files have
 * been successfully received.
 */
public class FileReceptionListener extends DefaultConnectionListener {

	/** CLIENT or SERVER station. */
	public enum Station {CLIENT, SERVER}
	private Station station = Station.SERVER;
	private PrintStream out;
	
	private List<File> received = new ArrayList<>();
	private List<TransmissionEvent> events = new ArrayList<>();
	private Object lock;
	private SemaphorLock semaphor;
	private TransmissionEventType signalType;
	private File errorFile;
	private int unlockThreshold;
	private int errorInfo;
	private boolean releaseOnFailure = true;

	/** A listener for the any station, equipped with a lock. The lock is
	 * released when the number of received files at the station is equal to 
	 * the value of the second parameter, or if the FILE_ABORTED event is
	 * received.
	 * 
	 * @param lock Object synchronising monitor of waiting threads
	 * @param unlockSize int number of files to unlock
	 * @param station Station client or server
	 */
	public FileReceptionListener (Object lock, int unlockSize, Station station) {
		Objects.requireNonNull(lock, "semaphore");
		Objects.requireNonNull(station, "station");
		this.lock = lock;
		unlockThreshold = unlockSize;
		this.station = station;
		out = station == Station.CLIENT ? System.err : System.out;
	}
	
	/** A listener for any station equipped with a semaphore lock. 
	 * The semaphore is decreased by any transmission event on this listener.
	 * 
	 * @param semaphor SemaphorLock
	 * @param station Station client or server
	 */
	public FileReceptionListener (SemaphorLock semaphor, FileReceptionListener.Station station) {
		Objects.requireNonNull(semaphor, "semaphore");
		Objects.requireNonNull(station, "station");
		this.semaphor = semaphor;
		this.station = station;
		unlockThreshold = semaphor.getCounter();
		out = station == Station.CLIENT ? System.err : System.out;
	}
	
	/** A listener for the given station w/o locking device.
	 *  
	 * @param station Station 
	 */
	public FileReceptionListener (Station station) {
		Objects.requireNonNull(station, "station");
		this.station = station;
		unlockThreshold = 0;
		out = station == Station.CLIENT ? System.err : System.out;
	}
	
	@Override
	public synchronized void objectReceived(Connection con, SendPriority priority, long objNr, Object obj) {
		out.println("*** OBJECT  RECEIVED: No. (" + objNr + "), Prio = " + priority + ", class = " + obj.getClass() 
				+ " FROM " + con.getRemoteAddress());
	}
	
	@Override
	public synchronized void transmissionEventOccurred (TransmissionEvent evt) {

		signalType = evt.getType();
		events.add(evt);
		
		switch (signalType) {
		case FILE_ABORTED:
			out.println("*** FILE ABORTED: No. " + evt.getObjectID()
				+ " from " + evt.getConnection().getRemoteAddress() 
				+ ", info = " + evt.getInfo() 
				+ ", direction = " + evt.getDirection() 
				+ ", dest-path = " + evt.getPath()
				+ ", file = " + evt.getFile());
			if (evt.getException() != null) {
				out.println("    Exception: " + evt.getException());
			}
			errorInfo = evt.getInfo();
			errorFile = evt.getFile();
			
			if (releaseOnFailure) {
				release_locks();
			}
			break;
			
		case FILE_INCOMING:
			out.println("*** FILE INCOMING: No. " + evt.getObjectID() 
					+ " from " + evt.getConnection().getRemoteAddress() 
					+ ", length " + evt.getExpectedLength() + " bytes at "
					+ evt.getFile());
			if (evt.getPath() != null) {
				out.println("    Target: " + evt.getPath()); 
			}
			break;
			
		case FILE_SENDING:
			out.println("*** FILE SENDING: No. " + evt.getObjectID() 
					+ " off " + evt.getConnection().getLocalAddress() 
					+ ", length " + evt.getExpectedLength() + " bytes at "
					+ evt.getFile());
			if (evt.getPath() != null) {
				out.println("    Target: " + evt.getPath()); 
			}
			break;
			
		case FILE_RECEIVED:
			File f = evt.getFile();
			out.println("*** FILE RECEIVED: No. " + evt.getObjectID() 
					+ " from " + evt.getConnection().getRemoteAddress() 
					+ ", length " + evt.getTransmissionLength() + " bytes --> " + f);
			received.add(f);
			if (evt.getPath() != null) {
				out.println("    target was: " + evt.getPath()); 
			}
			
			// notify waiting thread on LOCK if target reached
			check_lock();
			break;
			
		case FILE_CONFIRMED:
			out.println("*** FILE CONFIRMED: No. " + evt.getObjectID() 
					+ " from " + evt.getConnection().getRemoteAddress() 
					+ ", length " + evt.getTransmissionLength() + " bytes --> "
					+ evt.getFile());
			if (evt.getPath() != null) {
				out.println("    Target: " + evt.getPath()); 
			}
			break;
			
		default:
			// decrease SEMAPHOR if defined
			if (semaphor != null) {
				semaphor.inc();
			}
			break;
		}

		// decrease SEMAPHORE if defined
		if (semaphor != null) {
			semaphor.dec();
		}
	}

	/** check the LOCK release condition and notify all waiting threads
	 * if the unlockThreshold (int) is reached. 
	 */
	private void check_lock () {
		if (lock != null && received.size() >= unlockThreshold) {
			synchronized(lock) {
				lock.notifyAll();
			}
		}
	}

	private void release_locks () {
		if (lock != null) {
			synchronized(lock) {
				lock.notifyAll();
			}
		}
		if (semaphor != null) {
			semaphor.release();
		}
	}
	
	/** Sets whether locks shall open when a FILE_ABORTED or FILE_FAILED
	 * event is detected. Default value is true.
	 * 
	 * @param release boolean true = release on failure, false = don't release
	 *        on failure
	 */
	public void set_release_locks_on_failure (boolean release) {
		releaseOnFailure = release;
	}
	
	/** Returns a list of files received via file-transmission in the order of
	 * their appearance.
	 *  
	 * @return {@code List<File>}
	 */
	public List<File> getReceived () {
		return received;
	}
	
	/** Returns a list of transmission events which occurred on this listener.
	 * 
	 * @return {@code List<TransmissionEvent>}
	 */
	public List<TransmissionEvent> getEvents () {
		return events;
	}
	
	public synchronized void reportEvents () {
		out.println("# EVENT-LIST for " + station);
		for (TransmissionEvent evt : events) {
			out.println("   " + evt);
		}
	}
	
	/** Returns a subset of transmission events of the given event-type 
	 * occurring on this listener, in the order of their appearance.
	 * 
	 * @param type {@code TransmissionEventType} selection event-type
	 * @return {@code List<TransmissionEvent>}
	 */
	public List<TransmissionEvent> getEvents (TransmissionEventType type) {
		List<TransmissionEvent> list = new ArrayList<>();
		for (TransmissionEvent evt : getEvents()) {
			if (evt.getType() == type) {
				list.add(evt);
			}
		}
		return list;
	}
	
	/** Returns cardinality of the set of received events of the given type.
	 * 
	 * @param type {@code TransmissionEventType}
	 * @return int number of events
	 */
	public int countEvents (TransmissionEventType type) {
		return getEvents(type).size(); 
	}

	/** Whether we have received an event of the given specifics in the
	 * listener.
	 *  
	 * @param type {@code TransmissionEventType} event type
	 * @param info int value of event 'info'
	 * @return boolean
	 */
	public boolean hasTransmissionEvent (TransmissionEventType type, int info) {
		for (TransmissionEvent evt : getEvents()) {
			if (evt.getType() == type && evt.getInfo() == info) {
				return true;
			}
		}
		return false; 
	}
	
	/** Returns the first event we received of the given type or null if
	 * unavailable.
	 * 
	 * @param type {@code TransmissionEventType} event type
	 * @return {@code TransmissionEvent} or null
	 */
	public TransmissionEvent getFirstEventOf (TransmissionEventType type) {
		for (TransmissionEvent evt : getEvents()) {
			if (evt.getType() == type) {
				return evt;
			}
		}
		return null;
	}
	
	/** Returns the last event we received of the given type or null if
	 * unavailable.
	 * 
	 * @param type {@code TransmissionEventType} event type
	 * @return {@code TransmissionEvent} or null
	 */
	public TransmissionEvent getLastEventOf (TransmissionEventType type) {
		TransmissionEvent ev = null;
		for (TransmissionEvent evt : getEvents()) {
			if (evt.getType() == type) {
				ev = evt;
			}
		}
		return ev;
	}
	
	/** Returns the last event received or null if the list is empty.
	 * 
	 * @return {@code TransmissionEvent} or null
	 */
	public TransmissionEvent getLastEvent () {
		return events.isEmpty() ? null : events.get(events.size()-1);
	}
	
	/** Returns the first event received or null if the list is empty.
	 * 
	 * @return {@code TransmissionEvent} or null
	 */
	public TransmissionEvent getFirstEvent () {
		return events.isEmpty() ? null : events.get(0);
	}
	
	/** The 'info' value associated with the latest FILE_ABORTED or 
	 * FILE_FAILED event received.
	 * 
	 * @return int
	 */
	public int getErrorInfo () {
		return errorInfo;
	}
	
	/** The filepath of the latest FILE_ABORTED or FILE_FAILED event received.
	 * 
	 * @return File or null
	 */
	public File getErrorFile () {
		return errorFile;
	}
	
	/** The transmission-event-type of the latest event received.
	 * 
	 * @return {@code TransmissionEventType}
	 */
	public TransmissionEventType getSignalType() {
		return signalType;
	}
	
	/** Clears all lists and revives a semaphore lock.
	 */
	public void reset () {
		received.clear();
		events.clear();
		errorInfo = 0;
		errorFile = null;
		signalType = null;
		
		if (semaphor != null) {
			semaphor.setCounter(unlockThreshold);
		}
	}
	
	public void wait_on_release (long time) throws InterruptedException {
		// Esel
		if (lock != null && received.size() < unlockThreshold
			&& getEvents(TransmissionEventType.FILE_ABORTED).isEmpty()) {
			synchronized (lock) {
				lock.wait(time);
			}
		} else if (semaphor != null) {
			semaphor.lock_wait(time);
		}
	}

	public int getUnlockThreshold() {return unlockThreshold;}

	public void setUnlockThreshold (int v) {
		if (v < 0) 
			throw new IllegalArgumentException("value is negative");
		
		if (unlockThreshold != v) {
			unlockThreshold = v;
			check_lock();
		}
	}

	@Override
	public void closed(Connection con, int cause, String message) {
		out.println("*** CLOSED event: con = " + con + ", cause = " + cause + ", msg = " + message);
	}
	
	@Override
	public synchronized void shutdown (Connection con, int cause, String message) {
		out.println("*** SHUTDOWN event: con = " + con + ", cause = " + cause + ", msg = " + message);
	}

}