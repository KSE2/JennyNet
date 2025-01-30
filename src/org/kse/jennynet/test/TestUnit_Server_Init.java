/*  File: TestUnit_Server_Init.java
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
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.UUID;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerSignalMethod;
import org.kse.jennynet.util.Util;

public class TestUnit_Server_Init  {
   UUID randomUuid = UUID.randomUUID();

/** Tests initial features of a server.
 * @param sv Server
 */
private void initial_feature_test (Server sv) {
   assertTrue("AcceptQueueCapacity false value", sv.getAcceptQueueCapacity() == JennyNet.DEFAULT_QUEUE_CAPACITY);
   assertTrue("ThreadPriority false value", sv.getThreadPriority() == Thread.MAX_PRIORITY -2);
   assertNull("getName should be null", sv.getName());
   assertTrue("equals itself", sv.equals(sv));
   assertTrue("SignalMethod false value", sv.getSignalMethod() == ServerSignalMethod.LISTENER);
   assertTrue("getConnection should return null", sv.getConnection(randomUuid) == null);
   assertTrue("getConnections should return a value", sv.getConnections() != null);
   assertTrue("getConnections should return empty array", sv.getConnections().length == 0);
   assertTrue("getConnectionList should return a value", sv.getConnectionList() != null);
   assertTrue("getConnectionList should be empty", sv.getConnectionList().isEmpty());
}

/** Tests features of an unbound initial server.
 * @param sv Server
 */
private void unbound_feature_test (Server sv) {
   initial_feature_test(sv);
   assertTrue("getSocketAddress should be null", sv.getSocketAddress() == null);
   assertFalse("isBound should return false", sv.isBound());
}

/** Tests features of an unbound initial server.
 * 
 * @param sv Server
 * @param socketAddress InetSocketAddress
 */
private void bound_feature_test (Server sv, InetSocketAddress socketAddress) {
   initial_feature_test(sv);
   InetSocketAddress address = sv.getSocketAddress();
   assertTrue("isBound should return true", sv.isBound());
   assertTrue("getSocketAddress should return a value", address != null);
   assertTrue("getSocketAddress port number should be not 0", address.getPort() > 0
         & address.getPort() < 65535);
   assertTrue("getSocketAddress returns false value", address.equals(socketAddress));
}

@Test
public void test_unbound_init () {
   Server sv1 = null;
   
   try {
      sv1 = new Server();
      
      // features of an unbound server
      unbound_feature_test(sv1);
      assertFalse("isAlive should be false", sv1.isAlive());
      
      // close on unbound unstarted server
      sv1.close();
      unbound_feature_test(sv1);
      assertFalse("isAlive should be false", sv1.isAlive());
      
   } catch (IOException e) {
      e.printStackTrace();
      fail("TEST UNBOUND INIT Exception: " + e);
   }
}


@Test(expected = IllegalStateException.class)
public void test_unbound_start () throws Exception {
   Server sv1 = new Server();
   // should not be possible: start an unbound server
   sv1.start();
}

private void test_bound_server (Server svr, InetSocketAddress address) {
   try {
      bound_feature_test(svr, address);
      
      // start server
      svr.start();
      Thread.sleep(20);
      assertTrue("isAlive should be true", svr.isAlive());
      bound_feature_test(svr, address);
   
   } catch (InterruptedException e) {
   } catch (Exception e) {
      e.printStackTrace();
      fail("TEST BOUND START Exception: " + e);
   }
}

private void close_server (Server svr) {
   if (svr != null) {
      svr.close();
      Util.sleep(20);
   }
}

@Test (expected=IllegalArgumentException.class)
public void test_failed_bind_out_of_range_1 () throws Exception {
   Server sv = new Server();
   sv.bind(-1);
}

@Test (expected=IllegalArgumentException.class)
public void test_failed_bind_out_of_range_2 () throws Exception {
   Server sv = new Server();
   sv.bind(65536);
}

/**
 * This tests creation and start of servers with the wildcard
 * IP-address and a specified local port-number, in all possible methods.
 */
