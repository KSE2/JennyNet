/*  File: TestUnit_Various.java
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

import org.junit.Test;
import org.kse.jennynet.core.ConnectionImpl.OutputProcessor;
import org.kse.jennynet.core.JennyNet.ThreadUsage;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.poll.ConnectionPollService;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.test.ObjectReceptionListener;
import org.kse.jennynet.test.StandardServer;
import org.kse.jennynet.util.ArraySet;
import org.kse.jennynet.util.IO_Manager;
import org.kse.jennynet.util.Util;

public class TestUnit_Various {

	private ConnectionParameters param;
	
	public TestUnit_Various() throws IOException {
		param = JennyNet.getConnectionParameters();
		param.setTransmissionParcelSize(16*1024);
		param.setTransmissionSpeed(15000);
		param.setFileRootDir(new File("test/empfang"));

	}
	
	private class TestServerListener extends DefaultServerListener {

		@Override
		public void connectionAdded(IServer server, Connection connection) {
		}

		@Override
		public void errorOccurred (IServer server, Connection con, int transAction, Throwable e) {
			System.err.println("*** SERVER ERROR: " + con + ", action " + transAction + ", " + e);
		}
	}

	private class TestOutputProcListener implements ConnectionListener {

		private OutputProcessor processor;
		private ArraySet<String> set = new ArraySet<String>();
		
		TestOutputProcListener (OutputProcessor proc) {
			processor = proc;
		}

		private void testProc (String name) {
			assertTrue("diviating output-processor", Thread.currentThread() == processor.output);
			set.add(name);
//			System.out.println("-- output-proc valid: " + name);
		}
		
		public void report () {
			System.out.println("\n*** OUTPUT-PROCESSOR EVENT-TEST, Results:");
			for (String str : set) {
				System.out.println("    " + str);
			}
		}
		
		@Override
		public void connected (Connection connection) {
			testProc("CONNECTED");
		}

		@Override
		public void idleChanged(Connection connection, boolean idle, int exchange) {
			testProc("IDLE-CHANGED");
		}

		@Override
		public void closed(Connection connection, int cause, String message) {
			testProc("CLOSED");
		}

		@Override
		public void shutdown(Connection connection, int cause, String message) {
			testProc("SHUTDOWN");
		}

		@Override
		public void objectReceived(Connection connection, SendPriority priority, long objectNr, Object object) {
			testProc("OBJECT");
		}

		@Override
		public void objectAborted(Connection connection, long objectNr, Object object, int info, String msg) {
			testProc("OBJ-ABORTED");
		}

		@Override
		public void pingEchoReceived(PingEcho pingEcho) {
			testProc("PING-ECHO");
		}

		@Override
		public void transmissionEventOccurred(TransmissionEvent event) {
			testProc("FILE-TRANS-EVT: " + event.getType());
		}
		
	}
	
	@Test
	public void output_init () throws InterruptedException, IOException {
		Server sv = null;
		Client cl1 = null, cl2 = null, cl3 = null;
		ServerConnectionImpl sco1, sco2, sco3;
		ConnectionMonitor mon;

		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
		
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
			sv.getParameters().setFileRootDir(new File("test/empfang"));
			sv.addListener(new TestServerListener());
			sv.start();
			
			// set up running connections
			cl1 = new Client();
			cl1.setParameters(param);
			cl1.addListener(clObjectListener);
			cl1.connect(200, sv.getSocketAddress());
			System.out.println("-- connection established " + cl1.toString());
			Util.sleep(50);
			sco1 = (ServerConnectionImpl) sv.getConnection(cl1.getLocalAddress());
			assertNotNull("server connection missing", sco1);
		
			cl2 = new Client();
			cl2.setParameters(param);
			cl2.addListener(clObjectListener);
			cl2.connect(200, sv.getSocketAddress());
			System.out.println("-- connection established " + cl2.toString());
			Util.sleep(50);
			sco2 = (ServerConnectionImpl) sv.getConnection(cl2.getLocalAddress());
			assertNotNull("server connection missing", sco2);
		
			cl3 = new Client();
			cl3.setParameters(param);
			cl3.addListener(clObjectListener);
			cl3.connect(200, sv.getSocketAddress());
			System.out.println("-- connection established " + cl3.toString());
			Util.sleep(50);
			sco3 = (ServerConnectionImpl) sv.getConnection(cl1.getLocalAddress());
			assertNotNull("server connection missing", sco3);

			// test for uniqueness of output processor (default quality)
			boolean ok = cl1.getOutputProcessor() == cl2.getOutputProcessor();
			ok &= cl2.getOutputProcessor() == cl3.getOutputProcessor();
			assertTrue("not all output-proc equal (client)", ok);
			
			ok = sco1.getOutputProcessor() == sco2.getOutputProcessor();
			ok &= sco3.getOutputProcessor() == sco2.getOutputProcessor();
			assertTrue("not all output-proc equal (server)", ok);

			// server and client processors are divergent
			ok = sco1.getOutputProcessor() != cl1.getOutputProcessor();
			assertTrue("falsely equal: proc server and client", ok);
			
			// send object to all
			byte[] data = Util.randBytes(10000);
			sv.sendObjectToAll(data);
			Util.sleep(1000);
			List<ConnectionEvent> list = clObjectListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("object delivery error: " + list.size(), list.size() == 3);
			
			// switch one client to individual output-processor
			System.out.println("\n - - --- SETTING INDIVIDUAL OUTPUT-PROCESSOR - - - - ");
			cl1.getParameters().setDeliveryThreadUsage(ThreadUsage.INDIVIDUAL);
			assertFalse("output-proc is not separated", cl1.getOutputProcessor() == cl2.getOutputProcessor());
			assertFalse("output-proc is not separated", cl1.getOutputProcessor() == cl3.getOutputProcessor());
			assertTrue(cl2.getOutputProcessor() == cl3.getOutputProcessor());
			assertFalse("individual output not indicated in connection", cl1.isGlobalOutput());
			assertTrue("new processor not alive", cl1.getOutputProcessor().isAlive());
			assertTrue("old processor not alive", cl2.getOutputProcessor().isAlive());
			
			// repeated setting to INDIVIDUAL
			OutputProcessor oldProc = cl1.getOutputProcessor();
			cl1.getParameters().setDeliveryThreadUsage(ThreadUsage.INDIVIDUAL);
			assertFalse("output-proc is not separated", cl1.getOutputProcessor() == cl2.getOutputProcessor());
			assertTrue("unchanged output-proc expected", cl1.getOutputProcessor() == oldProc);
			assertFalse("individual output not indicated in connection", cl1.isGlobalOutput());

			// send another object
			sco1.sendData(Util.randBytes(2000), SendPriority.BOTTOM);
			Util.sleep(500);
			list = clObjectListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("object delivery error: " + list.size(), list.size() == 4);
			
			// return to GLOBAL processor
			cl1.getParameters().setDeliveryThreadUsage(ThreadUsage.GLOBAL);
			assertFalse("unchanged output-proc", cl1.getOutputProcessor() == oldProc);
			assertTrue("output-proc is not global", cl1.getOutputProcessor() == cl2.getOutputProcessor());
			assertTrue("global output not indicated in connection", cl1.isGlobalOutput());
			Util.sleep(100);
			assertFalse("old processor still alive", oldProc.isAlive());
			
			// send another object
			sco1.sendData(Util.randBytes(4000), SendPriority.TOP);
			Util.sleep(500);
			list = clObjectListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("object delivery error: " + list.size(), list.size() == 5);
			
			
			System.out.println("\n-------------- FINIS ---------------\n");
			
			mon = sco1.getMonitor();
			System.out.println( mon.report(4) );
			mon = cl1.getMonitor();
			System.out.println( mon.report(4) );
			
			} finally {
				// shutdown systems
				System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
						+ Runtime.getRuntime().totalMemory());
				if (sv != null) {
					sv.closeAndWait(3000);
				}
				if (cl1 != null) {
					cl1.waitForClosed(2000);
				}
				if (cl2 != null) {
					cl2.waitForClosed(2000);
				}
				if (cl3 != null) {
					cl3.waitForClosed(2000);
				}
				
				Util.sleep(100);
				assertTrue("global processor died", cl1.getOutputProcessor().isAlive());
				clObjectListener.reportEvents();
				svObjectListener.reportEvents();
			}
	}
	
	@Test
	public void output_events () throws InterruptedException, IOException {
		Server sv = null;
		Client cl1 = null, cl2 = null, cl3 = null;
		ServerConnectionImpl sco1, sco2, sco3;
		ConnectionMonitor mon;

		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener clObjectListener = new ObjectReceptionListener(Station.CLIENT);
		TestOutputProcListener testListener = null; 
		
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
			sv.getParameters().setFileRootDir(new File("test/empfang"));
			sv.addListener(new TestServerListener());
			sv.start();
			
			// set up running connections
			cl1 = new Client();
			cl1.setParameters(param);
			cl1.getParameters().setIdleCheckPeriod(5000);
			cl1.getParameters().setIdleThreshold(10000);
			cl1.addListener(clObjectListener);
			testListener = new TestOutputProcListener(cl1.getOutputProcessor());
			cl1.addListener(testListener);
			cl1.connect(200, sv.getSocketAddress());
			System.out.println("-- connection established " + cl1.toString());
			Util.sleep(50);
			sco1 = (ServerConnectionImpl) sv.getConnection(cl1.getLocalAddress());
			assertNotNull("server connection missing", sco1);
		
			cl1.sendPing();
			
			sco1.sendData(Util.randBytes(20000), SendPriority.NORMAL);
			TestUnit_Idle_Alive.sendFile(sco1, 10000, "esel.dat");
			
			long sendID = cl1.sendData(Util.randBytes(100000), SendPriority.HIGH);
			Util.sleep(1000);
			cl1.breakOutgoingTransfer(sendID, null);
			
			Util.sleep(12000);
			sco1.close();
			
			System.out.println("\n-------------- FINIS ---------------\n");
			
			mon = sco1.getMonitor();
			System.out.println( mon.report(4) );
			mon = cl1.getMonitor();
			System.out.println( mon.report(4) );
			
//		    } catch (Exception e) {
//		    	e.printStackTrace();
		    	
			} finally {
				// shutdown systems
				System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
						+ Runtime.getRuntime().totalMemory());
				if (sv != null) {
					sv.closeAndWait(3000);
				}
				if (cl1 != null) {
					cl1.waitForClosed(2000);
				}
				
				Util.sleep(100);
				assertTrue("global processor died", cl1.getOutputProcessor().isAlive());
				clObjectListener.reportEvents();
				svObjectListener.reportEvents();
				testListener.report();
			}
	}

	@Test
	public void polling_overload () throws IOException, InterruptedException {
		Server sv = null;
		Client cl1 = null, cl2 = null;
		Connection scon1 = null, scon2 = null;
		
		ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER);
		ObjectReceptionListener cl1ObjectListener = new ObjectReceptionListener(Station.CLIENT);
		ObjectReceptionListener cl2ObjectListener = new ObjectReceptionListener(Station.CLIENT);
		JennyNet.setConnectionBlockingControl(true);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svObjectListener);
		sv.getParameters().setFileRootDir(new File("test/empfang"));
		sv.getParameters().setIdleCheckPeriod(5000);
		sv.getParameters().setIdleThreshold(10000);
//		sv.getParameters().setAlivePeriod(8000);
		sv.start();
		
		// set up connection 1
		cl1 = new Client();
		cl1.setParameters(param);
		cl1.getParameters().setFileRootDir(new File("test/client1"));
		cl1.addListener(cl1ObjectListener);
		OutputProcessor oldOutput = cl1.getOutputProcessor();
		assertTrue(cl1.isGlobalOutput());

		// set up connection 2
		cl2 = new Client();
		cl2.setParameters(param);
		cl2.getParameters().setFileRootDir(new File("test/client1"));
		cl2.addListener(cl2ObjectListener);
		assertTrue(oldOutput == cl2.getOutputProcessor());
		assertTrue(cl2.isGlobalOutput());
	
		// initial poll-service
		ConnectionPollService poll = new ConnectionPollService(cl1, 10);
		assertTrue(poll.getConnection() == cl1);
		assertTrue("poll-service capacity error: " + poll.getCapacity(), 
					poll.getCapacity() == ConnectionPollService.MIN_QUEUE_CAPACITY);
		assertTrue(poll.isListening());

		// establish connections
		cl1.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established: CL1, " + cl1.toString());
		cl2.connect(200, sv.getSocketAddress());
		System.out.println("-- connection established: CL2, " + cl2.toString());
		Util.sleep(50);
		scon1 = sv.getConnection(cl1.getLocalAddress());
		scon2 = sv.getConnection(cl2.getLocalAddress());
		assertNotNull("server connection missing", scon1);
	
		// overload poll service by sending small blocks of data
		byte[] block = Util.randBytes(300);
		long objId = 0;
		for (int i = 0; i < poll.getCapacity() + 2; i++) {
		   objId = scon1.sendData(block, SendPriority.NORMAL);
		}
		System.out.println(cl1.getMonitor().report(4));
		assertTrue("bad OBJECT-ID (bulk sending)", objId > poll.getCapacity());
		Util.sleep(20000);
		System.out.println(cl1.getMonitor().report(4));
		assertFalse("output-processor still blocking", oldOutput.isBlocking());

		// test for new output-processor in 1
		assertTrue("OVERLOADING: no new output-processor created", cl1.getOutputProcessor() != oldOutput);
		assertFalse("new output-processor is blocking", cl1.getOutputProcessor().isBlocking());
		assertFalse("error in output status", cl1.isGlobalOutput());
		
		// test for old output-processor in 2
		assertTrue("OVERLOADING: expected old output-processor", cl2.getOutputProcessor() == oldOutput);
		assertTrue("error in output status", cl2.isGlobalOutput());
		
		// test poll-services state
		assertFalse("poll-service error, events expected", poll.isEmpty());
		assertTrue("poll-service.available", poll.available() == poll.getCapacity());
		assertTrue(poll.getCapacity() == ConnectionPollService.MIN_QUEUE_CAPACITY);
		assertTrue(poll.isListening());
		
		// transfer an object in con-2 (server)
		// this tests functioning of the global output-processor
		byte[] data1 = Util.randBytes(6000);
		scon2.sendData(data1, SendPriority.NORMAL);
		Util.sleep(500);
		assertFalse("object not received", cl2ObjectListener.getEvents(ConnectionEventType.OBJECT).isEmpty());
	
		cl1.closeAndWait(2000);
		
		// close poll-service
		poll.close();
		assertTrue(poll.available() == 0);
		assertTrue(poll.isEmpty());
		assertNull(poll.poll(0));
		assertFalse(poll.isListening());
		
		
		System.out.println( cl1.getMonitor().report(4) );
		
		System.out.println("\n-------------- FINIS ---------------");
		
		// shutdown net systems
		} finally {
			System.out.println("\n--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(3000);
			}
			if (cl1 != null) {
				cl1.waitForClosed(2000);
			}
			cl1ObjectListener.reportEvents();
			cl2ObjectListener.reportEvents();
			svObjectListener.reportEvents();
		}
	}
	
	@Test
	public void IO_manager () throws IOException, InterruptedException {
		IO_Manager man = IO_Manager.get();
		String path1 = "/esel/run/zurbringer.dat";
		String path2 = "/esel/run/dreiwalcher.fish";
		File file1 = new File(path1);
		File file2 = new File(path2);

		// blank test (control test)
		assertTrue("initial entry error", man.canAccessFile(path1, ComDirection.INCOMING));
		assertTrue("initial entry error", man.canAccessFile(path1, ComDirection.OUTGOING));
		assertTrue("initial entry error", man.canAccessFile(path2, ComDirection.INCOMING));
		assertTrue("initial entry error", man.canAccessFile(path2, ComDirection.OUTGOING));
		assertTrue("initial entry error", man.canAccessFile(file1, ComDirection.INCOMING));
		assertTrue("initial entry error", man.canAccessFile(file1, ComDirection.OUTGOING));
		assertTrue("initial entry error", man.canAccessFile(file2, ComDirection.INCOMING));
		assertTrue("initial entry error", man.canAccessFile(file2, ComDirection.OUTGOING));

		// enter WRITING file
		assertTrue("cannot enter new file", man.enterActiveFile(path1, ComDirection.OUTGOING));
		assertFalse("false entry state (INCOMING)", man.canAccessFile(path1, ComDirection.INCOMING));
		assertFalse("false entry state (OUTGOING)", man.canAccessFile(path1, ComDirection.OUTGOING));

		// control
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.INCOMING));
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.OUTGOING));
		
		// check double entry WRITING and READING
		assertFalse("double entry error OUTGOING", man.enterActiveFile(path1, ComDirection.OUTGOING));
		assertFalse("double entry error OUTGOING", man.enterActiveFile(path1, ComDirection.INCOMING));

		// remove WRITING file
		assertTrue("failure removing entry OUTGOING", man.removeActiveFile(path1, ComDirection.OUTGOING));
		assertTrue("false entry state (INCOMING)", man.canAccessFile(path1, ComDirection.INCOMING));
		assertTrue("false entry state (OUTGOING)", man.canAccessFile(path1, ComDirection.OUTGOING));

		// enter READING file  ---------------------------------------
		assertTrue("cannot enter READING file", man.enterActiveFile(path1, ComDirection.INCOMING));
		assertTrue("false entry state (INCOMING)", man.canAccessFile(path1, ComDirection.INCOMING));
		assertFalse("false entry state (OUTGOING)", man.canAccessFile(path1, ComDirection.OUTGOING));

		// control
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.INCOMING));
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.OUTGOING));
		
		// check double entry WRITING and READING (= second READING entry)
		assertFalse("double entry error OUTGOING", man.enterActiveFile(path1, ComDirection.OUTGOING));
		assertTrue("double entry error INCOMING", man.enterActiveFile(path1, ComDirection.INCOMING));
		assertTrue("false entry state (INCOMING)", man.canAccessFile(path1, ComDirection.INCOMING));
		assertFalse("false entry state (OUTGOING)", man.canAccessFile(path1, ComDirection.OUTGOING));

		// remove one READING entry (still blocking WRITE access)
		assertTrue("failure removing entry INCOMING", man.removeActiveFile(path1, ComDirection.INCOMING));
		assertTrue("false entry state (INCOMING)", man.canAccessFile(path1, ComDirection.INCOMING));
		assertFalse("false entry state (OUTGOING)", man.canAccessFile(path1, ComDirection.OUTGOING));
		assertFalse("double entry error OUTGOING", man.enterActiveFile(path1, ComDirection.OUTGOING));

		// control
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.INCOMING));
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.OUTGOING));
		
		// remove second READING entry (no more blocking WRITE access)
		assertTrue("failure removing entry INCOMING", man.removeActiveFile(path1, ComDirection.INCOMING));
		assertTrue("false entry state (INCOMING)", man.canAccessFile(path1, ComDirection.INCOMING));
		assertTrue("false entry state (OUTGOING)", man.canAccessFile(path1, ComDirection.OUTGOING));
		assertTrue("double entry error OUTGOING", man.enterActiveFile(path1, ComDirection.OUTGOING));
		
		// false: removing not present entries -------------------------------
		assertFalse("expected false: removing unknown entry INCOMING", man.removeActiveFile(path1, ComDirection.INCOMING));
		assertFalse("expected false: removing unknown entry INCOMING", man.removeActiveFile(path2, ComDirection.INCOMING));
		assertFalse("expected false: removing unknown entry OUTGOING", man.removeActiveFile(path2, ComDirection.OUTGOING));
		
		// control
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.INCOMING));
		assertTrue("control entry error", man.canAccessFile(path2, ComDirection.OUTGOING));
	}
}
