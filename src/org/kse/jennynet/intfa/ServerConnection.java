/*  File: ServerConnection.java
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

package org.kse.jennynet.intfa;

import java.io.IOException;

import org.kse.jennynet.exception.ClosedConnectionException;

/** Interface for a <code>Connection</code> which was rendered by a 
 * <code>Server</code> instance. This extension to interface <code>Connection
 * </code> consists in the facility to <i>start()</i> or <i>reject()</i> a 
 * connection before it commences operations. Starting or rejecting a 
 * connection is a requirement for the server application. If this does not
 * happen within a short time, the connection will get closed by the layer.
 * (The wait-time can be influenced over default connection parameter 
 * CONFIRM_TIMEOUT of the server, 
 * i.e. <i>server.getParameters().setConfirmTimeout(int)</i>.) 
 * 
 * <p>Typically a <code>ServerConnection</code> is indicated by the <code>Server
 * </code> in a non-started state in order to allow the application to 
 * decide whether to accept or reject this connection. Only when the
 * connection is started it will allocate required resources and become
 * operational. Correspondingly, the remote end <code>Client</code> has to
 * wait in its <code>connect()</code> method until a decision is taken on
 * the server or the request has timed out.
 * 
 * <p>The server-connection can be set to own primacy over transmission speed
 * setting. If this is set <b>true</b> the client will be disabled to set 
 * transmission speed.
 * 
 * @see Connection
 * @see IServer
 */

public interface ServerConnection extends Connection {

   /** Returns the server instance which was the source of this connection. 
    * 
    * @return Server
    */
   IServer getServer ();
   
   /** Starts operations of this server connection. 
    * If the connection request is found to be timed out
    * a <code>ClosedConnectionException</code> is thrown.
    * 
    * @throws ClosedConnectionException if connection is closed
    * @throws IllegalStateException if the socket is unconnected
    * @throws IOException
    */
   void start() throws IOException;

   /** Rejects a connection which was not yet started. After rejection,
    * a connection is closed and cannot be used or started. Does nothing if
    * the connection is closed.
    * 
    * @throws IOException
    */
   void reject() throws IOException;

   /** Sets this connection's transmission speed as the supreme setting.
    * This affects ignoring and resetting of remote (client) transmission
    * speed signals. If this value is <b>false</b> (the default) then any
    * of both sides can set the transmission speed for both via the TEMPO 
    * signal.
    *   
    * @param isFixed boolean
    */
   void setTempoFixed (boolean isFixed);
   
   /** Whether this connection's transmission speed is the supreme setting.
    * This affects ignoring and resetting of remote (client) transmission
    * speed signals. If this value is <b>false</b> (the default) then any
    * of both sides can set the transmission speed for both via
    * the TEMPO signal.
    *   
    * @return boolean 
    */
   boolean getTempoFixed ();
   
   /** Closes this server connection in the context of a server shutdown,
    * stating an optional text for the reason. The difference of this method
    * to <i>close()</i> is that client and listeners get properly informed 
    * about the server shutdown.
    * 
    * @param reason String shutdown reason, may be null
    */
   void shutdownClose (String reason);
   

}