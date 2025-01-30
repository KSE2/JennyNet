/*  File: TestUnit_JennyNet.java
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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.Util;

public class TestUnit_JennyNet {
	
@Test
public void global_shutdown () throws IOException, InterruptedException {
	Server sv = null;
	Client cl1, cl2;
	ObjectReceptionListener receptionListener = new ObjectReceptionListener(Station.SERVER);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), 
				receptionListener);
		sv.start();
		
		cl1 = new Client();
		cl2 = new Client(6782);
		
		cl1.connect(0, sv.getSocketAddress());
		
		assertTrue("error in number of active clients", JennyNet.getNrOfClients() == 1);
		assertTrue("error in number of active servers", JennyNet.getNrOfServers() == 1);
		
		// sending of data to charge connection
		byte[] data = Util.randBytes(200000);
		cl1.sendData(data, 0, data.length, SendPriority.NORMAL);
		
		// test layer shutdown
		long mark = System.currentTimeMillis();
		JennyNet.shutdownAndWait(5000);
		long time = System.currentTimeMillis() - mark;
		System.out.println("----- shutdown time = " + time);
		
		assertTrue("shutdown wait-lock not released", time <= 1000);
		assertTrue("error in global client set after shutdown", JennyNet.getNrOfClients() == 0);
		assertTrue("error in global server set after shutdown", JennyNet.getNrOfServers() == 0);
		assertFalse("server still alive after shutdown", sv.isAlive());
		assertTrue("server not closed after shutdown", sv.isClosed());
		assertTrue("server has open connections after shutdown", sv.getConnections().length == 0);

		assertTrue("client not closed after shutdown", cl1.isClosed());
		assertFalse("client still connected after shutdown", cl1.isConnected());
		
		// test data arrival
		assertTrue("data from client has not arrived", receptionListener.getSize() > 0);
		byte[] rece = receptionListener.getReceived().get(0);
		assertTrue("data not transferred correctly", Util.equalArrays(data, rece));
		
	} finally {
		if (sv != null) {
			sv.close();
			sv.closeAllConnections();
			Util.sleep(50);
		}
	}
}
	
@Test
public void global_client_list () throws IOException, InterruptedException {
	Server sv = null;
	
	try {
	// test zero state (no connections)
	assertNotNull("no global client list available, initial", JennyNet.getGlobalClientSet());

	sv = new Server(0);
	assertTrue("false initial size of global con-list, 1", JennyNet.getNrOfClients() == 0);
	sv.addListener(new DefaultServerListener() {

		@Override
		public void connectionAvailable(IServer server, ServerConnection connection) {
			try {
				connection.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	});
	
	Client cl1 = new Client();
	assertTrue("false initial size of global con-list after client creation, 1", JennyNet.getNrOfClients() == 0);
	
	Client cl2 = new Client(3045);
	assertTrue("false initial size of global con-list after client creation, 2", JennyNet.getNrOfClients() == 0);
	
	// start server
	sv.start();
	assertTrue("false initial size of global con-lis after server start", JennyNet.getNrOfClients() == 0);
	
	// test in connected states client-1
	cl1.connect(0, sv.getSocketAddress());
	if (cl1.isConnected()) {
		assertTrue("false size of global client list, 1", JennyNet.getNrOfClients() == 1);
		assertNotNull("no global client list available", JennyNet.getGlobalClientSet());
		assertTrue("global list does not contain connection, 1", JennyNet.getGlobalClientSet().get(0) == cl1);
		
	} else {
		fail("unconnected client");
	}

	// test in connected states client-2
	cl2.connect(0, sv.getSocketAddress());
	if (cl2.isConnected()) {
		assertTrue("false size of global client list, 2", JennyNet.getNrOfClients() == 2);
		assertTrue("global list does not contain connection, 1", JennyNet.getGlobalClientSet().contains(cl2));
		
	} else {
		fail("unconnected client");
	}

	// test closing connection client-1
	cl1.closeAndWait(3000);
	if (cl1.isClosed()) {
		assertTrue("false size of global client list, 3", JennyNet.getNrOfClients() == 1);
		assertNotNull("no global client list available", JennyNet.getGlobalClientSet());
		assertFalse("global list contains connection after close", JennyNet.getGlobalClientSet().contains(cl1));
		
	} else {
		fail("client not disconnected: cl1");
	}
	
	// test closing via server close
	sv.closeAndWait(3000);
	Util.sleep(200);
	if (cl2.isClosed()) {
		assertTrue("false size of global client list, was " + JennyNet.getNrOfClients(), JennyNet.getNrOfClients() == 0);
		assertNotNull("no global client list available", JennyNet.getGlobalClientSet());
		assertFalse("global list contains connection after close", JennyNet.getGlobalClientSet().contains(cl2));
		
	} else {
		fail("client not disconnected");
	}
	
	} finally {
		if (sv != null && sv.isAlive()) {
			sv.close();
			sv.closeAllConnections();
			Util.sleep(50);
		}
	}
}

}
