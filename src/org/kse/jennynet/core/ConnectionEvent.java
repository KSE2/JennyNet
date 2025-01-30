/*  File: ConnectionEvent.java
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

import java.util.Objects;

import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;

/** Class to enclose information about a connection event. 
 * <br>This can be used to conserve or document an event or as input for
 * dispatching this event to listeners.
 */
public class ConnectionEvent {

	private Connection connection;
	private ConnectionEventType type;
	private SendPriority priority;
	private TransmissionEvent transEvent;
	private Object object;
	private long objectNr;
	private int info;
	private String text;
	
	/** Creates a connection-event for an administration event type
	 * w/o object or further information.
	 * 
	 * @param connection {@code Connection}
	 * @param type {@code ConnectionEventType} event type
	 */
	public ConnectionEvent (Connection connection, ConnectionEventType type) {
		if (connection == null || type == null)
			throw new NullPointerException();
		this.connection = connection;
		this.type = type;
	}

	/** Creates a connection-event for a file {@code TransmissionEvent}.
	 * 
	 * @param transEvt {@code TransmissionEvent}
	 */
	public ConnectionEvent (TransmissionEvent transEvt) {
		Objects.requireNonNull(transEvt);
		type = ConnectionEventType.TRANS_EVT;
		priority = transEvt.getPriority();
		connection = transEvt.getConnection();
		transEvent = transEvt;
		objectNr = transEvt.getObjectID();
		info = transEvt.getInfo();
	}

	/** Creates a connection-event for an administration event type
	 * with information and message.
	 * 
	 * @param connection {@code Connection}
	 * @param type {@code ConnectionEventType} event type
	 * @param info int error code
	 * @param msg String text information
	 */
	public ConnectionEvent (Connection connection, ConnectionEventType type, int info, String msg) {
		this(connection, null, type, null, 0, info, msg);
	}
	
	/** Creates a connection-event with the full spectrum of settings.
	 * This to be used for events taking reference to an object.
	 * 
	 * @param connection {@code Connection}
	 * @param priority {@code SendPriority}, may be null
	 * @param type {@code ConnectionEventType} event type
	 * @param object Object data object reference
	 * @param objectNr long object identifier
	 * @param info int error code
	 * @param msg String text information
	 */
	public ConnectionEvent (Connection connection, SendPriority priority, ConnectionEventType type, Object object, long objectNr, int info, String msg) {
		Objects.requireNonNull(connection, "connection is null");
		Objects.requireNonNull(type, "type is null");
		
		this.connection = connection;
		this.type = type;
		this.object = object;
		this.objectNr = objectNr;
		this.info = info;
		this.text = msg;
	}

	/** Creates a connection-event of type OBJECT with the given object data.
	 * 
	 * @param connection {@code Connection}
	 * @param priority {@code SendPriority}
	 * @param object Object data object reference
	 * @param objectNr long object identifier
	 */
	public ConnectionEvent (Connection connection, SendPriority priority, Object object, long objectNr) {
		Objects.requireNonNull(connection, "connection is null");
		Objects.requireNonNull(priority, "priority is null");
		Objects.requireNonNull(object, "object is null");
		if (objectNr < 0)
			throw new IllegalArgumentException("object-nr is negative");
		
		this.connection = connection;
		this.type = ConnectionEventType.OBJECT;
		this.priority = priority;
		this.object = object;
		this.objectNr = objectNr;
	}

	public ConnectionEventType getType () {return type;}
	
	public SendPriority getPriority () {return priority;}

	/** The numerical information associated with this event or zero if no 
	 * such information is available. This is mostly an error code.
	 * 
	 * @return int
	 */
	public int getInfo () {
		return info;
	}

	/** If this event references an object other than a file, its identity 
	 * is rendered here.
	 *   
	 * @return long object identifier
	 */
	public long getObjectNr () {return objectNr;}

	/** The message associated with this event or null if no message is
	 * available.
	 * 
	 * @return String or null
	 */
	public String getText () {return text;}

	/** The {@code Connection} for which this even was issued.
	 * 
	 * @return {@code Connection}
	 */
	public Connection getConnection () {
		return connection;
	}

	@Override
	public String toString() {
		String infoStr = type == ConnectionEventType.IDLE ? (info == 0 ? "BUSY" : "IDLE") : ("info " + String.valueOf(info));
		return type.name() + "  " + objectNr + ", " + infoStr + ", msg = " + text + ", " + connection;
	}

	/** If this connection-event encloses a file-transmission event then this
	 * method returns a value not null.
	 * 
	 * @return {@code TransmissionEvent} or null
	 */
	public TransmissionEvent getTransmissionEvent() {
		return transEvent;
	}

	/** If this connection-event takes a strong reference to an object then 
	 * this method returns a value not null. Cases are OBJECT (delivery), 
	 * ABORTED and PING_ECHO.
	 * 
	 * @return {@code Object} or null
	 */
	public Object getObject() {
		return object;
	}

	
}
