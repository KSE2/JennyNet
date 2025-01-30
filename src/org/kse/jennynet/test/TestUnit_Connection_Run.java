/*  File: TestUnit_Connection_Run.java
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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.JennyNetByteBuffer;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.exception.ClosedConnectionException;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEventType;
import org.kse.jennynet.intfa.Connection.ConnectionState;
import org.kse.jennynet.intfa.Connection.LayerCategory;
import org.kse.jennynet.poll.ConnectionPollService;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.Util;

public class TestUnit_Connection_Run {

	private ConnectionParameters param;
	
	public TestUnit_Connection_Run() {
		param = JennyNet.getConnectionParameters();
		param.setTransmissionParcelSize(8*1024);
		param.setTransmissionSpeed(15000);
	}

	@Test
	public void close_shutdown_bare () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
//		final Object objectLock = new Object();
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		assertFalse(cl.isClosed());
		
		// close non-active connection
		cl.close();
		cl.waitForClosed(5000);
		scon.waitForClosed(5000);
		
		assertTrue("connection not closed", cl.isClosed());
		assertFalse("connection still conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection still idle", cl.isIdle());
		assertFalse("connection still transmitting", cl.isTransmitting());
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(cl.getMonitor().lastReceiveTime > 0);
		assertTrue(clObjectListener.getEvents().size() == 3);
		assertTrue(svObjectListener.getEvents().size() == 3);
		
		// interface locking
		try {
			cl.sendData(new byte[1000], SendPriority.NORMAL);
			fail("expected ClosedConnectionException");
		} catch (ClosedConnectionException e) {
		}

		try {
			// transmit file
			File src = Util.getTempFile(); 
			Util.makeFile(src, null);
			String targetPath = "empfang/ursula-1.dat";
			cl.sendFile(src, targetPath);
			fail("expected ClosedConnectionException");
		} catch (ClosedConnectionException e) {
		}
		
		clObjectListener.reportEvents();
		svObjectListener.reportEvents();
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAllConnections();
				sv.close();
				Util.sleep(10);
			}
			if (cl != null) {
				cl.close();
			}
		}
	}
	
	@Test
	public void close_shutdown_objects () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
//		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		assertFalse(cl.isClosed());
		
		// client sends 3 larger byte arrays to server
		// prepare 3 random data blocks to transmit
		int dataLen1 = 100000;
		byte[] block1 = Util.randBytes(dataLen1);
		int dataLen2 = 50000;
		byte[] block2 = Util.randBytes(dataLen2);
		int dataLen3 = 20000;
		byte[] block3 = Util.randBytes(dataLen3);

		cl.setTempo(50000);
		
		// send over connection
//		long time = System.currentTimeMillis();
		cl.sendData(block1, SendPriority.NORMAL);
		cl.sendData(block2, SendPriority.NORMAL);
		cl.sendData(block3, SendPriority.NORMAL);
		System.out.println("\n## orders put");
		
		// close connection
		cl.close();
		Util.sleep(50);
		
		assertTrue("connection closed", !cl.isClosed());
		assertTrue("connection not conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection is idle", cl.isIdle());
		assertTrue("connection not in transmission", cl.isTransmitting());
		assertTrue("not in SHUTDOWN state", cl.getOperationState() == ConnectionState.SHUTDOWN);
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(scon.getMonitor().lastReceiveTime > 0);

		// interface locking
		try {
			// send byte block 
			cl.sendData(new byte[1000], SendPriority.NORMAL);
			fail("expected ClosedConnectionException");
		} catch (ClosedConnectionException e) {
		}

		try {
			// transmit file
			File src = Util.getTempFile(); 
			Util.makeFile(src, null);
			String targetPath = "empfang/ursula-1.dat";
			cl.sendFile(src, targetPath);
			fail("expected ClosedConnectionException");
		} catch (ClosedConnectionException e) {
		}

		System.out.println("\n## waiting for disconnect ..");
		cl.waitForClosed(10000);
		scon.waitForClosed(10000);
		Util.sleep(50);
		
		assertTrue("connection not closed", cl.isClosed());
		assertFalse("connection still conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection is idle", cl.isIdle());
		assertFalse("connection not in transmission", cl.isTransmitting());
		assertTrue("not in CLOSED state", cl.getOperationState() == ConnectionState.CLOSED);
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(cl.getMonitor().lastReceiveTime > 0);
		assertTrue(clObjectListener.getEvents().size() == 3);
		assertTrue(svObjectListener.getEvents().size() == 6);
		assertTrue(svObjectListener.getReceived().size() == 3);
		
		
		clObjectListener.reportEvents();
		svObjectListener.reportEvents();
		
		// control received object data
		byte[] rec1 = svObjectListener.getReceived().get(0);
		assertTrue("error in received data 1", Util.equalArrays(rec1, block1));
		byte[] rec2 = svObjectListener.getReceived().get(1);
		assertTrue("error in received data 2", Util.equalArrays(rec2, block2));
		byte[] rec3 = svObjectListener.getReceived().get(2);
		assertTrue("error in received data 3", Util.equalArrays(rec3, block3));
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAllConnections();
				sv.close();
				Util.sleep(10);
			}
		}
	}
	
	@Test
	public void close_shutdown_files () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
		FileReceptionListener svReceptionListener = new FileReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svReceptionListener);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		assertFalse(cl.isClosed());
		
		// client sends 3 larger byte arrays to server
		// prepare 3 random data blocks to transmit
		int dataLen1 = 100000;
		byte[] block1 = Util.randBytes(dataLen1);
		int dataLen2 = 50000;
		byte[] block2 = Util.randBytes(dataLen2);
		int dataLen3 = 20000;
		byte[] block3 = Util.randBytes(dataLen3);

		cl.setTempo(50000);
		long time = System.currentTimeMillis();
		
		// send over connection
		File src1 = Util.getTempFile(); 
		Util.makeFile(src1, block1);
		String targetPath1 = "empfang/ursula-1.dat";
		cl.sendFile(src1, targetPath1);

		File src2 = Util.getTempFile(); 
		Util.makeFile(src2, block2);
		String targetPath2 = "empfang/corinna-2.dat";
		cl.sendFile(src2, targetPath2);
		
		File src3 = Util.getTempFile(); 
		Util.makeFile(src3, block3);
		String targetPath3 = "empfang/manuela-3.dat";
		cl.sendFile(src3, targetPath3);
		
		System.out.println("\n## orders put");
		
		// close connection
		cl.close();
		Util.sleep(50);
		
		System.out.println("\n## waiting for disconnect ..");
		cl.waitForClosed(10000);
		scon.waitForClosed(10000);
		Util.sleep(20);
		time = System.currentTimeMillis() - time;
		
		assertTrue("connection not closed", cl.isClosed());
		assertFalse("connection still conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection is idle", cl.isIdle());
		assertFalse("connection not in transmission", cl.isTransmitting());
		assertTrue("not in CLOSED state", cl.getOperationState() == ConnectionState.CLOSED);
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(cl.getMonitor().lastReceiveTime > 0);
		assertTrue(clObjectListener.getEvents().size() == 9);
		assertTrue(svReceptionListener.getEvents().size() == 6);
		assertTrue(svReceptionListener.getReceived().size() == 3);
		
		byte[][] results = new byte[3][];

		// control received file content
		results[0] = Util.readFile(svReceptionListener.getReceived().get(0));
		results[1] = Util.readFile(svReceptionListener.getReceived().get(1));
		results[2] = Util.readFile(svReceptionListener.getReceived().get(2));

		assertTrue("data integrity error in file-1 transmission", containsData(results, block1));
		assertTrue("data integrity error in file-2 transmission", containsData(results, block2));
		assertTrue("data integrity error in file-3 transmission", containsData(results, block3));
		System.out.println("transmission time = " + time);
		assertTrue("bad transmission time: " + time, time > 3400 & time < 4500);

		clObjectListener.reportEvents();
		svReceptionListener.reportEvents();
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(0);
			}
		}
	}
	
	@Test
	public void wait_termination () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		
		// test waiting in UNCONNECTED state
		// no wait-time for disconnected
		long timeStart = System.currentTimeMillis(); 
		cl.waitForDisconnect(2000);
		long timeEnd = System.currentTimeMillis();
		Util.sleep(50);
		assertTrue(timeEnd - timeStart < 50);
		assertTrue(clObjectListener.getEvents().isEmpty());
		
		// full wait-time for CLOSED
		timeStart = System.currentTimeMillis();
		cl.waitForClosed(2000);
		timeEnd = System.currentTimeMillis();
		Util.sleep(50);
		assertTrue(timeEnd - timeStart >= 2000);
		assertTrue(clObjectListener.getEvents().size() == 1);
		assertTrue(clObjectListener.getEvents().get(0).getType() == ConnectionEventType.CLOSED);
		assertTrue(cl.getOperationState() == ConnectionState.CLOSED);
		
		// connect to server
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		clObjectListener.reset();
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		assertFalse(cl.isClosed());
		assertTrue(clObjectListener.getEvents().size() == 1);
		
		// test waiting in CONNECTED state
		// full wait-time followed by disconnection
		timeStart = System.currentTimeMillis(); 
		cl.waitForDisconnect(2000);
		timeEnd = System.currentTimeMillis();
		Util.sleep(50);
		assertTrue(timeEnd - timeStart >= 2000);
		assertTrue(cl.isClosed());
		assertTrue(cl.getOperationState() == ConnectionState.CLOSED);
		assertFalse(cl.isConnected());
		assertTrue(clObjectListener.getEvents().size() == 2);
		assertTrue(clObjectListener.getEvents().get(1).getType() == ConnectionEventType.CLOSED);
		
		
		// shutdown net systems
		} finally {
			System.out.println("\n--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAllConnections();
				sv.close();
				Util.sleep(10);
			}
		}
	}	
	
	/** Whether 'base' contains 'data' by comparison of byte values 
	 * (equal-arrays).
	 * 
	 * @param base byte[][]
	 * @param data byte[]
	 * @return boolean
	 */
	private boolean containsData (byte[][] base, byte[] data) {
		for (byte[] token : base) {
			if (Util.equalArrays(token, data)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void close_shutdown_files_unlimited () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
		FileReceptionListener svReceptionListener = new FileReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svReceptionListener);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		assertFalse(cl.isClosed());
		
		// client sends 3 larger byte arrays to server
		// prepare 3 random data blocks to transmit
		int dataLen1 = 100000;
		byte[] block1 = Util.randBytes(dataLen1);
		int dataLen2 = 50000;
		byte[] block2 = Util.randBytes(dataLen2);
		int dataLen3 = 20000;
		byte[] block3 = Util.randBytes(dataLen3);
		long time = System.currentTimeMillis();
		
		// send over connection
		File src1 = Util.getTempFile(); 
		Util.makeFile(src1, block1);
		String targetPath1 = "empfang/ursula-1.dat";
		cl.sendFile(src1, targetPath1);
	
		File src2 = Util.getTempFile(); 
		Util.makeFile(src2, block2);
		String targetPath2 = "empfang/corinna-2.dat";
		cl.sendFile(src2, targetPath2);
		
		File src3 = Util.getTempFile(); 
		Util.makeFile(src3, block3);
		String targetPath3 = "empfang/manuela-3.dat";
		cl.sendFile(src3, targetPath3);
		
		System.out.println("\n## orders put");
		
		// close connection
		cl.close();
		Util.sleep(30);
		
		System.out.println("\n## waiting for disconnect ..");
		cl.waitForClosed(5000);
		scon.waitForClosed(5000);
		time = System.currentTimeMillis() - time;
		Util.sleep(20);
		
		assertTrue("connection not closed", cl.isClosed());
		assertFalse("connection still conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection is idle", cl.isIdle());
		assertFalse("connection not in transmission", cl.isTransmitting());
		assertTrue("not in CLOSED state", cl.getOperationState() == ConnectionState.CLOSED);
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(cl.getMonitor().lastReceiveTime > 0);
		assertTrue(clObjectListener.getEvents().size() == 9);
		assertTrue(svReceptionListener.getEvents().size() == 6);
		assertTrue(svReceptionListener.getReceived().size() == 3);
		
		byte[][] results = new byte[3][];
	
		// control received file content
		results[0] = Util.readFile(svReceptionListener.getReceived().get(0));
		results[1] = Util.readFile(svReceptionListener.getReceived().get(1));
		results[2] = Util.readFile(svReceptionListener.getReceived().get(2));
	
		assertTrue("data integrity error in file-1 transmission", containsData(results, block1));
		assertTrue("data integrity error in file-2 transmission", containsData(results, block2));
		assertTrue("data integrity error in file-3 transmission", containsData(results, block3));
		System.out.println("transmission time = " + time);
	
		clObjectListener.reportEvents();
		svReceptionListener.reportEvents();
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(0);
			}
		}
	}

	@Test
	public void close_shutdown_mixed () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
