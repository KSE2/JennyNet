/*  File: TestUnit_File_Transfer.java
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionMonitor;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.exception.FileInTransmissionException;
import org.kse.jennynet.exception.ListOverflowException;
import org.kse.jennynet.exception.RemoteTransferBreakException;
import org.kse.jennynet.exception.UserBreakException;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.intfa.TransmissionEventType;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.Util;

public class TestUnit_File_Transfer {

	public TestUnit_File_Transfer() {
	}

	/** This sends three files from client to server, each one under different
	 * circumstance and each one tested for the results.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void client_single () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection svCon;
		
		final Object lock = new Object();
		final SemaphorLock sendLock = new SemaphorLock(2);
		final FileReceptionListener receptionListener = new FileReceptionListener(lock, 1, Station.SERVER);
		final FileReceptionListener sendListener = new FileReceptionListener(sendLock, Station.CLIENT);

	try {
		System.out.println("\nTEST TRANSFER CLIENT TO SERVER: SINGLE");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setFileRootDir(new File("test"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.addListener(sendListener);
		cl.connect(100, sv.getSocketAddress());
		cl.setTempo(15000);
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		svCon = sv.getConnections()[0];
		
		synchronized (lock) {
			// CASE 1 : 50,000 data file, speed limit 15.000
			System.out.println("\nCASE 1 : single file 50,000 - no target, speed limit 15.000");

			// prepare data and source file
			int length = 50000;
			byte[] data = Util.randBytes(length);
			File src = Util.getTempFile(); 
			Util.makeFile(src, data);
			
			// transmit file (speed limit)
			String targetPath = "empfang/ursula-1.dat";
			cl.sendFile(src, targetPath);
			long stamp = System.currentTimeMillis();
			Util.sleep(600);
			
			// check client monitor values (after scheduling)
			ConnectionMonitor mon = cl.getMonitor();
			assertTrue(mon.transmitSpeed == 15000);
			assertTrue(mon.filesOutgoing == 1);
			assertTrue(mon.filesIncoming == 0);
			assertTrue(mon.transmitting);
			
			// check server connection monitor values (after scheduling)
			mon = svCon.getMonitor();
			assertTrue(mon.transmitSpeed == 15000);
			assertTrue(mon.filesOutgoing == 0);
			assertTrue(mon.filesIncoming == 1);
			assertTrue(mon.transmitting);
			
			// wait for completion
			receptionListener.wait_on_release(0);
			sendListener.wait_on_release(5000);
			long time = System.currentTimeMillis() - stamp;

			// control received file content
			File file = receptionListener.getReceived().get(0);
			File rootDir = svCon.getParameters().getFileRootDir();
			assertTrue("target path not realised", file.getAbsolutePath().endsWith(targetPath));
			assertTrue("root directory not met", file.getAbsolutePath().startsWith(rootDir.getAbsolutePath()));
			byte [] rece = Util.readFile(file);
			assertTrue("data integrity error in file transmission (1)", Util.equalArrays(rece, data));
			assertTrue("transmission time failure", time > 3000 & time < 5000);
			
			// check monitor values (after transmission)
			mon = cl.getMonitor();
			assertTrue(mon.transmitSpeed == 15000);
			assertTrue(mon.filesOutgoing == 0);
			assertTrue(mon.exchangedVolume >= 50000);
			assertTrue(mon.currentSendLoad == 0);
			assertTrue(mon.parcelsScheduled == 0);
			assertTrue(mon.lastSendTime > stamp & mon.lastSendTime < System.currentTimeMillis());
			assertTrue(mon.lastReceiveTime > stamp & mon.lastReceiveTime < System.currentTimeMillis());
			assertTrue(mon.transmitting);
			assertFalse(mon.isIdle);
			
			// check FILE-SENDING event
			TransmissionEvent evt = sendListener.getFirstEventOf(TransmissionEventType.FILE_SENDING);
			assertNotNull("event FILE_SENDING is missing", evt);
			assertTrue(evt.getConnection().equals(cl));
			assertTrue(evt.getDirection() == ComDirection.OUTGOING);
			assertTrue(evt.getDuration() == 0);
			assertNull(evt.getException());
			assertTrue(evt.getExpectedLength() == 50000);
			assertTrue(evt.getFile().equals(src));
			assertTrue(evt.getInfo() == 0);
			assertTrue(evt.getObjectID() == 1);
			assertTrue(evt.getPath().equals(targetPath));
			assertTrue(evt.getTransmissionLength() == 0);
			assertTrue(evt.getType() == TransmissionEventType.FILE_SENDING);
			
			// check FILE-CONFIRM event
			evt = sendListener.getFirstEventOf(TransmissionEventType.FILE_CONFIRMED);
			assertNotNull("event FILE_CONFIRM is missing", evt);
			assertTrue(evt.getConnection().equals(cl));
			assertTrue(evt.getDirection() == ComDirection.OUTGOING);
			assertTrue(evt.getDuration() > 3000);
			assertNull(evt.getException());
			assertTrue(evt.getExpectedLength() == 0);
			assertTrue(evt.getFile().equals(src));
			assertTrue(evt.getInfo() == 0);
			assertTrue(evt.getObjectID() == 1);
			assertTrue(evt.getPath().equals(targetPath));
			assertTrue(evt.getTransmissionLength() == 50000);
			assertTrue(evt.getType() == TransmissionEventType.FILE_CONFIRMED);
			
			// check FILE-INCOMING event
			evt = receptionListener.getFirstEventOf(TransmissionEventType.FILE_INCOMING);
			assertNotNull("event FILE_INCOMING is missing", evt);
			assertTrue(evt.getConnection().equals(svCon));
			assertTrue(evt.getDirection() == ComDirection.INCOMING);
			assertTrue(evt.getDuration() == 0);
			assertNull(evt.getException());
			assertTrue(evt.getExpectedLength() == 50000);
			assertNotNull(evt.getFile());
			assertTrue(evt.getFile().getPath().endsWith(".temp"));
			assertTrue(evt.getInfo() == 0);
			assertTrue(evt.getObjectID() == 1);
			assertTrue(evt.getPath().equals(targetPath));
			assertTrue(evt.getTransmissionLength() == 0);
			assertTrue(evt.getType() == TransmissionEventType.FILE_INCOMING);
			
			// check FILE-RECEIVED event
			evt = receptionListener.getFirstEventOf(TransmissionEventType.FILE_RECEIVED);
			assertNotNull("event FILE_RECEIVED is missing", evt);
			assertTrue(evt.getConnection().equals(svCon));
			assertTrue(evt.getDirection() == ComDirection.INCOMING);
			assertTrue("duration was " + evt.getDuration(), evt.getDuration() > 2500);
			assertNull(evt.getException());
			assertTrue(evt.getExpectedLength() == 50000);
			assertNotNull(evt.getFile());
			assertTrue(evt.getFile().getPath().endsWith(targetPath));
			assertTrue(evt.getInfo() == 0);
			assertTrue(evt.getObjectID() == 1);
			assertTrue(evt.getPath().equals(targetPath));
			assertTrue(evt.getTransmissionLength() == 50000);
			assertTrue(evt.getType() == TransmissionEventType.FILE_RECEIVED);
			
			// CASE 2 : transmit file (no speed limit)
			System.out.println("\nCASE 2 : single file 50,000 - no target, no speed limit");
			receptionListener.reset();
			sendListener.reset();
			targetPath = "empfang/ursula-2.dat";
			cl.setTempo(-1);
			cl.sendFile(src, targetPath);
			stamp = System.currentTimeMillis();
			
			// wait for completion
			receptionListener.wait_on_release(0);
			sendListener.wait_on_release(5000);
			time = System.currentTimeMillis() - stamp;

			// control received file content
			file = receptionListener.getReceived().get(0);
			assertTrue("target path not realised", file.getAbsolutePath().endsWith(targetPath));
			rece = Util.readFile(file);
			assertTrue("data integrity error in file transmission (2)", Util.equalArrays(rece, data));
			assertTrue("transmission time failure", time > 0 & time < 500);
			
			// CASE 3 : empty data file
			System.out.println("\nCASE 3 : single empty file - no target");
			receptionListener.reset();
			sendListener.reset();
			src = Util.getTempFile();
			targetPath = "empfang/ursula-3.dat";
			cl.sendFile(src, targetPath);
			stamp = System.currentTimeMillis();
			
			// wait for completion
			receptionListener.wait_on_release(5000);
			sendListener.wait_on_release(5000);
			time = System.currentTimeMillis() - stamp;

			// control received file
			assertFalse("no file received", receptionListener.getReceived().isEmpty());
			file = receptionListener.getReceived().get(0);
			assertTrue("target path not realised", file.getAbsolutePath().endsWith(targetPath));
			assertTrue("root directory not met", file.getAbsolutePath().startsWith(rootDir.getAbsolutePath()));
			assertTrue("transmitted file should be empty but has length " + file.length(), file.length() == 0);
			assertTrue("transmission time failure", time > 0 & time < 500);
			
			// wait (test)
			Util.sleep(2000);
		}		
	} finally {
		System.out.println();
		if (sv != null) {
			if (sv.getConnections().length > 0) {
				System.out.println("# transmission volume of Server : " + 
					sv.getConnections()[0].getMonitor().exchangedVolume);
			}
			sv.closeAndWait(2000);
		}
		if (cl != null) {
			System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
			cl.closeAndWait(1000);
		}
	}
	}


	@Test
	public void client_single_fail () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null, cl2 = null;
		Connection svCon, svCon2;

		final Object lock = new Object();
		final SemaphorLock sendLock = new SemaphorLock(2);
		final SemaphorLock receLock = new SemaphorLock(2);
		final FileReceptionListener receptionListener = new FileReceptionListener(receLock, Station.SERVER);
		final FileReceptionListener sendListener = new FileReceptionListener(sendLock, Station.CLIENT);

	try {
		System.out.println("\nTEST TRANSFER CLIENT TO SERVER: SINGLE, TARGET DIR UNDEFINED");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setFileRootDir(new File("test"));
		sv.start();

		// set up 2 running connections
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(8*1024);
//		cl.getParameters().setMaxFileSendProcessors(0);
		cl.addListener(sendListener);
		cl.connect(100, sv.getSocketAddress());
		cl.setTempo(15000);
		cl2 = new Client();
		cl2.getParameters().setTransmissionParcelSize(8*1024);
		cl2.connect(100, sv.getSocketAddress());
		cl2.setTempo(15000);
		
		System.out.println("-- (test) connection established " + cl.toString());
		Util.sleep(50);
		svCon = sv.getConnection(cl.getLocalAddress());
		assertNotNull("connection missing on server", svCon);
		svCon2 = sv.getConnection(cl2.getLocalAddress());
		assertNotNull("second connection missing on server", svCon2);
		svCon2.getParameters().setFileRootDir(null);
		
		synchronized (lock) {
			// CASE 1 : 50,000 data file
			System.out.println("\nCASE 1 : single file 50,000 - target ");

			// prepare data and source files
			int length = 50000;
			byte[] data = Util.randBytes(length);
			File src = Util.getTempFile(); 
			Util.makeFile(src, data);
			String remotePath = "kannenschleifer.data";
			
			byte[] data2 = Util.randBytes(length);
			File src2 = Util.getTempFile(); 
			Util.makeFile(src2, data2);
			String remotePath2 = "oberrainerhaus.data";
			
//			// 1. ***  unsupported send attempt (file-processors at zero) 
//			try {
//				cl.sendFile(src, remotePath);
//				fail("expected UnsupportedOperationException");
//			} catch (UnsupportedOperationException e) {
//			}
			
//			cl.getParameters().setMaxFileSendProcessors(2);
			
			// 2. ***  file-not-found sending attempt 
			try {
				cl.sendFile(new File("buxtehude-strandkorb"), remotePath);
				fail("expected FileNotFoundException");
			} catch (FileNotFoundException e) {
			}
			
			// 3. ***  remote-path void sending attempt 
			try {
				cl.sendFile(src, null);
				fail("expected NullPointerException");
			} catch (NullPointerException e) {
			}
			
			// 4. ***  source-file void sending attempt 
			try {
				cl.sendFile(null, remotePath);
				fail("expected NullPointerException");
			} catch (NullPointerException e) {
			}
			
			// 5. ***  remote-path empty sending attempt 
			try {
				cl.sendFile(src, "");
				fail("expected IllegalArgumentException");
			} catch (IllegalArgumentException e) {
			}
			
			// sending valid file
			long fid1 = cl.sendFile(src, remotePath);
			Util.sleep(50);
			
			// 6. ***  duplicate file-sending attempt (while in transmission) 
			try {
				cl.sendFile(src2, remotePath);
				fail("expected FileInTransmissionException");
			} catch (FileInTransmissionException e) {
			}
			
			// 7.  ***  sender aborted transmission
			Util.sleep(1000);
			String msg1 = "did not want it any more";
			boolean chk = cl.breakTransfer(fid1, ComDirection.INCOMING, msg1);
			assertFalse("unreal transmission", chk);
			cl.breakTransfer(fid1, ComDirection.OUTGOING, msg1);
			
			// wait for events
			sendLock.lock_wait(1000);
			receLock.lock_wait(1000);

			// control FILE_ABORTED event (server)
			TransmissionEvent evt = receptionListener.getLastEvent();
			assertTrue("FILE_ABORTED event expected", evt.getType() == TransmissionEventType.FILE_ABORTED);
			assertTrue(evt.getDirection() == ComDirection.INCOMING);
			assertTrue("error-info 106 expected", evt.getInfo() == 106);
			assertNotNull("file-info expected", evt.getFile());
			assertTrue("TEMP file expected to exist", evt.getFile().exists());
			assertTrue(evt.getExpectedLength() == 50000);
			assertTrue("duration = " + evt.getDuration(), evt.getDuration() >= 400);
			assertNotNull(evt.getException());
			assertTrue(evt.getObjectID() == fid1);
			assertTrue(evt.getPath().equals(remotePath));
			assertTrue("T-length = " + evt.getTransmissionLength(), evt.getTransmissionLength() == 8192);
			assertTrue("file received, none expected", receptionListener.getReceived().isEmpty());

			// control FILE_ABORTED event (client)
			evt = sendListener.getLastEvent();
			assertTrue("FILE_ABORTED event expected", sendListener.getSignalType() == TransmissionEventType.FILE_ABORTED);
			assertTrue(evt.getDirection() == ComDirection.OUTGOING);
			assertTrue("error-info 105 expected", evt.getInfo() == 105);
			assertNotNull("file-info expected", evt.getFile());
			assertTrue("sender file has disappeared", evt.getFile().exists());
			assertTrue(evt.getExpectedLength() == 50000);
			assertTrue("duration = " + evt.getDuration(), evt.getDuration() >= 400);
			assertNotNull(evt.getException());
			assertTrue(evt.getObjectID() == fid1);
			assertTrue(evt.getPath().equals(remotePath));
			assertTrue("T-length = " + evt.getTransmissionLength(), evt.getTransmissionLength() == 8192);
			
			// 8.  ***  receiver aborted transmission
			System.out.println("\n------- Sending second file ---------\n");
			sendListener.reset();
			receptionListener.reset();
			long fid2 = cl.sendFile(src2, remotePath2);
			Util.sleep(1000);
			
			String msg2 = "no use for this file";
			chk = svCon.breakTransfer(fid2, ComDirection.OUTGOING, msg2);
			assertFalse("unreal transmission", chk);
			chk = svCon.breakTransfer(fid2, ComDirection.INCOMING, msg2);
			assertTrue("unreal transmission", chk);

			// wait for events
			sendLock.lock_wait(1000);
			receLock.lock_wait(1000);
			
			// check FILE_ABORTED event (server)
			evt = receptionListener.getLastEvent();
			assertTrue("FILE_ABORTED event expected", evt.getType() == TransmissionEventType.FILE_ABORTED);
			assertTrue(evt.getDirection() == ComDirection.INCOMING);
			assertTrue("error-info 108 expected", evt.getInfo() == 108);
			assertTrue(evt.getConnection() == svCon);
			assertTrue(evt.getObjectID() == fid2);
			assertTrue(evt.getPath().equals(remotePath2));
			assertNotNull("file-info expected", evt.getFile());
			assertTrue("TEMP file expected to exist", evt.getFile().exists());
			assertTrue(evt.getExpectedLength() == 50000);
			assertTrue("T-length = " + evt.getTransmissionLength(), evt.getTransmissionLength() == 8192);
			assertTrue("duration = " + evt.getDuration(), evt.getDuration() >= 380);
			Throwable ex = evt.getException();
			assertNotNull(ex);
			assertTrue("expected UserBreakException", ex instanceof UserBreakException);
			assertTrue("file received, none expected", receptionListener.getReceived().isEmpty());
			
			// check FILE_ABORTED event (client)
			evt = sendListener.getLastEvent();
			assertTrue("FILE_ABORTED event expected", evt.getType() == TransmissionEventType.FILE_ABORTED);
			assertTrue(evt.getDirection() == ComDirection.OUTGOING);
			assertTrue("error-info 108 expected", evt.getInfo() == 107);
			assertTrue(evt.getConnection() == cl);
			assertTrue(evt.getObjectID() == fid2);
			assertTrue(evt.getPath().equals(remotePath2));
			assertNotNull("file-info expected", evt.getFile());
			assertTrue("source file disappeared", evt.getFile().exists());
			assertTrue(evt.getExpectedLength() == 50000);
			assertTrue("T-length = " + evt.getTransmissionLength(), evt.getTransmissionLength() == 8192);
			assertTrue("duration = " + evt.getDuration(), evt.getDuration() >= 400);
			ex = evt.getException();
			assertNotNull(ex);
			assertTrue("expected RemoteTransferBreakException", ex instanceof RemoteTransferBreakException);
			assertTrue("file received, none expected", sendListener.getReceived().isEmpty());
			
			// failed send attempt on missing remote file-root-dir
			System.out.println("\n------- Sending on second connection (no root-dir) ---------\n");
			sendListener.reset();
			receptionListener.reset();
			cl2.addListener(sendListener);
			svCon2.addListener(receptionListener);
			long fid3 = cl2.sendFile(src2, remotePath2);
			
			Util.sleep(1000);
			evt = sendListener.getFirstEventOf(TransmissionEventType.FILE_ABORTED);
			assertNotNull(evt);
			assertTrue(evt.getConnection() == cl2);
			assertTrue(evt.getObjectID() == fid3);
			assertTrue(evt.getDirection() == ComDirection.OUTGOING);
			assertTrue(evt.getInfo() == 101);
			assertTrue(evt.getPath().equals(remotePath2));
			assertTrue(evt.getTransmissionLength() == 8192);
			assertTrue(evt.getDuration() > 0);
			ex = evt.getException();
			assertNotNull("error exception expected, found null", ex);
			assertTrue("expected RemoteTransferBreakException", ex instanceof RemoteTransferBreakException);

			// silent reproach on server connection
			assertTrue(receptionListener.getEvents().isEmpty());
			
		}		
	} finally {
		System.out.println();
		if (sv != null) {
			if (sv.getConnections().length > 0) {
				System.out.println("# transmission volume of Server : " + 
					sv.getConnections()[0].getMonitor().exchangedVolume);
			}
			sv.closeAndWait(3000);
		}
		if (cl != null) {
			System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
			cl.closeAndWait(1000);
		}
	}
	}

	/** This sends three files from client to server, each one under different
	 * circumstance and each one tested for the results.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void client_masses () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		Connection svCon;
		long time = 0;
		
		final Object lock = new Object();
		final FileReceptionListener receptionListener = new FileReceptionListener(lock, 10, Station.SERVER);
		final FileReceptionListener sendListener = new FileReceptionListener(Station.CLIENT);

		try {
			System.out.println("\nTEST TRANSFER CLIENT TO SERVER: MASSES OF FILES, QUEUE TESTING");
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			File dir = new File("test/masses");
			dir.mkdir();
			sv.getParameters().setFileRootDir(dir);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.getParameters().setTransmissionParcelSize(16*1024);
			cl.getParameters().setObjectQueueCapacity(10);
			cl.addListener(sendListener);
			cl.connect(100, sv.getSocketAddress());
			cl.setTempo(50000);
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(20);
			svCon = sv.getConnections()[0];
	
			// create 10 random datafiles
			int fileSize = 50000;
			File[] farr = new File[10];
			for (int i = 0; i < 10; i++) {
				farr[i] = Util.randomTempFile(fileSize);
			}
			
			time = System.currentTimeMillis();
			// send file-vector
			for (int i = 0; i < farr.length; i++) {
				cl.sendFile(farr[i], "random-" + i + ".dat");
			}
			
			// fail: send one more file
			try {
				cl.sendFile(farr[0], "error-file.dat");
				fail("expected ListOverflowException");
			} catch (ListOverflowException e) {
			}
			
			// on the way
			ConnectionMonitor monitor = cl.getMonitor();
			System.out.println("--- (monitor) files outgoing: " + monitor.filesOutgoing);
			assertTrue(monitor.filesOutgoing == 10);
			
			Util.sleep(20);
			monitor = cl.getMonitor();
			System.out.println("--- (monitor) parcels scheduled: " + monitor.parcelsScheduled);
			System.out.println("--- (monitor) sendload: " + monitor.currentSendLoad);
			
			// close connection (enter SHUTDOWN)
			cl.close();
			
			synchronized (lock) {
				lock.wait();
				System.out.println("## Reception-Lock released");
			}
			monitor = svCon.getMonitor();
			assertTrue(monitor.filesReceived == 10);
			assertTrue(monitor.exchangedVolume > 500000);
			
			cl.waitForClosed(5000);
			
		} finally {
			System.out.println("\n### FINAL,  duration = " + (System.currentTimeMillis() - time));
			if (sv != null) {
				if (sv.getConnections().length > 0) {
					System.out.println("# transmission volume of Server : " + 
						sv.getConnections()[0].getMonitor().exchangedVolume);
				}
				sv.closeAndWait(3000);
			}
			if (cl != null) {
				System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
				cl.closeAndWait(1000);
			}
		}
	}
	
	/** Whether 'base' contains 'data' by comparison of byte values 
	 * (equal-arrays).
	 * 
	 * @param base byte[][]
	 * @param data byte[]
	 * @return
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
	public void client_multi () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null, cl2;
		
		final Object lock = new Object();
		final FileReceptionListener receptionListener = new FileReceptionListener(lock, 3, Station.SERVER);
		final FileReceptionListener sendListener = new FileReceptionListener(Station.CLIENT);
		byte[][] results;

	try {
		System.out.println("\nCase-1: TEST TRANSFER CLIENT TO SERVER: MULTI-FILE");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
		sv.getParameters().setFileRootDir(new File("test"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.addListener(sendListener);
		cl.connect(100, sv.getSocketAddress());
		cl.setTempo(15000);
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		// second client
		cl2 = new Client();
		cl2.getParameters().setTransmissionParcelSize(8*1024);
		cl.addListener(sendListener);
		cl2.connect(100, sv.getSocketAddress());
		cl2.setTempo(15000);
		
		synchronized (lock) {
			// CASE 1 : 3 x 50,000 data files
			System.out.println("\nCASE 1 : multi data files 50,000 - 1 processor, priority Normal");

			// prepare data and source file
			int length = 50000;
			byte[] data1 = Util.randBytes(length);
			byte[] data2 = Util.randBytes(length);
			byte[] data3 = Util.randBytes(length);
			File src1 = Util.getTempFile(); 
			File src2 = Util.getTempFile(); 
			File src3 = Util.getTempFile(); 
			Util.makeFile(src1, data1);
			Util.makeFile(src2, data2);
			Util.makeFile(src3, data3);
			String path1 = "data/esel.dat";
			String path2 = "data/leopard.dat";
			String path3 = "data/elefant.dat";
			
			// place transmit file orders
			cl.sendFile(src1, path1);
			cl.sendFile(src2, path2);
			cl.sendFile(src3, path3);
			long stamp = System.currentTimeMillis();
			
			// check monitor for queue sizes
			ConnectionMonitor mon = cl.getMonitor();
			assertTrue(mon.transmitSpeed == 15000);
			assertTrue(mon.filesOutgoing == 3);
			assertTrue(mon.filesIncoming == 0);

			
			// wait for completion
			System.out.println("--- all files sent (client), waiting ..");
//			System.out.println("--- scheduled: " + mon.parcelsScheduled + ", send-load: " + mon.currentSendLoad);
			lock.wait(20000);
			long time = System.currentTimeMillis() - stamp;
			assertTrue("no / not all files received", receptionListener.getReceived().size() == 3);
			
			results = new byte[3][];

			// control received file content
			results[0] = Util.readFile(receptionListener.getReceived().get(0));
			results[1] = Util.readFile(receptionListener.getReceived().get(1));
			results[2] = Util.readFile(receptionListener.getReceived().get(2));

			assertTrue("data integrity error in file-1 transmission", containsData(results, data1));
			assertTrue("data integrity error in file-2 transmission", containsData(results, data2));
			assertTrue("data integrity error in file-3 transmission", containsData(results, data3));
			assertTrue("transmission time failure", time > 9000 & time < 12000);
			System.out.println("CASE-1 transmission time = " + time);
			
			// CASE 2 : 3 x 30,000 data files
			System.out.println("\n----------------------------------------------------------------");
			System.out.println("\nCASE 2 : multi data files 30,000 - priority Normal");
			receptionListener.reset();
			
			// prepare data and source file
			length = 30000;
			data1 = Util.randBytes(length);
			data2 = Util.randBytes(length);
			data3 = Util.randBytes(length);
			src1 = Util.getTempFile(); 
			src2 = Util.getTempFile(); 
			src3 = Util.getTempFile(); 
			Util.makeFile(src1, data1);
			Util.makeFile(src2, data2);
			Util.makeFile(src3, data3);
			
			// place transmit file orders
			cl.sendFile(src1, path1);
			cl.sendFile(src2, path2);
			cl.sendFile(src3, path3);
			stamp = System.currentTimeMillis();
			
			// check monitor for queue sizes
			mon = cl.getMonitor();
			assertTrue(mon.transmitSpeed == 15000);
			assertTrue(mon.filesOutgoing == 3);
			assertTrue(mon.filesIncoming == 0);
			
			// wait for completion
			System.out.println("--- all files sent (client), waiting ..");
//			System.out.println("--- scheduled: " + mon.parcelsScheduled + ", send-load: " + mon.currentSendLoad);
			lock.wait(20000);
			time = System.currentTimeMillis() - stamp;
			assertTrue("no / not all files received", receptionListener.getReceived().size() == 3);
			
			// control received file content
			results = new byte[3][];
			results[0] = Util.readFile(receptionListener.getReceived().get(0));
			results[1] = Util.readFile(receptionListener.getReceived().get(1));
			results[2] = Util.readFile(receptionListener.getReceived().get(2));

			assertTrue("data integrity error in file-1 transmission", containsData(results, data1));
			assertTrue("data integrity error in file-2 transmission", containsData(results, data2));
			assertTrue("data integrity error in file-3 transmission", containsData(results, data3));
			assertTrue("transmission time failure: time = " + time, time > 6000 & time < 8000);
			System.out.println("CASE-2 transmission time = " + time);
			
//			// CASE 2 : 1 x 50,000 data file, sent parallel to 3 clients
//			System.out.println("\nCASE 2 : single data file 50,000 (server) - 3 destination clients, priority Normal");

//			// third client
//			cl3 = new Client();
//			cl3.getParameters().setAlivePeriod(0);
//			cl3.getParameters().setTransmissionParcelSize(8*1024);
//			cl3.connect(100, sv.getSocketAddress());
//			cl3.setTempo(15000);

//			sv.sendFileToAll(src3, path3, SendPriority.Normal);
			
			
		}		
	} finally {
		System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnection(cl.getLocalAddress()).getMonitor().exchangedVolume);
		}
		
		if (sv != null) {
			sv.closeAndWait(2000);
		}
		if (cl != null) {
			cl.closeAndWait(1000);
		}
	}
	}


	@Test
	public void client_server_cross () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		
//		final SemaphorLock lock = new SemaphorLock(6);
//		final SemaphorLock sendLock = new SemaphorLock(6);
		final Object lock = new Object();
		final Object sendLock = new Object();
		final FileReceptionListener serverListener = new FileReceptionListener(lock, 3,
				FileReceptionListener.Station.SERVER);
		final FileReceptionListener clientListener = new FileReceptionListener(sendLock, 3,
				FileReceptionListener.Station.CLIENT);
		byte[][] results;

	try {
		System.out.println("\nTEST TRANSFER CLIENT/SERVER CROSS: MULTI-FILE, NO TARGET");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), serverListener);
		sv.getParameters().setTransmissionParcelSize(12*1024);
		sv.getParameters().setAlivePeriod(5000);
		sv.getParameters().setFileRootDir(new File("test"));
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setAlivePeriod(5000);
		cl.getParameters().setTransmissionParcelSize(8*1024);
		cl.getParameters().setFileRootDir(new File("test"));
		cl.addListener(clientListener);
		cl.connect(100, sv.getSocketAddress());
		cl.setTempo(25000);
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		// get server connection
		Connection scon = sv.getConnections()[0];
		
			System.out.println("\nCASE 1 : multi data files, cross 50,000 - no target");

			// prepare data and source files (client and server)
			int length = 100000;
			byte[] cdata1 = Util.randBytes(length);
			byte[] cdata2 = Util.randBytes(length);
			byte[] cdata3 = Util.randBytes(length);
			byte[] sdata1 = Util.randBytes(length);
			byte[] sdata2 = Util.randBytes(length);
			byte[] sdata3 = Util.randBytes(length);
			File src1 = Util.getTempFile(); 
			File src2 = Util.getTempFile(); 
			File src3 = Util.getTempFile(); 
			File src4 = Util.getTempFile(); 
			File src5 = Util.getTempFile(); 
			File src6 = Util.getTempFile(); 
			Util.makeFile(src1, cdata1);
			Util.makeFile(src2, cdata2);
			Util.makeFile(src3, cdata3);
			Util.makeFile(src4, sdata1);
			Util.makeFile(src5, sdata2);
			Util.makeFile(src6, sdata3);
			String path1 = "data/miriam.dat";
			String path2 = "data/sarah.dat";
			String path3 = "data/gundula.dat";
			String path4 = "data/martin.dat";
			String path5 = "data/alexander.dat";
			String path6 = "data/rudolf.dat";
			
			// transmit files CLIENT and SERVER
			cl.sendFile(src3, path1, SendPriority.LOW);
			scon.sendFile(src6, path4, SendPriority.LOW);
			Util.sleep(100);
			cl.sendFile(src2, path2);
			scon.sendFile(src5, path5);
			Util.sleep(100);
			cl.sendFile(src1, path3, SendPriority.HIGH);
			scon.sendFile(src4, path6, SendPriority.HIGH);
			long stamp = System.currentTimeMillis();
			
			// wait for completion
			System.out.println("--- waiting ..");
			serverListener.wait_on_release(30000);
			clientListener.wait_on_release(30000);
			long time = System.currentTimeMillis() - stamp;
			System.err.println("--> RECEIVED FILES after wait: server=" + serverListener.getReceived().size() 
					+ ", client=" + clientListener.getReceived().size());
			System.out.println("--- time elapsed for all: " + time);
			assertTrue("no / not all files received (server)", serverListener.getReceived().size() == 3);
			assertTrue("no / not all files received (client)", clientListener.getReceived().size() == 3);

			// control received file content (server received)
			results = new byte[3][];
			results[0] = Util.readFile(serverListener.getReceived().get(0));
			results[1] = Util.readFile(serverListener.getReceived().get(1));
			results[2] = Util.readFile(serverListener.getReceived().get(2));

			assertTrue("data integrity error in client file-1 transmission", containsData(results, cdata1));
			assertTrue("data integrity error in client file-2 transmission", containsData(results, cdata2));
			assertTrue("data integrity error in client file-3 transmission", containsData(results, cdata3));
			
			// control received file content (client received)
			results = new byte[3][];
			results[0] = Util.readFile(clientListener.getReceived().get(0));
			results[1] = Util.readFile(clientListener.getReceived().get(1));
			results[2] = Util.readFile(clientListener.getReceived().get(2));
			
			assertTrue("data integrity error in server file-1 transmission", containsData(results, sdata1));
			assertTrue("data integrity error in server file-2 transmission", containsData(results, sdata2));
			assertTrue("data integrity error in server file-3 transmission", containsData(results, sdata3));

			assertTrue("transmission time failure", time > 11000 & time < 13000);
			
	} finally {
		System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnections()[0].getMonitor().exchangedVolume);
		}
		
		sv.closeAndWait(2000);
		if (cl != null) {
			cl.closeAndWait(1000);
		}
	}

	}


	@Test
	public void transfer_break () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		long stamp, time, fid;
		
		final SemaphorLock lock = new SemaphorLock(4);
		final SemaphorLock sendLock = new SemaphorLock(4);
		final FileReceptionListener serverConListener = new FileReceptionListener(lock, 
							FileReceptionListener.Station.SERVER);
		final FileReceptionListener clientListener = new FileReceptionListener(sendLock, 
							FileReceptionListener.Station.CLIENT);
	
		serverConListener.set_release_locks_on_failure(false);
		clientListener.set_release_locks_on_failure(false);
		
	try {
		System.out.println("\nTEST TRANSFER CROSS - BREAK TRANSFER");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), serverConListener);
		sv.getParameters().setTransmissionParcelSize(12*1024);
		File base = new File(System.getProperty("java.io.tmpdir"));
		File tardir = new File(base, "JN-Target-" + Util.nextRand(100000));
		tardir.mkdirs();
		sv.getParameters().setFileRootDir(tardir);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(8*1024);
		tardir = new File(base, "JN-Target-" + Util.nextRand(100000));
		tardir.mkdirs();
		cl.getParameters().setFileRootDir(tardir);
		cl.addListener(clientListener);
		cl.connect(100, sv.getSocketAddress());
		cl.setTempo(15000);
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		// get server connection
		Connection scon = sv.getConnections()[0];
		
		// prepare data and source files (client and server)
		int length = 100000;
		byte[] cdata1 = Util.randBytes(length);
		byte[] cdata2 = Util.randBytes(length);
		byte[] sdata1 = Util.randBytes(length);
		byte[] sdata2 = Util.randBytes(length);
		File src1 = Util.getTempFile(); 
		File src2 = Util.getTempFile(); 
		File src4 = Util.getTempFile(); 
		File src5 = Util.getTempFile(); 
		Util.makeFile(src1, cdata1);
		Util.makeFile(src2, cdata2);
		Util.makeFile(src4, sdata1);
		Util.makeFile(src5, sdata2);
		
		System.out.println("\nCASE 1 : 2 files cross 100,000 - targets - BROKEN by receiver");
		
		// transmit files
		stamp = System.currentTimeMillis();
		cl.sendFile(src1, "client-file-1");
		fid = scon.sendFile(src4, "server-file-1");

		// break incoming transfer on client (causes error)
		Util.sleep(2000);
		System.out.println("-- breaking transfer for \"server-file-1\" on client (incoming");
		cl.breakTransfer(fid, ComDirection.INCOMING, "test break");
		
		// wait for transfer completion (failure and success)
		System.out.println("--- waiting ..");
		lock.lock_wait(30000);
		sendLock.lock_wait(5000);
		time = System.currentTimeMillis() - stamp;

		System.out.println("--- time elapsed for preceding: " + time);
		assertTrue("transmission time failure", time > 6000 & time < 8000);

		// control received file content (server received)
		assertTrue("no / not all files received", serverConListener.getReceived().size() == 1);
		File file = serverConListener.getReceived().get(0);
		byte [] rece = Util.readFile(file);
		assertTrue("data integrity error in client-file-1 transmission", Util.equalArrays(rece, cdata1));

		// control received transmission events
		TransmissionEvent evt = serverConListener.getFirstEventOf(TransmissionEventType.FILE_ABORTED);
		assertNotNull("ABORTED transmission event missing (server)", evt);
		assertTrue("false event info, 107 expected", evt.getInfo() == 107);
		assertTrue("false target path detected", "server-file-1".equals(evt.getPath()));
		assertTrue("false number of transmission events, 4 expected", serverConListener.getEvents().size() == 4);
		
		evt = clientListener.getFirstEventOf(TransmissionEventType.FILE_ABORTED);
		assertNotNull("ABORTED transmission event missing (server)", evt);
		assertTrue("false event info, 108 expected", evt.getInfo() == 108);
		assertTrue("false target path detected", "server-file-1".equals(evt.getPath()));
		assertTrue("false number of transmission events, 4 expected", clientListener.getEvents().size() == 4);

		System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
		System.out.println("\nCASE 2 : 2 files cross 100,000 - targets - BROKEN by sender");
		
		serverConListener.reset();
		clientListener.reset();
		
		// transmit files
		stamp = System.currentTimeMillis();
		fid = cl.sendFile(src2, "client-file-2");
		scon.sendFile(src5, "server-file-2");

		// break outgoing transfer on client (causes error)
		Util.sleep(2000);
		System.out.println("-- breaking transfer for \"client-file-2\" on client (outgoing");
		cl.breakTransfer(fid, ComDirection.OUTGOING, "test break");
		
		// wait for transfer completion (failure and success)
		System.out.println("--- waiting ..");
		lock.lock_wait(30000);
		sendLock.lock_wait(5000);
		time = System.currentTimeMillis() - stamp;

		System.out.println("--- time elapsed for preceding: " + time);
		assertTrue("transmission time failure, was " + time, time > 5000 & time < 7000);
		
		// control received file content (server received)
		assertTrue("no files received", clientListener.getReceived().size() == 1);
		file = clientListener.getReceived().get(0);
		rece = Util.readFile(file);
		assertTrue("data integrity error in server-file-2 transmission", Util.equalArrays(rece, sdata2));

		// control received transmission events
		evt = serverConListener.getFirstEventOf(TransmissionEventType.FILE_ABORTED);
		assertNotNull("ABORTED transmission event missing (client)", evt);
		assertTrue("false event info, 106 expected", evt.getInfo() == 106);
		assertTrue("event direction error", evt.getDirection() == ComDirection.INCOMING);
		assertTrue("false target path detected", "client-file-2".equals(evt.getPath()));
		assertTrue("false number of transmission events, 3 expected", serverConListener.getEvents().size() == 4);
		
		evt = clientListener.getFirstEventOf(TransmissionEventType.FILE_ABORTED);
		assertNotNull("ABORTED transmission event missing (client)", evt);
		assertTrue("false event info, 105 expected", evt.getInfo() == 105);
		assertTrue("event direction error", evt.getDirection() == ComDirection.OUTGOING);
		assertTrue("false target path detected", "client-file-2".equals(evt.getPath()));
		assertTrue("false number of transmission events, 3 expected", clientListener.getEvents().size() == 4);

	} catch (Throwable e) {
		e.printStackTrace();
	} finally {
		System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnections()[0].getMonitor().exchangedVolume);
		}
		
		if (sv != null) {
			sv.closeAndWait(3000);
		}
	}
	}


	@Test
	public void aborted_by_close () throws IOException, InterruptedException {
		Server sv = null;
		Client cl = null;
		long stamp, time, fid;
		
		final SemaphorLock lock = new SemaphorLock(4);
		final SemaphorLock sendLock = new SemaphorLock(4);
		final FileReceptionListener serverConListener = new FileReceptionListener(lock, 
							FileReceptionListener.Station.SERVER);
		final FileReceptionListener clientListener = new FileReceptionListener(sendLock, 
							FileReceptionListener.Station.CLIENT);
	
		serverConListener.set_release_locks_on_failure(false);
		clientListener.set_release_locks_on_failure(false);
		
	try {
		System.out.println("\nTEST TRANSFER CROSS - ABORTED BY CLOSE");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), serverConListener);
		sv.getParameters().setTransmissionParcelSize(12*1024);
		File base = new File(System.getProperty("java.io.tmpdir"));
		File tardir = new File(base, "JN-Target-" + Util.nextRand(100000));
		tardir.mkdirs();
		sv.getParameters().setFileRootDir(tardir);
		sv.start();
		
		// set up a running connection
		cl = new Client();
		cl.getParameters().setTransmissionParcelSize(16*1024);
		tardir = new File(base, "JN-Target-" + Util.nextRand(100000));
		tardir.mkdirs();
		cl.getParameters().setFileRootDir(tardir);
		cl.addListener(clientListener);
		cl.connect(100, sv.getSocketAddress());
		cl.setTempo(15000);
		System.out.println("-- connection established " + cl.toString());
		Util.sleep(20);
		
		// get server connection
		Connection scon = sv.getConnections()[0];
		
			// prepare data and source files (client and server)
			int length = 100000;
			byte[] cdata1 = Util.randBytes(length);
			byte[] cdata2 = Util.randBytes(length);
			byte[] sdata1 = Util.randBytes(length);
			byte[] sdata2 = Util.randBytes(length);
			File src1 = Util.getTempFile(); 
			File src2 = Util.getTempFile(); 
			File src4 = Util.getTempFile(); 
			File src5 = Util.getTempFile(); 
			Util.makeFile(src1, cdata1);
			Util.makeFile(src2, cdata2);
			Util.makeFile(src4, sdata1);
			Util.makeFile(src5, sdata2);
			
			System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
			System.out.println("\nCASE 1 : 2 files cross 100,000 - targets - Close by receiver (server)");
			
			// transmit files
			cl.sendFile(src1, "client-file-1");
			scon.sendFile(src4, "server-file-1");
			stamp = System.currentTimeMillis();

			// break outgoing transfer on client (causes error)
			Util.sleep(2000);
			System.out.println("-- closing connection by server con");
			scon.closeHard();
			
			// wait for transfer completion (failure and success)
			System.out.println("--- waiting ..");
			lock.lock_wait(30000);
			sendLock.lock_wait(5000);
			time = System.currentTimeMillis() - stamp;

			// control received file content (server received)
			assertTrue("no files expected (server)", serverConListener.getReceived().size() == 0);
			assertTrue("no files expected (client)", clientListener.getReceived().size() == 0);

			// control received transmission events
			TransmissionEventType type = TransmissionEventType.FILE_ABORTED;
			assertTrue("false number of ABORTED events (server)", serverConListener.countEvents(type) == 2);
			assertTrue("false number of ABORTED events (client)", clientListener.countEvents(type) == 2);
			assertTrue("missing event ABORTED, info 113, on server side", serverConListener.hasTransmissionEvent(type, 113));
			assertTrue("missing event ABORTED, info 114, on server side", serverConListener.hasTransmissionEvent(type, 114));
			assertTrue("missing event ABORTED, info 115, on client side", clientListener.hasTransmissionEvent(type, 115));
			assertTrue("missing event ABORTED, info 116, on client side", clientListener.hasTransmissionEvent(type, 116));

			System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
			System.out.println("\nCASE 2 : 2 files cross 100,000 - targets - Close by sender (client)");
			
			serverConListener.reset();
			clientListener.reset();
			cl = new Client();
			cl.getParameters().setTransmissionParcelSize(8*1024);
			tardir = new File(base, "JN-Target-" + Util.nextRand(100000));
			tardir.mkdirs();
			cl.getParameters().setFileRootDir(tardir);
			cl.addListener(clientListener);
			cl.connect(100, sv.getSocketAddress());
			cl.setTempo(15000);
			System.out.println("-- connection established " + cl.toString());
			Util.sleep(50);
			
			// get server connection
			scon = sv.getConnection(cl.getLocalAddress());
			
			// transmit files
			cl.sendFile(src2, "client-file-2");
			scon.sendFile(src5, "server-file-2");
			stamp = System.currentTimeMillis();

			// break outgoing transfer on client (causes error)
			Util.sleep(3000);
			System.out.println("-- closing connection by sender (client)");
			cl.closeHard();
			
			// wait for transfer completion (failure and success)
			System.out.println("--- waiting ..");
			lock.lock_wait(30000);
			sendLock.lock_wait(5000);
			time = System.currentTimeMillis() - stamp;

			// control received file content (server received)
			assertTrue("no files expected (server)", serverConListener.getReceived().size() == 0);
			assertTrue("no files expected (server)", clientListener.getReceived().size() == 0);

			// control received transmission events
			assertTrue("false number of ABORTED events (server)", serverConListener.countEvents(type) == 2);
			assertTrue("false number of ABORTED events (client)", clientListener.countEvents(type) == 2);
			assertTrue("missing event ABORTED, info 115, on server side", serverConListener.hasTransmissionEvent(type, 115));
			assertTrue("missing event ABORTED, info 116, on server side", serverConListener.hasTransmissionEvent(type, 116));
			assertTrue("missing event ABORTED, info 113, on client side", clientListener.hasTransmissionEvent(type, 113));
			assertTrue("missing event ABORTED, info 114, on client side", clientListener.hasTransmissionEvent(type, 114));
			System.out.println("- - - - - TERMINATED TEST-BLOCK - - - - - - - -");

			
	} finally {
		System.out.println("\n# transmission volume of Client : " + cl.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnections()[0].getMonitor().exchangedVolume);
		}
		
		sv.closeAndWait(3000);
		cl.closeAndWait(1000);
	}
	
	}

	@Test
	public void server_multiplexor () throws IOException, InterruptedException {
		Server sv = null;
		Client cl1 = null, cl2, cl3;
		
		final Object lock1 = new Object();
		final Object lock2 = new Object();
		final FileReceptionListener serverListener = new FileReceptionListener(lock1, 3, Station.SERVER);
		final FileReceptionListener clientListener = new FileReceptionListener(lock2, 3, Station.CLIENT);
		byte[][] results;
	
	try {
		System.out.println("\nTEST TRANSFER SERVER TO 3 CLIENTS, MULTIPLEXOR");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), serverListener);
		sv.getParameters().setTransmissionParcelSize(8*1024);
//		sv.getParameters().setAlivePeriod(5000);
		sv.getParameters().setFileRootDir(new File("test"));
//		sv.addListener(new EventReporter());
		sv.start();
		
		// set up a running connection
		cl1 = new Client();
		cl1.getParameters().setTransmissionParcelSize(8*1024);
		cl1.getParameters().setFileRootDir(new File("test/client1"));
		cl1.getParameters().setTransmissionSpeed(15000);
		cl1.addListener(clientListener);
		cl1.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl1.toString());
		Util.sleep(20);
		
		// second client
		cl2 = new Client();
		cl2.getParameters().setTransmissionParcelSize(8*1024);
		cl2.getParameters().setFileRootDir(new File("test/client2"));
		cl2.getParameters().setTransmissionSpeed(15000);
		cl2.addListener(clientListener);
		cl2.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl1.toString());
//		cl2.setTempo(15000);
		
		// third client
		cl3 = new Client();
		cl3.getParameters().setTransmissionParcelSize(8*1024);
		cl3.getParameters().setFileRootDir(new File("test/client3"));
		cl3.getParameters().setTransmissionSpeed(15000);
		cl3.addListener(clientListener);
		cl3.connect(100, sv.getSocketAddress());
		System.out.println("-- connection established " + cl1.toString());
//		cl3.setTempo(15000);

		synchronized (lock2) {
			int trn1, trn2, trn3;
		
			// CASE 1 : 50,000 data file, sent parallel to 3 clients by server
			System.out.println("\nCASE 1 : multi data file 50,000 - priority Normal");
	
			// prepare data and source file
			int length = 50000;
			byte[] data1 = Util.randBytes(length);
			byte[] data2 = Util.randBytes(length);
			byte[] data3 = Util.randBytes(length);
			File src1 = Util.getTempFile(); 
			File src2 = Util.getTempFile(); 
			File src3 = Util.getTempFile(); 
			Util.makeFile(src1, data1);
			Util.makeFile(src2, data2);
			Util.makeFile(src3, data3);
			String path1 = "esel.dat";
			String path2 = "leopard.dat";
			String path3 = "elefant.dat";
			
			long stamp = System.currentTimeMillis();
			trn1 = sv.sendFileToAll(src3, path3, SendPriority.NORMAL);
			assertTrue("negative transaction number", trn1 > 0);
			
			// wait for completion
			System.out.println("--- file sent to 3 clients, waiting ..");
			lock2.wait(20000);
			long time = System.currentTimeMillis() - stamp;
			assertTrue("no / not all files received", clientListener.getReceived().size() == 3);
			System.out.println("--- all files received");
			
			results = new byte[3][];
	
			// control received file content
			results[0] = Util.readFile(clientListener.getReceived().get(0));
			results[1] = Util.readFile(clientListener.getReceived().get(1));
			results[2] = Util.readFile(clientListener.getReceived().get(2));
	
			assertTrue("data integrity error in file transmission", Util.equalArrays(results[0], data3));
			assertTrue("data integrity error in file transmission", Util.equalArrays(results[1], data3));
			assertTrue("data integrity error in file transmission", Util.equalArrays(results[2], data3));
			assertTrue("transmission time failure", time > 3000 & time < 5000);
			
			assertTrue("missing or undefined file reception", clientListener.countEvents(TransmissionEventType.FILE_RECEIVED) == 3);
			
			// CASE 2 : 50,000 data file, sent parallel to 2 clients (one exempted) 
			System.out.println("\n----------------------------------------");
			System.out.println("\nCASE 2 : single data file 50,000 (server) - 2 destination clients, 1 exempted");

			clientListener.reset();
			clientListener.setUnlockThreshold(2);
			UUID exempted = sv.getConnections()[1].getUUID();
			
			stamp = System.currentTimeMillis();
			trn2 = sv.sendFileToAllExcept(exempted, src3, path2, SendPriority.TOP);
			assertTrue("negative transaction number", trn1 > 0);
			assertTrue("non-unique transaction number", trn1 != trn2);
			
			// wait for completion
			System.out.println("--- file sent to 2 clients, waiting ..");
			lock2.wait(20000);
			time = System.currentTimeMillis() - stamp;
			Util.sleep(1000);
			int h = clientListener.getReceived().size();
			assertTrue("incorrect number of files received: " + h, h == 2);
			System.out.println("--- all files received");

			results = new byte[2][];
			
			// control received file content
			results[0] = Util.readFile(clientListener.getReceived().get(0));
			results[1] = Util.readFile(clientListener.getReceived().get(1));
	
			assertTrue("data integrity error in file transmission", Util.equalArrays(results[0], data3));
			assertTrue("data integrity error in file transmission", Util.equalArrays(results[1], data3));
			assertTrue("transmission time failure", time > 3000 & time < 5000);

			assertTrue("missing or undefined file reception", clientListener.countEvents(TransmissionEventType.FILE_RECEIVED) == 2);
			
		}		
		} catch (Exception e) {
			e.printStackTrace();
		
	} finally {
		System.out.println("\n# transmission volume of Client : " + cl1.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnections()[0].getMonitor().exchangedVolume);
		}
		
		sv.closeAndWait(2000);
	}
	}
	
	@Test
	public void priority_mixed () throws IOException, InterruptedException {
		Server sv = null;
		Client cl1 = null;
		
		final Object lock1 = new Object();
		final Object lock2 = new Object();
		final FileReceptionListener serverListener = new FileReceptionListener(lock1, 3, Station.SERVER);
//		final FileReceptionListener clientListener = new FileReceptionListener(lock2, 3, Station.CLIENT);
		final ObjectReceptionListener svObjectListener = new ObjectReceptionListener(Station.SERVER); 
		byte[][] results;
	
	try {
		System.out.println("\nTEST TRANSFER TO SERVER WITH VARIOUS PRIORITIES AND OBJECTS MIXED");
		sv = new StandardServer(new InetSocketAddress("localhost", 3000), serverListener);
		sv.getParameters().setTransmissionParcelSize(8*1024);
		sv.getParameters().setFileRootDir(new File("test"));
//		sv.addListener(new EventReporter());
		sv.start();
		
		// set up a running connection
		cl1 = new Client();
		cl1.getParameters().setTransmissionParcelSize(8*1024);
		cl1.getParameters().setFileRootDir(new File("test/client1"));
		cl1.getParameters().setTransmissionSpeed(15000);
//		cl1.addListener(clientListener);
		cl1.connect(100000, sv.getSocketAddress());
		System.out.println("-- connection established " + cl1.toString());
		Util.sleep(50);

		// set up server connection
		Connection svCon = sv.getConnections()[0];
		svCon.addListener(svObjectListener);
		
		// create data files
		int length = 50000;
		byte[] data1 = Util.randBytes(length);
		byte[] data2 = Util.randBytes(length);
		byte[] data3 = Util.randBytes(length);
		File src1 = Util.getTempFile(); 
		File src2 = Util.getTempFile(); 
		File src3 = Util.getTempFile(); 
		Util.makeFile(src1, data1);
		Util.makeFile(src2, data2);
		Util.makeFile(src3, data3);
		String path1 = "esel.dat";
		String path2 = "leopard.dat";
		String path3 = "elefant.dat";
		
		// create send objects
		byte[] obj1 = Util.randBytes(30000);
		byte[] obj2 = Util.randBytes(40000);
		byte[] obj3 = Util.randBytes(50000);
		
		synchronized (lock1) {
			// send three files w/ different priorities
			// transmit file (speed limit)
			long stamp = System.currentTimeMillis();
			cl1.sendFile(src1, path1, SendPriority.BOTTOM);
			Util.sleep(1000);
			cl1.sendFile(src2, path2, SendPriority.NORMAL);
			Util.sleep(1000);
			cl1.sendFile(src3, path3, SendPriority.HIGH);
			Util.sleep(1000);

			// send three array objects w/ different priorities
			cl1.sendData(obj1, SendPriority.BOTTOM);
			Util.sleep(1000);
			cl1.sendData(obj2, SendPriority.NORMAL);
			Util.sleep(1000);
			cl1.sendData(obj3, SendPriority.HIGH);
			
			serverListener.wait_on_release(20000);
			long time = System.currentTimeMillis() - stamp;
			System.out.println("--- FILE + DATA TRANSFER in " + time + " ms");
			
			List<File> receivedFiles = serverListener.getReceived();
			List<byte[]> receivedObjs = svObjectListener.getReceived();
			assertTrue(receivedFiles.size() == 3); 
			assertTrue(receivedObjs.size() == 3);

			// verify data reception (disregarding order)
			byte[][] recData = new byte[6][];
			int i = 0;
			for (byte[] a : receivedObjs) {
				recData[i++] = a;
			}
			for (File f : receivedFiles) {
				recData[i++] = Util.readFile(f);
			}
			assertTrue("error in data transfer", containsData(recData, data1));
			assertTrue("error in data transfer", containsData(recData, data2));
			assertTrue("error in data transfer", containsData(recData, data3));
			assertTrue("error in data transfer", containsData(recData, obj1));
			assertTrue("error in data transfer", containsData(recData, obj2));
			assertTrue("error in data transfer", containsData(recData, obj3));
			
			// check order of object reception
			assertTrue("object order mismatched, no. 3", Util.equalArrays(obj3, receivedObjs.get(0)));
			assertTrue("object order mismatched, no. 2", Util.equalArrays(obj2, receivedObjs.get(1)));
			assertTrue("object order mismatched, no. 1", Util.equalArrays(obj1, receivedObjs.get(2)));
			
			// check order of reception (files)
			assertTrue("file order mismatched, no. 3", receivedFiles.get(0).getPath().endsWith(path3));
			assertTrue("file order mismatched, no. 2", receivedFiles.get(1).getPath().endsWith(path2));
			assertTrue("file order mismatched, no. 1", receivedFiles.get(2).getPath().endsWith(path1));
			
			
			
		}		
		} catch (Exception e) {
			e.printStackTrace();
		
	} finally {
		System.out.println("\n# transmission volume of Client : " + cl1.getMonitor().exchangedVolume);
		if (sv.getConnections().length > 0) {
			System.out.println("# transmission volume of Server : " + 
				sv.getConnections()[0].getMonitor().exchangedVolume);
		}
		
		if (sv != null) {
			sv.closeAndWait(2000);
		}
	}
	}		
}
