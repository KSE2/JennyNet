package org.kse.jennynet.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Point;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.JavaSerialisation;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.KryoSerialisation;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.exception.SerialisationException;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.ArraySet;
import org.kse.jennynet.util.Util;

public class TestUnit_Serialisation {

	public TestUnit_Serialisation() {
	}

    private void test_ser_admin (Serialization ser, int method, String name) {
		Serialization ser1, ser2;
		List<Class<?>> clist;
		
		// naming
		assertTrue("unexpected method number", ser.getMethodID() == method);
		assertNotNull("no serialisation name defined", ser.getName());
		assertTrue("core name element not found", ser.getName().toLowerCase().indexOf(name.toLowerCase()) > -1);
		assertTrue("core name element not in toString()", ser.toString().toLowerCase().indexOf(name.toLowerCase()) > -1);
		
		// content initial
		clist = ser.getRegisteredClasses();
		assertNotNull("reg-classes is null", clist);
		assertTrue("initial class-list not empty", ser.getRegisteredClasses().isEmpty());
		assertTrue("error in registered-size", ser.getRegisteredSize() == 0);
		assertFalse("unexpected initial registered class", ser.isRegisteredClass(byte[].class));
		assertFalse("unexpected initial registered class", ser.isRegisteredClass(String.class));
		
		// copy + clone
		ser1 = ser.copy();
		assertNotNull("copy is not created", ser1);
		assertFalse("copy not a copy", ser1 == ser);
		assertTrue("copy method mismatch", ser1.getMethodID() == method);
		assertTrue("name not equal on copy", ser.getName().equals(ser1.getName()));
		assertTrue("copy class-list not empty", ser1.getRegisteredClasses().isEmpty());
		assertTrue("error in registered-size", ser1.getRegisteredSize() == 0);
		ser2 = ser.copy();
		assertNotNull("copyFor not working", ser2);
		assertFalse("copy not a copy", ser1 == ser);
		assertTrue("copy method mismatch", ser2.getMethodID() == method);
		assertTrue("name not equal on copy", ser.getName().equals(ser2.getName()));
		
		ser2 = ser.copy();
		assertNotNull("copyFor not working", ser2);
		assertFalse("copy not a copy", ser1 == ser);
		assertTrue("copy method mismatch", ser2.getMethodID() == method);
		assertTrue("core name element not found", ser2.getName().toLowerCase().indexOf(name.toLowerCase()) > -1);
		
		// register classes
		try {
			ser2.registerClass(byte[].class);
			ser2.registerClass(String.class);
			ser2.registerClass(Integer.class);
			ser2.registerClass(Hashtable.class);
		} catch (NotSerializableException e) {
			e.printStackTrace();
			fail("class not serialisable: " + e.getMessage());
		}
		
		int size = ser2.getRegisteredClasses().size();
		assertTrue("class-list size error, was " + size, size == 4);
		assertTrue("error in registered-size", ser2.getRegisteredSize() == 4);
		assertTrue("reg. class missing", ser2.isRegisteredClass(byte[].class));
		assertTrue("reg. class missing", ser2.isRegisteredClass(String.class));
		assertTrue("reg. class missing", ser2.isRegisteredClass(Integer.class));
		assertTrue("reg. class missing", ser2.isRegisteredClass(Hashtable.class));
		
		// copy with registrations
		ser1 = ser2.copy();
		assertNotNull("copy is not created", ser1);
		assertFalse("copy not a copy", ser1 == ser2);
		assertTrue("copy method mismatch", ser1.getMethodID() == method);
		assertTrue("name not equal on copy", ser1.getName().equals(ser2.getName()));
		assertFalse("class-list was not copied", ser1.getRegisteredClasses().isEmpty());
		size = ser1.getRegisteredClasses().size();
		assertTrue("class-list size error, was " + size, size == 4);
		assertTrue("error in registered-size", ser1.getRegisteredSize() == 4);
		assertTrue("reg. class missing", ser1.isRegisteredClass(byte[].class));
		assertTrue("reg. class missing", ser1.isRegisteredClass(String.class));
		assertTrue("reg. class missing", ser1.isRegisteredClass(Integer.class));
		assertTrue("reg. class missing", ser1.isRegisteredClass(Hashtable.class));
		assertFalse("unexpected class registration", ser.isRegisteredClass(Long.class));
		
	}
	
