/*  File: StandardServer.java
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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.function.Predicate;

import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

/** The {@code StandardServer} is a {@code Server} with some commodity 
 * settings about the predicate which determines the set of acceptable
 * connections and a set of {@code ConnectionListener} which is automatically
 * added to each new connection when accepted by this server. Both settings 
 * are application based implementations. Furthermore, a record of parameter 
 * values can be defined which forms the default for new connections. If this
 * record is not explicitly set, the <i>JennyNet</i> default parameter set
 * is coming into place.  
 * 
 */
public class StandardServer extends Server {
	private static final ConnectionListener[] EMPTY_CONLISTENER_SET = new ConnectionListener[0];
	private Predicate<Connection> accept;
	private ConnectionListener[] conListeners;
	private ConnectionParameters params;
	
	/** Creates a standard server of the given socket address. Incoming
	 * connections are accepted (started) if the given 'accept' predicate
	 * returns true or the argument is null. The given connection-
	 * listener is added to accepted connections.
	 * 
	 * @param address SocketAddress server address or null for any free port
	 * @param listener {@code ConnectionListener} optional listener added to 
	 * 		  connections, may be null
	 * @param accept {@code Predicate<Connection>}, may be null
	 * @throws IOException
	 */
	public StandardServer (SocketAddress address, ConnectionListener listener,
			Predicate<Connection> accept) throws IOException {
		this(address, new ConnectionListener[] {listener}, accept);
	}
	
	/** Creates a standard server of the given socket address. All incoming
	 * connections are accepted (started) and the given connection-listener 
	 * is added.
	 * 
	 * @param address SocketAddress server address or null for any free port
	 * @param listener {@code ConnectionListener} optional listener added to 
	 * 		  connections, may be null
	 * @throws IOException
	 */
	public StandardServer (SocketAddress address, ConnectionListener listener) throws IOException {
		this(address, new ConnectionListener[] {listener}, null);
	}
	
	/** Creates a standard server of the given socket address. All incoming
	 * connections are accepted (started).
	 * 
	 * @param address SocketAddress server address or null for any free port
	 * @throws IOException
	 */
	public StandardServer (SocketAddress address) throws IOException {
		this(address, (ConnectionListener)null);
	}
	
	/** Creates a standard server of the given socket address. All incoming
	 * connections are accepted (started) and the given set of connection-
	 * listeners is added to them.
	 * 
	 * @param address SocketAddress server address or null for any free port
	 * @param listeners {@code ConnectionListener[]} optional listeners added 
	 * 		  to connections, may be null
	 * @throws IOException
	 */
	public StandardServer (SocketAddress address, ConnectionListener[] listeners) throws IOException {
		this(address, listeners, null);
	}
	
	/** Creates a standard server of the given socket address. Incoming
	 * connections are accepted (started) if the given 'accept' predicate
	 * returns true or the argument is null. The given connection-
	 * listeners are added to accepted connections.
	 * 
	 * @param address SocketAddress server address or null for any free port
	 * @param listeners {@code ConnectionListener[]} optional set of listeners
	 * 		  added to connections, may be null
	 * @param accept {@code Predicate<Connection>}, predicate for acception
	 * 		  of incoming connection requests, null for accept-all
	 * @throws IOException
	 */
	 public StandardServer (SocketAddress address, ConnectionListener[] listeners,
			Predicate<Connection> accept) throws IOException {
		super(address);
		this.accept = accept;
		this.conListeners = listeners;
		
		addListener(new DefaultServerListener() {
	
			@Override
			public void connectionAvailable(IServer server, ServerConnection con) {
				try {
					boolean accepted = StandardServer.this.accept == null || StandardServer.this.accept.test(con);
					if (accepted) {
						// set default parameters
						if (params != null) {
							con.setParameters(params);
						}
						
						// add standard listeners
						if (conListeners != null) {
							for (ConnectionListener li : conListeners) {
								con.addListener(li);
							}
						}
						if (JennyNet.debug) {
							con.addListener(new EventReporter());
						}
						
						con.start();
						
					} else {
						con.reject();
					}
					Util.sleep(10);
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	 }

	/** Returns the predicate on the set of possible connections (interface
	 * {@code Connection}) which determines the subset of acceptable 
	 * connections for this server. If this predicate is undefined, all
	 * incoming connections are accepted.
	 * 
	 * @return {@code Predicate<Connection>} or null if undefined
	 */
	public Predicate<Connection> getAccept () {
		return accept;
	}

	/** Sets the predicate on the set of possible connections (interface
	 * {@code Connection}) which determines the subset of acceptable 
	 * connections for this server. If this predicate is undefined (null), all
	 * incoming connections are accepted.
	 * 
	 * @param accept {@code Predicate<Connection>}, may be null
	 */
	public void setAccept (Predicate<Connection> accept) {
		this.accept = accept;
	}

	/** Returns the set of {@code ConnectionListener} which will be added
	 * to each new connection by this server. If no listeners are defined, 
	 * an empty array is returned.
	 * 
	 * @return {@code ConnectionListener[]}
	 */
	public ConnectionListener[] getConListeners() {
		return conListeners == null ? EMPTY_CONLISTENER_SET : conListeners;
	}

	/** Sets the set of {@code ConnectionListener} which will be added
	 * to each new connection by this server.
	 * 
	 * @param conListeners {@code ConnectionListener[]}, null for nothing
	 */
	public void setConListeners (ConnectionListener[] conListeners) {
		this.conListeners = conListeners;
	}

	/** Returns the parameter set which is defined as default for each new 
	 * connection accepted by this server or null if undefined. The returned
	 * value is a direct reference, modifications persist. The default value
	 * is null.
	 * 
	 * @return {@code ConnectionParameters} or null
	 */
	public ConnectionParameters getParams () {
		return params;
	}

	/** Sets the parameter set which is defined as default for each new 
	 * connection accepted by this server. If the value for this option is
	 * null (the default) then the <i>JennyNet</i> default parameter set
     * becomes operative for new connections.  
	 * 
	 * @param params {@code ConnectionParameters}, may be null
	 */
	public void setParams (ConnectionParameters params) {
		this.params = params;
	}
	 
	 

}