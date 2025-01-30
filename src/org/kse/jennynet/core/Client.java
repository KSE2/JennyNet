/*
*  File: Client.java
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.kse.jennynet.exception.ClosedConnectionException;
import org.kse.jennynet.exception.ConnectionTimeoutException;
import org.kse.jennynet.exception.DoubleConnectionException;
import org.kse.jennynet.exception.JennyNetHandshakeException;
import org.kse.jennynet.intfa.IClient;

/** 
 * {@code Client} is an extension of the {@code Connection} implementation of 
 * the <i>JennyNet</i> layer with the ability to bind a local port address 
 * (socket) and to establish a running connection to a remote or local server
 * station ({@code Server}) which is running the same <i>JennyNet</i> service
 * layer. 
 * 
 * <p>The initial operation parameters for this {@code Connection} are 
 * provided by the {@code JennyNet} global class. 
 * 
 * @see org.kse.jennynet.intfa.Connection
 * @see org.kse.jennynet.intfa.IClient
 * @see Server
 */
public class Client extends ConnectionImpl implements IClient {

   private Socket socket = new Socket();


   /** Creates an unbound client. When connecting an unbound client, an 
    * ephemeral local port number is automatically assigned to the connection.
    */
   public Client () {
	   super(LayerCategory.CLIENT);
   }
   
   /** Creates a client which is bound to the specified port. A port number of
    * zero selects any free port.
    * 
    * @param port int local port number
    * @throws IOException if binding to the specified port is not possible
    */
   public Client (int port) throws IOException {
	   this();
       bind(port);
   }
   
   /** Creates a client with the given socket address. An address of null 
    * creates a socket with the wildcard IP-address on any free port.
    * 
    * @param address SocketAddress client address or null for
    *                any free port
    * @throws IOException
    */
   public Client (SocketAddress address) throws IOException {
      this();
      if (address == null) {
         address = new InetSocketAddress(0);
      }
      bind(address);
   }
   
   @Override
   protected void connectionClosing (ErrorObject error) {
	   boolean ok = JennyNet.removeClientFromGlobalSet(this);
	   if (JennyNet.debug & ok) {
		   prot("-- removed CLIENT from global client set (" 
				   + JennyNet.getNrOfClients() + ") : " + getLocalAddress());
	   }
   }

   @Override
   public void bind (int port) throws IOException {
      bind( new InetSocketAddress(port) );
   }


   @Override
   public void bind (SocketAddress address) throws IOException {
      socket.setReuseAddress(true);
      socket.bind(address);
   }

   @Override
   public boolean isBound () {
      return socket.isBound();
   }

   /** Starts the network socket with a connection attempt, handles the
    * <i>JennyNet</i> layer handshake and waits for connection approval from 
    * the remote end (application action). After approval the connection 
    * resources are allocated and it moves to CONNECTED operation state. A 
    * 'connected' connection event is issued. In case of remote rejection, 
    * error conditions or timeout, exceptions are thrown.  
    * <p>If CONNECTED the connection is added to JennyNet's global client list.
    * 
    * @param target
    * @param timeout
    * @throws IOException
    */
   private void startSocket (InetSocketAddress target, int timeout) throws IOException {
      // control and correct parameters
      checkConnectionTarget(target);
      if (timeout < 1) {
         timeout = getParameters().getConfirmTimeout() / 2;
      }

      // attempt connection on socket level
      long startTime = System.currentTimeMillis();
      int loop = 0;
      do {
	      try {
	         socket.connect(target, timeout);
	         
	      } catch (SocketTimeoutException e) {
	         throw new ConnectionTimeoutException("socket timeout: " + timeout + " ms");
	         
	      } catch (SocketException e) {
	    	  // step out w/ exception in second loop
	    	  if (loop == 1) {
	    		  if (debug) {
	    			  prot("*** FAIL: socket.connect error: " + e);
	    		  }
	    		  throw e;
	    	  }
	    	  
	          // refresh an error-stiken socket (first loop)
	          int localPort = socket.getLocalPort();
	          if (debug) {
	        	  prot("-- creating new socket for port " + localPort);
	          }
	       	  socket = new Socket();
	       	  if (localPort > -1) {
	       		  bind(localPort);
	       	  }
	    	  
	      } catch (IOException e) {
	    	 if (debug) {
	    		 prot("*** FAIL: socket.connect error: " + e);
	    	 }
	    	 throw e;
	      }
      } while (loop++ == 0 && !socket.isConnected());
      
      // verify JennyNet layer handshake
      if (!JennyNet.verifyNetworkLayer(1, socket, getTimer(), timeout)) {
         throw new JennyNetHandshakeException("no remote JennyNet layer");
      }

      // verify connection was accepted
      int time = timeout - (int)(System.currentTimeMillis() - startTime); 
      JennyNet.waitForConnection(socket, getTimer(), time);
      
      // only then start Connection resources (running status)
      start(socket);
      
      // integrate to global active client list
	  JennyNet.addClientToGlobalSet(this);
	  if (JennyNet.debug) {
		  prot("-- created NEW CLIENT, added to global client set (size " + 
			   JennyNet.getNrOfClients() + ") : " + getLocalAddress());
	  }
	   
      // dispatch CONNECTED event
	  setOperationState(ConnectionState.CONNECTED, null);
   }
   
   /**
    * Controls whether this client can be connected and that
    * the assigned port number is valid.
    * 
    * @param addr InetSocketAddress
    * @throws DoubleConnectionException
    * @throws ClosedConnectionException
    * @throws IllegalArgumentException
    */
   private void checkConnectionTarget (InetSocketAddress addr) {
      if (isConnected()) 
         throw new DoubleConnectionException();
      if (isClosed())
         throw new ClosedConnectionException("this connection is closed!");
      int port = addr.getPort();
      if (port < 0 | port > 65535) {
         throw new IllegalArgumentException("port number out of range");
      }
   }

   @Override
   public void connect (int timeout, String host, int port) throws IOException {
      InetSocketAddress target = new InetSocketAddress(host, port);
      startSocket(target, timeout);
   }

   @Override
   public void connect (int timeout, InetAddress host, int port) throws IOException {
      InetSocketAddress target = new InetSocketAddress(host, port);
      startSocket(target, timeout);
   }

   @Override
   public void connect (int timeout, InetSocketAddress host) throws IOException {
      startSocket(host, timeout);
   }

   @Override
   public void setPerformancePreferences (int connectionTime, int latency, int bandwidth) {
      if (socket.isConnected()) 
         throw new IllegalStateException("socket must be unconnected");
      
      socket.setPerformancePreferences(connectionTime, latency, bandwidth);
   }

   @Override
   public InetSocketAddress getLocalAddress () {
      return (InetSocketAddress)socket.getLocalSocketAddress();
   }

   @Override
   public InetSocketAddress getRemoteAddress () {
      return (InetSocketAddress)socket.getRemoteSocketAddress();
   }

   @Override
   protected Socket getSocket () {
      return socket;
   }
   
}
