/*  File: TestUnit_Idle_Alive.java
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.junit.Test;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.test.FileReceptionListener;
import org.kse.jennynet.test.ObjectReceptionListener;
import org.kse.jennynet.test.StandardServer;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.Util;

public class TestUnit_Idle_Alive {

	private ConnectionParameters param;
	
	public TestUnit_Idle_Alive() {
		param = JennyNet.getConnectionParameters();
		param.setTransmissionParcelSize(16*1024);
		param.setTransmissionSpeed(15000);
	}

	@Test
	public void aliveop_plain () throws IOException, InterruptedException {
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
		sv.getParameters().setAlivePeriod(3000);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
	
		// control of ALIVE system setup (both sides, 5,000)
		ConnectionMonitor mon = cl.getMonitor();
		assertTrue("ALIVE sending not active", mon.aliveSendPeriod == JennyNet.MIN_ALIVE_PERIOD);
		assertTrue("unexpected ALIVE timeout checking", mon.aliveTimeout == 0);
		mon = scon.getMonitor();
		int timeout = JennyNet.MIN_ALIVE_PERIOD * 3 / 2;
		System.out.println( mon.report(4) );
		assertTrue("ALIVE controlling not active", mon.aliveTimeout == timeout);
		assertTrue("unexpected ALIVE sending period", mon.aliveSendPeriod == 0);
		
		// control of ALIVE signals send (number of)
		Util.sleep(15000);
		assertNotNull("expected timeout-task not null", ((ConnectionImpl)scon).aliveTimeoutTask);
		assertNotNull("expected signal-task not null", cl.aliveSignalTask);
		int signals = ((ConnectionImpl)scon).aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received: " + signals, signals == 3);
		
		// change signal period
		System.out.println("\n---- CHANGING ALIVE PERIOD to 8 seconds ------");
		scon.getParameters().setAlivePeriod(8000);
		Util.sleep(500);
		
		// control of ALIVE system setup (both sides, 8,000)
		mon = cl.getMonitor();
		assertTrue("ALIVE period incorrect", mon.aliveSendPeriod == 8000);
		assertTrue("unexpected ALIVE timeout checking", mon.aliveTimeout == 0);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("ALIVE controlling not active", mon.aliveTimeout == 12000);
		assertTrue("unexpected ALIVE sending period", mon.aliveSendPeriod == 0);
		
		// control of ALIVE signals send (number of)
		Util.sleep(24000);
		signals = ((ConnectionImpl)scon).aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received: " + signals, signals == 6);

		// switch off ALIVE (via connection parameters)
		System.out.println("\n---- SWITCHING OFF ALIVE (via ConnectionParameter ------");
		scon.getParameters().setAlivePeriod(0);
		Util.sleep(500);

		// control of ALIVE system setup (both sides, 8,000)
		mon = cl.getMonitor();
		assertTrue("unexpected client ALIVE period", mon.aliveSendPeriod == 0);
		assertTrue("unexpected client ALIVE timeout", mon.aliveTimeout == 0);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("unexpected ALIVE timeout", mon.aliveTimeout == 0);
		assertTrue("unexpected ALIVE period", mon.aliveSendPeriod == 0);
		
		// control of ALIVE signals send (number of)
		Util.sleep(9000);
		assertNull("expected timeout-task to be null", ((ConnectionImpl)scon).aliveTimeoutTask);
		assertNull("expected signal-task to be null", cl.aliveSignalTask);
		
		// switch on ALIVE (via connection parameters)
		System.out.println("\n---- SWITCHING ON ALIVE (via ConnectionParameter ------");
		scon.getParameters().setAlivePeriod(6000);
		Util.sleep(500);

		// control of ALIVE system setup (both sides, 6,000)
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("unexpected client ALIVE period", mon.aliveSendPeriod == 6000);
		assertTrue("unexpected client ALIVE timeout", mon.aliveTimeout == 0);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("unexpected ALIVE timeout", mon.aliveTimeout == 9000);
		assertTrue("unexpected ALIVE period", mon.aliveSendPeriod == 0);
		
		// control of ALIVE signals send (number of)
		Util.sleep(7000);
		assertNotNull("expected timeout-task not null", ((ConnectionImpl)scon).aliveTimeoutTask);
		assertNotNull("expected signal-task not null", cl.aliveSignalTask);
		signals = ((ConnectionImpl)scon).aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received: " + signals, signals == 1);
		
		// control success of failure (we shutdown ALIVE sending)
		System.out.println("\n---- TRIGGERING FAILURE (timeout) ------");
		cl.cancelAliveSystem();
		Util.sleep(10000);
		
		List<ConnectionEvent> events = svObjectListener.getEvents(ConnectionEventType.SHUTDOWN);
		assertTrue("no SHUTDOWN event for ALIVE timeout", events.size() == 1);
		assertTrue("incorrect error code for ALIVE timeout", events.get(0).getInfo() == 9);
		
		events = svObjectListener.getEvents(ConnectionEventType.CLOSED);
		assertTrue("no CLOSED event for ALIVE timeout", events.size() == 1);
		assertTrue("incorrect error code for ALIVE timeout", events.get(0).getInfo() == 9);
		
		
		System.out.println("\n-------------- FINIS ---------------\n");
		
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		
//	    } catch (Exception e) {
//	    	e.printStackTrace();
	    	
		} finally {
			// shutdown systems
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

	/** Various settings of ALIVE periods on both sides of a Connection
	 * under load of data exchange.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void aliveop_mixed () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		Timer timer = new Timer(); 
				
//		FileOutputStream fout = new FileOutputStream(new File("test/aliveop_mixed.log"));
//		PrintStream printer = new PrintStream(fout);
//		PrintStream systemOut = System.out;
//		System.setOut(printer);
		
		class ReflectConnectionListener extends ObjectReceptionListener {

			public ReflectConnectionListener (Station station) {
				super(station);
			}

			@Override
			public synchronized void objectReceived (Connection con, SendPriority priority, long objNr, Object obj) {
				super.objectReceived(con, priority, objNr, obj);
				
				assertTrue("received object type error", obj instanceof JennyNetByteBuffer);
				System.out.println("**** DATA-OBJECT received from " + con.getRemoteAddress().getPort());
				TimerTask reflectTask = new TimerTask() {
					@Override
					public void run() {
						con.sendObject(obj);
					}
				};
				timer.schedule(reflectTask, 500);
			}
		}
		
		ObjectReceptionListener svObjectListener = new ReflectConnectionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ReflectConnectionListener(Station.CLIENT);
//		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
	
	try {
		ConnectionListener[] svLisArr = new ConnectionListener[] {svObjectListener};
		
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svLisArr);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.getParameters().setAlivePeriod(JennyNet.MIN_ALIVE_PERIOD);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(JennyNet.MIN_ALIVE_PERIOD);
		cl.getParameters().setTransmissionSpeed(-1);
		cl.getParameters().setTransmissionParcelSize(128*1024);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(500);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		int dataLen = JennyNet.MEGA;
		cl.sendData(Util.randBytes(dataLen), SendPriority.NORMAL);
		Util.sleep(250);
		scon.sendData(Util.randBytes(dataLen), SendPriority.NORMAL);
	
		// control of ALIVE system setup (both sides, 5,000)
		int timeout = JennyNet.MIN_ALIVE_PERIOD * 3 / 2;
		ConnectionMonitor mon = cl.getMonitor();
		assertTrue("ALIVE sending not active", mon.aliveSendPeriod == JennyNet.MIN_ALIVE_PERIOD);
		assertTrue("unexpected ALIVE timeout checking", mon.aliveTimeout == timeout);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("ALIVE controlling not active", mon.aliveTimeout == timeout);
		assertTrue("unexpected ALIVE sending period", mon.aliveSendPeriod == JennyNet.MIN_ALIVE_PERIOD);
		
		// control of ALIVE signals send (number of)
		Util.sleep(15000);
		assertNotNull("expected timeout-task not null (server)", ((ConnectionImpl)scon).aliveTimeoutTask);
		assertNotNull("expected signal-task not null (server)", ((ConnectionImpl)scon).aliveSignalTask);
		assertNotNull("expected signal-task not null (client)", cl.aliveSignalTask);
		assertNotNull("expected timeout-task not null (client)", cl.aliveTimeoutTask);
		int signals = ((ConnectionImpl)scon).aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received (server) " + signals, signals == 3);
		signals = cl.aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received (client) " + signals, signals == 3);
		
		// change signal period (server only)
		System.out.println("\n---- CHANGING ALIVE PERIOD to 8 seconds ------");
		scon.getParameters().setAlivePeriod(8000);
		Util.sleep(500);
		
		// control of ALIVE system setup (both sides, 8,000)
		mon = cl.getMonitor();
		assertTrue("ALIVE period incorrect", mon.aliveSendPeriod == 8000);
		assertTrue("unexpected ALIVE timeout checking", mon.aliveTimeout == timeout);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("ALIVE controlling not active", mon.aliveTimeout == 12000);
		assertTrue("unexpected ALIVE sending period", mon.aliveSendPeriod == JennyNet.MIN_ALIVE_PERIOD);
		
		// control of ALIVE signals send (number of)
		Util.sleep(24000);
		signals = ((ConnectionImpl)scon).aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received (server) " + signals, signals == 6);
		signals = cl.aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received (client) " + signals, signals == 7 | signals == 8);

		// switch off ALIVE (via connection parameters)
		System.out.println("\n---- SWITCHING OFF ALIVE (via ConnectionParameter ------");
		scon.getParameters().setAlivePeriod(0);
		cl.getParameters().setAlivePeriod(0);
		Util.sleep(500);

		// control of ALIVE system setup (both sides)
		mon = cl.getMonitor();
		assertTrue("unexpected client ALIVE period", mon.aliveSendPeriod == 0);
		assertTrue("unexpected client ALIVE timeout", mon.aliveTimeout == 0);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("unexpected ALIVE timeout", mon.aliveTimeout == 0);
		assertTrue("unexpected ALIVE period", mon.aliveSendPeriod == 0);
		
		// control of ALIVE signals send (number of)
		Util.sleep(9000);
		assertNull("expected timeout-task to be null", ((ConnectionImpl)scon).aliveTimeoutTask);
		assertNull("expected signal-task to be null", ((ConnectionImpl)scon).aliveSignalTask);
		assertNull("expected signal-task to be null", cl.aliveTimeoutTask);
		assertNull("expected signal-task to be null", cl.aliveSignalTask);
		
		// switch on ALIVE (via connection parameters)
		System.out.println("\n---- SWITCHING ON ALIVE (via ConnectionParameter ------");
		scon.getParameters().setAlivePeriod(6000);
		cl.getParameters().setAlivePeriod(6000);
		Util.sleep(500);

		// control of ALIVE system setup (both sides, 6,000)
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("unexpected client ALIVE period", mon.aliveSendPeriod == 6000);
		assertTrue("unexpected client ALIVE timeout", mon.aliveTimeout == 9000);
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		assertTrue("unexpected ALIVE timeout", mon.aliveTimeout == 9000);
		assertTrue("unexpected ALIVE period", mon.aliveSendPeriod == 6000);
		
		// control of ALIVE signals send (number of)
		Util.sleep(7000);
		assertNotNull("expected timeout-task not null (server)", ((ConnectionImpl)scon).aliveTimeoutTask);
		assertNotNull("expected signal-task not null (server)", ((ConnectionImpl)scon).aliveSignalTask);
		assertNotNull("expected signal-task not null (client)", cl.aliveSignalTask);
		assertNotNull("expected timeout-task not null (client)", cl.aliveTimeoutTask);
		signals = ((ConnectionImpl)scon).aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received (server) " + signals, signals == 1);
		signals = cl.aliveTimeoutTask.getNrSignals();
		assertTrue("not enough ALIVE signals received (client) " + signals, signals == 1);
		
		// control success of failure (we shutdown ALIVE sending)
		System.out.println("\n---- TRIGGERING FAILURE (timeout) ------");
		cl.cancelAliveSystem();
		Util.sleep(9500);
		
		List<ConnectionEvent> events = svObjectListener.getEvents(ConnectionEventType.SHUTDOWN);
		assertTrue("no SHUTDOWN event for ALIVE timeout", events.size() == 1);
		assertTrue("incorrect error code for ALIVE timeout", events.get(0).getInfo() == 9);
		
		
		System.out.println("\n-------------- FINIS ---------------\n");
		
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		
//	    } catch (Exception e) {
//	    	e.printStackTrace();
	    	
		} finally {
			// shutdown systems
//			System.setOut(systemOut);
//			printer.close();
			
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
	public void idle_plain () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		ConnectionMonitor mon;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
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
		Util.sleep(50);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		scon.getParameters().setIdleCheckPeriod(5000);
		scon.getParameters().setIdleThreshold(10000);
		assertTrue(scon.getParameters().getIdleCheckPeriod() == 5000);
		assertTrue(scon.getParameters().getIdleThreshold() == 10000);

		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		
		cl.sendData(Util.randBytes(10000), SendPriority.NORMAL);
		Util.sleep(12000);
		
		List<ConnectionEvent> evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("IDLE state expected", scon.isIdle());
		assertTrue("connection event(s) missing", evl.size() == 1);
		assertTrue("expected state IDLE, received BUSY", evl.get(0).getInfo() == 1);

		// immediate state change
		assertTrue("false IDLE state", scon.isIdle());
		cl.sendData(Util.randBytes(10000), SendPriority.NORMAL);
		Util.sleep(1000);
		
		assertFalse("immediate state change not working", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("connection event(s) missing", evl.size() == 2);
		assertTrue("expected state BUSY, received IDLE", evl.get(1).getInfo() == 0);
		
		// switch off (may not issue event but changes state to IDLE)
		scon.getParameters().setIdleThreshold(0);
		assertTrue(scon.getParameters().getIdleThreshold() == 0);
		assertFalse("false IDLE state", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("unexpected connection event", evl.size() == 2);

		scon.sendData(Util.randBytes(10000), SendPriority.NORMAL);
		Util.sleep(50);
		assertFalse("false IDLE state", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("unexpected connection event", evl.size() == 2);
		
		// switch on (period = 10000)
		scon.getParameters().setIdleThreshold(10000);
		assertTrue(scon.getParameters().getIdleThreshold() == 10000);
		assertFalse("false IDLE state", scon.isIdle());
		scon.getParameters().setIdleCheckPeriod(10000);
		assertTrue(scon.getParameters().getIdleCheckPeriod() == 10000);
		assertFalse("false IDLE state", scon.isIdle());
		Util.sleep(1500);
		cl.sendData(Util.randBytes(5000), SendPriority.NORMAL);
		Util.sleep(1500);
		scon.sendData(Util.randBytes(5000), SendPriority.NORMAL);
		Util.sleep(1500);
		cl.sendData(Util.randBytes(15000), SendPriority.NORMAL);

		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("missing IDLE event", evl.size() == 2);
//		assertTrue("expected state BUSY, received IDLE", evl.get(3).getInfo() == 0);
		assertFalse("false IDLE state", scon.isIdle());
		
		Util.sleep(6000);
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("missing IDLE event", evl.size() == 2);
		assertFalse("false IDLE state", scon.isIdle());
		
		Util.sleep(10000);
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("missing IDLE event", evl.size() == 3);
		assertTrue("expected state IDLE, received BUSY", evl.get(2).getInfo() == 1);
		assertTrue("false IDLE state", scon.isIdle());
		
		System.out.println("\n-------------- FINIS ---------------\n");
		
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		
		} finally {
			// shutdown systems
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
	
	/** Tests whether the IDLE system is not disturbed by signal sending
	 * of the ALIVE system.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void idle_alive () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		ConnectionMonitor mon;
		List<ConnectionEvent> evl;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
	try {
		ConnectionListener[] svLisArr = new ConnectionListener[] {svObjectListener, fileRecListener};
		
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svLisArr);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.setParameters(param);
		cl.getParameters().setAlivePeriod(6000);
		cl.addListener(clObjectListener);
		cl.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		scon.getParameters().setIdleCheckPeriod(10000);
		scon.getParameters().setIdleThreshold(10000);
		scon.getParameters().setAlivePeriod(5000);

		// no-traffic checking
		Util.sleep(7000);
		assertFalse("false IDLE state (server)", scon.isIdle());
		assertFalse("false IDLE state (client)", cl.isIdle());
		
		// traffic check
		cl.sendData(Util.randBytes(5000), SendPriority.NORMAL);
		Util.sleep(1000);
		assertFalse("false IDLE state (server)", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("unexpected IDLE event", evl.size() == 0);
		Util.sleep(4000);
		assertFalse("false IDLE state (server)", scon.isIdle());

		// new IDLE check
		Util.sleep(9000);
		assertTrue("false IDLE state (server)", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("missing IDLE event", evl.size() == 1);
		assertTrue("expected state IDLE, received BUSY", evl.get(0).getInfo() == 1);
		
		System.out.println("\n-------------- FINIS ---------------\n");
		
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		
		} finally {
			// shutdown systems
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

	/** Sends a file with the given amount of random data over the given
	 * connection at the given target-path.
	 * 
	 * @param con Connection
	 * @param size int file size
	 * @param target String target filepath
	 * @return long file-ID
	 * @throws IOException
	 */
	public static long sendFile (Connection con, int size, String target) throws IOException {
		byte[] data = Util.randBytes(size);
		File file = File.createTempFile("jenny-", ".dat");
		Util.makeFile(file, data);
		return con.sendFile(file, target);
	}
	
	/** Tests to control whether threshold values are controlled correctly,
	 * using files as data transport.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void idle_threshold () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection scon = null;
		ConnectionMonitor mon;
		List<ConnectionEvent> evl;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		FileReceptionListener fileRecListener = new FileReceptionListener(Station.SERVER); 
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
	
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
		Util.sleep(50);
		scon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("server connection missing", scon);
		scon.getParameters().setIdleCheckPeriod(15000);
		scon.getParameters().setIdleThreshold(100000);

		// traffic check
		String path = "dreiangel.dat";
		sendFile(cl, 1000, path);
		Util.sleep(1000);
		assertFalse("false IDLE state (server)", scon.isIdle());
		
		// second part (no change in IDLE status, data-size = 80,000, time = 11)
		sendFile(cl, 20000, path);
		Util.sleep(2000);
		sendFile(cl, 40000, path);
		Util.sleep(8000);
		sendFile(cl, 20000, path);
		Util.sleep(1000);
		assertFalse("false IDLE state (server)", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("unexpected IDLE event", evl.size() == 0);
//		assertTrue("expected state BUSY, received IDLE", evl.get(0).getInfo() == 0);

		// new IDLE check (time = 16)
		Util.sleep(20000);
		assertTrue("false IDLE state (server)", scon.isIdle());
		evl = svObjectListener.getEvents(ConnectionEventType.IDLE);
		assertTrue("missing IDLE event", evl.size() == 1);
		assertTrue("expected state IDLE, received BUSY", evl.get(0).getInfo() == 1);
		
		
		System.out.println("\n-------------- FINIS ---------------\n");
		
		mon = scon.getMonitor();
		System.out.println( mon.report(4) );
		mon = cl.getMonitor();
		System.out.println( mon.report(4) );
		
		} finally {
			// shutdown systems
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
}
