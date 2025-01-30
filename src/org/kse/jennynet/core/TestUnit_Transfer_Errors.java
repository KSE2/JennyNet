/*  File: TestUnit_Transfer_Errors.java
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import org.junit.Test;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.intfa.TransmissionEventType;
import org.kse.jennynet.test.FileReceptionListener;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.test.ObjectReceptionListener;
import org.kse.jennynet.test.StandardServer;
import org.kse.jennynet.util.Util;

public class TestUnit_Transfer_Errors {
	
	private class NoConfirmFileReceptionListener extends FileReceptionListener {

		public NoConfirmFileReceptionListener(Object lock, int unlockSize, Station station) {
			super(lock, unlockSize, station);
		}

		@Override
		public synchronized void transmissionEventOccurred(TransmissionEvent evt) {
			if (evt.getType() == TransmissionEventType.FILE_INCOMING) {
				System.out.println("  ++++++++++ FILE_INCOMING, switching to CONFIRM-suppression");
				ConnectionImpl con = (ConnectionImpl)evt.getConnection(); 
				con.setSuppressFileConfirm(true);
			}
			super.transmissionEventOccurred(evt);
		}
	}

	@Test
	public void missing_transmission_confirm () throws IOException, InterruptedException {
		Object lock1 = new Object(), lock2 = new Object();
		final FileReceptionListener receptionListener = new NoConfirmFileReceptionListener(lock2, 2, Station.SERVER);
		final FileReceptionListener sendListener = new FileReceptionListener(lock1, 1, Station.CLIENT);
		
		System.out.println("\nTEST UNCONFIRMED TRANSFER CLIENT TO SERVER: SINGLE, TARGET DIR UNDEFINED");
		Server sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setFileRootDir(new File("test"));
		sv.start();

		// set up a running connection
		Client cl = new Client();
		cl.getParameters().setAlivePeriod(0);
		cl.getParameters().setTransmissionParcelSize(16*1024);
		cl.getParameters().setConfirmTimeout(5000);
		cl.addListener(sendListener);
		cl.connect(200, sv.getSocketAddress());
		cl.setTempo(15000);
		
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		
		// prepare data and source file
		int length = 72000;
		byte[] data = Util.randBytes(length);
		File src = Util.getTempFile(); 
		Util.makeFile(src, data);
		
		// transmit file (speed limit)
		cl.setNextObjectNr(25);
		cl.sendFile(src, "empfang/albrecht.dat");
		
		// wait for completion
		synchronized(lock1) {
			lock1.wait();
		}
		synchronized(lock2) {
			lock2.wait();
		}

		System.out.println("\nSENDER EVENTS:");
		for (TransmissionEvent evt : sendListener.getEvents()) {
			String hstr = evt.getType() + ", " + evt.getInfo() + ", object=" + evt.getObjectID() + ", path=" + evt.getPath();
			System.out.println(hstr);
			assertTrue("event object-nr error", evt.getObjectID() == 25);
			assertTrue("event direction error", evt.getDirection() == ComDirection.OUTGOING);
		}
		System.out.println("\nRECEIVER EVENTS:");
		for (TransmissionEvent evt : receptionListener.getEvents()) {
			String hstr = evt.getType() + ", " + evt.getInfo() + ", object=" + evt.getObjectID() + ", path=" + evt.getPath();
			System.out.println(hstr);
			assertTrue("event object-nr error", evt.getObjectID() == 25);
			assertTrue("event direction error", evt.getDirection() == ComDirection.INCOMING);
		}
		
		assertTrue("missing event FILE_ABORTED, info 103, on client side", sendListener.hasTransmissionEvent(
				   TransmissionEventType.FILE_ABORTED, 103));
		assertTrue("missing event FILE_ABORTED, info 104, on server side", receptionListener.hasTransmissionEvent(
				   TransmissionEventType.FILE_ABORTED, 104));

		Util.sleep(1000);
		sv.close();
		sv.closeAllConnections();
	}

	@Test
		public void internal_IO_error_outgoing () throws IOException, InterruptedException {
			Object lock1 = new Object(), lock2 = new Object();
			final FileReceptionListener receptionListener = new FileReceptionListener(lock2, 2, Station.SERVER);
			final FileReceptionListener sendListener = new FileReceptionListener(lock1, 1, Station.CLIENT);
			
			System.out.println("\nTEST UNCOMFIRMED TRANSFER CLIENT TO SERVER: SINGLE, TARGET DIR UNDEFINED");
			Server sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			sv.getParameters().setFileRootDir(new File("test"));
			sv.start();
	
			// set up a running connection
			Client cl = new Client();
			cl.getParameters().setAlivePeriod(0);
			cl.getParameters().setTransmissionParcelSize(16*1024);
			cl.getParameters().setConfirmTimeout(5000);
			cl.addListener(sendListener);
			cl.connect(200, sv.getSocketAddress());
			cl.setTempo(15000);
			cl.setProcessingTestError(ComDirection.OUTGOING, 3);
			
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(50);
			
			// prepare data and source file
			int length = 72000;
			byte[] data = Util.randBytes(length);
			File src = Util.getTempFile(); 
			Util.makeFile(src, data);
			
			// transmit file (speed limit)
			cl.sendFile(src, "empfang/ursula.dat");
			
			// wait for completion
			synchronized(lock1) {
				lock1.wait();
			}
			synchronized(lock2) {
				lock2.wait();
			}
	
			System.out.println("\nSENDER EVENTS:");
			for (TransmissionEvent evt : sendListener.getEvents()) {
				String hstr = evt.getType() + ", " + evt.getInfo() + ", object=" + evt.getObjectID() + ", path=" + evt.getPath();
				System.out.println(hstr);
				assertTrue("event direction error", evt.getDirection() == ComDirection.OUTGOING);
			}
			System.out.println("\nRECEIVER EVENTS:");
			for (TransmissionEvent evt : receptionListener.getEvents()) {
				String hstr = evt.getType() + ", " + evt.getInfo() + ", object=" + evt.getObjectID() + ", path=" + evt.getPath();
				System.out.println(hstr);
				assertTrue("event direction error", evt.getDirection() == ComDirection.INCOMING);
			}
			
			assertTrue("missing event FILE_FAILED, info 111, on client side", sendListener.hasTransmissionEvent(
					   TransmissionEventType.FILE_ABORTED, 111));
			assertTrue("missing event FILE_ABORTED, info 112, on server side", receptionListener.hasTransmissionEvent(
					   TransmissionEventType.FILE_ABORTED, 112));
	
			Util.sleep(1000);
			sv.close();
			sv.closeAllConnections();
		}

	@Test
	public void internal_IO_error_incoming () throws IOException, InterruptedException {
		Object lock1 = new Object(), lock2 = new Object();
		final FileReceptionListener receptionListener = new FileReceptionListener(lock2, 2, Station.SERVER);
		final FileReceptionListener sendListener = new FileReceptionListener(lock1, 2, Station.CLIENT);
		
		System.out.println("\nTEST UNCOMFIRMED TRANSFER CLIENT TO SERVER: SINGLE, TARGET DIR UNDEFINED");
		Server sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setFileRootDir(new File("test"));
		sv.start();
	
		// set up a running connection
		Client cl = new Client();
		cl.getParameters().setTransmissionParcelSize(16*1024);
		cl.getParameters().setConfirmTimeout(5000);
		cl.addListener(sendListener);
		cl.connect(200, sv.getSocketAddress());
		cl.setTempo(15000);
		Util.sleep(50);
		
		// set up error condition for test
		ConnectionImpl svcon = (ConnectionImpl) sv.getConnections()[0];
		svcon.setProcessingTestError(ComDirection.INCOMING, 3);
		
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(50);
		
		// prepare data and source file
		int length = 72000;
		byte[] data = Util.randBytes(length);
		File src = Util.getTempFile(); 
		Util.makeFile(src, data);
		
		// transmit file (speed limit)
		cl.sendFile(src, "empfang/janis.dat");
		
		// wait for completion
		sendListener.wait_on_release(0);
		receptionListener.wait_on_release(0);
	
		System.out.println("\nSENDER EVENTS:");
		for (TransmissionEvent evt : sendListener.getEvents()) {
			String hstr = evt.getType() + ", " + evt.getInfo() + ", object=" + evt.getObjectID() + ", path=" + evt.getPath();
			System.out.println(hstr);
			assertTrue("event direction error", evt.getDirection() == ComDirection.OUTGOING);
		}
		System.out.println("\nRECEIVER EVENTS:");
		for (TransmissionEvent evt : receptionListener.getEvents()) {
			String hstr = evt.getType() + ", " + evt.getInfo() + ", object=" + evt.getObjectID() + ", path=" + evt.getPath();
			System.out.println(hstr);
			assertTrue("event direction error", evt.getDirection() == ComDirection.INCOMING);
		}
		
		assertTrue("missing event FILE_ABORTED, info 101, on client side", sendListener.hasTransmissionEvent(
				   TransmissionEventType.FILE_ABORTED, 101));
		assertTrue("missing event FILE_ABORTED, info 110, on server side", receptionListener.hasTransmissionEvent(
				   TransmissionEventType.FILE_ABORTED, 110));
	
		Util.sleep(1000);
		sv.close();
		sv.closeAllConnections();
	}
	
	@Test
	public void serial_method_unavailable () throws IOException, InterruptedException {
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(Station.SERVER);
		final ObjectReceptionListener clientListener = new ObjectReceptionListener(Station.CLIENT);
		Server sv = null;
		Client cl = null;
		ServerConnectionImpl svcon;
	
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.addListener(clientListener);
			
			cl.setSerialMethodAvailability(1, false);
			ConnectionParameters par = cl.getParameters();
			int method = par.getSerialisationMethod();
			assertTrue("initial serialisation setting not zero: ", method == 0);
		
			// set unavailable method in parameters
			par.setSerialisationMethod(1);
			
			cl.connect(100, sv.getSocketAddress());
			Util.sleep(20);
			System.out.println("-- connection established " + cl.toString());
			svcon = (ServerConnectionImpl) sv.getConnection(cl.getLocalAddress());
			
			// still can retrieve serialisation of available method
			assertTrue(cl.getSendSerialization(0).getMethodID() == 0);
			assertTrue(cl.getReceiveSerialization(0).getMethodID() == 0);
			
			// fails retrieving unavailable serialisation
			try {
				cl.getSendSerialization();
				fail("expected exception (unavailable serialisation");
			} catch (SerialisationUnavailableException e) {
				e.printStackTrace();
			}

			try {
				cl.getSendSerialization(1);
				fail("expected exception (unavailable serialisation");
			} catch (SerialisationUnavailableException e) {
				e.printStackTrace();
			}

			// fails sending for the specified and default serialisation
			Object send1 = new Integer(2376827);
			try {
				cl.sendObject(send1, 1, SendPriority.BOTTOM);
				fail("expected exception (unavailable serialisation");
			} catch (SerialisationUnavailableException e) {
				e.printStackTrace();
			}

			try {
				cl.sendObject(send1);
				fail("expected exception (unavailable serialisation");
			} catch (SerialisationUnavailableException e) {
				e.printStackTrace();
			}
			
			// switch to TEST receiving section
			cl.setSerialMethodAvailability(1, true);
			svcon.setSerialMethodAvailability(1, false);
			
			cl.sendObject(send1);
			Util.sleep(100);
			
			List<ConnectionEvent> list = clientListener.getEvents(ConnectionEventType.ABORTED);
			assertTrue("reception ABORTED event expected", list.size() == 1);
			assertTrue("failed ABORTED event info", list.get(0).getInfo() == 209);

			System.out.println("\nAll's well that ends well.");
			
			// shutdown net systems
		} finally {
			System.out.println("------------------------------------------------------------------ ");
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(3000);
			}
			if (cl != null) {
				cl.closeAndWait(3000);
			}

			receptionListener.reportEvents();
			clientListener.reportEvents();
		}
	}
	
}
