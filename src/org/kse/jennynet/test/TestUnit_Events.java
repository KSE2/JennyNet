/*  File: TestUnit_Events.java
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Timer;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Connection.LayerCategory;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.Util;

public class TestUnit_Events {

	Timer timer = new Timer();
	
	/** Tests existence and information content of SHUTDOWN and CLOSED
	 * events on the given connection.
	 *  
	 * @param con {@code Connection}
	 */
	private void test_shutdown_close_events (Connection con, LayerCategory test, ObjectReceptionListener listener) {
		String orient = con.getCategory().name();
		int reqInfo;
		if (test == LayerCategory.CLIENT) {
			reqInfo = con.getCategory() == LayerCategory.CLIENT ? 0 : 2;
		} else {
			reqInfo = con.getCategory() == LayerCategory.CLIENT ? 2 : 0;
		}
				
		// test CLOSED event 
		List<ConnectionEvent> list = listener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on ".concat(orient), list.size() == 1);
		assertTrue("false 'info' value on ".concat(orient), list.get(0).getInfo() == reqInfo);
		String text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("closed by user") > -1);
		
		// test SHUTDOWN event
		list = listener.getEvents(ConnectionEventType.SHUTDOWN);
		assertTrue("no shutdown-event on ".concat(orient), list.size() == 1);
		assertTrue("expected info = 0", list.get(0).getInfo() == reqInfo);
		text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("closed by user") > -1);
		
//		// test CLOSED event (server)
//		list = svListener.getEvents(ConnectionEventType.CLOSED);
//		assertTrue("no close-event on server", list.size() == 1);
//		int info = list.get(0).getInfo();
//		assertTrue("false info value in server CLOSED event: " + info, info == 2);
//		text = list.get(0).getText();
//		assertNotNull("expected event-text", text);
//		assertTrue("expected local event text", text.indexOf("closed by user") > -1);
//		
//		// test SHUTDOWN event (server)
//		list = svListener.getEvents(ConnectionEventType.SHUTDOWN);
//		assertTrue("no shutdown-event on server", list.size() == 1);
//		assertTrue("expected info = 0", list.get(0).getInfo() == 0);
//		text = list.get(0).getText();
//		assertNotNull("expected event-text", text);
//		assertTrue("expected local event text", text.indexOf("closed by user") > -1);
		
	}
	
	@Test
	public void client_close () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon;
		
		final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnections()[0];
		
		// send an object
		byte[] data = Util.randBytes(200000);
		cl.sendData(data, 0, data.length, SendPriority.NORMAL);
		
		// close connection and wait
		cl.close();
		long mark = System.currentTimeMillis();
		cl.waitForClosed(2000);
		scon.waitForClosed(2000);
		long time = System.currentTimeMillis() - mark;
		
		// test client state
		assertTrue("client not closed after shutdown", cl.isClosed());
		assertFalse("client still connected after shutdown", cl.isConnected());
		
		// test server-con state
		assertTrue("server-con not closed after shutdown", scon.isClosed());
		assertFalse("server-con still connected after shutdown", scon.isConnected());
		
		test_shutdown_close_events(cl, LayerCategory.CLIENT, clListener);
		test_shutdown_close_events(scon, LayerCategory.CLIENT, svListener);
		
		// test for object received
		assertTrue("object not delivered to remote", svListener.getSize() == 1);

		// test for proper sequence of events
		List<ConnectionEvent> list = svListener.getEvents();
		assertTrue("CLOSED event out of sequence", list.get(list.size()-1).getType() == ConnectionEventType.CLOSED);
		
		System.out.println("-- waited for CLOSED: " + time + " ms");
		
		// shutdown net systems
		} catch (InterruptedException e) {
			fail("wait interrupted");
			
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
		}
	}
	
	@Test
	public void server_con_close () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon;
		
		final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionSpeed(50000);
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnections()[0];
		
		// send an object
		byte[] data = Util.randBytes(200000);
		cl.sendData(data, 0, data.length, SendPriority.NORMAL);
		
		// close connection and wait
		scon.close();
		long mark = System.currentTimeMillis();
		scon.waitForClosed(10000);
		cl.waitForClosed(10000);
		long time = System.currentTimeMillis() - mark;
		
		// test server-con state
		assertTrue("server-con not closed after shutdown", scon.isClosed());
		assertFalse("server-con still connected after shutdown", scon.isConnected());
		
		// test client state
		assertTrue("client not closed after shutdown", cl.isClosed());
		assertFalse("client still connected after shutdown", cl.isConnected());
		
		test_shutdown_close_events(cl, LayerCategory.SERVER, clListener);
		test_shutdown_close_events(scon, LayerCategory.SERVER, svListener);
		
		// test for proper sequence of events
		List<ConnectionEvent> list = svListener.getEvents();
		assertTrue("CLOSED event out of sequence", list.get(list.size()-1).getType() == ConnectionEventType.CLOSED);
		
		// test for object received
		assertTrue("object not delivered to remote", svListener.getSize() == 1);
		
		System.out.println("-- waited for CLOSED: " + time + " ms");
		
		// shutdown net systems
		} catch (InterruptedException e) {
			fail("wait interrupted");
			
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
		}
	}

	@Test
	public void server_shutdown_text () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon;
		
		final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionSpeed(100000);
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnections()[0];
		
		// send an object
		byte[] data = Util.randBytes(200000);
		cl.sendData(data, 0, data.length, SendPriority.NORMAL);
		
		// close all server connections (server shutdown)
		long mark = System.currentTimeMillis();
		String closeReason = "ein gummibär";
		sv.closeAllConnections(closeReason);
		sv.waitForAllClosed(10000);
		cl.waitForClosed(2000);
		long time = System.currentTimeMillis() - mark;
		
		// test server-con state
		assertTrue("server-con not closed after shutdown", scon.isClosed());
		assertFalse("server-con still connected after shutdown", scon.isConnected());
		
		// test client state
		assertTrue("client not closed after shutdown", cl.isClosed());
		assertFalse("client still connected after shutdown", cl.isConnected());
		
		// test CLOSED event (client)
		List<ConnectionEvent> list = clListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on client", list.size() == 1);
		assertTrue("false info value", list.get(0).getInfo() == 3);
		String text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf(closeReason) > -1);
		
		// test CLOSED event (server)
		list = svListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on server", list.size() == 1);
		int info = list.get(0).getInfo();
		assertTrue("false info value in server CLOSED event: " + info, info == 1);
		text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf(closeReason) > -1);
		
		// test for object received
		assertTrue("object not delivered to remote", svListener.getSize() == 1);
		
		// test for proper sequence of events
		System.out.println("-- waited for CLOSED: " + time + " ms");
		
		// shutdown net systems
		} catch (InterruptedException e) {
			fail("wait interrupted");
			
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
		}
	}

	@Test
	public void server_shutdown_plain () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon;
		
		final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
