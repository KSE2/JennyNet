/*  File: FakeConnection.java
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.kse.jennynet.core.ConnectionMonitor;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.util.Util;

public class FakeConnection implements Connection {

   public enum Action {OBJECT, FILE, TEMPO, PING}
   
   private UUID uuid = UUID.randomUUID();
   private byte[] shortId = Util.makeShortId(uuid);
   private String connectionName;
   private LayerCategory category = LayerCategory.CLIENT;
   private ConnectionParameters parameters = JennyNet.getConnectionParameters();
   private Properties properties; 
   private Serialization sendSerialisation;
   private Serialization receiveSerialisation;
   private InetSocketAddress remoteAddress;
   private InetSocketAddress localAddress;
   private boolean closed;
   private boolean makeError;
   
   private ConnectionState operationState = ConnectionState.UNCONNECTED;
   private int[] counter = new int[Action.values().length];
   private Object lastSendObject;
   private int nextObjectNumber;
   private int nextPingNumber;
   
   public class PingObject {
	   long pingNr;
	   public PingObject (long id) {
		   pingNr = id;
	   }
   }
   
   public FakeConnection () {
	   // instantiate serialisation devices
	   int method = getParameters().getSerialisationMethod();
	   try {
		   Serialization ser = JennyNet.getDefaultSerialisation(method);
		   receiveSerialisation = ser.copy();
		   sendSerialisation = ser.copy();
	   } catch (SerialisationUnavailableException e) {
	   }
   }
   
   public FakeConnection (LayerCategory category) {
	   Objects.requireNonNull(category);
	   this.category = category;
   }
   
   public FakeConnection (InetSocketAddress local, InetSocketAddress remote) {
      remoteAddress = remote;
      localAddress = local;
   }
   
   /** Returns the occurrence counter value for the 
    * specified send action. (Test function)
    * 
    * @param a Action
    * @return int counter value
    */
   int getCounterValue(Action a) {
      return counter[a.ordinal()];
   }
   
   Object getLastSendObject() {
      return lastSendObject;
   }

	@Override
	public int getLastPingTime() {return 360;}

   /** Sets whether this connection will create an error condition
    * on all send activities.
    * 
    * @param v boolean true == throw error
    */
   public void setErrorMaker (boolean v) {
      makeError = v;
   }
   
   /** Resets the test counter of send actions to zero values. */
   public void resetActionCounter () {
      counter = new int[Action.values().length];
   }
   
   @Override
   public ConnectionParameters getParameters () {
      return parameters;
   }

   @Override
   public void setParameters (ConnectionParameters parameters) 
         throws IOException {
      if (parameters != null) {
         this.parameters = (ConnectionParameters)parameters.clone();
      }
   }

   @Override
   public Serialization getSendSerialization () {
      return sendSerialisation;
   }

   @Override
   public Serialization getReceiveSerialization () {
      return receiveSerialisation;
   }

   @Override
   public UUID getUUID () {
      return uuid;
   }

   
   @Override
   public void setUUID(UUID uuid) {
	   this.uuid = uuid;
   }

@Override
   public byte[] getShortId () {
      return shortId;
   }

   @Override
   public boolean isConnected () {
      return !closed;
   }

   @Override
   public boolean isClosed () {
      return closed;
   }

   @Override
   public boolean isTransmitting () {
      return false;
   }

   @Override
   public long sendObject (Object object, SendPriority priority) {
      if (makeError) {
         throw new IllegalStateException("TEST EXEPTION");
      }
      lastSendObject = object;
      counter[Action.OBJECT.ordinal()]++;
      return ++nextObjectNumber;
   }

	@Override
	public long sendObject(Object object, int method, SendPriority priority) {
		return sendObject(object, priority);
	}

   @Override
   public long sendFile (File file, String remotePath, SendPriority priority, int transaction) throws IOException {
      if (makeError) {
         throw new IOException("TEST EXCEPTION");
      }
      lastSendObject = file;
      counter[Action.FILE.ordinal()]++;
      return ++nextObjectNumber;
   }

   
   @Override
   public long sendData (byte[] buffer, int start, int length, SendPriority priority) {
      return sendObject(null);
   }

   @Override
   public long sendPing () {
//      sendObject(Signal.newPingSignal(++nextPingNumber));
      sendObject(new PingObject(++nextPingNumber));
      counter[Action.PING.ordinal()]++;
      return nextPingNumber;
   }

   @Override
   public void setTempo (int baud) {
      sendObject(new Integer(baud));
      counter[Action.TEMPO.ordinal()]++;
   }

   @Override
   public boolean breakTransfer (long objectID, ComDirection direction, String text) {
	   return false;
   }

   @Override
   public boolean breakTransfer (long objectID, ComDirection direction) {
	   return false;
   }

   @Override
   public void close () {
      closed = true;
   }

   @Override
   public void close (String reason) {
	   closed = true;
   }
   
   @Override
   public void closeHard() {
	   closed = true;
   }

   @Override
   public void addListener (ConnectionListener listener) {
   }

   @Override
   public void removeListener (ConnectionListener listener) {
   }

	@Override
	public Set<ConnectionListener> getListeners() {return null;}

   @Override
   public InetSocketAddress getRemoteAddress () {
      return remoteAddress;
   }

   @Override
   public InetSocketAddress getLocalAddress () {
      return localAddress;
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
   public boolean isIdle () {
      return false;
   }

   @Override
   public Properties getProperties () {
      if (properties == null) {
         properties = new Properties();
      }
      return properties;
   }

//   @Override
//   public void setSendSerialization (Serialization s) {
//   }
//
//   @Override
//   public void setReceiveSerialization (Serialization s) {
//   }

	@Override
	public int getTransmissionSpeed() {
		return -1;
	}

	@Override
	public void waitForDisconnect(long time) throws InterruptedException {
	}
	
	@Override
	public void waitForClosed(long time) throws InterruptedException {
	}

	@Override
	public boolean isGlobalOutput() {
		return false;
	}

	@Override
	public ConnectionMonitor getMonitor() {
		return new ConnectionMonitor();
	}

	@Override
	public ConnectionState getOperationState() {
		return operationState;
	}

	@Override
	public LayerCategory getCategory() {
		return category;
	}

	@Override
	public Serialization getSendSerialization (int method) {
		return getSendSerialization();
	}

	@Override
	public Serialization getReceiveSerialization (int method) {
		return getReceiveSerialization();
	}

}
