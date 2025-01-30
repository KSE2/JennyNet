/*  File: TestUnit_Server_Operate.java
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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionEvent;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.exception.ConnectionRejectedException;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionEventType;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerListener;
import org.kse.jennynet.intfa.ServerSignalMethod;
import org.kse.jennynet.test.FileReceptionListener.Station;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

public class TestUnit_Server_Operate {

   public static final int START_ANSWER = 0;
   public static final int REJECT_ANSWER = 1;

   private class TestServer extends Server {


      public TestServer (int port) throws IOException {
         super(new InetSocketAddress("localhost", port));
         init();
      }

      // public TestServer(InetAddress ipAddress, int port) throws IOException {
      // super(ipAddress, port);
      // }

      @SuppressWarnings("unused")
      public TestServer(SocketAddress address) throws IOException {
         super(address);
         init();
      }

      private void init () {
         ConnectionParameters par = getParameters();
         par.setConfirmTimeout(1000);
         
//         addConnectionListener(new EventReporter());
      }
      
   }

   private class AcceptThread extends Thread {

      List<Connection> conList = new ArrayList<Connection>();
      private Server server;
      private boolean terminate;
      private int answerModus;
      private int eventCounter, connectedCt;
      private int errorCt;

      public AcceptThread(Server sv) {
         server = sv;
      }

      @Override
      public void run () {
         System.out.println("*** Server ACCEPT THREAD STARTED: "
               + server.getSocketAddress());
         while (!terminate) {
            try {
               ServerConnection con = server.accept(0);
               conList.add(con);
               eventCounter++;

               try {
                  if (answerModus == START_ANSWER) {
                     con.start();
                     connectedCt++;
                     System.out.println("++++++ Connection STARTED (Server): "
                           + fullConName(con));
                  } else {
                     con.reject();
                     System.out.println("++++++ Connection REJECTED (Server): "
                           + fullConName(con));
                  }
               } catch (IllegalStateException e) {
                  System.out.println("------ Connection has lost connection: "
                        + fullConName(con));
                  e.printStackTrace();
               } catch (IOException e) {
                  System.out
                        .println("------ Connection START FAILS with IOException: "
                              + fullConName(con));
                  e.printStackTrace();
                  errorCt++;
               }

            } catch (InterruptedException e) {
            } catch (IllegalStateException e) {
               e.printStackTrace();
               terminate();
            }
         }
         System.out.println("--- Server ACCEPT THREAD TERMINATED: "
               + server.getSocketAddress());
      }

      public void terminate () {
         terminate = true;
         interrupt();
      }

      /** 0 = start connection, else = reject connection */
      public void setAnswerModus (int modus) {
         answerModus = modus;
      }

      /** List of all connections being made AVAILABLE (ever). */
      public List<Connection> getSignalledCons () {
         return conList;
      }

      /** Amount of connections signalled as AVAILABLE. */
      public int getAvailableCounter () {
         return eventCounter;
      }

      public int getConnectedCounter () {
         return connectedCt;
      }

      public int getErrorCounter () {
         return errorCt;
      }
   }

   /**
    * Default ANSWER-MODUS is START.
    */
   private class TestServerListener implements ServerListener {

      List<Connection> conList = new ArrayList<Connection>();
      List<Connection> conAddedList = new ArrayList<Connection>();
      List<Connection> conRemovedList = new ArrayList<Connection>();
      List<Connection> validList = new ArrayList<Connection>();

      int answerModus;
      int errorCt, transErrorCt;
      int connectedCt;
      int[] eventCounter = new int[4];

      @Override
      public void connectionAvailable (IServer server, ServerConnection connection) {
         conList.add(connection);
         try {
            if (answerModus == START_ANSWER) {
               connection.start();
               connectedCt++;
               System.out.println("++++++ Connection STARTED (Server): "
                     + fullConName(connection));
            } else {
               connection.reject();
               System.out.println("++++++ Connection REJECTED (Server): "
                     + fullConName(connection));
            }
         } catch (IllegalStateException e) {
            System.out.println("------ Connection has lost connection: "
                  + fullConName(connection));
            e.printStackTrace();
         } catch (IOException e) {
            System.out
                  .println("------ Connection START FAILS with IOException: "
                        + fullConName(connection));
            e.printStackTrace();
            errorCt++;
         }
         eventCounter[0]++;
      }

      @Override
      public void connectionAdded (IServer server, Connection connection) {
         conAddedList.add(connection);
         validList.add(connection);
         eventCounter[1]++;
      }

      @Override
      public void connectionRemoved (IServer server, Connection connection) {
         conRemovedList.add(connection);
         validList.remove(connection);
         eventCounter[2]++;
      }

      @Override
      public void serverClosed (IServer server) {
         eventCounter[3]++;
      }

      @Override
      public void errorOccurred (IServer server, Connection con, int transAction, Throwable e) {
         transErrorCt++;
         System.out.println("***** TRANSACTION ERROR: error " + transErrorCt
               + ", Con = " + con + ", action = " + transAction);
         e.printStackTrace();
      }

      public void setAnswerModus (int modus) {
         answerModus = modus;
      }

      public int getErrorCounter () {
         return errorCt;
      }

      public int getConnectedCounter () {
         return connectedCt;
      }

      /** Amount of connections signalled as AVAILABLE. */
      public int getAvailableCounter () {
         return eventCounter[0];
      }

      /** Amount of connections signalled as ADDED. */
      public int getAddedCounter () {
         return eventCounter[1];
      }

      /** Amount of connections signalled as REMOVED. */
      public int getRemovedCounter () {
         return eventCounter[2];
      }

      /** Amount of connections currently listed as valid (OPEN). */
      public int getValidCounter () {
         return validList.size();
      }

      /** Amount of SERVER CLOSED events. */
      public int getServerClosedCounter () {
         return eventCounter[3];
      }

      /** List of all ADDED connections (ever). */
      public List<Connection> getAddedCons () {
         return conAddedList;
      }

      /** List of all REMOVED connections (ever). */
      public List<Connection> getRemovedCons () {
         return conRemovedList;
      }

      /** List of all connections being made AVAILABLE (ever). */
      public List<Connection> getSignalledCons () {
         return conList;
      }

      /**
       * List of all connections with valid connection status (should be
       * identical to server's <i>getConnections()</i>).
       */
      public List<Connection> getValidCons () {
         return validList;
      }
   }

   @Test
   public void addRemove_serverListener () {
      TestServer sv1 = null, sv2 = null;

      try {
         sv1 = new TestServer(4500);
         sv2 = new TestServer(4400);
         TestServerListener listener = new TestServerListener();

         assertTrue("not empty initial server listener list",
               sv1.getListeners().length == 0);
         assertTrue("not empty initial server listener list",
               sv2.getListeners().length == 0);

         // adding a server listener
         sv1.addListener(listener);
         assertTrue("failed to add server listener", sv1.getListeners().length == 1);
         assertTrue("falsely added server listener (control server)",
               sv2.getListeners().length == 0);

         // useless repeated adding
         sv1.addListener(listener);
         assertTrue("false adding action", sv1.getListeners().length == 1);

         // adding another server listener
         TestServerListener li2 = new TestServerListener();
         sv1.addListener(li2);
         assertTrue("failed to add server listener",
               sv1.getListeners().length == 2);
         assertTrue("falsely added server listener (control server)",
               sv2.getListeners().length == 0);

         // exception on null parameter
         try {
            sv1.addListener(null);
            fail("addListener should throw an exception on null parameter");
         } catch (Exception e) {
            assertTrue("addListener false exception thrown",
                  e instanceof NullPointerException);
         }

         // remove one listener
         sv1.removeListener(listener);
         assertTrue("failed to remove server listener",
               sv1.getListeners().length == 1);
         assertTrue("false control value (control server)",
               sv2.getListeners().length == 0);

         // useless second removing
         sv1.removeListener(listener);
         assertTrue("false remove action", sv1.getListeners().length == 1);

         // remove other listener
         sv1.removeListener(li2);
         assertTrue("failed to remove server listener",
               sv1.getListeners().length == 0);

         // no exception on null parameter
         try {
            sv1.removeListener(null);
         } catch (Exception e) {
            fail("removeListener should not throw an exception on null parameter");
         }

         // START SERVER
         sv1.start();

         // adding a server listener after start
         sv1.addListener(listener);
         assertTrue("failed to add server listener",
               sv1.getListeners().length == 1);

         // useless repeated adding
         sv1.addListener(listener);
         assertTrue("false adding action", sv1.getListeners().length == 1);

         // adding another server listener
         sv1.addListener(li2);
         assertTrue("failed to add server listener",
               sv1.getListeners().length == 2);

         // remove other listener
         sv1.removeListener(li2);
         assertTrue("failed to remove server listener",
               sv1.getListeners().length == 1);

      } catch (IOException e) {
         e.printStackTrace();
         fail("TEST ADD SERVERLISTENER: " + e);
      } finally {
         sv1.close();
         sv2.close();
      }
   }

   private String fullConName (ServerConnection con) {
//      InetSocketAddress remote = con.getRemoteAddress();
      String txt = con.toString();
      return txt;
   }