//		cl.getParameters().setTransmissionSpeed(100000);
		cl.getParameters().setTransmissionParcelSize(16*1024);
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnections()[0];
		
		// send an object
		byte[] data = Util.randBytes(200000);
		cl.sendData(data, 0, data.length, SendPriority.NORMAL);
		
		// close all server connections (server shutdown)
		long mark = System.currentTimeMillis();
		sv.closeAllConnections();
		sv.waitForAllClosed(10000);
		cl.waitForClosed(2000);
		long time = System.currentTimeMillis() - mark;
		
		// test server-con state
		assertTrue("server-con not closed after shutdown", scon.isClosed());
		assertFalse("server-con still connected after shutdown", scon.isConnected());
		
		// test client state
		assertTrue("client not closed after shutdown", cl.isClosed());
		assertFalse("client still connected after shutdown", cl.isConnected());
		
		// test CLOSED event (client)
		List<ConnectionEvent> list = clListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on client", list.size() == 1);
		assertTrue("false info value", list.get(0).getInfo() == 3);
		String text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected other shutdown event text", text.indexOf("remote server shutdown") > -1);
		
		// test CLOSED event (server)
		list = svListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on server", list.size() == 1);
		int info = list.get(0).getInfo();
		assertTrue("false info value in server CLOSED event: " + info, info == 1);
		text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("local server shutdown") > -1);
		
		// test for object received
		assertTrue("object not delivered to remote", svListener.getSize() == 1);
		
		// test for proper sequence of events
		System.out.println("-- waited for CLOSED: " + time + " ms");
		
		// shutdown net systems
		} catch (InterruptedException e) {
			fail("wait interrupted");
			
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
		}
	}

	@Test
	public void client_close_hard () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon;
		
		final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionSpeed(30000);
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnections()[0];
		
		// send an object
		byte[] data = Util.randBytes(200000);
		cl.sendData(data, 0, data.length, SendPriority.NORMAL);
		Util.sleep(1000);
		
		// hard-close connection and wait
		cl.closeHard();
		long mark = System.currentTimeMillis();
		cl.waitForClosed(3000);
		scon.waitForClosed(3000);
		long time = System.currentTimeMillis() - mark;
		assertTrue("wait-for-close unfinished: " + time, time <= 1000);
		System.out.println("-- waited for CLOSED: " + time + " ms");
		
		// test client state
		assertTrue("client not closed after shutdown", cl.isClosed());
		assertFalse("client still connected after shutdown", cl.isConnected());
		
		// test server-con state
		assertTrue("server-con not closed after shutdown", scon.isClosed());
		assertFalse("server-con still connected after shutdown", scon.isConnected());
		
		// test client CLOSED event 
		List<ConnectionEvent> list = clListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on client", list.size() == 1);
		assertTrue("false 'info' value on client", list.get(0).getInfo() == 10);
		String text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("closed hardly") > -1);
		
		// test client SHUTDOWN event
		list = svListener.getEvents(ConnectionEventType.SHUTDOWN);
		assertTrue("unexpected shutdown-event on client", list.size() == 0);
		
		// test for proper sequence of events
		list = clListener.getEvents();
		assertTrue("CLOSED event out of sequence", list.get(list.size()-1).getType() == ConnectionEventType.CLOSED);

		// test CLOSED event (server)
		list = svListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on server", list.size() == 1);
		int info = list.get(0).getInfo();
		assertTrue("false info value in server CLOSED event: " + info, info == 2);
		text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("closed hardly") > -1);

		// test for proper sequence of events
		list = svListener.getEvents();
		assertTrue("CLOSED event out of sequence", list.get(list.size()-1).getType() == ConnectionEventType.CLOSED);
		
		// test for object received
		assertTrue("object should not be received by remote", svListener.getSize() == 0);
	
		
		// shutdown net systems
		} catch (InterruptedException e) {
			fail("wait interrupted");
			
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
		}
	}

	private class SocketClient extends Client {

		@Override
		public Socket getSocket() {
			return super.getSocket();
		}
	}
	
	@Test
	public void socket_failure () throws IOException, InterruptedException {
		Server sv = null;
		SocketClient cl = null;
		Connection scon;
		
		final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new SocketClient();
		cl.getParameters().setTransmissionSpeed(50000);
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnections()[0];
		
		// send an object
		byte[] data = Util.randBytes(200000);
		cl.sendData(data, 0, data.length, SendPriority.NORMAL);
		Util.sleep(1500);
		
		// inject socket-closure
		cl.getSocket().close();
		long mark = System.currentTimeMillis();
		cl.waitForClosed(2000);
		scon.waitForClosed(2000);
		long time = System.currentTimeMillis() - mark;
		
		// test client state
		assertTrue("client not closed after shutdown", cl.isClosed());
		assertFalse("client still connected after shutdown", cl.isConnected());
		
		// test server-con state
		assertTrue("server-con not closed after shutdown", scon.isClosed());
		assertFalse("server-con still connected after shutdown", scon.isConnected());
		
		// test client CLOSED event 
		List<ConnectionEvent> list = clListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on client", list.size() == 1);
		assertTrue("false 'info' value on client", list.get(0).getInfo() == 6);
		String text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("SocketException") > -1 || text.indexOf("EOFException") > -1);
		
		// test client SHUTDOWN event
		list = clListener.getEvents(ConnectionEventType.SHUTDOWN);
		assertTrue("unexpected shutdown-event on client", list.size() == 0);
		
		// test for proper sequence of events
		list = clListener.getEvents();
		assertTrue("CLOSED event out of sequence", list.get(list.size()-1).getType() == ConnectionEventType.CLOSED);
	
		// test CLOSED event (server)
		list = svListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no close-event on server: " + list.size(), list.size() == 1);
		int info = list.get(0).getInfo();
		assertTrue("false info value in server CLOSED event: " + info, info == 6);
		text = list.get(0).getText();
		assertNotNull("expected event-text", text);
		assertTrue("expected local event text", text.indexOf("SocketException") > -1 || text.indexOf("EOFException") > -1);
	
		// test for proper sequence of events
		list = svListener.getEvents();
		assertTrue("CLOSED event out of sequence", list.get(list.size()-1).getType() == ConnectionEventType.CLOSED);
		
		// test for object received
		assertTrue("object should not be received by remote", svListener.getSize() == 0);
	
		System.out.println("-- waited for CLOSED: " + time + " ms");
		
		// shutdown net systems
		} catch (InterruptedException e) {
			fail("wait interrupted");
			
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
		}
	}
	
	
}