//		final Object objectLock = new Object();
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
	
	try {
		ConnectionListener[] svLisArr = new ConnectionListener[] {svObjectListener, fileRecListener};
		
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svLisArr);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		assertFalse(cl.isClosed());
		
		// client sends 3 larger byte arrays to server
		// prepare 3 random data blocks to transmit
		int dataLen1 = 100000;
		byte[] block1 = Util.randBytes(dataLen1);
		int dataLen2 = 50000;
		byte[] block2 = Util.randBytes(dataLen2);
		int dataLen3 = 20000;
		byte[] block3 = Util.randBytes(dataLen3);

		cl.setTempo(50000);
		long time = System.currentTimeMillis();
		
		// send over connection
		File src1 = Util.getTempFile(); 
		Util.makeFile(src1, block1);
		String targetPath1 = "empfang/ursula-1.dat";
		cl.sendFile(src1, targetPath1);
		cl.sendData(block1, SendPriority.NORMAL);

		File src2 = Util.getTempFile(); 
		Util.makeFile(src2, block2);
		String targetPath2 = "empfang/corinna-2.dat";
		cl.sendFile(src2, targetPath2);
		cl.sendData(block2, SendPriority.NORMAL);
		
		File src3 = Util.getTempFile(); 
		Util.makeFile(src3, block3);
		String targetPath3 = "empfang/manuela-3.dat";
		cl.sendFile(src3, targetPath3);
		cl.sendData(block3, SendPriority.NORMAL);
		
		System.out.println("\n## orders put");
		
		// close connection
		cl.close();
		Util.sleep(50);
