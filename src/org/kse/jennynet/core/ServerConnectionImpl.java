/*  File: ServerConnectionImpl.java
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;

/** A <code>Connection</code> implementation originating from a server socket.
 * A <code>ServerConnection</code> must be explicitly started by the receiving
 * application in order to commence operations.
 *   
 */
class ServerConnectionImpl extends ConnectionImpl implements ServerConnection {

   private IServer server;
   private Socket startSocket;
   private boolean started;

   /** Creates a new server-connection walking from a server and a socket
    * for the connection. The socket has to be connected.
    * 
    * @param server {@code IServer}
    * @param socket {@code Socket}
    * @throws IllegalArgumentException
    */
   public ServerConnectionImpl (IServer server, Socket socket) {
      super(LayerCategory.SERVER);
      Objects.requireNonNull(server, "server == null");
      Objects.requireNonNull(socket, "socket == null");
      if (!socket.isConnected()) {
         throw new IllegalArgumentException("socket must be connected!");
      }

      this.server = server;
      startSocket = socket;
   }

   @Override
   public void start () throws IOException {
      if (!started) {
         // write connection confirm to remote
         JennyNet.sendConnectionConfirm(this);

         // start connection's operational resources
         super.start(startSocket);
         started = true;

	   	 if (debug) {
		     prot("-- created NEW SERVER-CONNECTION : " + this);
		 }
	   	  
         // dispatch CONNECTED event
   	     setOperationState(ConnectionState.CONNECTED, null);
      }
   }
   
   @Override
   public void reject () throws IOException {
      if (!started & startSocket.isConnected()) {
         startSocket.close();
         super.close();  // this sets the "closed" marker for the connection
      }
   }

   @Override
   public void close () {
      // we have to secure the start-socket close operation
      // because "super.close" would not do it in unstarted state
      if (!started) {
         try {
            reject();
         } catch (IOException e) {
            e.printStackTrace();
         }
      } else {
         super.close();
      }
   }
   
   @Override
   public void shutdownClose (String reason) {
	   if (!started) {
		   close();
	   } else {
		   super.closeShutdown(new ErrorObject(1, reason));
	   }
   }

   @Override
   public void setTempoFixed(boolean isFixed) {
	   fixedTransmissionSpeed = isFixed;
   }
      
	@Override
	public boolean getTempoFixed() {
		return fixedTransmissionSpeed;
	}

//   private void test1 () throws IOException {
//      Server server = new Server();
//      final File rootDirectory = new File("/tmp");
//      
//      server.addListener( new DefaultServerListener() {
//         
//         @Override
//         public void connectionAvailable(IServer server, ServerConnection connection) {
//            // we think about whether we can handle this connection
//            boolean isAcceptable = true;
//            // we accept or refute it
//            try {
//               if (isAcceptable) {
//                  // we can set some parameter, like e.g. the file-root-path
//                  connection.getParameters().setFileRootDir(rootDirectory);
//                  connection.start();
//               } else {
//                  connection.reject();
//               }
//            } catch (IOException e) {
//               e.printStackTrace();
//            }
//         }
//      });
//   }
   
   @Override
   public InetSocketAddress getRemoteAddress() {
      return (InetSocketAddress)startSocket.getRemoteSocketAddress();
   }

   @Override
   public InetSocketAddress getLocalAddress() {
      return (InetSocketAddress)startSocket.getLocalSocketAddress();
   }

   @Override
   protected Socket getSocket () {
      return startSocket;
   }

   @Override
   public IServer getServer () {
      return server;
   }

  
}
