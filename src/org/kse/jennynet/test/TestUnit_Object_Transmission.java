/*  File: TestUnit_Object_Transmission.java
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
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.JennyNetByteBuffer;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.exception.ClosedConnectionException;
import org.kse.jennynet.exception.ListOverflowException;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.exception.UnconnectedException;
import org.kse.jennynet.exception.UnregisteredObjectException;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.CRC32;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

public class TestUnit_Object_Transmission {

	public TestUnit_Object_Transmission() {
	}

	@Test
	public void tempo_sending_single_object () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.SERVER);

	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setSerialisationMethod(1);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.getParameters().setSerialisationMethod(1);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		synchronized (lock) {
			
			// CASE 1
			// prepare random data block to transmit
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);
			cl.setTempo(5000);
			assertTrue("error in TEMPO setting", cl.getTransmissionSpeed() == 5000);
			
			// send over connection
			long time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			int elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			assertFalse("no object received by server", receptionListener.getReceived().isEmpty());
			byte[] rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 1)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 1 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time too quick", elapsed >= 18);
			
			// CASE 2
			// prepare random data block to transmit
			dataLen = 500000;
			block = Util.randBytes(dataLen);
			cl.setTempo(33000);
			cl.getParameters().setTransmissionParcelSize(16*1024);
			receptionListener.reset();
			assertTrue("error is TEMPO setting", cl.getTransmissionSpeed() == 33000);
			
			// send over connection
			time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 2)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 2 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time too quick", elapsed >= 14);

			// CASE 3
			// prepare random data block to transmit
			dataLen = 1000000;
			block = Util.randBytes(dataLen);
			cl.setTempo(100000);
			cl.getParameters().setTransmissionParcelSize(20*1024);
			receptionListener.reset();
			assertTrue("error is TEMPO setting", cl.getTransmissionSpeed() == 100000);
			
			// send over connection
			time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 3)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 3 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time too quick", elapsed >= 9);

			// CASE 4
			// prepare random data block to transmit
			dataLen = 1000000;
			block = Util.randBytes(dataLen);
			cl.setTempo(1000000);
			cl.getParameters().setTransmissionParcelSize(32*1024);
			receptionListener.reset();
			assertTrue("error is TEMPO setting", cl.getTransmissionSpeed() == 1000000);
			
			// send over connection
			time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 4)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 4 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time error", elapsed >= 0 & elapsed < 3);

			// CASE 5
			// prepare random data block to transmit
			dataLen = 2000000;
			block = Util.randBytes(dataLen);
			cl.setTempo(-1);
			cl.getParameters().setTransmissionParcelSize(64*1024);
			receptionListener.reset();
			assertTrue("error is TEMPO setting", cl.getTransmissionSpeed() == -1);
			
			// send over connection
			time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time);
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 4)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 5 verified, time elapsed " + elapsed + " ms");
			assertTrue("transmission time error", elapsed >= 0 & elapsed < 1000);
		}
	
	// shutdown net systems
	} finally {
		System.out.println("# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnections()[0].getMonitor().exchangedVolume);
		}
		
		System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
				+ Runtime.getRuntime().totalMemory());
		if (sv != null) {
			sv.closeAndWait(2000);
		}
		if (cl != null) {
			cl.closeAndWait(1000);
		}
	}
	}


	@Test
	public void tempo_sending_multi_object () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 3, Station.SERVER);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(10*1024);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		synchronized (lock) {
			
			// CASE 1
			// prepare 3 random data blocks to transmit
			int dataLen1 = 100000;
			byte[] block1 = Util.randBytes(dataLen1);
			int dataLen2 = 50000;
			byte[] block2 = Util.randBytes(dataLen2);
			int dataLen3 = 120000;
			byte[] block3 = Util.randBytes(dataLen3);

			cl.setTempo(33000);
			
			// send over connection
			long time = System.currentTimeMillis();
			cl.sendData(block1, 0, block1.length, SendPriority.NORMAL);
			cl.sendData(block2, 0, block2.length, SendPriority.NORMAL);
			cl.sendData(block3, 0, block3.length, SendPriority.NORMAL);
			System.out.println("-- orders put, waiting for results ...");
			lock.wait();
			int elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data blocks
			int size = receptionListener.getReceived().size();
			assertFalse("no object received by server", size == 0);
			assertTrue("error in number of received objects", size == 3);
			byte[] rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 1)", Util.equalArrays(block1, rece));
			rece = receptionListener.getReceived().get(1);
			assertTrue("data integrity error (transmitted 2)", Util.equalArrays(block2, rece));
			rece = receptionListener.getReceived().get(2);
			assertTrue("data integrity error (transmitted 3)", Util.equalArrays(block3, rece));

			// check transmission time
			System.out.println("-- transmission verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time error", elapsed > 6 & elapsed < 10);
			
		}
	
//	} catch (Throwable e) {
//		e.printStackTrace();
//		fail()
		
	// shutdown net systems
	} finally {
		System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
				+ Runtime.getRuntime().totalMemory());
		if (sv != null) {
			sv.closeAndWait(5000);
		}
		if (cl != null) {
			cl.closeAndWait(2000);
		}
	}
		
	}

	@Test
	public void simple_unrestricted_transfer () throws IOException, InterruptedException {
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.SERVER);
		final ObjectReceptionListener clientListener = new ObjectReceptionListener(Station.CLIENT);
//		ConnectionListener eventReporter = new EventReporter();
		Server sv = null;
		Client cl = null;
		long time;
	
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			sv.getParameters().setAlivePeriod(0);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.getParameters().setAlivePeriod(0);
			cl.getParameters().setTransmissionParcelSize(8*1024);
			cl.addListener(clientListener);
			cl.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(20);
			
			synchronized (lock) {
				
				// CASE 1
				// prepare random data block to transmit
				int dataLen = 100000;
				byte[] block = Util.randBytes(dataLen);
				
				// send over connection and close it immediately
				time = System.currentTimeMillis();
				cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
				cl.close();
				lock.wait();
				int elapsed = (int)(System.currentTimeMillis() - time);
				
				// check received data
				assertFalse("no object received by server", receptionListener.getReceived().isEmpty());
				byte[] rece = receptionListener.getReceived().get(0);
				assertTrue("data integrity error (transmitted 1)", Util.equalArrays(block, rece));
				System.out.println("-- transmission 1 verified, time elapsed " + elapsed + " ms");
				assertTrue("transmission time too long (limit 160, was " + elapsed, elapsed <= 160);
				
				System.out.println("\r\n## pausing ...");
				Util.sleep(1000);
				System.out.println("\r\n## done");
				
			}
			
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

	@Test
	public void tempo_receiving_single_object () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000));
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(5000);
		cl.addListener(receptionListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		synchronized (lock) {
			
			Connection scon = sv.getConnections()[0];
			scon.getParameters().setTransmissionParcelSize(8*1024);

			// CASE 1
			// prepare random data block to transmit
			System.out.println("----- CASE 1 : 100,000 block, TEMPO 5,000");
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);
			cl.setTempo(5000);
			
			// send over connection
			long time = System.currentTimeMillis();
			scon.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			int elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			assertFalse("no object received by server", receptionListener.getReceived().isEmpty());
			byte[] rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 1)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 1 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time too quick: " + elapsed, elapsed >= 18);
			
			// CASE 2
			// prepare random data block to transmit
			System.out.println("\n----- CASE 2 : 500,000 block, TEMPO 33,000");
			dataLen = 500000;
			block = Util.randBytes(dataLen);
			cl.setTempo(33000);
			scon.getParameters().setTransmissionParcelSize(16*1024);
			receptionListener.reset();
			
			// send over connection
			time = System.currentTimeMillis();
			scon.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 2)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 2 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time too quick", elapsed >= 14);
	
			// CASE 3
			// prepare random data block to transmit
			System.out.println("\n----- CASE 3 : 1,000,000 block, TEMPO 100,000");
			dataLen = 1000000;
			block = Util.randBytes(dataLen);
			cl.setTempo(100000);
			scon.getParameters().setTransmissionParcelSize(20*1024);
			receptionListener.reset();
			
			// send over connection
			time = System.currentTimeMillis();
			scon.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 3)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 3 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time too quick", elapsed >= 9);
	
			// CASE 4
			// prepare random data block to transmit
			System.out.println("\n----- CASE 4 : 1,000,000 block, TEMPO 1,000,000");
			dataLen = 1000000;
			block = Util.randBytes(dataLen);
			cl.setTempo(1000000);
			scon.getParameters().setTransmissionParcelSize(32*1024);
			receptionListener.reset();
			
			// send over connection
			time = System.currentTimeMillis();
			scon.sendData(block, 0, dataLen, SendPriority.NORMAL);
			lock.wait();
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			
			// check received data
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 4)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 4 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission time error", elapsed >= 0 & elapsed < 3);
		}
	
	// shutdown net systems
	} finally {
		System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
				+ Runtime.getRuntime().totalMemory());
		if (sv != null) {
			sv.closeAndWait(2000);
		}
		if (cl != null) {
			cl.closeAndWait(1000);
		}
	}
	}

	@Test
	public void speed_settings () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.addListener(receptionListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		synchronized (lock) {
			
			ServerConnection scon = sv.getConnections()[0];
			assertTrue("false initial server speed setting", scon.getTransmissionSpeed() < 0);
			assertTrue("false initial client speed setting", cl.getTransmissionSpeed() < 0);

			// test setting by server (unpriorised)
			int speed = 20000;
			scon.setTempo(speed);
			Util.sleep(50);
			assertTrue("error in server speed setting", scon.getTransmissionSpeed() == speed);
			assertTrue("client does not receive server speed setting", cl.getTransmissionSpeed() == speed);
			
			// test setting by client (unpriorised)
			speed = 50000;
			cl.setTempo(speed);
			Util.sleep(50);
			assertTrue("error in client speed setting", scon.getTransmissionSpeed() == speed);
			assertTrue("server does not receive client speed setting", scon.getTransmissionSpeed() == speed);
			
			// test setting by server (server priorised)
			scon.setTempoFixed(true);
			speed = 100000;
			scon.setTempo(speed);
			Util.sleep(50);
			assertTrue("error in priorised server speed setting", scon.getTransmissionSpeed() == speed);
			assertTrue("client does not receive priorised server speed setting", cl.getTransmissionSpeed() == speed);
			
			// test rejected setting by client (server priorised)
			speed = 23000;
			int oldSpeed = scon.getTransmissionSpeed();
			cl.setTempo(speed);
			Util.sleep(50);
			assertFalse("server falsely adopts client speed setting (server priorised)", scon.getTransmissionSpeed() == speed);
			assertTrue("server loses speed setting through client speed setting (server priorised)", 
					scon.getTransmissionSpeed() == oldSpeed);
			assertFalse("client falsely adopts speed setting (priorised)", cl.getTransmissionSpeed() == speed);
			
			// test setting by server, II (unpriorised)
			scon.setTempoFixed(false);
			speed = 330000;
			scon.setTempo(speed);
			Util.sleep(50);
			assertTrue("error in unpriorised server speed setting", scon.getTransmissionSpeed() == speed);
			assertTrue("client does not receive unpriorised server speed setting", cl.getTransmissionSpeed() == speed);
			
			// test setting by client, II (unpriorised)
			speed = 66000;
			cl.setTempo(speed);
			Util.sleep(50);
			assertTrue("error in unpriorised client speed setting", cl.getTransmissionSpeed() == speed);
			assertTrue("server does not receive unpriorised client speed setting", scon.getTransmissionSpeed() == speed);
			
			// test speed rest to unlimited (speed off)
			speed = -1;
			cl.setTempo(speed);
			Util.sleep(50);
			assertTrue("error in client speed setting (speed off)", cl.getTransmissionSpeed() == speed);
			assertTrue("server does not receive client speed setting (speed off)", scon.getTransmissionSpeed() == speed);
			
		}
		// shutdown net systems
		} finally {
			if (sv != null) {
				sv.closeAndWait(1000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
		}
	
	@Test
	public void transmission_off () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.CLIENT);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.addListener(receptionListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		synchronized (lock) {
			
			ServerConnection scon = sv.getConnections()[0];
			int speed = 10000;
			scon.setTempo(speed);
			Util.sleep(50);
			
			// prepare random data block to transmit
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);
			assertTrue("error in TEMPO setting (client)", cl.getTransmissionSpeed() == speed);
			
			// CASE 1 : interrupting object sending with SPEED = 0, resuming after a while
			// start sending from client
			long time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			Util.sleep(2000);
			
			// set transmission off and on again
			scon.setTempo(0);
			Util.sleep(20000);
			scon.setTempo(speed);

			lock.wait();
			int elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			assertTrue("transmission off setting not working, elapsed: " + elapsed, elapsed >= dataLen/speed + 18);
			
			// check received data
			assertFalse("no object received by server", receptionListener.getReceived().isEmpty());
			byte[] rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 1)", Util.equalArrays(block, rece));
			System.out.println("-- transmission 1 verified, time elapsed " + elapsed + " sec");
			
			// CASE 2 : object sending when speed is off
			System.out.println("\n-------------------- CASE 2 -------------------");
			receptionListener.reset();
			cl.setTempo(0);
			Util.sleep(50);
			assertTrue("false initial server speed setting", scon.getTransmissionSpeed() == 0);
			assertTrue("false initial client speed setting", cl.getTransmissionSpeed() == 0);

			time = System.currentTimeMillis();
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);

			lock.wait(5000);
			assertTrue("unexpected object reception", receptionListener.getSize() == 0);
			scon.setTempo(50000);
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			assertTrue("transmission off setting not working, elapsed: " + elapsed, elapsed >= 5);
			
			lock.wait(5000);
			assertTrue("object reception missing", receptionListener.getSize() == 1);
			rece = receptionListener.getReceived().get(0);
			assertTrue("data integrity error (transmitted 2)", Util.equalArrays(block, rece));
			elapsed = (int)(System.currentTimeMillis() - time) / 1000;
			System.out.println("-- transmission 2 verified, time elapsed " + elapsed + " sec");
			assertTrue("transmission off setting not working correctly, elapsed: " + elapsed, elapsed >= 6);
			
			
		}
		// shutdown net systems
		} finally {
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
	}

	private static class TestObject_C1 {
		private enum Choice {eins, zwei, drei}

		private long serialNr = Util.nextRand(Integer.MAX_VALUE);
		private int testNr = Util.nextRand(600000);
		private String text = "Und sie konnten ihn kaum noch tragen!";
		private byte[] userData;
		private Choice choice = Choice.drei;
		
		TestObject_C1 (byte[] data) {
			userData = data;
		}
		
		TestObject_C1 () {
		}
		
		public int getCrc() {
			CRC32 crc = new CRC32();
			crc.update(testNr);
			crc.update(serialNr);
			crc.update(text.getBytes());
			crc.update(choice.ordinal());
			if (userData != null) {
				crc.update(userData);
			}
			return crc.getIntValue();
		}
	}

	/** A <code>ConnectionListener</code> to receive 
		 * <code>JennyNetByteBuffer</code> and return them via an output method
		 * as list of byte arrays. There is a lock as parameter which gets 
		 * notified with each reception event.
		 */
		private class RealObjectReceptionListener extends DefaultConnectionListener {
	
			private List<Object> received = new ArrayList<Object>();
			private Object lock;
			private int unlockThreshold;
	
			public RealObjectReceptionListener (Object lock, int unlockSize) {
				this.lock = lock;
				unlockThreshold = unlockSize;
			}
			
			@Override
			public void objectReceived (Connection con, SendPriority priority, long objNr, Object obj) {
				received.add(obj);
				
				if (received.size() == unlockThreshold)
				synchronized(lock) {
					lock.notify();
				}
			}
			
			public List<Object> getReceived () {
				return received;
			}
			
	//		public void reset (int unlockSize) {
	//			received.clear();
	//			unlockThreshold = unlockSize;
	//		}
	
			public void reset () {
				received.clear();
			}
		}


	@Test
	public void transmit_real_objects () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final RealObjectReceptionListener receptionListener = new RealObjectReceptionListener(lock, 1);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000));
		sv.getParameters().setSerialisationMethod(1);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.getParameters().setSerialisationMethod(1);
		cl.addListener(receptionListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		synchronized (lock) {
			
			Connection scon = sv.getConnections()[0];
	
			// CASE 1
			// prepare random data block to transmit
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);

			// prepare object of class TestObject_C1
			TestObject_C1 obj1 = new TestObject_C1(block);
			int crc1 = obj1.getCrc();
			scon.getSendSerialization().registerClass(TestObject_C1.class);
			cl.getReceiveSerialization().registerClass(TestObject_C1.class);
			
			// send over connection
			long time = System.currentTimeMillis();
			scon.sendObject(obj1);
			lock.wait(1000);
			int elapsed = (int)(System.currentTimeMillis() - time);
			
			// check received data
			assertFalse("no object received by server", receptionListener.getReceived().isEmpty());
			Object object = receptionListener.getReceived().get(0);
			assertTrue("received object is not of sender class", object instanceof TestObject_C1);
			TestObject_C1 recObj = (TestObject_C1)object;
			assertTrue("data integrity error (transmitted 1)", recObj.getCrc() == crc1);
			System.out.println("-- transmission 1 verified, time elapsed " + elapsed + " ms");
			assertTrue("transmission time error", elapsed >= 0 & elapsed < 200);
			
		}
	
	// shutdown net systems
	} finally {
		System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
				+ Runtime.getRuntime().totalMemory());
		if (sv != null) {
			sv.closeAndWait(2000);
		}
		if (cl != null) {
			cl.closeAndWait(2000);
		}
	}
	}


	@Test
	public void send_queue_full () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000));
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.getParameters().setAlivePeriod(5000);
			cl.getParameters().setObjectQueueCapacity(50);
			cl.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(50);
			
			ServerConnection scon = sv.getConnections()[0];
			int speed = 20000;
			scon.setTempo(speed);
			Util.sleep(10);
			
			// prepare random data block to transmit
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);
			
			// start sending from client
			try {
				for (int i = 0; i < 100; i++) {
					cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
				}
				fail("ListOverflowException for send-queue (client) overflow expected");
			} catch (ListOverflowException e) {
			}
			
		// shutdown net systems
		} finally {
			if (sv != null) {
				sv.closeAndWait(2000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
	}


	@Test
	public void send_serialisation_overflow () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000));
			sv.getParameters().setAlivePeriod(0);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.getParameters().setAlivePeriod(5000);
			cl.getParameters().setMaxSerialisationSize(1000);
			cl.addListener(new EventReporter());
			cl.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(50);
			
			ServerConnection scon = sv.getConnections()[0];
			int speed = 20000;
			scon.setTempo(speed);
			scon.getParameters().setMaxSerialisationSize(2000);
			Util.sleep(50);

			// prepare random data block to transmit
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);
			
			// start sending from client
			cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