    private void test_ser_operation_standards (Serialization ser) {
		try {
		try {
			ser.registerClass(byte[].class);
			ser.registerClass(String.class);
			ser.registerClass(Integer.class);
			ser.registerClass(HashSet.class);
			ser.registerClass(CustomClass_2.class);
		} catch (NotSerializableException e) {
			throw new SerialisationException(0, e);
		}

		// CASE 1: byte array of random data
		int dlen = 3500;
		byte[] data = Util.randBytes(dlen);
		byte[] serdat1 = ser.serialiseObject(data);
		assertNotNull("no serialisation returned for byte data", serdat1);
		prot ("-- serial for " + dlen + " random bytes, length = " + serdat1.length);
		
		Object o = ser.deserialiseObject(serdat1);
		assertNotNull("no object returned for serialisation (de-serialise)", o);
		try {
			byte[] deserData = (byte[]) o;
			assertTrue("data integrity error in deserialised byte-array", Util.equalArrays(data, deserData));
		} catch (ClassCastException e) {
			fail("false type returned for byte-array deserialisation: " + o.getClass().getCanonicalName());
		}

		// CASE 2: byte array w/ zeros
		data = new byte[dlen];
		serdat1 = ser.serialiseObject(data);
		assertNotNull("no serialisation returned for byte data", serdat1);
		prot ("-- serial for " + dlen + " zero bytes, length = " + serdat1.length);
		
		o = ser.deserialiseObject(serdat1);
		assertNotNull("no object returned for serialisation (de-serialise)", o);
		try {
			byte[] deserData = (byte[]) o;
			assertTrue("data integrity error in deserialised byte-array", Util.equalArrays(data, deserData));
		} catch (ClassCastException e) {
			fail("false type returned for byte-array deserialisation: " + o.getClass().getCanonicalName());
		}
		
		// CASE 3: String including alien characters
		String dstr = "Über Kämmerling ließe sich vieles sagen was betrüblich wäre.";
		serdat1 = ser.serialiseObject(dstr);
		assertNotNull("no serialisation returned for String object", serdat1);
		prot ("-- serial for String of length " + dstr.length() + ", size = " + serdat1.length);
		o = ser.deserialiseObject(serdat1);
		assertNotNull("no object returned for serialisation (de-serialise)", o);
		try {
			String deserStr = (String) o;
			assertTrue("data integrity error in deserialised byte-array", deserStr.equals(dstr));
		} catch (ClassCastException e) {
			fail("false type returned for String deserialisation: " + o.getClass().getCanonicalName());
		}

		// CASE 4: Integer /w value
		Integer dint = 8923811;
		serdat1 = ser.serialiseObject(dint);
		assertNotNull("no serialisation returned for Integer object", serdat1);
		prot ("-- serial for Integer, size = " + serdat1.length);
		o = ser.deserialiseObject(serdat1);
		assertNotNull("no object returned for serialisation (de-serialise)", o);
		try {
			Integer j = (Integer) o;
			assertTrue("integrity error in deserialised Integer", j.intValue() == dint.intValue());
		} catch (ClassCastException e) {
			e.printStackTrace();
			fail("false type returned for Integer deserialisation: " + o.getClass().getCanonicalName());
		}

		// CASE 5: HashSet containing some Integer values
		HashSet<Integer> set = new HashSet<Integer>();
		set.add(30987);
		set.add(-28);
		serdat1 = ser.serialiseObject(set);
		assertNotNull("no serialisation returned for HashSet object", serdat1);
		prot ("-- serial for 2 element HashSet, length = " + serdat1.length);
		
		o = ser.deserialiseObject(serdat1);
		assertNotNull("no object returned for serialisation (de-serialise)", o);
		try {
			@SuppressWarnings("unchecked")
			HashSet<Integer> deserSet = (HashSet<Integer>) o;
			assertTrue("data integrity error in deserialised HashSet", deserSet.equals(set));
			assertTrue("data integrity error", deserSet.contains(new Integer(30987)));
			assertTrue("data integrity error", deserSet.contains(new Integer(-28)));
		} catch (ClassCastException e) {
			fail("false type returned for HashSet deserialisation: " + o.getClass().getCanonicalName());
		}

		// CASE 6: CustomClass_2 (serialisable)
		CustomClass_2 custom = new CustomClass_2();
		serdat1 = ser.serialiseObject(custom);
		assertNotNull("no serialisation returned for HashSet object", serdat1);
		prot ("-- serial for CustomClass_2, length = " + serdat1.length);
		
		o = ser.deserialiseObject(serdat1);
		assertNotNull("no object returned for serialisation (de-serialise)", o);
		try {
			CustomClass_2 custo = (CustomClass_2) o;
			assertTrue("data integrity error in deserialised CustomClass_2", custo.equals(custom));
		} catch (ClassCastException e) {
			fail("false type returned for CustomClass_2 deserialisation: " + o.getClass().getCanonicalName());
		}

		} catch (SerialisationException e) {
			e.printStackTrace();
			fail("SerialisatioException: " + e);
		}
		
    }
    