@Test
public void test_bound_start_1 () {
   Server sv1=null, sv2=null, sv3=null, sv4=null, sv5=null, sv6=null;
   
   try {
      sv1 = new Server();
      sv1.bind(7726);
      test_bound_server(sv1, new InetSocketAddress(7726));
      
      sv2 = new Server(2365);
      test_bound_server(sv2, new InetSocketAddress(2365));
      
      InetSocketAddress address = new InetSocketAddress((InetAddress)null, 4590);
      sv3 = new Server(address);
      test_bound_server(sv3, address);

      sv4 = new Server();
      sv4.bind(null);
      address = new InetSocketAddress((InetAddress)null, sv4.getSocketAddress().getPort());
      test_bound_server(sv4, address);

      address = new InetSocketAddress((InetAddress)null, 1188);
      sv6 = new Server();
      sv6.bind(address);
      test_bound_server(sv6, address);

      
   } catch (IOException e) {
      e.printStackTrace();
      fail("TEST BOUND START Exception: " + e);
   } finally {
      close_server(sv1);
      close_server(sv2);
      close_server(sv3);
      close_server(sv6);
   }
}

/**
 * This tests creation and start of servers with the wildcard
 * IP-address and a system created random port-number, in all possible methods.
 */
@Test
public void test_bound_start_2 () {
   Server sv1=null, sv2=null, sv3=null, sv4=null, sv5=null, sv6=null;
   
   try {
      sv1 = new Server();
      sv1.bind(0);
      test_bound_server(sv1, new InetSocketAddress(sv1.getSocketAddress().getPort()));
      
      sv2 = new Server(0);
      test_bound_server(sv2, new InetSocketAddress(sv2.getSocketAddress().getPort()));
      
      InetSocketAddress address = new InetSocketAddress((InetAddress)null, 0);
      sv3 = new Server(address);
      address = sv3.getSocketAddress();
      test_bound_server(sv3, address);

      address = new InetSocketAddress((InetAddress)null, 0);
      sv6 = new Server();
      sv6.bind(address);
      address = sv6.getSocketAddress();
      test_bound_server(sv6, address);

   } catch (IOException e) {
      e.printStackTrace();
      fail("TEST BOUND START 2 Exception: " + e);
   } finally {
      close_server(sv1);
      close_server(sv2);
      close_server(sv3);
      close_server(sv6);
   }
}

/**
 * This tests creation and start of servers with a full definition of
 * IP-address and port-number, in all possible methods.
 */
@Test
public void test_bound_start_3 () {
   Server sv1=null, sv2=null, sv3=null, sv4=null, sv5=null, sv6=null;
   InetAddress ipAddr;
   InetSocketAddress address;
   
   try {
//      ipAddr = InetAddress.getByName("java.sun.com");
//      address = new InetSocketAddress(ipAddr, 3001);
//      sv3 = new Server(address);
//      address = sv3.getSocketAddress();
//      test_bound_server(sv3, address);
      
      ipAddr = InetAddress.getByName("localhost");
//      ipAddr = InetAddress.getByName("188.192.40.229");
      address = new InetSocketAddress(ipAddr, 3002);
      sv6 = new Server();
      sv6.bind(address);
//      address = sv6.getSocketAddress();
      test_bound_server(sv6, address);

   } catch (IOException e) {
      e.printStackTrace();
      fail("TEST BOUND START 3 Exception: " + e);
   } finally {
      close_server(sv1);
      close_server(sv2);
      close_server(sv3);
      close_server(sv6);
   }
}

@Test
public void test_set_name () {
   
   try {
      Server sv = new Server(2098);
      
      String name1 = "Hans";
      sv.setName(name1);
      assertTrue("setName does not install new value, 1", name1.equals(sv.getName()));

      String name2 = "Marry";
      sv.setName(name2);
      assertTrue("setName does not install new value, 2", name2.equals(sv.getName()));

      sv.setName(null);
      assertNull("setName cannot set null value", sv.getName());
      
      sv.close();
   } catch (Exception e) {
      e.printStackTrace();
      fail();
   }
}

