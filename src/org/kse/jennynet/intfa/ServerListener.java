/*  File: ServerListener.java
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

/** Interface for a listener to events issued by an <code>IServer</code>
 *  instance.
 *  
 *  @see IServer
 */

public interface ServerListener {

   /** A new connection has reached the server and requests operation.
    * The connection given as argument can be started or rejected by the 
    * application. If it is rejected, its resources are cancelled and its 
    * socket closed down. If it is accepted, the connection is started and 
    * added to the connection registry of the server.
    * <p>If the application does not decide on the case, the connection is
    * automatically rejected after a timeout defined by server parameter 
    * CONFIRM_TIMEOUT. 
    * 
    * @param server <code>IServer</code> server source of the event
    * @param connection <code>ServerConnection</code> incoming connection
    */
   void connectionAvailable (IServer server, ServerConnection connection);
   
   /** The given <code>Connection</code> instance was added to the connection
    * registry of the issuing server.
    * 
    * @param server <code>IServer</code> server source of the event
    * @param connection <code>Connection</code> connection added to registry
    */
   void connectionAdded (IServer server, Connection connection);
   
   /** The given <code>Connection</code> instance was removed from the 
    * connection registry of the issuing server.
    * 
    * @param server <code>IServer</code> server source of the event
    * @param connection <code>Connection</code> connection removed from registry
    */
   void connectionRemoved (IServer server, Connection connection);
   
   /** The server has been closed. It will not issue any further events.
    *  
    * @param server <code>IServer</code> server source of the event
    */
   void serverClosed (IServer server);
   
   /** An error involving an exception thrown for a specific connection has 
    * occurred in one of the multiplexing transactions of this server.
    * <p><small>Such errors are reported together with the transaction call.
    * The transaction may actually not have completed its operations when this
    * event is brought up. Other errors can lead to aborted single actions
    * on a connection; these errors are reported to the connection listeners. 
     </small>
    * 
    * @param server <code>IServer</code> server source of the event
    * @param con <code>Connection</code> throwing the error condition
    * @param transAction int id number as returned by the transaction call
    * @param e Throwable error condition
    */
   void errorOccurred (IServer server, Connection con, int transAction, Throwable e);
}