	private void prot (String text) {
		System.out.println(text);
	}

	@Test
	public void kryo_admin () {
		Serialization kryo = new KryoSerialisation();
		test_ser_admin(kryo, 1, "kryo");
	}

	@Test
	public void kryo_operation () throws SerialisationException {
		Serialization kryo = new KryoSerialisation();
		test_ser_operation_standards(kryo);
	}

	@Test
	public void java_admin () {
		Serialization java = new JavaSerialisation();
		test_ser_admin(java, 0, "java");
	}

	@Test
	public void java_operation () throws SerialisationException {
		Serialization java = new JavaSerialisation();
		test_ser_operation_standards(java);
	}
	
	@Test
	public void java_failure () {
		Serialization ser = new JavaSerialisation();
//		byte[] serial;
		
		// attempt unregistered serialisation
		byte[] dump = Util.randBytes(500);
		try {
			ser.serialiseObject(dump);
			fail("expected not registered class: byte buffer");
		} catch (SerialisationException e) {
		} catch (Exception e) {
			e.printStackTrace();
			fail("unexpected exception: " + e);
		}
		
		Object obj1 = new CustomClass_1();
		try {
			ser.serialiseObject(obj1);
			fail("expected not registered class: CustomClass_1");
		} catch (SerialisationException e) {
		} catch (Exception e) {
			e.printStackTrace();
			fail("unexpected exception: " + e);
		}

		// register serialisables
		try {
			ser.registerClass(Integer.class);
			ser.registerClass(String.class);
		} catch (NotSerializableException e) {
			e.printStackTrace();
			fail("unexpected not-serialisable exception");
		}

		// try register not-serialisables
		try {
			ser.registerClass(CustomClass_1.class);
			fail("expected not-serialisable exception: CustomClass_1");
		} catch (NotSerializableException e) {
		}
		
		try {
			ser.registerClass(ByteBuffer.class);
			fail("expected not-serialisable exception: ByteBuffer");
		} catch (NotSerializableException e) {
		}
		
		// register serialisable class which shall cause runtime error
		try {
			ser.registerClass(CustomClass_3.class);
		} catch (NotSerializableException e) {
		}
		
		// try serialise an object containing a non-serialisable member object
		try {
			ser.serialiseObject(new CustomClass_3());
			fail("expected not-serialisable exception in runtime for: CustomClass_3");
		} catch (SerialisationException e) {
		}
	}
	
	@Test
	public void setting_connection () throws IOException, InterruptedException {
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.SERVER);
		final ObjectReceptionListener clientListener = new ObjectReceptionListener(Station.CLIENT);
		Server sv = null;
		Client cl = null;
		Connection svcon;
	
