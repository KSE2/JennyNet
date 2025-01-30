/*  File: FakeServerConnection.java
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
import java.net.InetSocketAddress;

import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;

public class FakeServerConnection extends FakeConnection implements ServerConnection {

	IServer server;
	boolean started;
	boolean tempoFixed;
	
	public FakeServerConnection (IServer server) {
		this.server = server;
	}

	public FakeServerConnection (InetSocketAddress local, InetSocketAddress remote) {
		super(local, remote);
	}

	@Override
	public void start() throws IOException {
		started = true;
	}

	@Override
	public void reject() throws IOException {
	}

	@Override
	public void setTempoFixed (boolean isFixed) {
		tempoFixed = isFixed;
	}

	@Override
	public IServer getServer() {
		return server;
	}

	@Override
	public boolean getTempoFixed() {
		return tempoFixed;
	}

	@Override
	public void shutdownClose (String reason) {
		close();
	}

}