//				System.out.println("-- EXCEPTION thrown for SEND-DATA on client");
			
			// start sending from server
//			scon.sendData(block, 0, dataLen, SendPriority.Normal);
			Util.sleep(1000);
			
			assertTrue("expected connection closed (client)", cl.isClosed());
			assertTrue("expected connection closed (server)", scon.isClosed());
			
			// TODO test exception type via connection listener
			
		// shutdown net systems
		} finally {
			System.out.println("## ENTERING FINALLY SECTION");
			if (sv != null) {
				sv.closeAndWait(2000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
	}


	@Test
	public void multi_client_unrestricted_transfer () throws IOException, InterruptedException {
		
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 3, Station.SERVER);
		final ObjectReceptionListener clientListener = new ObjectReceptionListener(lock, 3, Station.CLIENT);
//		ConnectionListener eventReporter = new EventReporter();
		Server sv = null;
		Client cl1 = null, cl2 = null, cl3 = null;
		int crc1, crc2, crc3;
	
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			sv.getParameters().setAlivePeriod(0);
			sv.start();
			
			JennyNet.getDefaultParameters().setTransmissionParcelSize(8*1024);
			
			// set up a running connection
			cl1 = new Client();
			cl1.addListener(clientListener);
			cl1.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl1.toString());
			
			cl2 = new Client();
			cl2.addListener(clientListener);
			cl2.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl2.toString());
			
			cl3 = new Client();
			cl3.addListener(clientListener);
			cl3.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl3.toString());
			
			
			Util.sleep(20);
			
			synchronized (lock) {
				
				// CASE 1
				// prepare random data block to transmit
				int dataLen = 100000;
				byte[] block1 = Util.randBytes(dataLen);
				crc1 = Util.CRC32_of(block1);
				byte[] block2 = Util.randBytes(dataLen);
				crc2 = Util.CRC32_of(block2);
				byte[] block3 = Util.randBytes(dataLen);
				crc3 = Util.CRC32_of(block3);
				
				// send over connection and close it immediately
				long time = System.currentTimeMillis();
				cl1.sendData(block1, 0, dataLen, SendPriority.NORMAL);
				cl2.sendData(block2, 0, dataLen, SendPriority.NORMAL);
				cl3.sendData(block3, 0, dataLen, SendPriority.NORMAL);
				cl1.close();
				cl2.close();
				cl3.close();
				
				lock.wait();
				int elapsed = (int)(System.currentTimeMillis() - time);
				
				// check received data
				List<byte[]> recList = receptionListener.getReceived();
				assertFalse("no object received by server", recList.isEmpty());
				assertTrue("false received list size: " + recList.size(), recList.size() == 3);

				assertTrue("data integrity error (transmit cl-1)", receptionListener.containsBlockCrc(crc1));
				assertTrue("data integrity error (transmit cl-2)", receptionListener.containsBlockCrc(crc2));
				assertTrue("data integrity error (transmit cl-3)", receptionListener.containsBlockCrc(crc3));
				System.out.println("-- 3 transmissions verified, time elapsed " + elapsed + " ms");
				assertTrue("transmission time too long (limit 160, was " + elapsed, elapsed <= 300);
				
				System.out.println("-- transmission verified: " + receptionListener.getSize() + ", time elapsed " + elapsed + " ms");
				System.out.println("\r\n## pausing ...");
				Util.sleep(1000);
				System.out.println("\r\n## done");
				
			}
			
			// shutdown net systems
		} finally {
			System.out.println("------------------------------------------------------------------ ");
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(5000);
			}
			
			receptionListener.reportEvents();
			clientListener.reportEvents();
		}
	}

	@Test
	public void break_transmission () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		final Object lock = new Object();
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
		final ObjectReceptionListener svListener = new ObjectReceptionListener(lock, 1, Station.SERVER);
	
	try {
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
		sv.getParameters().setAlivePeriod(0);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.getParameters().setTransmissionSpeed(10000);
		cl.addListener(clListener);
		cl.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(30);
		ServerConnection scon = sv.getConnections()[0];
		
		synchronized (lock) {
			
			// prepare random data block to transmit
			int dataLen = 100000;
			byte[] block = Util.randBytes(dataLen);
			
			// CASE 1 : interrupting object sending 
			// start sending from client
			long sendId1 = cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			Util.sleep(2000);

			// break transmission
			String text = "did not mean it to happen";
			cl.breakTransfer(sendId1, ComDirection.OUTGOING, text);
			Util.sleep(50);

			// check reception side
			assertTrue("falsely object received by server", svListener.getReceived().isEmpty());
			assertTrue("falsely issued ABORTED event on reception", svListener.getEvents(ConnectionEventType.ABORTED).isEmpty());

			// check sender side: ABORTED event occurring
			List<ConnectionEvent> list = clListener.getEvents(ConnectionEventType.ABORTED);
			assertFalse("no ABORTED even on sender", list.isEmpty());
			assertTrue("too many ABORTED events on sender", list.size() == 1);
			ConnectionEvent evt = list.get(0);
			assertTrue("false event-info value", evt.getInfo() == 201);
			assertNotNull(evt.getObject());
			assertTrue("JennyNetByteBuffer expected", evt.getObject() instanceof JennyNetByteBuffer);
			assertTrue(evt.getObjectNr() == sendId1);
			assertTrue(evt.getText().indexOf(text) > -1);
			
			// CASE 2 : hard-closure while sending 
			// start sending from client
			sendId1 = cl.sendData(block, 0, dataLen, SendPriority.NORMAL);
			Util.sleep(3000);

			// hard-close connection
			cl.closeHard();
			Util.sleep(50);

			// check reception side
			assertTrue("falsely object received by server", svListener.getReceived().isEmpty());
			assertTrue("falsely issued ABORTED event on reception", svListener.getEvents(ConnectionEventType.ABORTED).isEmpty());

			// check sender side: ABORTED event occurring
			list = clListener.getEvents(ConnectionEventType.ABORTED);
			assertTrue("too many ABORTED events on sender", list.size() == 2);
			evt = list.get(1);
			assertTrue("false event-info value", evt.getInfo() == 205);
			assertNotNull(evt.getObject());
			assertTrue("JennyNetByteBuffer expected", evt.getObject() instanceof JennyNetByteBuffer);
			assertTrue(evt.getObjectNr() == sendId1);
			assertTrue(evt.getText().indexOf("connection closed hardly") > -1);
			
		}
		// shutdown net systems
		} finally {
			System.out.println("\n--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
	}

	@Test
	public void send_failure () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		long sendId1;
		
		final Object lock = new Object();
		final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
		final ObjectReceptionListener svListener = new ObjectReceptionListener(lock, 1, Station.SERVER);
	
		try {
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.getParameters().setTransmissionParcelSize(8*1024);
			cl.getParameters().setTransmissionSpeed(100000);
			cl.addListener(clListener);
			
			// CASE 1: unconnected client
			try {
				cl.sendObject(new Integer(428));
				fail("UnconnectedException excepted");
			} catch (UnconnectedException e) {
			}
			
			cl.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(30);
			ServerConnection scon = sv.getConnections()[0];
			
			// CASE 2: null object
			try {
				cl.sendObject(null);
				fail("NullPointerException excepted");
			} catch (NullPointerException e) {
			}
			
			// CASE 3: unregistered object class
			CustomClass_2 object = new CustomClass_2();
			
			try {
				sendId1 = cl.sendObject(object);
				fail("UnregisteredObjectException excepted");
			} catch (UnregisteredObjectException e) {
			}

			// CASE 4: no send-serialisation
			int method = cl.getParameters().getSerialisationMethod();
			cl.getParameters().setSerialisationMethod(2);
			try {
				sendId1 = cl.sendObject(new Integer(428));
				fail("SerialisationUnavailableException excepted");
			} catch (SerialisationUnavailableException e) {
				cl.getParameters().setSerialisationMethod(method);
			}
			
			// CASE 5: remote class failure (remote unregistered class)
			Serialization serial = cl.getSendSerialization();
			serial.registerClass(CustomClass_2.class);
			sendId1 = cl.sendObject(object);
			Util.sleep(100);

			// check reception side
			assertTrue("falsely object received by server", svListener.getReceived().isEmpty());
			assertTrue("falsely issued ABORTED event on reception", svListener.getEvents(ConnectionEventType.ABORTED).isEmpty());

			// check sender side: ABORTED event occurring
			List<ConnectionEvent> list = clListener.getEvents(ConnectionEventType.ABORTED);
			assertFalse("no ABORTED even on sender", list.isEmpty());
			assertTrue("too many ABORTED events on sender", list.size() == 1);
			ConnectionEvent evt = list.get(0);
			assertTrue("false event-info value", evt.getInfo() == 207);
			assertTrue(evt.getObjectNr() == sendId1);
			
//			// CASE 6: no receive serialisation
//			scon.getParameters().setSerialisationMethod(2);
//			sendId1 = cl.sendObject(new Integer(428));
//			Util.sleep(100);
//			cl.getParameters().setSerialisationMethod(method);
//			
//			// check reception side
//			assertTrue("falsely object received by server", svListener.getReceived().isEmpty());
//			assertTrue("falsely issued ABORTED event on reception", svListener.getEvents(ConnectionEventType.ABORTED).isEmpty());
//
//			// check sender side: ABORTED event occurring
//			list = clListener.getEvents(ConnectionEventType.ABORTED);
//			assertTrue("too many ABORTED events on sender", list.size() == 2);
//			evt = list.get(1);
//			assertTrue("false event-info value", evt.getInfo() == 209);
//			assertTrue(evt.getObjectNr() == sendId1);
			
			// CASE 7: closed connection
			cl.closeAndWait(5000);
			assertTrue("connection close failure", cl.isClosed());
			try {
				cl.sendObject(new Integer(428));
				fail("ClosedConnectionException excepted");
			} catch (ClosedConnectionException e) {
			}
			
		// shutdown net systems
		} finally {
			System.out.println("\n--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv != null) {
				sv.closeAndWait(2000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
	}
	
	@Test
	/** We test that the sender can use a different serialisation method
	 * than the default on the receiver.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void serialisation_divergence () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
		try {
			final ObjectReceptionListener clListener = new ObjectReceptionListener(Station.CLIENT);
			final ObjectReceptionListener svListener = new ObjectReceptionListener(Station.SERVER);
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), svListener);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.addListener(clListener);
			cl.connect(100, sv.getSocketAddress());
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(50);
			
			ServerConnection scon = sv.getConnections()[0];
			scon.getParameters().setSerialisationMethod(1);
			assertTrue(cl.getParameters().getSerialisationMethod() == 0);
			assertTrue(cl.getSendSerialization().getMethodID() == 0);
			assertTrue(scon.getParameters().getSerialisationMethod() == 1);
			assertTrue(scon.getReceiveSerialization().getMethodID() == 1);
			
			// send object on default method (0)
			cl.sendObject(new Integer(89925));
			Util.sleep(300);
			
			assertFalse("unexpected connection closed (client)", cl.isClosed());
			assertFalse("unexpected connection closed (server)", scon.isClosed());
			
			// check exception type via connection listener
			List<ConnectionEvent> list = svListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("no OBJECT event (receiver)", list.size() == 1);
			ConnectionEvent evt = list.get(0);
			assertTrue("false object type", evt.getObject() instanceof Integer);
			assertTrue("false object value", ((Integer)evt.getObject()).intValue() == 89925);
			
			// send object on deviant method (1)
			System.out.println("\n---- CASE diverse send method (1) -------");
			cl.sendObject(new Integer(66301), 1, SendPriority.NORMAL);
			Util.sleep(300);
			
			assertFalse("unexpected connection closed (client)", cl.isClosed());
			assertFalse("unexpected connection closed (server)", scon.isClosed());
			
			// check exception type via connection listener
			list = svListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("no OBJECT event (receiver)", list.size() == 2);
			evt = list.get(1);
			assertTrue("false object type", evt.getObject() instanceof Integer);
			assertTrue("false object value", ((Integer)evt.getObject()).intValue() == 66301);
			
			
			
		// shutdown net systems
		} finally {
			System.out.println("## ENTERING FINALLY SECTION");
			if (sv != null) {
				sv.closeAndWait(2000);
			}
			if (cl != null) {
				cl.closeAndWait(1000);
			}
		}
	}

	private static class CustomClass_2 implements Serializable {
		private static final long serialVersionUID = 9340972872487L;
		
		byte[] klamm = Util.randBytes(300);
		int anzahl = 105;
		String text = "Drei Beine hat der Teufel";
		
		@Override
		public boolean equals(Object obj) {
			CustomClass_2 o = (CustomClass_2) obj;
			return o.anzahl == this.anzahl && o.text.equals(text) &&
					o.klamm == klamm || Util.equalArrays(o.klamm, klamm);
		}
	}


}