		try {
			JennyNet.reset();
			sv = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			sv.start();
			
			// set up a running connection
			cl = new Client();
			cl.addListener(clientListener);
			
			// initial state of serialisations
			ConnectionParameters par = cl.getParameters();
			int method = par.getSerialisationMethod();
			assertTrue("initial serialisation setting not zero: ", method == 0);
			assertTrue(cl.getSendSerialization().getMethodID() == 0);
			assertTrue(cl.getReceiveSerialization().getMethodID() == 0);
			assertTrue(cl.getSendSerialization(0).getMethodID() == 0);
			assertTrue(cl.getReceiveSerialization(0).getMethodID() == 0);
			assertTrue(cl.getSendSerialization(1).getMethodID() == 1);
			assertTrue(cl.getReceiveSerialization(1).getMethodID() == 1);
			prot("default send-serialisation: " + cl.getSendSerialization(method));
			prot("default receive-serialisation: " + cl.getReceiveSerialization(method));
			
			// custom serialisation unavailable
			try {
				cl.getSendSerialization(2);
				fail("expected SerialisationUnavailableException");
			} catch (SerialisationUnavailableException e) {
			}
			
			cl.connect(100, sv.getSocketAddress());
			Util.sleep(20);
			System.out.println("-- connection established " + cl.toString());
			svcon = sv.getConnection(cl.getLocalAddress());
			assertTrue("receiver false initial serialisation method", svcon.getReceiveSerialization().getMethodID() == 0);

			// change sender serialisation method (divergence to receiver method)
			par.setSerialisationMethod(1);
			par = cl.getParameters();
			assertTrue("error in modifying serialisation: ", par.getSerialisationMethod() == 1);
			assertTrue(cl.getSendSerialization().getMethodID() == 1);
			assertTrue(cl.getReceiveSerialization().getMethodID() == 1);
			prot("send-serialisation: " + cl.getSendSerialization());
			prot("receive-serialisation: " + cl.getReceiveSerialization());

			byte[] data = Util.randBytes(64);
			cl.sendData(data, SendPriority.NORMAL);
			
			synchronized (lock) {
				lock.wait(10000);
			}
			int size = receptionListener.getSize();
			assertTrue("received objects mismatch: " + size, size == 1);
			byte[] recBlock = receptionListener.getReceived().get(0);
			assertTrue("error in data transfer", Util.equalArrays(data, recBlock));
			
			// choose a serial-method on sending methods
			// send test objects
			Integer int1 = 36729;
			Integer int2 = 9923382;
			cl.sendObject(int1, 0, SendPriority.TOP);
			cl.sendObject(int2, 1, SendPriority.TOP);
			Util.sleep(100);

			// verify received objects
			List<ConnectionEvent> list = receptionListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("receive objects list error", list.size() == 3);
			Object ro1 = list.get(1).getObject();
			Object ro2 = list.get(2).getObject();
			assertTrue("object integrity error", int1.equals(ro1));
			assertTrue("object integrity error", int2.equals(ro2));
			 
			// check unchanged default settings
			assertTrue(cl.getSendSerialization().getMethodID() == 1);
			assertTrue(svcon.getReceiveSerialization().getMethodID() == 0);

			// choose a non-existent serialisation method
			try {
				cl.sendObject(int2, 2, SendPriority.NORMAL);
				fail("expected SerialisationUnavailableException");
			} catch (SerialisationUnavailableException e) {
			}
			 
			// setup a custom serialisation (global)
			Serialization custom = new OurSerialisation();
			custom.registerClass(Integer.class);
			JennyNet.setCustomSerialisation(custom);
			
			// send object in custom serialisation
			Integer int3 = new Integer(5522277);
			cl.sendObject(int3, 2, SendPriority.NORMAL);
			Util.sleep(100);

			// verify received objects
			list = receptionListener.getEvents(ConnectionEventType.OBJECT);
			assertTrue("receive objects list error, was " + list.size(), list.size() == 4);
			ro1 = list.get(3).getObject();
			assertTrue("object integrity error", int3.equals(ro1));
			
			
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
	public void setting_global () throws IOException, InterruptedException {
		final Object lock = new Object();
		final ObjectReceptionListener receptionListener = new ObjectReceptionListener(lock, 1, Station.SERVER);
		final ObjectReceptionListener clientListener = new ObjectReceptionListener(Station.CLIENT);
		Server sv1 = null, sv2 = null;
		Client cl1 = null, cl2;
		Connection svcon;
		Serialization ser1, ser2, ser3, custom;

		// custom serialisation unavailable
		JennyNet.reset();
		try {
			JennyNet.getDefaultSerialisation(2);
			fail("expected SerialisationUnavailableException");
		} catch (SerialisationUnavailableException e) {
		}
		
		// set global serialisations registries
		Serialization globalSer = JennyNet.getDefaultSerialisation(0);
		globalSer.registerClass(CustomClass_3.class);
		globalSer = JennyNet.getDefaultSerialisation(1);
		globalSer.registerClass(CustomClass_2.class);
		assertTrue("JennyNet default serial-method error", JennyNet.getDefaultSerialisationMethod() == 0);
		assertTrue("JennyNet default parameters setting", JennyNet.getDefaultParameters().getSerialisationMethod() == 0);
		
		try {
			sv1 = new StandardServer(new InetSocketAddress("localhost", 3000), receptionListener);
			sv1.start();
			
			// set up a running connection
			cl1 = new Client();
			cl1.addListener(clientListener);
			
			cl1.connect(100, sv1.getSocketAddress());
			Util.sleep(20);
			System.out.println("-- (Client-1) connection established " + cl1.toString());
			svcon = sv1.getConnection(cl1.getLocalAddress());

			// check connections' class registrations via global registrations (client and server-con)
			assertTrue(cl1.getSendSerialization().getMethodID() == 0);
			assertTrue(cl1.getReceiveSerialization().getMethodID() == 0);
			assertTrue(cl1.getSendSerialization().isRegisteredClass(CustomClass_3.class));
			assertTrue(cl1.getReceiveSerialization().isRegisteredClass(CustomClass_3.class));
			assertFalse(cl1.getSendSerialization().isRegisteredClass(CustomClass_2.class));
			assertFalse(cl1.getReceiveSerialization().isRegisteredClass(CustomClass_2.class));
			
			assertTrue(svcon.getSendSerialization().getMethodID() == 0);
			assertTrue(svcon.getReceiveSerialization().getMethodID() == 0);
			assertTrue(svcon.getSendSerialization().isRegisteredClass(CustomClass_3.class));
			assertTrue(svcon.getReceiveSerialization().isRegisteredClass(CustomClass_3.class));
			assertFalse(svcon.getSendSerialization().isRegisteredClass(CustomClass_2.class));
			assertFalse(svcon.getReceiveSerialization().isRegisteredClass(CustomClass_2.class));
			
			// set a new global SERIALIZATION METHOD (and its effects to new connections)
			JennyNet.setDefaultSerialisationMethod(1);
			assertTrue("JennyNet default serial-method error", JennyNet.getDefaultSerialisationMethod() == 1);
			assertTrue("JennyNet default parameters setting", JennyNet.getDefaultParameters().getSerialisationMethod() == 1);
			
			// global default setting does not modify existing connections' default
			assertTrue(cl1.getSendSerialization().getMethodID() == 0);
			assertTrue(cl1.getReceiveSerialization().getMethodID() == 0);

			// effects on a new server
			sv2 = new StandardServer(new InetSocketAddress("localhost", 3001), receptionListener);
			sv2.start();
			assertTrue(sv2.getParameters().getSerialisationMethod() == 1);
			
			// effects on a new client
			cl2 = new Client();
			cl2.connect(100, sv2.getSocketAddress());
			Util.sleep(20);
			System.out.println("-- (Client-2) connection established " + cl2.toString());
			svcon = sv2.getConnection(cl2.getLocalAddress());
			
			// check connections' class registrations via global registrations (client and server-con)
			assertTrue(cl2.getParameters().getSerialisationMethod() == 1);
			assertTrue(cl2.getSendSerialization().getMethodID() == 1);
			assertTrue(cl2.getReceiveSerialization().getMethodID() == 1);
			assertTrue(cl2.getSendSerialization().isRegisteredClass(CustomClass_2.class));
			assertTrue(cl2.getReceiveSerialization().isRegisteredClass(CustomClass_2.class));
			assertFalse(cl2.getSendSerialization().isRegisteredClass(CustomClass_3.class));
			assertFalse(cl2.getReceiveSerialization().isRegisteredClass(CustomClass_3.class));
			
			assertTrue(svcon.getSendSerialization().getMethodID() == 1);
			assertTrue(svcon.getReceiveSerialization().getMethodID() == 1);
			assertTrue(svcon.getSendSerialization().isRegisteredClass(CustomClass_2.class));
			assertTrue(svcon.getReceiveSerialization().isRegisteredClass(CustomClass_2.class));
			assertFalse(svcon.getSendSerialization().isRegisteredClass(CustomClass_3.class));
			assertFalse(svcon.getReceiveSerialization().isRegisteredClass(CustomClass_3.class));
			
			// changing global class registrations does not change existing connections
			JennyNet.getDefaultSerialisation(0).registerClass(CustomClass_4.class);
			JennyNet.getDefaultSerialisation(1).registerClass(CustomClass_4.class);
			assertFalse(cl1.getSendSerialization().isRegisteredClass(CustomClass_4.class));
			assertFalse(cl1.getReceiveSerialization().isRegisteredClass(CustomClass_4.class));
			assertFalse(cl2.getSendSerialization().isRegisteredClass(CustomClass_4.class));
			assertFalse(cl2.getReceiveSerialization().isRegisteredClass(CustomClass_4.class));
			assertFalse(svcon.getSendSerialization().isRegisteredClass(CustomClass_4.class));
			assertFalse(svcon.getReceiveSerialization().isRegisteredClass(CustomClass_4.class));
			
			// changing default parameters value for serial-method
			JennyNet.getDefaultParameters().setSerialisationMethod(0);
			assertTrue("JennyNet default serial-method error", JennyNet.getDefaultSerialisationMethod() == 0);
			assertTrue("JennyNet default parameters setting", JennyNet.getDefaultParameters().getSerialisationMethod() == 0);

			// setup a (global) custom serialisation
			custom = new OurSerialisation();
			JennyNet.setCustomSerialisation(custom);
			
			ser1 = JennyNet.getDefaultSerialisation(2);
			assertNotNull(ser1);
			ser1.registerClass(CustomClass_2.class);
			
			// invoke a global custom serialisation at a connection
			ser2 = cl2.getSendSerialization(2);
			ser3 = cl2.getReceiveSerialization(2);
			assertNotNull(ser2);
			assertNotNull(ser3);
			assertTrue(ser2.getMethodID() == 2);
			assertTrue(ser3.getMethodID() == 2);
			assertTrue(ser2.isRegisteredClass(CustomClass_2.class));
			assertTrue(ser3.isRegisteredClass(CustomClass_2.class));
			assertTrue(cl2.getSendSerialization().getMethodID() == 1);
			assertTrue(cl2.getReceiveSerialization().getMethodID() == 1);

			// set custom serialisation into use at a connection
			cl2.getParameters().setSerialisationMethod(2);
			assertTrue(cl2.getSendSerialization().getMethodID() == 2);
			assertTrue(cl2.getReceiveSerialization().getMethodID() == 2);
			assertTrue(cl2.getSendSerialization().isRegisteredClass(CustomClass_2.class));
			assertTrue(cl2.getReceiveSerialization().isRegisteredClass(CustomClass_2.class));
			
			
			// shutdown net systems
		} finally {
			System.out.println("\n------------------------------------------------------------------ ");
			System.out.println("--- System Memory : " + Runtime.getRuntime().freeMemory() + " / " 
					+ Runtime.getRuntime().totalMemory());
			if (sv1 != null) {
				sv1.closeAndWait(3000);
			}
			if (sv2 != null) {
				sv2.closeAndWait(3000);
			}

			receptionListener.reportEvents();
			clientListener.reportEvents();
		}
	}
	
//	@Test
//	public void linked_serialisation () throws NotSerializableException, SerialisationException {
//		Serialization ser1 = JennyNet.createSerialisation(0);
//		int nrRegCl = ser1.getRegisteredSize();
//		LinkedSerialisation ls1 = new LinkedSerialisation(ser1);
//		System.out.println("-- linked serialisation w/ " + ls1.getRegisteredSize() + " registrations");
//		
//		// compare properties of linked serialisations
//		String text = "property mismatch in linked-serialisation";
//		assertTrue(text, ls1.getRegisteredSize() == nrRegCl);
//		assertTrue(text, ls1.getMethodID() == ser1.getMethodID());
//		assertTrue(text, ls1.getName().equals(ser1.getName()));
//		assertTrue(text, ls1.getRegisteredClasses().equals(ser1.getRegisteredClasses()));
//		for (Class<?> cla : ser1.getRegisteredClasses()) {
//			assertTrue("missing class registration", ls1.isRegisteredClass(cla));
//		}
//		
//		// intrinsic values
//		assertTrue("false link reference", ls1.getLink() == ser1);
//		assertFalse("false link property", ls1.isHardLinked());
//
//		// transitive modifications on original (soft-link)
//		ser1.registerClass(CustomClass_4.class);
//		assertTrue(ser1.getRegisteredSize() > nrRegCl);
//		assertTrue(ser1.isRegisteredClass(CustomClass_4.class));
//		assertTrue("registration of new class not transitive", ser1.getRegisteredSize() == ls1.getRegisteredSize());
//		assertTrue("registration not available in link", ls1.isRegisteredClass(CustomClass_4.class));
//		System.out.println("-- linked serialisation w/ " + ls1.getRegisteredSize() + " registrations");
//		
//		// create a copy of linked-serialisation
//		LinkedSerialisation ls2 = ls1.copy();
//		assertFalse(ls2 == ls1);
//		assertTrue("class-list error in copy of linked-serial", ls2.getRegisteredClasses().equals(ls1.getRegisteredClasses()));
//		assertTrue(text, ls2.getRegisteredSize() == nrRegCl+1);
//		assertTrue(text, ls2.getMethodID() == ser1.getMethodID());
//		assertTrue(text, ls2.getName().equals(ser1.getName()));
//		for (Class<?> cla : ls1.getRegisteredClasses()) {
//			assertTrue("missing class registration", ls2.isRegisteredClass(cla));
//		}
//		assertTrue("false link reference", ls2.getLink() == ser1);
//		assertFalse("false link property", ls2.isHardLinked());
//		
//		// register new class on copy, thus transform it to hard-link
//		ls2.registerClass(ArrayList.class);
//		assertTrue("missing class registration", ls2.isRegisteredClass(ArrayList.class));
//		assertFalse("incorrect class registration", ls1.isRegisteredClass(ArrayList.class));
//		assertTrue(text, ls2.getRegisteredSize() == nrRegCl+2);
//		assertTrue(text, ls2.getMethodID() == ser1.getMethodID());
//		assertTrue(text, ls2.getName().equals(ser1.getName()));
//		for (Class<?> cla : ls1.getRegisteredClasses()) {
//			assertTrue("missing class registration", ls2.isRegisteredClass(cla));
//		}
//		assertFalse("false link reference", ls2.getLink() == ser1);
//		assertFalse("false link reference", ls2.getLink() == ls2);
//		assertNotNull("link is null", ls2.getLink());
//		assertTrue("false link property", ls2.isHardLinked());
//
//		// hard-link is registration-separate from original
//		ser1.registerClass(ArraySet.class);
//		assertTrue(ls2.getRegisteredSize() == nrRegCl+2);
//		assertFalse("incorrect class registration in hard-link", ls2.isRegisteredClass(ArraySet.class));
//		
//		//create ls3 as copy of ls1
//		LinkedSerialisation ls3 = ls1.copy();
//		assertTrue("false link reference", ls3.getLink() == ser1);
//		assertFalse("false link property", ls3.isHardLinked());
//		
//		// set new serialisation as hard-link
//		Serialization ser2 = JennyNet.createSerialisation(1);
//		ls3.setSerialisation(ser2);
//		assertTrue("false link reference", ls3.getLink() == ser2);
//		assertTrue("false link property", ls3.isHardLinked());
//		assertTrue("class-list error", ls3.getRegisteredClasses().equals(ser2.getRegisteredClasses()));
//		
//		//create ls4 as copy of ls1
//		LinkedSerialisation ls4 = ls1.copy();
//		
//		// turns into hard-link after 'clear'
//		ls4.clear();
//		assertFalse("false link reference", ls4.getLink() == ser1);
//		assertTrue("false link property", ls4.isHardLinked());
//		assertTrue(text, ls4.getRegisteredSize() == 0);
//		assertTrue(text, ls4.getMethodID() == ls1.getMethodID());
//		for (Class<?> cla : ser1.getRegisteredClasses()) {
//			assertFalse("missing class registration", ls4.isRegisteredClass(cla));
//		}
//
//		ls4 = ls1.copy();
//		assertTrue(ls4.isRegisteredClass(ArraySet.class));
//		assertFalse(ls4.isHardLinked());
//		
//		// execution of serialisation (soft-link)
//		ArraySet<Integer> set1 = createArraySet (10);
//		byte[] sdata1 = ls4.serialiseObject(set1);
//		assertNotNull("no serialisation created", sdata1);
//		assertTrue("empty serialisation", sdata1.length > 100);
//		System.out.println("-- serialisation 1 of size " + sdata1.length);
//		
//		// de-serialise
//		Object obj1 = ls4.deserialiseObject(sdata1);
//		assertNotNull("no de-serialisation performed", obj1);
//		assertTrue("unexpected deserialised type", obj1 instanceof ArraySet);
//		assertTrue("deserialisation value failed", ((ArraySet)obj1).equals(set1));
//
//		// execution of serialisation (hard-link)
//		ls4.setSerialisation(ser2);
//		assertTrue("false link property", ls3.isHardLinked());
//		ser2.registerClass(ArraySet.class);
//		
//		sdata1 = ls4.serialiseObject(set1);
//		assertNotNull("no serialisation created", sdata1);
//		assertTrue("empty serialisation", sdata1.length > 50);
//		System.out.println("-- serialisation 2 of size " + sdata1.length);
//		
//		// de-serialise
//		obj1 = ls4.deserialiseObject(sdata1);
//		assertNotNull("no de-serialisation performed", obj1);
//		assertTrue("unexpected deserialised type", obj1 instanceof ArraySet);
//		assertTrue("deserialisation value failed", ((ArraySet)obj1).equals(set1));
//	}

	/** Returns a set of random Integer values of the given cardinality.
	 * 
	 * @param size int size of set
	 * @return {@code ArraySet<Integer>}
	 */
	private ArraySet<Integer> createArraySet (int size) {
		ArraySet<Integer> set = new ArraySet<>();
		for (int i = 0; i < size; i++) {
			Integer j = Util.nextRand(Integer.MAX_VALUE);
			set.add(j);
		}
		return set;
	}

	private static class CustomClass_1 {
		byte[] dataheap = Util.randBytes(500);
		int pointer = 12;
		String comment = "Urban und Zölibat";
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

	private static class CustomClass_3 implements Serializable {
		private static final long serialVersionUID = 93409762222487L;
		
		byte[] klamm = Util.randBytes(1000);
		int anzahl = 2819;
		CustomClass_1 custom = new CustomClass_1();
	}

	private static class CustomClass_4 implements Serializable {
		private static final long serialVersionUID = 93409762222487L;
		
		Point point = new Point(12, 305);
		int anzahl = 2819;
		CustomClass_1 custom1 = new CustomClass_1();
		CustomClass_2 custom2 = new CustomClass_2();
	}
	
	private class OurSerialisation extends JavaSerialisation {
		@Override
		public int getMethodID() {return 2;}

		@Override
		public String getName() {return "Test-Serialisation-1";}
	}
}
