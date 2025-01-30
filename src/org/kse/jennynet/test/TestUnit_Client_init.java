/*  File: TestUnit_Client_init.java
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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.UUID;

import org.junit.Test;
import org.kse.jennynet.appl.PlainReflectServer;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.core.JennyNet.ThreadUsage;
import org.kse.jennynet.exception.ConnectionRejectedException;
import org.kse.jennynet.exception.ConnectionTimeoutException;
import org.kse.jennynet.exception.JennyNetHandshakeException;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

public class TestUnit_Client_init {
   UUID randomUuid = UUID.randomUUID();
   
   static Server testServer;

   private static class SvListener extends DefaultServerListener {
      /** operation modus for answering incoming connections.
       * 0 = reject; 1 = accept.
       */
      int modus;
      
      /** milliseconds of delay before connection is started or rejected. 
       */
      int delay;
      
      public SvListener (int modus) {
         this(modus, 0);
      }
      
      public SvListener (int modus, int delay) {
         this.modus = modus;
         this.delay = delay;
      }
      
      @Override
      public void connectionAvailable (IServer server, ServerConnection connection) {
         try {
            Util.sleep(delay);
            
            if ( modus == 1 ) {
                  connection.start();
            } else {
               connection.reject();
            }
         } catch (Exception e) {
            System.out.println("*** SERVER-LISTENER ERROR: ***");
            e.printStackTrace();
         }
      }

      @Override
      public void errorOccurred (IServer server, Connection con, int transAction, Throwable e) {
         super.errorOccurred(server, con, transAction, e);
      }
      
   }
   
   static {
      try {
         testServer = new Server(new InetSocketAddress("localhost", 4000));
         testServer.start();
         testServer.addListener(new SvListener(1));
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
   
   /** Tests initial features of a server.
    * @param cli Client
    */
   private void initial_feature_test (Client cli) {
      assertFalse("not in closed state", cli.isClosed());
      assertFalse("not in idle state", cli.isIdle());
      assertFalse("not in transmitting state", cli.isTransmitting());
      assertTrue("not on global output processor", cli.isGlobalOutput());
      assertNull("getName should be null", cli.getName());
      assertTrue("equals itself", cli.equals(cli));
      assertNotNull("has UUID (initial)", cli.getUUID());
      assertNotNull("has short-ID (initial)", cli.getShortId());
      assertTrue("illegal short-ID length (initial)", cli.getShortId().length == 4);
      assertNotNull("has parameters (initial)", cli.getParameters());
      assertNotNull("has receive serialisation (initial)", cli.getReceiveSerialization());
      assertNotNull("has send serialisation (initial)", cli.getSendSerialization());
   }

   /** Tests features of an unbound initial server.
    * 
    * @param cli Client to test
    * @param socketAddress InetSocketAddress local socket address
    */
   private void bound_feature_test (Client cli, InetSocketAddress socketAddress) {
      initial_feature_test(cli);
      InetSocketAddress address = cli.getLocalAddress();
      assertTrue("isBound should return true", cli.isBound());
      assertTrue("getLocalAddress should return a value", address != null);
      assertTrue("getLocalAddress port number should be not 0", address.getPort() > 0
            & address.getPort() < 65535);
      assertTrue("local socket address is a false value", address.equals(socketAddress));
   }

   /** Tests features of an unbound initial server.
    * @param sv Client
    */
   private void unbound_feature_test (Client cli) {
      initial_feature_test(cli);
      assertFalse("not in connected state", cli.isConnected());
      assertFalse("isBound should return false", cli.isBound());
      assertNull("has no local address (initial)", cli.getLocalAddress());
      assertNull("has no remote address (initial)", cli.getRemoteAddress());
   }

   /** Tests features of an unbound initial server.
    * 
    * @param cli Client to test
    * @param localAddress InetSocketAddress local socket address
    * @param remoteAddress InetSocketAddress remote (server) socket address
    */
   private void connected_feature_test (Client cli, 
         InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
      bound_feature_test(cli, localAddress);
      InetSocketAddress address = cli.getRemoteAddress();
      assertTrue("should be in connected state", cli.isConnected());
      assertTrue("getRemoteAddress should return a value", address != null);
      assertTrue("getRemoteAddress port number should be not 0", address.getPort() > 0
            & address.getPort() < 65535);
      assertTrue("remote socket address is a false value", address.equals(remoteAddress));
   }

   @Test
   public void delivery_setting () throws IOException, InterruptedException {
	   Server sv = new Server(3000);
       sv.addListener(new SvListener(1));
	   sv.start();
	   Client cl = new Client();
	   cl.getSendSerialization().registerClass(byte[].class);
	   cl.addListener(new EventReporter());
	   ConnectionParameters param = cl.getParameters();
	   
	   // default setting is GLOBAL delivery
	   assertTrue("initial delivery thread setting not equal to JennyNet setting",
			   param.getDeliveryThreadUsage() == JennyNet.getConnectionParameters().getDeliveryThreadUsage());
	   assertTrue("client not on global delivery thread", cl.isGlobalOutput());
	   
	   // set delivery INDIVIDUAL
	   param.setDeliveryThreadUsage(ThreadUsage.INDIVIDUAL);
	   assertTrue("setting to INDIVIDUAL delivery thread failed", param.getDeliveryThreadUsage() == ThreadUsage.INDIVIDUAL);
	   assertFalse("client falsely on global delivery thread", cl.isGlobalOutput());
	   
	   // set delivery GLOBAL
	   param.setDeliveryThreadUsage(ThreadUsage.GLOBAL);
	   assertTrue("setting to GLOBAL delivery thread failed", param.getDeliveryThreadUsage() == ThreadUsage.GLOBAL);
	   assertTrue("client not on global delivery thread", cl.isGlobalOutput());
	   
	   cl.connect(0, sv.getSocketAddress());
	   System.out.println("-- connected, sending data ..");
	   byte[] data = Util.randBytes(50000);
	   cl.sendObject(data);
	   param = cl.getParameters();

	   // default setting is GLOBAL delivery
	   assertTrue("initial delivery thread setting not equal to JennyNet setting",
			   param.getDeliveryThreadUsage() == JennyNet.getConnectionParameters().getDeliveryThreadUsage());
	   assertTrue("client not on global delivery thread", cl.isGlobalOutput());
	   
	   // can set thread usage when connected
	   param.setDeliveryThreadUsage(ThreadUsage.INDIVIDUAL);
	   assertTrue(param.getDeliveryThreadUsage() == ThreadUsage.INDIVIDUAL);
	   
	   sv.closeAndWait(3000);
	   Util.sleep(20);
   }
   
   @Test
   public void unbound_init () {
      Client cli = null;
      
      try {
         cli = new Client();
         
         // features of an unbound client
         unbound_feature_test(cli);
         
         // close on unbound unstarted client
         cli.close();
         assertTrue("not in closed state", cli.isClosed());
         
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST UNBOUND INIT Exception: " + e);
      }
   }

   @Test
   public void unbound_connect () throws Exception {
      Server sv;
      Client cli;
      
      try {
         InetSocketAddress sad = new InetSocketAddress("localhost", 3025);
         sv = new Server(sad);
         sv.addListener(new SvListener(1));
         sv.start();
         
         // should be possible: unbound client connect 1
         cli = new Client();
         cli.connect(1000, "localhost", 3025);
         connected_feature_test(cli, cli.getLocalAddress(), sv.getSocketAddress());
         cli.close();
         
         // should be possible: unbound client connect 2
         cli = new Client();
         InetSocketAddress tarAdr = new InetSocketAddress("localhost", 3025);
         cli.connect(1000, tarAdr);
         connected_feature_test(cli, cli.getLocalAddress(), sv.getSocketAddress());
         cli.close();
         
         // should be possible: unbound client connect 3
         cli = new Client();
         InetAddress inetAdr = InetAddress.getByName("localhost");
         cli.connect(1000, inetAdr, 3025);
         connected_feature_test(cli, cli.getLocalAddress(), sv.getSocketAddress());
         cli.close();
         
         sv.close();
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST UNBOUND CONNECT Exception: " + e);
      }
   }

   @Test
   public void bound_connect () throws Exception {
      Server sv = null;
      Client cli;
      
      try {
         InetSocketAddress sad = new InetSocketAddress("localhost", 3026);
         InetSocketAddress cad = new InetSocketAddress("localhost", 4051);
         sv = new Server(sad);
         sv.addListener(new SvListener(1));
         sv.start();
         
         // should be possible: bound client connect 1
         cli = new Client(4050);
         cli.connect(1000, "localhost", 3026);
         connected_feature_test(cli, cad, sad);
         cli.close();
         Util.sleep(300);
         
         // should be possible: bound client connect 2
         cli = new Client(cad);
         InetSocketAddress tarAdr = new InetSocketAddress("localhost", 3026);
         cli.connect(1000, tarAdr);
         connected_feature_test(cli, cad, sad);
         cli.close();
         Util.sleep(300);
         
         // should be possible: bound client connect 3
         cli = new Client(cad);
         InetAddress inetAdr = InetAddress.getByName("localhost");
         cli.connect(1000, inetAdr, 3026);
         connected_feature_test(cli, cli.getLocalAddress(), sv.getSocketAddress());
         cli.close();
         
         sv.close();
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST BOUND CONNECT Exception: " + e);
      } finally {
    	  if (sv != null) {
    		  sv.closeAndWait(2000);
    	  }
      }
   } 

   @Test
   public void repeated_connect () throws Exception {
      Server sv = null;
      Client cli;
      
      try {
    	 Util.sleep(200);
         InetSocketAddress sad = new InetSocketAddress("localhost", 3026);
         InetSocketAddress cad = new InetSocketAddress("localhost", 4050);
         sv = new Server(sad);
         sv.addListener(new SvListener(1));
         sv.start();
         
         // connection failure: no service at port
         cli = new Client(4050);
         try {
        	 cli.connect(1000, "localhost", 1344);
        	 fail("left connect w/o exception");
         } catch (ConnectException e) {
         }

         // second connect attempt w/ correct address
         try {
        	 cli.connect(1000, sad);
        	 connected_feature_test(cli, cad, sad);
         } catch (Exception e) {
        	 fail("no success with second connect attempt");
        	 e.printStackTrace();
         }
         cli.closeAndWait(1000);
         sv.closeAndWait(1000);
         
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST REPEATED CONNECT Exception: " + e);
      } finally {
    	  sv.closeAndWait(1000);
      }
   }

   /**
    * This tests creation and start of clients with the wildcard
    * IP-address and a specified local port-number, in all possible methods.
    */
   @Test
   public void bound_start_1 () {
      Client cli1=null, cli2=null, cli3=null, cli4=null;
      
      try {
         cli1 = new Client();
         cli1.bind(0);
         bound_client(cli1, cli1.getLocalAddress());
         
         cli1 = new Client();
         cli1.bind(7726);
         bound_client(cli1, 7726);
         
         cli2 = new Client(2365);
         bound_client(cli2, 2365);
         
         InetSocketAddress address = new InetSocketAddress((InetAddress)null, 4590);
         cli3 = new Client(address);
         bound_client(cli3, address);
   
         address = new InetSocketAddress((InetAddress)null, 1188);
         cli4 = new Client();
         cli4.bind(address);
         bound_client(cli4, address);
   
         
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST BOUND START Exception: " + e);
      } finally {
         close_client(cli1);
         close_client(cli2);
         close_client(cli3);
         close_client(cli4);
      }
   }

   @Test (expected = IllegalArgumentException.class)
   public void failed_outofrange_port_1 () throws Exception {
      Client cli1 = new Client(60000);
      try {
         cli1.connect(0, "grus.ukleurzeuzreuberspace.de", -1);
      } finally {
         close_client(cli1);
      }
   }
   
   @Test (expected = IllegalArgumentException.class)
   public void failed_outofrange_port_2 () throws Exception {
      Client cli1 = new Client(60000);
      try {
         cli1.connect(0, "grus.uberspace.de", 65536);
      } finally {
         close_client(cli1);
      }
   }
   
   @Test (expected = IllegalArgumentException.class)
   public void failed_null_host () throws Exception {
      Client cli1 = new Client(60000);
      try {
         cli1.connect(0, (String)null, 45890);
      } finally {
         close_client(cli1);
      }
   }
   
   @Test (expected = java.net.UnknownHostException.class)
   public void failed_unknown_host_1 () throws Exception {
      Client cli1 = new Client(60000);
      try {
         cli1.connect(0, "grus.ukleurzeuzreuberspace.de", 45890);
      } finally {
         close_client(cli1);
      }
   }
   
   @Test (expected = java.net.UnknownHostException.class)
   public void failed_unknown_host_2 () throws Exception {
      Client cli1 = new Client(60000);
      try {
         cli1.connect(10000, "grus.ukleurzeuzreuberspace.de", 45890);
      } finally {
         close_client(cli1);
      }
   }
   
   @Test (expected = java.net.UnknownHostException.class)
   public void failed_unknown_host_3 () throws Exception {
      Client cli1 = new Client(60000);
      try {
//         cli1.connect(0, "grus.uberspace.de", 70000);
         cli1.connect(0, "grus.ukleurzeuzreuberspace.de", 0);
         
      } finally {
         close_client(cli1);
      }
   }
   
   @Test
   public void failed_unreachable_host_1 () throws Exception {
      Client cli1 = new Client(60000);
      Client cli2 = new Client(61000);
      try {
         // this is a non-existing address
         cli1.connect(0, "grus.uberspace.de", 0);
         
      } catch (UnknownHostException | NoRouteToHostException e) {   
      } finally {
         close_client(cli1);
         close_client(cli2);
      }
   }
   
//   @Test (expected = java.net.ConnectException.class)
//   public void failed_unreachable_host_2 () throws Exception {
//      Client cli1 = new Client(60000);
//      try {
//         // this is an existing address but without listener (should be)
//         cli1.connect(0, "grus.uberspace.de", 62557);
////         cli1.connect(0, "localhost", 62557);
//         
//      } catch (UnknownHostException e) {   
//      } finally {
//         close_client(cli1);
//      }
//   }
   
   @Test (expected = JennyNetHandshakeException.class)
   public void failed_connection_handshake () throws Exception {
      PlainReflectServer rsv = new PlainReflectServer();
      Client cli1 = new Client(60000);
      try {
         rsv.start();

         // this is an existing address but without listener (should be)
         cli1.connect(0, "localhost", rsv.getPort());
         
      } finally {
         close_client(cli1);
         rsv.terminate();
      }
   }
   
   @Test (expected = ConnectionRejectedException.class)
   public void failed_rejected_connection () throws Exception {
      Client cli1 = new Client(60000);
      Server sv = new Server(8000);
      try {
         sv.addListener(new SvListener(0));
         sv.start();

         // this is an existing address but without listener (should be)
         cli1.connect(0, "localhost", 8000);
         
      } finally {
         close_client(cli1);
         sv.close();
         Util.sleep(30);
      }
   }
   
   @Test (expected = ConnectionTimeoutException.class)
   public void failed_timeout_connect () throws Exception {
      Client cli1 = new Client(60000);
      cli1.getParameters().setConfirmTimeout(1000);
      Server sv = new Server(8000);
      try {
         sv.addListener(new SvListener(0, 2000));
         sv.start();

         // this is an existing address but without listener (should be)
         cli1.connect(0, "localhost", 8000);
         
      } finally {
         close_client(cli1);
         sv.close();
         Util.sleep(30);
      }
   }
   
   
   private void bound_client (Client cli, int port) {
      bound_client(cli, new InetSocketAddress(port));
   }
   
   private void bound_client (Client cli, InetSocketAddress localAddress) {
      try {
         bound_feature_test(cli, localAddress);
         
         // start server
         InetSocketAddress serverAddress = testServer.getSocketAddress();
         cli.connect(1000, serverAddress);
         Thread.sleep(20);
         assertTrue("port address changed illegally", cli.getLocalAddress().getPort() == 
               localAddress.getPort());
         localAddress = cli.getLocalAddress();
         connected_feature_test(cli, localAddress, serverAddress);
      
      } catch (InterruptedException e) {
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST BOUND CLIENT Exception: " + e);
      }
   }

   private void close_client (Client cli) {
      if (cli != null) {
         cli.close();
         try {
            Thread.sleep(20);
         } catch (InterruptedException e) {
         }
      }
   }

}