@Test
public void test_set_threadPriority () {
   
   try {
      Server sv = new Server(2098);

      // exception on illegal value LOW
      try {
         sv.setThreadPriority(Thread.MIN_PRIORITY -1);
         fail("setThreadPriority should throw an exception on illegal value");
      } catch (Exception e) {
         assertTrue("setThreadPriority false exception thrown", e instanceof IllegalArgumentException);
      }
      
      // exception on illegal value HIGH
      try {
         sv.setThreadPriority(Thread.MAX_PRIORITY +1);
         fail("setThreadPriority should throw an exception on illegal value");
      } catch (Exception e) {
         assertTrue("setThreadPriority false exception thrown", e instanceof IllegalArgumentException);
      }
      
      // identify a new priority value
      int pri = 3;
      if (pri == sv.getThreadPriority()) {
         pri = 4;
      }

      // set new priority value (ante start)
      sv.setThreadPriority(pri);
      assertTrue("setThreadPriority does not install new value, 1", 
            pri == sv.getThreadPriority());

      // set new priority value
      sv.setThreadPriority(Thread.MIN_PRIORITY);
      assertTrue("setThreadPriority does not install new value, 2", 
            Thread.MIN_PRIORITY == sv.getThreadPriority());

      // start server and test status
      sv.start();
      assertTrue("starting server modifies ThreadPriority", 
            Thread.MIN_PRIORITY == sv.getThreadPriority());
      
      // set new priority value (post start)
      sv.setThreadPriority(pri);
      assertTrue("setThreadPriority does not install new value, 3", 
            pri == sv.getThreadPriority());

      // set maximum priority
      sv.setThreadPriority(Thread.MAX_PRIORITY);
      assertTrue("setThreadPriority does not install maximum value", 
            Thread.MAX_PRIORITY == sv.getThreadPriority());

      sv.close();
   } catch (Exception e) {
      e.printStackTrace();
      fail();
   }
}

@Test
public void test_set_queueCapacity () {
   Server sv = null;
	
   try {
      sv = new Server(2098);

      // identify a new priority value
      int cap = 300;

      // set new priority value (ante start)
      sv.setAcceptQueueCapacity(cap);
      assertTrue("setAcceptQueueCapacity does not install new value, 1", 
            cap == sv.getAcceptQueueCapacity());

      // allows for Integer.MAX_VALUE (ante start)
      sv.setAcceptQueueCapacity(Integer.MAX_VALUE);
      assertTrue("setAcceptQueueCapacity does not allow Integer.MAX_VALUE", 
            Integer.MAX_VALUE == sv.getAcceptQueueCapacity());

      // value has minimum of 1
      sv.setAcceptQueueCapacity(0);
      assertTrue("AcceptQueueCapacity should have a mimimum of one", 
            1 == sv.getAcceptQueueCapacity());

      // tolerates negative arguments (corrected to minimum)
      sv.setAcceptQueueCapacity(-1);
      assertTrue("AcceptQueueCapacity should tolerate negative argument", 
            1 == sv.getAcceptQueueCapacity());

      // start server
      sv.start();
      assertTrue("starting server modifies AcceptQueueCapacity", 
            1 == sv.getAcceptQueueCapacity());
      
      // set new capacity value is ignored (post start) 
      try {
         sv.setAcceptQueueCapacity(cap);
         fail("setAcceptQueueCapacity should throw an exception after server started");
      } catch (Exception e) {
         assertTrue("setAcceptQueueCapacity false exception thrown", e instanceof IllegalStateException);
      }

      sv.close();
      
   } catch (Exception e) {
      e.printStackTrace();
      fail();
   } finally {
	  if (sv != null) {
		  sv.close();
	  }
   }
}

