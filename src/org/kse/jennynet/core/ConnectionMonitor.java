/*  File: ConnectionMonitor.java
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

import org.kse.jennynet.intfa.Connection.ConnectionState;
import org.kse.jennynet.intfa.Connection.LayerCategory;

/** Defines a set of values for inspection of the operation states of a 
 * {@code Connection}. Offers a report text string.
 */
public class ConnectionMonitor {

	public LayerCategory category;
	public ConnectionState operationState;
	public String trajectory;
	public int serialMethod;
	
	public int filesSent;
	public int filesReceived;
	/** size of send-file-order queue */
	public int filesOutgoing;
	/** number of file-agglomerations */
	public int filesIncoming;
	public long objectsSent;
	public long objectsReceived;
	public int objectsOutgoing;
	public int objectsIncoming;

	public int parcelsScheduled;  // global queue
	public long currentSendLoad;  // conn value
	public long exchangedVolume;
	public long lastSendTime;
	public long lastReceiveTime;
	public int transmitSpeed;
	public int lastPingValue;
	public int aliveSendPeriod;
	public int aliveTimeout;
	public int idleThreshold;
	public int idleCheckPeriod;
	
	public boolean isIdle;
	public boolean closed;
	public boolean connected;
	public boolean transmitting;

	/** Returns a multi-line text rendering of a selection of inspection 
	 * values.
	 *  
	 * @param offset int indentation spaces
	 * @return String text
	 */
	public String report (int offset) {
		StringBuffer buf = new StringBuffer(512);
		String hstr;
		addBuf(buf, offset, "category         ".concat(category.name()));
		if (trajectory != null) {
			addBuf(buf, offset, "trajectory       ".concat(trajectory));
		}
		addBuf(buf, offset, "op-state         ".concat(operationState.name()));
		addBuf(buf, offset, "serialisation    ".concat(String.valueOf(serialMethod)));
		addBuf(buf, offset, "idle             ".concat(isIdle? "true" : "false"));
		addBuf(buf, offset, "transmitting     ".concat(transmitting ? "true" : "false"));
		hstr = transmitSpeed == -1 ? "unlimited" : String.valueOf(transmitSpeed);
		addBuf(buf, offset, "transmit-speed   ".concat(hstr));
		addBuf(buf, offset, "ping-time        ".concat(String.valueOf(lastPingValue)));
		addBuf(buf, offset, "alive sending    ".concat(String.valueOf(aliveSendPeriod)));
		addBuf(buf, offset, "alive timeout    ".concat(String.valueOf(aliveTimeout)));
		addBuf(buf, offset, "idle threshold   ".concat(String.valueOf(idleThreshold)));
		addBuf(buf, offset, "idle period      ".concat(String.valueOf(idleCheckPeriod)));
		addBuf(buf, offset, "exchange volume  ".concat(String.valueOf(exchangedVolume)));
		addBuf(buf, offset, "sendload         ".concat(String.valueOf(currentSendLoad)));
		addBuf(buf, offset, "core-send        ".concat(String.valueOf(parcelsScheduled)));
		addBuf(buf, offset, "send-time        ".concat(String.valueOf(lastSendTime)));
		addBuf(buf, offset, "receive-time     ".concat(String.valueOf(lastReceiveTime)));
		buf.append('\n');
		addBuf(buf, offset, "objects sent     ".concat(String.valueOf(objectsSent)));
		addBuf(buf, offset, "objects rece     ".concat(String.valueOf(objectsReceived)));
		addBuf(buf, offset, "objects outg     ".concat(String.valueOf(objectsOutgoing)));
		addBuf(buf, offset, "files sent       ".concat(String.valueOf(filesSent)));
		addBuf(buf, offset, "files rece       ".concat(String.valueOf(filesReceived)));
		addBuf(buf, offset, "files outg       ".concat(String.valueOf(filesOutgoing)));
		
		return buf.toString();
	}

	private void addBuf (StringBuffer buf, int offset, String concat) {
		for (int i = 0; i < offset; i++) {
			buf.append(' ');
		}
		if (concat != null) {
			buf.append(concat);
		}
		buf.append('\n');
	}
}