//   @Test
//   public void addRemove_connection () {
//      TestServer sv1 = null, sv2 = null, sv3 = null;
//
//      try {
//         sv1 = new TestServer(4500);
//         sv2 = new TestServer(4400);
//         sv3 = new TestServer(5000);
//         sv3.start();
//         TestServerListener listener = new TestServerListener();
//         sv3.addListener(listener);
//
//         Connection con, con1, con3;
//         con1 = new ServerConnectionImpl();
//
//         // BEFORE START
//         assertTrue("not empty initial connection list",
//               sv1.getConnections().length == 0);
//         assertTrue("not empty initial connection list",
//               sv2.getConnections().length == 0);
//
//         for (int repeat = 0; repeat < 2; repeat++) {
//
//            if (repeat == 1) {
//               sv1.start();
//            }
//
//            // tolerance on null parameter
//            try {
//               sv1.addConnection(null);
//            } catch (Exception e) {
//               fail("addConnection should not throw exception on parameter null");
//            }
//
//            // adding a new connection
//            sv1.addConnection(con1);
//            assertTrue("fails adding a new (unstarted) connection",
//                  sv1.getConnections().length == 1);
//            assertTrue("falsely added a connection (control server)",
//                  sv2.getConnections().length == 0);
//
//            // useless second adding
//            sv1.addConnection(con1);
//            assertTrue("falsely adding connection (double entry)",
//                  sv1.getConnections().length == 1);
//
//            // adding a second, running (external source) connection
//            con3 = createConnectedConnection(sv3);
//            sv1.addConnection(con3);
//            assertTrue("fails adding a connected connection (external source)",
//                  sv1.getConnections().length == 2);
//            assertTrue("falsely added a connection (control server)",
//                  sv2.getConnections().length == 0);
//            con = sv1.getConnection(con3.getUUID());
//            assertTrue("fails adding connection; object not found", con3 == con);
//
//            // automatic remove (external source)
//            con3.close();
//            Util.sleep(50);
//            assertTrue(
//                  "failed automatic removal of closed connection (external close)",
//                  sv1.getConnections().length == 1);
//            assertNull(
//                  "failed automatic removal of closed connection (external close)",
//                  sv1.getConnection(con3.getUUID()));
//
//            // manual remove
//            sv1.removeConnection(con1);
//            assertTrue("failed removal of connection",
//                  sv1.getConnections().length == 0);
//
//            // fails: adding a closed connection
//            sv1.addConnection(con3);
//            assertTrue("falsely added a closed connection",
//                  sv1.getConnections().length == 0);
//            assertTrue("falsely added a closed connection (control server)",
//                  sv2.getConnections().length == 0);
//         }
//
//      } catch (IOException e) {
//         e.printStackTrace();
//         fail("TEST ADD CONNECTION: " + e);
//      } finally {
//         sv1.close();
//         sv2.close();
//         sv3.close();
//      }
//   }

   @Test
   public void accepting_LISTEN () {
      TestServer sv1 = null;
      int h;

      try {
         sv1 = new TestServer(3000);

         TestServerListener listen1 = new TestServerListener();
         sv1.addListener(listen1);
         sv1.start();
         assertTrue("server listener init error",
               listen1.getAvailableCounter() == 0);

         ServerConnection scon1, scon2, scon3;
         Client con3, con1, con2;
         con1 = new Client();
         con1.connect(0, sv1.getSocketAddress());
         sleep(30);

         // ASSERT: signalled connections get accepted (started)
         // test whether client connection was properly signalled to server
         // listener
         h = listen1.getAvailableCounter();
         scon1 = (ServerConnection)listen1.getSignalledCons().get(0);
         assertTrue("false value client signalled counter: " + h, h == 1);
         assertTrue("signalled con was not actually started",
               listen1.getConnectedCounter() == 1);
         assertTrue("false client signalled",
               scon1.getRemoteAddress().equals(con1.getLocalAddress()));

         // test whether client connection was properly added to connection list
         assertTrue("client con was not added to server's list", sv1.getConnections().length == 1);
         assertTrue("client con was not added to server's list", sv1.getConnectionList().size() == 1);
         assertNotNull("false client con was added to server's list", sv1.getConnection(scon1.getUUID()));
         assertTrue("client con was not signalled as ADDED to listener", listen1.getAddedCounter() == 1);
         assertTrue("false client con was added to listener's list", listen1.getAddedCons().contains(scon1));

         // start a second client
         con2 = new Client();
         con2.connect(0, sv1.getSocketAddress());
         sleep(20);

         // test whether client connection was properly signalled to server
         // listener
         h = listen1.getAvailableCounter();
         scon2 = (ServerConnection)listen1.getSignalledCons().get(1);
         assertTrue("false value client signalled counter: " + h, h == 2);
         assertTrue("signalled con was not actually started",
               listen1.getConnectedCounter() == 2);
         assertTrue("false client signalled",
               scon2.getRemoteAddress().equals(con2.getLocalAddress()));

         // test whether client connection was properly added to connection list
         assertTrue("client con was not added to server's list", sv1.getConnections().length == 2);
         assertNotNull("false client con was added to server's list", sv1.getConnection(scon2.getUUID()));
         assertTrue("client con was not signalled as ADDED to listener", listen1.getAddedCounter() == 2);
         assertTrue("false client con was added to listener's list", listen1.getAddedCons().contains(scon2));

         // start a third client that will be rejected
         // add a new server-listener to server (test multiple listeners)
         listen1.setAnswerModus(REJECT_ANSWER);
         con3 = new Client();
         try {
            con3.connect(0, sv1.getSocketAddress());
            fail("exception expected: ConnectionRejectedException");
         } catch (Exception e) {
            assertTrue("false exception",
                  e instanceof ConnectionRejectedException);
         }
         sleep(50);

         // test server listener condition
         scon3 = (ServerConnection)listen1.getSignalledCons().get(2);
         assertTrue("false client signalled", scon3.getRemoteAddress()
               .getPort() == con3.getLocalAddress().getPort());
         h = listen1.getAvailableCounter();
         assertTrue("false value client signalled counter: " + h, h == 3);
         assertTrue("signalled con was falsely started", listen1.getConnectedCounter() == 2);
         assertTrue("client con was signalled as ADDED to listener", listen1.getAddedCounter() == 2);
         assertFalse("client con was added to listener's ADDED list", listen1.getAddedCons().contains(scon3));

         // test server condition
         assertTrue("client con was added to server's list", sv1.getConnections().length == 2);
         assertNull("client con was added to server's list", sv1.getConnection(scon3.getUUID()));
         assertTrue("listener error counter > 0", listen1.getErrorCounter() == 0);

      } catch (IOException e) {
         e.printStackTrace();
         fail("TEST ACCEPTING LISTEN: " + e);
      } finally {
    	 if (sv1 != null) {
    		 sv1.closeAllConnections();
    		 sv1.close();
    		 sleep(20);
    	 }
      }
   }

   @Test
   public void accepting_ACCEPT () throws InterruptedException {
      TestServer sv1 = null;
      AcceptThread acceptThread = null;
      int h;

      try {
         sv1 = new TestServer(3000);
//         sv1.getParameters().setAlivePeriod(0);

         TestServerListener listen1 = new TestServerListener();
         sv1.addListener(listen1);

         try {
            sv1.accept(50);
            fail("accept on dead server should throw Exception");
         } catch (Exception e) {
            assertTrue("IllegalStateException expected",
                  e instanceof IllegalStateException);
         }

         acceptThread = new AcceptThread(sv1);
         sv1.setSignalMethod(ServerSignalMethod.ACCEPT);
         sv1.start();
         acceptThread.start();
         assertTrue("server acceptThread init error", acceptThread.getAvailableCounter() == 0);

         ServerConnection scon1, scon2, scon3;
         Client con1, con2, con3;
         con1 = new Client(new InetSocketAddress("localhost", 55555));
         con1.connect(0, sv1.getSocketAddress());
         sleep(50);
         con1.sendObject("Hello CON-1!");

         // ASSERT: signalled connections get accepted (started)
         // test client has been signalled to ACCEPT method
         h = acceptThread.getAvailableCounter();
         assertTrue("false value client signalled counter: " + h, h == 1);
         assertTrue("signalled con was not actually started", acceptThread.getConnectedCounter() == 1);
         scon1 = (ServerConnection)acceptThread.getSignalledCons().get(0);
         assertTrue("false client signalled (" + scon1.getRemoteAddress() + " -- " + con1.getLocalAddress() + ")",
               scon1.getRemoteAddress().equals(con1.getLocalAddress()));

         // test that client connection was NOT signalled to server listener
         assertTrue("falsely signalled client to listener", listen1.getAvailableCounter() == 0);
         assertTrue("client con was not signalled as ADDED to listener", listen1.getAddedCounter() == 1);
         assertTrue("client con was not added to listener's list", listen1.getAddedCons().contains(scon1));

         // test whether client connection was properly added to server's
         // connection list
         assertTrue("client con was not added to server's list", sv1.getConnections().length == 1);
         assertTrue("client con was not added to server's list", sv1.getConnectionList().size() == 1);
         assertNotNull("false client con was added to server's list",
               sv1.getConnection(scon1.getUUID()));

         // start a second client
         con2 = new Client();
         con2.connect(0, sv1.getSocketAddress());
         sleep(50);

         // test whether client connection was properly signalled to ACCEPT
         // method
         h = acceptThread.getAvailableCounter();
         scon2 = (ServerConnection)acceptThread.getSignalledCons().get(1);
         assertTrue("false value client signalled counter: " + h, h == 2);
         assertTrue("signalled con was not actually started", acceptThread.getConnectedCounter() == 2);
         assertTrue("false client signalled", scon2.getRemoteAddress().equals(con2.getLocalAddress()));

         // test whether client connection was properly added to connection list
         assertTrue("client con was not added to server's list", sv1.getConnections().length == 2);
         assertTrue("client con was not added to server's list", sv1.getConnectionList().size() == 2);
         assertNotNull("false client con was added to server's list", sv1.getConnection(scon2.getUUID()));
         assertTrue("client con was not signalled as ADDED to listener", listen1.getAddedCounter() == 2);
         assertTrue("false client con was added to listener's list", listen1.getAddedCons().contains(scon2));

         // start a third client that will be rejected
         // add a new server-listener to server (test multiple listeners)
         acceptThread.setAnswerModus(REJECT_ANSWER);
         con3 = new Client();
         try {
            con3.connect(0, sv1.getSocketAddress());
            fail("exception expected: ConnectionRejectedException");
         } catch (Exception e) {
            assertTrue("false exception", e instanceof ConnectionRejectedException);
         }
         sleep(20);

         // test server listener condition
         scon3 = (ServerConnection)acceptThread.getSignalledCons().get(2);
         assertTrue("false client signalled", scon3.getRemoteAddress()
               .getPort() == con3.getLocalAddress().getPort());
         h = acceptThread.getAvailableCounter();
         assertTrue("false value client signalled counter: " + h, h == 3);
         assertTrue("signalled con was falsely started", acceptThread.getConnectedCounter() == 2);
         assertTrue("client con was falsely signalled as ADDED to listener", listen1.getAddedCounter() == 2);
         assertFalse("client con was falsely added to listener's ADDED list",
               listen1.getAddedCons().contains(scon3));

         // test server condition (unmodified)
         assertTrue("client con was added to server's list", sv1.getConnections().length == 2);
         assertTrue("client con was not added to server's list", sv1.getConnectionList().size() == 2);
         assertNull("client con was added to server's list", sv1.getConnection(scon3.getUUID()));
         assertTrue("acceptThread error counter > 0", acceptThread.getErrorCounter() == 0);

      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST ACCEPTING LISTEN: " + e);
      } finally {
         acceptThread.terminate();
         sv1.closeAndWait(2000);
      }
   }

   @Test
   public void remote_closing () {
      TestServer sv1 = null;

      try {
         sv1 = new TestServer(3000);

         TestServerListener listen1 = new TestServerListener();
         sv1.addListener(listen1);
         sv1.start();

         // create 3 connections to server
         ServerConnection scon1, scon2, scon3;
         Client con1, con2, con3;
         con1 = new Client();
         con2 = new Client();
         con3 = new Client();
         con1.connect(0, sv1.getSocketAddress());
         con2.connect(0, sv1.getSocketAddress());
         con3.connect(0, sv1.getSocketAddress());
         sleep(50);

         // confirm signalled connections
         assertTrue("signalled con was not actually started",
               listen1.getConnectedCounter() == 3);
         scon1 = (ServerConnection)listen1.getSignalledCons().get(0);
         assertTrue("false client signalled",
               scon1.getRemoteAddress().equals(con1.getLocalAddress()));
         assertTrue("server connection alive", scon1.isConnected());
         scon2 = (ServerConnection)listen1.getSignalledCons().get(1);
         assertTrue("false client signalled",
               scon2.getRemoteAddress().equals(con2.getLocalAddress()));
         assertTrue("server connection alive", scon2.isConnected());
         scon3 = (ServerConnection)listen1.getSignalledCons().get(2);
         assertTrue("false client signalled",
               scon3.getRemoteAddress().equals(con3.getLocalAddress()));
         assertTrue("server connection alive", scon3.isConnected());

         // confirm server connections
         assertTrue("server connections length failure", sv1.getConnections().length == 3);
         assertTrue("server connections length failure", sv1.getConnectionList().size() == 3);
         assertNotNull("server connection unavailable",
               sv1.getConnection(scon1.getUUID()));
         assertNotNull("server connection unavailable",
               sv1.getConnection(scon2.getUUID()));
         assertNotNull("server connection unavailable",
               sv1.getConnection(scon3.getUUID()));

         // close connections from client side
         con1.close();
         con2.close();
         con1.waitForClosed(5000);
         con2.waitForClosed(5000);
         sleep(500);

         // check server connections
         assertTrue("server connections length failure: " + sv1.getConnections().length, sv1.getConnections().length == 1);
         assertTrue("server connections length failure", sv1.getConnectionList().size() == 1);
         assertNull("server connection unavailable",
               sv1.getConnection(scon1.getUUID()));
         assertNull("server connection unavailable",
               sv1.getConnection(scon2.getUUID()));
         assertNotNull("server connection unavailable",
               sv1.getConnection(scon3.getUUID()));

         // check listener connections
         assertTrue("signalled connections list error",
               listen1.getValidCounter() == 1);
         assertTrue("error in number signalled connections",
               listen1.getAvailableCounter() == 3);
         assertTrue("error in number removed connections (listener)",
               listen1.getRemovedCounter() == 2);
         assertTrue("removed list does not contain connection", listen1
               .getRemovedCons().contains(scon1));
         assertTrue("removed list does not contain connection", listen1
               .getRemovedCons().contains(scon2));
         assertFalse("removed list does not contain connection", listen1
               .getRemovedCons().contains(scon3));

      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST REMOTE CLOSING: " + e);
      } finally {
         sv1.closeAllConnections();
         sv1.close();
         sleep(20);
      }
   }

   @Test
   public void connection_timeout () throws Exception {
      Server sv1 = new TestServer(3000);

      // prepare server to ignore incoming connections
      sv1.setSignalMethod(ServerSignalMethod.ACCEPT);
      sv1.getParameters().setConfirmTimeout(1000);
      sv1.start();

      // connection attempt to server (must fail with ConnectionRejectedException)
      Client con1 = new Client();
      try {
         con1.connect(0, sv1.getSocketAddress());
         fail("missing exception on connection timeout, 1");
      } catch (Exception e) {
         assertTrue("false exception thrown, 1",
               e instanceof ConnectionRejectedException);
      }

      // we can repeat this multiple times
      Client con2 = new Client();
      try {
         con2.connect(0, sv1.getSocketAddress());
         fail("missing exception on connection timeout, 2");
      } catch (Exception e) {
         assertTrue("false exception thrown, 2",
               e instanceof ConnectionRejectedException);
      }
      /*
       * try { // this must throw a Connection Closed exception ServerConnection
       * scon = sv1.accept(0); assertTrue("connection should be closed, 1",
       * scon.isClosed()); assertFalse("connection should be not connected, 1",
       * scon.isConnected()); scon.start();
       * fail("missing exception on closed connection start"); } catch
       * (Exception e) { assertTrue("false exception thrown, 2", e instanceof
       * ClosedConnectionException); }
       * 
       * try { // reject should be no-operation ServerConnection scon =
       * sv1.accept(0); assertTrue("connection should be closed, 2",
       * scon.isClosed()); assertFalse("connection should be not connected, 1",
       * scon.isConnected()); scon.reject(); } catch (Exception e) {
       * fail("falsely exception thrown on connection reject"); }
       */
      // TODO create new test case for above outcommented

   }

   private static void sleep (int millis) {
      Util.sleep(millis);
   }

   /**
    * Creates and returns a Client instance connected to the given server.
    * 
    * @param sv3 TestServer
    * @return Connection
    */
   private Connection createConnectedConnection (TestServer sv3) {
      Client con = new Client();
      try {
         con.connect(0, sv3.getSocketAddress());
         return con;
      } catch (IOException e) {
         e.printStackTrace();
         fail("cannot create connection to " + sv3.getSocketAddress());
         return null;
      }
   }

   @Test
   public void close_server () throws IOException, InterruptedException {
      Server sv1 = null;
      Client cl1 = null, cl2 = null;
      ServerConnection sc1 = null;

      try {
         sv1 = new TestServer(3490);
         assertFalse("server should not be closed before start", sv1.isClosed());

         TestServerListener listen1 = new TestServerListener();
         sv1.addListener(listen1);
         sv1.start();
         assertFalse("server should not be closed", sv1.isClosed());

         cl1 = new Client();
         cl1.connect(0, sv1.getSocketAddress());
         Util.sleep(50);

         assertTrue("server has not listed connection", sv1.getConnections().length == 1);
         assertTrue("server connections length failure", sv1.getConnectionList().size() == 1);
         sc1 = sv1.getConnections()[0];
         assertTrue("server connection is not connected", sc1.isConnected());
         assertTrue("illegal server closed event", listen1.getServerClosedCounter() == 0);

         // close the server and verify consequent state
         sv1.close();
         assertFalse("server falsely reported ALIVE", sv1.isAlive());
         // assertFalse("server falsely reported BOUND", sv1.isBound());
         assertTrue("server closed event missing",
               listen1.getServerClosedCounter() == 1);
         assertTrue("server lost connection after close", sv1.getConnections().length == 1);
         assertTrue("server connections length failure", sv1.getConnectionList().size() == 1);
         sc1 = sv1.getConnections()[0];
         assertFalse("server connection is falsely closed", sc1.isClosed());
         assertTrue("server connection has lost connected state",
               sc1.isConnected());

         // may not connect to closed server
         try {
            cl2 = new Client();
            cl2.connect(0, sv1.getSocketAddress());
            fail("missing exception: connecting to closed server");
         } catch (Exception e) {
            assertTrue("false exception thrown: " + e,
                  e instanceof ConnectException);
         }

         // close all connections in server closed state 
         sv1.closeAllConnections();
         sv1.waitForAllClosed(5000);
         assertTrue("server fails to close connection", sc1.isClosed());
         assertFalse("server fails to close connection", sc1.isConnected());
         assertTrue("server fails to remove connection", sv1.getConnections().length == 0);
         assertTrue("server connections length failure", sv1.getConnectionList().isEmpty());
         assertTrue("server failed to issue 'connection removed' event",
               listen1.getRemovedCounter() == 1);

      } finally {
    	  if (sv1 != null) {
    		  sv1.close();
    	  }
    	  if (cl1 != null) {
    		  cl1.close();
    	  }
      }
   }

   @Test
   public void close_all_connections () throws IOException, InterruptedException {
	   Server sv1 = null;
	   Client cl1 = null, cl2 = null, cl3 = null;
	
	   try {
	      sv1 = new TestServer(3490);
	      assertFalse("server should not be closed before start", sv1.isClosed());
	      
	      TestServerListener listen1 = new TestServerListener();
	      sv1.addListener(listen1);
	      sv1.start();
	      assertFalse("server should not be closed", sv1.isClosed());
	
	      cl1 = new Client();
	      cl1.connect(0, sv1.getSocketAddress());
	
	      cl2 = new Client();
	      cl2.connect(0, sv1.getSocketAddress());
	
	      cl3 = new Client();
	      cl3.connect(0, sv1.getSocketAddress());
	
	      Util.sleep(50);
	      UUID conId = sv1.getConnections()[1].getUUID(); 
	
	      // ante test
	      assertTrue("server has not listed connections", sv1.getConnections().length == 3);
	      assertTrue("server has not signalled listed connections, 1", listen1.getAddedCounter() == 3);
	      assertTrue("server has not signalled listed connections, 2", listen1.getValidCounter() == 3);
	      assertNotNull("server connection not found", sv1.getConnection(conId));
	
	      sv1.closeAllConnections();
	      sv1.waitForAllClosed(5000);
	      sleep(50);
	
	      // check server states after close-all
	      assertTrue("server went offline after close-all", sv1.isAlive());
	      assertFalse("server is closed after close-all", sv1.isClosed());
	      assertTrue("server has not listed connections", sv1.getConnections().length == 0);
	      assertTrue("server connections length failure", sv1.getConnectionList().isEmpty() );
	      assertNull("server connection not found", sv1.getConnection(conId));
	      
	      // check event signalling after closeAll
	      assertTrue("server has not signalled listed connections, 1", listen1.getRemovedCounter() == 3);
	      assertTrue("server has not signalled listed connections, 2", listen1.getValidCounter() == 0);
	      
	      // check clients are closed
	      sleep(500);
	      assertTrue("client not closed after close-all", cl1.isClosed());
	      assertTrue("client not closed after close-all", cl2.isClosed());
	      assertTrue("client not closed after close-all", cl3.isClosed());
	      
	      // may still connect to server
	      try {
	         cl2 = new Client();
	         cl2.connect(0, sv1.getSocketAddress());
	         Util.sleep(30);
	         
	         assertTrue("server has not listed connection", sv1.getConnections().length == 1);
	      } catch (Exception e) {
	         fail("cannot connect to server after close-all");
	      }
	
	   } finally {
		  if (sv1 != null) {
			  sv1.close();
		  }
		  if (cl1 != null) {
			  cl1.close();
		  }
		  if (cl2 != null) {
			  cl2.close();
		  }
		  if (cl3 != null) {
			  cl3.close();
		  }
	   }
   }
   
   @Test
   public void add_connection_listeners () throws IOException, InterruptedException {
	   Server sv1 = null;
	   Client cl1 = null, cl2 = null, cl3 = null;
	   Connection scon;
	
	   try {
	      sv1 = new TestServer(3491);
	      assertFalse("server should not be closed before start", sv1.isClosed());
	      
	      TestServerListener listen1 = new TestServerListener();
	      sv1.addListener(listen1);
	      sv1.start();
	      assertFalse("server should not be closed", sv1.isClosed());
	      assertTrue(sv1.getConnectionList().isEmpty());
	      assertTrue("initial non-empty connection-listeners", sv1.getConnectionListeners().isEmpty());
	      
	      cl1 = new Client();
	      cl1.connect(0, sv1.getSocketAddress());
	      Util.sleep(100);
	
	      assertTrue(sv1.getConnectionList().size() == 1);
	      assertTrue("initial non-empty connection-listeners", sv1.getConnectionListeners().isEmpty());
	      
	      // add two connection listeners to server
	      EventReporter reporter = new EventReporter();
	      sv1.addConnectionListener(reporter);
	      ObjectReceptionListener objectListener = new ObjectReceptionListener(Station.SERVER);
	      sv1.addConnectionListener(objectListener);

	      // test acceptance of listeners
	      Set<ConnectionListener> listeners = sv1.getConnectionListeners();
	      assertTrue("connection-listeners not accepted in server", listeners.size() == 2);
	      assertTrue(listeners.contains(reporter));
	      assertTrue(listeners.contains(objectListener));

	      // test listeners not in already existing connections
	      scon = sv1.getConnection(cl1.getLocalAddress());
	      assertNotNull(scon);
	      assertFalse(scon.getListeners().contains(reporter));
	      assertFalse(scon.getListeners().contains(objectListener));
	      
	      // create new connection
	      cl2 = new Client();
	      cl2.connect(0, sv1.getSocketAddress());
	      Util.sleep(100);
	      scon = sv1.getConnection(cl2.getLocalAddress());
	      assertNotNull(scon);
	
	      // test listeners contained in new connection
	      assertTrue(scon.getListeners().contains(reporter));
	      assertTrue(scon.getListeners().contains(objectListener));
	      
	      // to be sure, send an object
	      Integer in1 = 8927918;
	      cl2.sendObject(in1);
	      Util.sleep(50);
	      
	      List<ConnectionEvent> events = objectListener.getEvents(ConnectionEventType.OBJECT);
	      assertTrue(events.size() == 1);
	      Integer in2 = (Integer) events.get(0).getObject(); 
	      assertTrue("transmission failure", in2.equals(in1));
	      
	      // remove listener from server
	      sv1.removeConnectionListener(objectListener);
	      listeners = sv1.getConnectionListeners();
	      assertFalse(listeners.contains(objectListener));

	      // test no effect on already existing connections
	      assertTrue(scon.getListeners().contains(reporter));
	      assertTrue(scon.getListeners().contains(objectListener));
	   
	   } finally {
		  if (sv1 != null) {
			  sv1.closeAndWait(2000);
		  }
	   }
   }
}