@Test
public void test_set_signalMethod () {
   Server sv=null;
   
   try {
      sv = new Server(2098);

      assertTrue("SignalMethod initial value should be \"Listener\"", 
            ServerSignalMethod.LISTENER == sv.getSignalMethod());

      // set new signal method "ACCEPT" (ante start)
      sv.setSignalMethod(ServerSignalMethod.ACCEPT);
      assertTrue("setSignalMethod does not install alternate value, 1", 
            ServerSignalMethod.ACCEPT == sv.getSignalMethod());

      // start server
      sv.start();
      assertTrue("starting server modifies SignalMethod", 
            ServerSignalMethod.ACCEPT == sv.getSignalMethod());
      
   } catch (Exception e) {
      e.printStackTrace();
      fail();
   } finally {
	  if (sv != null) {
		  sv.close();
	  }
   }
}

@Test (expected=IllegalStateException.class)
public void test_fail_signalMethod () throws Exception {
   Server sv =  new Server(2098);
   try {
      sv.start();
      // set new signal method "LISTENER" (post start)
      sv.setSignalMethod(ServerSignalMethod.LISTENER);
   } finally {
		  sv.close();
   }
}  

@Test
public void test_double_bind () {
   Server sv = null;

   // bind(port)  
   try {
      sv = new Server();
      sv.bind(3400);
      sv.bind(3401);
      fail("DOUBLE-BIND should not be possible: bind(port)");
   } catch (Exception e) {
      assertTrue("DOUBLE-BIND false exception thrown", e instanceof IOException);
   } finally {
	  if (sv != null) {
		  sv.close();
	  }
   }

   // bind(address)  
   try {
      sv = new Server();
      InetSocketAddress address = new InetSocketAddress(8828);
      sv.bind(address);
      sv.bind(address);
      fail("DOUBLE-BIND should not be possible: bind(address)");
   } catch (Exception e) {
      assertTrue("DOUBLE-BIND false exception thrown", e instanceof IOException);
   } finally {
	  if (sv != null) {
		  sv.close();
	  }
   }

   // bind(address)  
   try {
      sv = new Server();
      InetSocketAddress address = new InetSocketAddress(8828);
      sv.bind(address);
      sv.bind(3400);
      fail("DOUBLE-BIND should not be possible: bind(address)-bind(port)");
   } catch (Exception e) {
      assertTrue("DOUBLE-BIND false exception thrown", e instanceof IOException);
   } finally {
	  if (sv != null) {
		  sv.close();
	  }
   }

   // bind(address) after init(port)  
   try {
      sv = new Server(2900);
      InetSocketAddress address = new InetSocketAddress(8828);
      sv.bind(address);
      fail("DOUBLE-BIND should not be possible: bind(address) after init(port)");
   } catch (Exception e) {
      assertTrue("DOUBLE-BIND false exception thrown", e instanceof IOException);
   } finally {
	  if (sv != null) {
		  sv.close();
	  }
   }
}

/** Tests whether a port address can be re-used after the bearing server
 * has been closed.
 */
@Test
public void test_unbind () {
   try {
      Server sv = new Server(2300);
      InetSocketAddress address = new InetSocketAddress(2300);
      sv.close();
      Thread.sleep(10);

      // case 1
      sv = new Server();
      sv.bind(2300);
      test_bound_server(sv, address);
      sv.close();
      Thread.sleep(10);
   
      // case 2
      sv = new Server();
      sv.bind(address);
      test_bound_server(sv, address);
      sv.close();
   
   } catch (Exception e) {
      e.printStackTrace();
      fail("Exception Double Bind: " + e);
   }
}