//			cl.closeAndWait(60000);
		
		assertTrue("connection closed", !cl.isClosed());
		assertTrue("connection not conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection is idle", cl.isIdle());
		assertTrue("connection not in transmission", cl.isTransmitting());
		assertTrue("not in SHUTDOWN state", cl.getOperationState() == ConnectionState.SHUTDOWN);
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(scon.getMonitor().lastReceiveTime > 0);

		// interface locking
		try {
			// send byte block 
			cl.sendData(new byte[1000], SendPriority.NORMAL);
			fail("expected ClosedConnectionException");
		} catch (ClosedConnectionException e) {
		}

		try {
			// transmit file
			File src = Util.getTempFile(); 
			Util.makeFile(src, null);
			String targetPath = "empfang/ursula-1.dat";
			cl.sendFile(src, targetPath);
			fail("expected ClosedConnectionException");
		} catch (ClosedConnectionException e) {
		}

		System.out.println("\n## waiting for disconnect ..");
		cl.waitForClosed(10000);
		scon.waitForClosed(10000);
		Util.sleep(20);
		
		assertTrue("connection not closed", cl.isClosed());
		assertFalse("connection still conneted", cl.isConnected());
		assertTrue("connection not bound", cl.isBound());
		assertFalse("connection is idle", cl.isIdle());
		assertFalse("connection not in transmission", cl.isTransmitting());
		assertTrue("not in CLOSED state", cl.getOperationState() == ConnectionState.CLOSED);
		assertTrue(cl.getMonitor().lastSendTime > 0);
		assertTrue(cl.getMonitor().lastReceiveTime > 0);
		assertTrue(clObjectListener.getEvents().size() == 9);
		assertTrue(svObjectListener.getEvents().size() == 12);
		assertTrue(svObjectListener.getReceived().size() == 3);
		assertTrue(fileRecListener.getReceived().size() == 3);
		
		clObjectListener.reportEvents();
		svObjectListener.reportEvents();
		
		// control received object data
		byte[] rec1 = svObjectListener.getReceived().get(0);
		assertTrue("error in received data 1", Util.equalArrays(rec1, block1));
		byte[] rec2 = svObjectListener.getReceived().get(1);
		assertTrue("error in received data 2", Util.equalArrays(rec2, block2));
		byte[] rec3 = svObjectListener.getReceived().get(2);
		assertTrue("error in received data 3", Util.equalArrays(rec3, block3));
		
		byte[][] results = new byte[3][];
		results[0] = Util.readFile(fileRecListener.getReceived().get(0));
		results[1] = Util.readFile(fileRecListener.getReceived().get(1));
		results[2] = Util.readFile(fileRecListener.getReceived().get(2));

		assertTrue("data integrity error in file-1 transmission", containsData(results, block1));
		assertTrue("data integrity error in file-2 transmission", containsData(results, block2));
		assertTrue("data integrity error in file-3 transmission", containsData(results, block3));
		
		time = System.currentTimeMillis() - time;
		System.out.println("transmission time = " + time);
		assertTrue("bad transmission time: " + time, time > 6000 & time < 8000);

		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAllConnections();
				sv.close();
				Util.sleep(10);
			}
		}
	}

	@Test
	public void idle_reporting () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
	
	try {
		ConnectionListener[] svLisArr = new ConnectionListener[] {svObjectListener, fileRecListener};
		
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svLisArr);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.getParameters().setIdleCheckPeriod(5000);
		sv.getParameters().setIdleThreshold(1000);
		sv.getParameters().setAlivePeriod(7000);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		
		cl.sendData(Util.randBytes(5000), SendPriority.NORMAL);
		System.out.println("--- sleeping 1 minute");
		Util.sleep(12000);
		
		cl.sendData(Util.randBytes(5000), SendPriority.NORMAL);
		Util.sleep(12000);
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(3000);
			}
			if (cl != null) {
				cl.waitForClosed(2000);
			}
			clObjectListener.reportEvents();
			svObjectListener.reportEvents();
		}
	}

	@Test
	public void ping () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.getParameters().setIdleCheckPeriod(5000);
		sv.getParameters().setIdleThreshold(10000);
		sv.getParameters().setAlivePeriod(8000);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.getParameters().setAlivePeriod(5000);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		
		// send PINGs
		long pingCli = cl.sendPing();
		long pingID = scon.sendPing();
		assertTrue("no or bad PING-ID", pingCli > 0);
		assertTrue("no or bad PING-ID", pingID > 0);
		Util.sleep(100);
		
		// control PING answer 
		List<ConnectionEvent> events = svObjectListener.getEvents(ConnectionEventType.PING_ECHO);
		assertTrue("no PING echo received", events.size() == 1);
		ConnectionEvent evt = events.get(0);
		assertTrue("incorrect PING-ID in echo", evt.getObjectNr() == pingID);
		assertTrue("implausible PING runtime", evt.getInfo() >= 0);
		System.out.println("---- PING runtime = " + evt.getInfo() + " ms");

		// transfer an object (server)
		scon.sendData(Util.randBytes(5000), SendPriority.NORMAL);
		Util.sleep(20);
		
		// CASE: second PING in a short time -> not sent
		pingCli = cl.sendPing();
		pingID = scon.sendPing();
		assertTrue("second PING should not occur (client)", pingCli == -1);
		assertTrue("second PING should not occur (server)", pingID == -1);
		Util.sleep(5000)		;
		
		// CASE: continue new PING, increment of ID
		
		// send PINGs from both sides
		pingCli = cl.sendPing();
		pingID = scon.sendPing();
		Util.sleep(20);
		
		assertTrue("no PING-ID", pingCli != 0);
		assertTrue("bad PING-ID (no increment)", pingID == 2);

		// control Client PING-echo
		events = clObjectListener.getEvents(ConnectionEventType.PING_ECHO);
		assertTrue("no PING echo received (client)", events.size() == 2);
		evt = events.get(1);
		assertTrue("incorrect PING-ID in echo", evt.getObjectNr() == pingCli);
		assertTrue("implausible PING runtime: " + evt.getInfo(), evt.getInfo() >= 0);
		
		// control Server PING-echo
		events = svObjectListener.getEvents(ConnectionEventType.PING_ECHO);
		assertTrue("no PING echo received (client)", events.size() == 2);
		evt = events.get(1);
		assertTrue("incorrect PING-ID in echo", evt.getObjectNr() == pingID);
		assertTrue("implausible PING runtime", evt.getInfo() >= 0);
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(3000);
			}
			if (cl != null) {
				cl.waitForClosed(2000);
			}
			clObjectListener.reportEvents();
			svObjectListener.reportEvents();
		}
	}

	/** This class performs event polling from a given {@code Connection}
	 * by application of the service defined in {@code ConnectionPollService}.
	 * Received events are reported to the console by reference to the poll
	 * service.
	 */
	private class Pollinator {
		private Connection connection;
		private ConnectionPollService service;
		private List<ConnectionEvent> eventList = new ArrayList<>();
		boolean localService;
		boolean terminate;
		
		private Thread poller = new Thread("Test Poller in Connection_Run") {
			@Override
			public void run() {
				while (!terminate) {
					try {
						ConnectionEvent evt = service.take();
						eventList.add(evt);
						String text = "+++ POLL-EVENT: " + evt;
						if (connection.getMonitor().category == LayerCategory.SERVER) {
							System.out.println(text);
						} else {
							System.err.println(text);
						}
						
					} catch (InterruptedException e) {
						System.out.println("*** POLLING is INTERRUPTED! " + e);
					}
				}
			}
		};

		Pollinator (Connection con) {
			this(new ConnectionPollService(con));
			localService = true;
		}
		
		Pollinator (ConnectionPollService service) {
			Objects.requireNonNull(service);
			this.service = service;
			this.connection = service.getConnection();
			poller.start();
		}
		
		public void terminate () {
			terminate = true;
			poller.interrupt();
			if (localService) {
				service.close();
			}
		}

		/** The number of connection-events store in this pollinator.
		 * 
		 * @return int nr of events
		 */
		public int stored () {return eventList.size();}
		
		/** Whether an event of the given type has been received.
		 * 
		 * @param type ConnectionEventType
		 * @return boolean
		 */
		public boolean hasEvent (ConnectionEventType type) {
			return getEvents(type).size() > 0;
		}
		
		/** Whether an event of the given transmission-event type has been 
		 * received.
		 * 
		 * @param type TransmissionEventType
		 * @return boolean
		 */
		public boolean hasEvent (TransmissionEventType type) {
			List<ConnectionEvent> list = getEvents(ConnectionEventType.TRANS_EVT);
			for (ConnectionEvent evt : list) {
				if (evt.getTransmissionEvent().getType() == type) {
					return true;
				}
			}
			return false;
		}
		
		/** Returns the list of events which occurred on this listener.
		 * 
		 * @return {@code List<TransmissionEvent>}
		 */
		public List<ConnectionEvent> getEvents () {
			return new ArrayList<ConnectionEvent>(eventList);
		}

		/** Returns a subset of connection-events of the given type 
		 * occurring on this listener, in the order of their appearance.
		 * 
		 * @param type {@code ConnectionEvent} selection event-type
		 * @return {@code List<ConnectionEvent>}
		 */
		public List<ConnectionEvent> getEvents (ConnectionEventType type) {
			Objects.requireNonNull(type);
			List<ConnectionEvent> list = new ArrayList<>();
			for (ConnectionEvent evt : getEvents()) {
				if (evt.getType() == type) {
					list.add(evt);
				}
			}
			return list;
		}
	}
	
	@Test
	public void polling_com () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		Pollinator poller = null;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.getParameters().setIdleCheckPeriod(5000);
		sv.getParameters().setIdleThreshold(10000);
		sv.getParameters().setAlivePeriod(8000);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setFileRootDir(new File("test/client1"));
		
		poller = new Pollinator(cl);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);

		// send PING
		long pingID = cl.sendPing();
		assertTrue("no or bad PING-ID", pingID > 0);
		Util.sleep(20);
		
		// transfer an object (server)
		byte[] data1 = Util.randBytes(6000);
		scon.sendData(data1, SendPriority.NORMAL);
		Util.sleep(50);
		
		// transfer an object (server)
		byte[] data2 = Util.randBytes(10000);
		scon.sendData(data2, SendPriority.NORMAL);
		Util.sleep(50);
		
		// transfer a file
		byte[] data3 = Util.randBytes(6000);
		File src1 = Util.getTempFile(); 
		Util.makeFile(src1, data3);
		scon.sendFile(src1, "schweine.dat");
		
		// verify events received
		cl.closeAndWait(10000);
		assertTrue("no events received from polling", poller.stored() > 0);
		assertTrue("bad number of events received: " + poller.stored(), poller.stored() == 8);
		assertTrue("event missing in poll-service", poller.hasEvent(ConnectionEventType.CONNECTED));
		assertTrue("event missing in poll-service", poller.hasEvent(ConnectionEventType.SHUTDOWN));
		assertTrue("event missing in poll-service", poller.hasEvent(ConnectionEventType.CLOSED));
		assertTrue("event missing in poll-service", poller.hasEvent(ConnectionEventType.OBJECT));
		assertTrue("event missing in poll-service", poller.hasEvent(ConnectionEventType.PING_ECHO));
		assertTrue("event missing in poll-service", poller.hasEvent(TransmissionEventType.FILE_INCOMING));
		assertTrue("event missing in poll-service", poller.hasEvent(TransmissionEventType.FILE_RECEIVED));
		
		// verify objects received
		List<ConnectionEvent> list = poller.getEvents(ConnectionEventType.OBJECT);
		assertTrue("missing OBJECT event", list.size() == 2);
		byte[][] darr = new byte[2][];
		darr[0] = ((JennyNetByteBuffer) list.get(0).getObject()).getData();
		darr[1] = ((JennyNetByteBuffer) list.get(1).getObject()).getData();
		assertTrue("object data error", containsData(darr, data1));
		assertTrue("object data error", containsData(darr, data2));
		
		// verify file received
		list = poller.getEvents(ConnectionEventType.TRANS_EVT);
		File recF = list.get(1).getTransmissionEvent().getFile();
		assertTrue("receive-file name error", recF.getName().equals("schweine.dat"));
		assertTrue("receive file data error", Util.equalArrays(data3, Util.readFile(recF)));
		
		System.out.println( cl.getMonitor().report(4) );
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(3000);
			}
			if (cl != null) {
				cl.waitForClosed(2000);
			}
			clObjectListener.reportEvents();
			svObjectListener.reportEvents();
			poller.terminate();
		}
	}
	
	@Test
	public void polling_run () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		Pollinator poller1 = null;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.getParameters().setIdleCheckPeriod(5000);
		sv.getParameters().setIdleThreshold(10000);
		sv.getParameters().setAlivePeriod(8000);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setFileRootDir(new File("test/client1"));

		// initial poll-service
		ConnectionPollService poll = new ConnectionPollService(cl);
		assertTrue(poll.getConnection() == cl);
		assertTrue(poll.isListening());
		assertTrue(poll.isEmpty());
		assertTrue(poll.available() == 0);
		assertTrue(poll.getCapacity() == cl.getParameters().getObjectQueueCapacity());

		// minimum poll-queue capacity
		cl.getParameters().setObjectQueueCapacity(50);
		ConnectionPollService poll2 = new ConnectionPollService(cl, 0); 
		assertTrue(poll2.getCapacity() == ConnectionPollService.MIN_QUEUE_CAPACITY);
		assertTrue(poll2.isListening());
		poll2.stopListening();
		assertFalse(poll2.isListening());

		// establish connection
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);

		// send PING
		long pingID = cl.sendPing();
		assertTrue("no or bad PING-ID", pingID > 0);
		Util.sleep(20);
		
		// test poll-services state
		assertTrue("poll-service error, empty expected", poll2.isEmpty());
		assertFalse("poll-service error, events expected", poll.isEmpty());
		assertTrue("poll-service.available", poll.available() == 2);
		
		// transfer an object (server)
		byte[] data1 = Util.randBytes(6000);
		scon.sendData(data1, SendPriority.NORMAL);
		Util.sleep(50);
		
		// send PING fails (time limit)
		pingID = cl.sendPing();
		assertTrue("PING-ID failure expected", pingID == -1);
		Util.sleep(2000);
		
		// test poll-services state
		assertTrue("poll-service error, empty expected", poll2.isEmpty());
		assertFalse("poll-service error, events expected", poll.isEmpty());
		assertTrue("poll-service.available: " + poll.available(), poll.available() == 3);

		// activate a pollinator
		poller1 = new Pollinator(poll);
		Util.sleep(20);
		assertTrue("poll-service error, emptiness expected", poll.isEmpty());
		assertTrue("error in poll-service content", poller1.stored() == 3);
		assertTrue("event missing in poll-service", poller1.hasEvent(ConnectionEventType.CONNECTED));
		assertTrue("event missing in poll-service", poller1.hasEvent(ConnectionEventType.OBJECT));
		assertTrue("event missing in poll-service", poller1.hasEvent(ConnectionEventType.PING_ECHO));
		
		// close poll-service
		poll.stopListening();
		cl.closeAndWait(2000);
		assertTrue("error in poll-service content", poller1.stored() == 3);
		assertTrue(poll.available() == 0);
		assertTrue(poll.isEmpty());
		assertFalse(poll.isListening());
		
		
		System.out.println( cl.getMonitor().report(4) );
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(3000);
			}
			if (cl != null) {
				cl.waitForClosed(2000);
			}
			clObjectListener.reportEvents();
			svObjectListener.reportEvents();
			
			if (poller1 != null) {
				poller1.terminate();
			}
		}
	}
		
}