@Test
public void test_set_parameters () {
   ConnectionParameters par;
   
   try {
      final Thread testThread = Thread.currentThread(); 
      Server sv = new Server(2098);
      
      // default set defined
      par = sv.getParameters();
      assertNotNull("getParameters should return a value", par);
      
      // exception on null value
      try {
         sv.setParameters(null);
         fail("setParameters should throw an exception on null value");
      } catch (Exception e) {
         assertTrue("setParameters false exception thrown", e instanceof NullPointerException);
      }
      
      // isolate 3 parameter values and modify
      int transParSize = par.getTransmissionParcelSize();
      int alivePeriod = par.getAlivePeriod();
      int newTransParSize = transParSize + 22978;
      int newAlivePeriod = alivePeriod + 15600;
      File newRootF = JennyNet.getTempDirectory();
      
      // modify and test server parameters (direct modus)
      sv.getParameters().setTransmissionParcelSize(newTransParSize);
      sv.getParameters().setAlivePeriod(newAlivePeriod);
      sv.getParameters().setFileRootDir(newRootF);

      ConnectionParameters par2 = sv.getParameters();
      assertTrue("METHOD-1, parameter value mismatch", par2.getAlivePeriod() == newAlivePeriod);
      assertTrue("METHOD-1, parameter value mismatch", par2.getTransmissionParcelSize() == newTransParSize);
      assertTrue("METHOD-1, file-root parameter is null", par2.getFileRootDir() != null);
      assertTrue("METHOD-1, parameter file-root value mismatch", par2.getFileRootDir().equals(newRootF));
      
      // control check global parameters (old set of parameters)
      ConnectionParameters parN = JennyNet.getConnectionParameters();
      assertTrue("GLOBAL-1, parameter value mismatch", parN.getAlivePeriod() == alivePeriod);
      assertTrue("GLOBAL-1, parameter value mismatch", parN.getTransmissionParcelSize() == transParSize);
      assertTrue("GLOBAL-1, file-root parameter is null", parN.getFileRootDir() == null);

      // set new set of parameters
      newTransParSize = transParSize + 10890;
      newAlivePeriod = alivePeriod + 25600;
      newRootF = new File(JennyNet.getTempDirectory(), "ZEBRAWEBRA");
      Files.createDirectories(newRootF.toPath(), new FileAttribute<?>[0]);
      parN.setTransmissionParcelSize(newTransParSize);
      parN.setAlivePeriod(newAlivePeriod);
      parN.setFileRootDir(newRootF);
      sv.setParameters(parN);
      
      par2 = sv.getParameters();
      assertTrue("PARAMETERS should be contained object", parN != par2);
      assertTrue("METHOD-2, parameter value mismatch", par2.getAlivePeriod() == newAlivePeriod);
      assertTrue("METHOD-2, parameter value mismatch", par2.getTransmissionParcelSize() == newTransParSize);
      assertTrue("METHOD-2, file-root parameter is null", par2.getFileRootDir() != null);
      assertTrue("METHOD-2, parameter file-root value mismatch", par2.getFileRootDir().equals(newRootF));
      
      // start server and test parameters
      sv.start();
      par2 = sv.getParameters();
      assertTrue("AFTER-START, parameter value mismatch", par2.getAlivePeriod() == newAlivePeriod);
      assertTrue("AFTER-START, parameter value mismatch", par2.getTransmissionParcelSize() == newTransParSize);
      assertTrue("AFTER-START, file-root parameter is null", par2.getFileRootDir() != null);
      assertTrue("AFTER-START, parameter file-root value mismatch", par2.getFileRootDir().equals(newRootF));
      
      // TEST CLIENT (new server-connection)
      // add a server listener to catch incoming clients
      // we right-in-place test their parameter settings to match server modifications
      final int v11 = newAlivePeriod;
      final int v12 = newTransParSize;
      final File rootF = newRootF;
      sv.addListener(new DefaultServerListener() {

         @Override
         public void connectionAvailable (IServer server, ServerConnection con) {
            
            try {
               con.start();
            } catch (IOException e) {
               e.printStackTrace();
            }
            ConnectionParameters par = con.getParameters();
            assertTrue("CON, parameter value mismatch", par.getAlivePeriod() == v11);
            assertTrue("CON, parameter value mismatch", par.getTransmissionParcelSize() == v12);
            assertTrue("CON, file-root parameter is null", par.getFileRootDir() != null);
            assertTrue("CON, parameter file-root value mismatch", par.getFileRootDir().equals(rootF));
            testThread.interrupt();
         }
      });

      // create and connect a client
      Client client = new Client();
      client.connect(1000, "localhost", 2098);
      
      boolean interrupted = !Util.sleep(1000);
      assertTrue("connection was not caught and tested", interrupted);

      client.closeAndWait(2000);
      sv.closeAndWait(2000);
      
   } catch (Exception e) {
      e.printStackTrace();
      fail();
   }
}

}
