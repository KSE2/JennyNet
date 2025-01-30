/*  File: TestUnit_Connection_Static.java
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;
import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.core.JennyNet.ThreadUsage;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.Connection.ConnectionState;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

public class TestUnit_Connection_Static {
   UUID randomUuid = UUID.randomUUID();
   
   static Server testServer;
   static int ListenerIdCounter;

   private static class TClient extends Client {

      public TClient() {
		 super();
	  }

      public int getListenerCount () {
         return getConListeners().length;
      }
   }
   
   private static class ConListener extends DefaultConnectionListener {
      int id = ++ListenerIdCounter;
      
      @Override
      public void objectReceived (Connection connection, SendPriority priority, long objectNr, Object object) {
         if (object instanceof String) {
            System.out.println("-- (L" + id + ") RECEIVED STRING == [" + (String)object + "]");
         }
      }
   }

   private static class SvListener extends DefaultServerListener {
      /** operation modus for answering incoming connections.
       * 0 = reject; 1 = accept.
       */
      int modus;
      ConnectionListener conListener = new ConListener();
      
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
               connection.addListener(conListener);
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
         // creates a test server with listener which starts connections
         testServer = new Server(new InetSocketAddress("localhost", 4000));
         testServer.start();
         testServer.addListener(new SvListener(1));
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
   
   /** Tests initial features of a connection.
    * 
    * @param con Connection
    */
   private void initial_feature_test (Connection con) {
      assertFalse("not in closed state", con.isClosed());
      assertFalse("in idle state", con.isIdle());
      assertFalse("not in transmitting state", con.isTransmitting());
      assertTrue("initial operation state", con.getOperationState() == ConnectionState.UNCONNECTED);
      assertNull("getName should be null", con.getName());
      assertTrue("equals itself", con.equals(con));
      assertNotNull("has UUID (initial)", con.getUUID());
      assertNotNull("has short-ID (initial)", con.getShortId());
      assertTrue("illegal short-ID length (initial)", con.getShortId().length == 4);
      assertNotNull("has parameters (initial)", con.getParameters());
      assertNotNull("has receive serialisation (initial)", con.getReceiveSerialization());
      assertNotNull("has send serialisation (initial)", con.getSendSerialization());
      int method = con.getParameters().getSerialisationMethod();
      assertTrue("method error in initial send-serialisation", con.getSendSerialization().getMethodID() == method); 
      assertTrue("unverified initial send-serialisation", con.getSendSerialization().getName() 
    		     == JennyNet.getDefaultSerialisation(method).getName());
      assertTrue("unverified initial receive-serialisation", con.getReceiveSerialization().getName() 
    		     == JennyNet.getDefaultSerialisation(method).getName());
   }

   private void test_can_set_parameter_values (ConnectionParameters par, boolean connected) {
      String errorMsg = "cannot set parameter value";
      
      try {
         int newBaseThreadPrio = par.getBaseThreadPriority() + 2;
         par.setBaseThreadPriority(newBaseThreadPrio);
         assertTrue(errorMsg, par.getBaseThreadPriority() == newBaseThreadPrio);
         
         int newTransmitThreadPrio = par.getTransmitThreadPriority() - 2;
         par.setTransmitThreadPriority(newTransmitThreadPrio);
         assertTrue(errorMsg, par.getTransmitThreadPriority() == newTransmitThreadPrio);
         
         int newTransmissionParcelSize = par.getTransmissionParcelSize() + 23000;
         par.setTransmissionParcelSize(newTransmissionParcelSize);
         assertTrue(errorMsg, par.getTransmissionParcelSize() == newTransmissionParcelSize);
         
         int newTransmissionSpeed = 23000;
         par.setTransmissionSpeed(newTransmissionSpeed);
         assertTrue(errorMsg, par.getTransmissionSpeed() == newTransmissionSpeed);
         
         int newConfirmTimeout = par.getConfirmTimeout() + 11034;
         par.setConfirmTimeout(newConfirmTimeout);
         assertTrue(errorMsg, par.getConfirmTimeout() == newConfirmTimeout);
         
         int newIdleThreshold = par.getIdleThreshold() + 100000;
         par.setIdleThreshold(newIdleThreshold);
         assertTrue(errorMsg, par.getIdleThreshold() == newIdleThreshold);
         
         int newIdleCheckPeriod = par.getIdleCheckPeriod() + 10001;
         par.setIdleCheckPeriod(newIdleCheckPeriod);
         assertTrue(errorMsg, par.getIdleCheckPeriod() == newIdleCheckPeriod);
         
         int newAlivePeriod = par.getAlivePeriod() + 30200; 
         par.setAlivePeriod(newAlivePeriod);
         assertTrue(errorMsg, par.getAlivePeriod() == newAlivePeriod);
         
         // identify unique new directory for fileRoot setting
         File newTransferRoot = JennyNet.getTempDirectory(); 
         if (newTransferRoot.equals(par.getFileRootDir())) {
        	 String name = Util.randString(12);
        	 newTransferRoot = new File(newTransferRoot, name);
        	 Files.createDirectory(newTransferRoot.toPath(), new FileAttribute<?>[0]);
         }
         assertTrue("failure: directory does not exist: " + newTransferRoot, newTransferRoot.isDirectory());
         assertFalse("cannot find new root directory", newTransferRoot.equals(par.getFileRootDir()));
         par.setFileRootDir(newTransferRoot);
         assertTrue(errorMsg, newTransferRoot.equals(par.getFileRootDir()));
         
         ThreadUsage newDeliveryUsage = par.getDeliveryThreadUsage() == ThreadUsage.GLOBAL ? 
        		 	ThreadUsage.INDIVIDUAL : ThreadUsage.GLOBAL; 
         par.setDeliveryThreadUsage(newDeliveryUsage);
         assertTrue(errorMsg, par.getDeliveryThreadUsage() == newDeliveryUsage);

         int newDeliveryTolerance = par.getDeliverTolerance() + 20450; 
         par.setDeliverTolerance(newDeliveryTolerance);
         assertTrue(errorMsg, par.getDeliverTolerance() == newDeliveryTolerance);
         
         int newMaxSerialiseSize = par.getMaxSerialisationSize() - 200000; 
         par.setMaxSerialisationSize(newMaxSerialiseSize);
         assertTrue(errorMsg, par.getMaxSerialisationSize() == newMaxSerialiseSize);
         
         par.setSerialisationMethod(2);
         assertTrue(errorMsg, par.getSerialisationMethod() == 2);
         par.setSerialisationMethod(1);
         assertTrue(errorMsg, par.getSerialisationMethod() == 1);
         par.setSerialisationMethod(0);
         assertTrue(errorMsg, par.getSerialisationMethod() == 0);
         
         if (!connected) {
            int newObjectQueueCapacity = par.getObjectQueueCapacity() + 155; 
            par.setObjectQueueCapacity(newObjectQueueCapacity);
            assertTrue(errorMsg, par.getObjectQueueCapacity() == newObjectQueueCapacity);
   
            int newParcelQueueCapacity = par.getParcelQueueCapacity() + 350; 
            par.setParcelQueueCapacity(newParcelQueueCapacity);
            assertTrue(errorMsg, par.getParcelQueueCapacity() == newParcelQueueCapacity);
         }
         
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception in CAN SET PARAMETER VALUES");
      }
   }
   
   private void test_parameters_con(Connection con, boolean connected) throws IOException {
	  // test connection has parameters and they can get cloned
      ConnectionParameters origPar = con.getParameters(); 
      assertNotNull("con has no parameters", origPar);
      ConnectionParameters par1 = (ConnectionParameters)origPar.clone();
      assertNotNull("parameters clone is null", par1);
      assertTrue("parameters clone is not equal-values", par1.equalValues(origPar));

      // test that we can set values in the parameter set
      test_can_set_parameter_values(origPar, connected);
      
      // clone is data-separate
      assertFalse("parameter-clone is not data separate", origPar.getIdleThreshold() 
                  == par1.getIdleThreshold());
      assertFalse("parameter-clone is not data separate", origPar.getTransmissionParcelSize() 
            == par1.getTransmissionParcelSize());

      // in UNCONNECTED state: test collective parameter setting (set mode)
      if (!connected) {
         con.setParameters(par1);
         ConnectionParameters par2 = con.getParameters(); 
         assertNotNull("parameters is null after setting", par2);
         assertFalse("parameter sets are equal (should not be)", par2 == par1);
      }
         
//         // new parameter values (all options) are set on connection
//         assertTrue("failed collective parameter setting", par2.equalValues(par1));
//         
//         assertTrue("failed collective parameter setting", par2.getAlivePeriod() 
//                 == par1.getAlivePeriod());
//         assertTrue("failed parameter set assignment", par2.getBaseThreadPriority() 
//               == par1.getBaseThreadPriority());
//         assertTrue("failed parameter set assignment", par2.getConfirmTimeout() 
//               == par1.getConfirmTimeout());
//         if (par1.getFileRootDir() != null) {
//            assertTrue("failed parameter set assignment", par1.getFileRootDir().equals 
//                  (par2.getFileRootDir()));
//         }
//         assertTrue("failed parameter set assignment", par2.getIdleThreshold() 
//               == par1.getIdleThreshold());
//         assertTrue("failed parameter set assignment", par2.getObjectQueueCapacity() 
//               == par1.getObjectQueueCapacity());
//         assertTrue("failed parameter set assignment", par2.getParcelQueueCapacity() 
//               == par1.getParcelQueueCapacity());
//         assertTrue("failed parameter set assignment", par2.getSerialisationMethod() 
//               == par1.getSerialisationMethod());
//         assertTrue("failed parameter set assignment", par2.getTransmissionParcelSize() 
//               == par1.getTransmissionParcelSize());
//         assertTrue("failed parameter set assignment", par2.getTransmitThreadPriority() 
//               == par1.getTransmitThreadPriority());
//         assertTrue("failed parameter set assignment", par2.getTransmissionSpeed() 
//                 == par1.getTransmissionSpeed());
   }
   
   @Test
   public void test_parameters () {
      Client cl1 = null;
      
      try {
         cl1 = new Client();
         initial_feature_test(cl1);
         test_parameters_con(cl1, false);
         
         cl1.connect(0, "localhost", 4000);
         test_parameters_con(cl1, true);

         cl1.close();
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST PARAMETERS EXCEPTION");
      }
   }
   
   
   @Test
   public void test_parameters_failure () throws IOException {
      Client cl1 = null;
      cl1 = new Client();
      
      try {
         cl1.setParameters(null);
         fail("fails to throw exception on null parameter");
      } catch (Exception e) {
         assertTrue("false exception thrown (expected: NullPointerException)", 
               e instanceof NullPointerException);
      }
      
      cl1.connect(0, "localhost", 4000);
      
      // cannot set parameters when connection established
      try {
         cl1.setParameters(JennyNet.getConnectionParameters());
         fail("fails to throw exception on setParameters() in CONNECTED state");
      } catch (Exception e) {
         assertTrue("false exception thrown (expected: IllegalStateException)", 
               e instanceof IllegalStateException);
      }
      
      // cannot set some single parameters when connection established
      try {
         cl1.getParameters().setObjectQueueCapacity(23);
         fail("fails to throw exception on setParameters()");
      } catch (Exception e) {
         assertTrue("false exception thrown (expected: IllegalStateException)", 
               e instanceof IllegalStateException);
      }
      
      try {
         cl1.getParameters().setParcelQueueCapacity(24);
         fail("fails to throw exception on setParameters()");
      } catch (Exception e) {
         assertTrue("false exception thrown (expected: IllegalStateException)", 
               e instanceof IllegalStateException);
      }
      
      try {
          cl1.getParameters().setSerialisationMethod(-1);
          fail("fails to throw IllegalArgumentException on setSerialisationMethod()");
       } catch (IllegalArgumentException e) {
       }
       
      try {
          cl1.getParameters().setSerialisationMethod(JennyNet.MAX_SERIAL_DEVICE);
          fail("fails to throw IllegalArgumentException on setSerialisationMethod()");
       } catch (IllegalArgumentException e) {
       }
       
      cl1.close();
   }

   @Test
   public void test_parameters_default () throws IOException {
	  ConnectionParameters par = JennyNet.getConnectionParameters();
	   
      try {
          assertTrue(par.getBaseThreadPriority() == JennyNet.DEFAULT_BASE_PRIORITY);
          assertTrue(par.getTransmitThreadPriority() == JennyNet.DEFAULT_TRANSMIT_PRIORITY);
          assertTrue(par.getTransmissionParcelSize() == JennyNet.DEFAULT_TRANSMISSION_PARCEL_SIZE);
          assertTrue(par.getTransmissionSpeed() == JennyNet.DEFAULT_TRANSMISSION_TEMPO);
          assertTrue(par.getConfirmTimeout() == JennyNet.DEFAULT_CONFIRM_TIMEOUT);
          assertTrue(par.getIdleThreshold() == 0);
          assertTrue(par.getIdleCheckPeriod() == JennyNet.DEFAULT_IDLE_CHECK_PERIOD);
          assertTrue(par.getAlivePeriod() == JennyNet.DEFAULT_ALIVE_PERIOD);
          assertTrue(par.getFileRootDir() == null);
          assertTrue(par.getDeliveryThreadUsage() == JennyNet.DEFAULT_THREAD_USAGE);
          assertTrue(par.getDeliverTolerance() == JennyNet.DEFAULT_DELIVER_TOLERANCE);
          assertTrue(par.getMaxSerialisationSize() == JennyNet.DEFAULT_MAX_SERIALISE_SIZE);
          assertTrue(par.getObjectQueueCapacity() == JennyNet.DEFAULT_QUEUE_CAPACITY);
          assertTrue(par.getParcelQueueCapacity() == JennyNet.DEFAULT_PARCEL_QUEUE_CAPACITY);
          assertTrue(par.getSerialisationMethod() == JennyNet.DEFAULT_SERIALISATION_METHOD);

       } catch (Exception e) {
          e.printStackTrace();
          fail("Exception in CAN SET PARAMETER VALUES");
       }
   }
   
   @Test
   public void test_serialisation_setting () throws IOException {
      Serialization sendSer1, sendSer2, recSer1, recSer2;
      Client cl1 = new Client();
      
     // test we have default serialisations after instantiation 
     sendSer1 = cl1.getSendSerialization();
     recSer1 = cl1.getReceiveSerialization();
     assertNotNull("con has no default send serialisation", sendSer1);
     assertNotNull("con has no default receive serialisation", recSer1);

     // test unavailable custom serialisation
     try {
    	 cl1.getSendSerialization(2);
    	 fail("expected SerialisationUnavailableException");
     } catch (SerialisationUnavailableException e) {
     }
     
     // test exception in illegal method argument 
     try {
    	 cl1.getSendSerialization(3);
    	 fail("expected IllegalArgumentException");
     } catch (IllegalArgumentException e) {
     }
     
     // test reaction to void custom serialisation
     ConnectionParameters par2 = JennyNet.getConnectionParameters();
     par2.setSerialisationMethod(2);
     cl1.setParameters(par2);
     try {
    	 cl1.getSendSerialization();
    	 fail("expected SerialisationUnavailableException");
     } catch (SerialisationUnavailableException e) {
     }
     try {
    	 cl1.getReceiveSerialization();
    	 fail("expected SerialisationUnavailableException");
     } catch (SerialisationUnavailableException e) {
     }
     
     // we also register some individual classes for serialisation
     cl1.getParameters().setSerialisationMethod(1);
     sendSer1 = cl1.getSendSerialization();
     sendSer2 = cl1.getReceiveSerialization();
     Class<?> clas = Client.class;
     sendSer1.registerClass(clas);
     sendSer2.registerClass(clas);
     assertTrue("class not registered: Client", sendSer1.isRegisteredClass(clas));
     assertTrue("class not registered: Client", sendSer2.isRegisteredClass(clas));
     
     // test we have persistent serialisation instances in connection
     sendSer1 = cl1.getSendSerialization();
     sendSer2 = cl1.getSendSerialization();
     recSer1 = cl1.getReceiveSerialization();
     recSer2 = cl1.getReceiveSerialization();
     assertTrue("serialisation not persistent (send)", sendSer1 == sendSer2);
     assertTrue("serialisation not persistent (receive)", recSer1 == recSer2);
     
     // test we can add some individual classes in connection
     clas = Server.class;
     sendSer1.registerClass(clas);
     sendSer2 = cl1.getSendSerialization();
     recSer2 = cl1.getReceiveSerialization();
     assertTrue("class not registered in con: Server", sendSer2.isRegisteredClass(clas));
     assertFalse("class falsely registered in con (receive): Server", recSer2.isRegisteredClass(clas));
     assertFalse("verification on registration", sendSer2.isRegisteredClass(Socket.class));
   }
   
   @Test
   public void test_listener_registry () {
      TClient cl1 = null, cl2 = null;
      cl1 = new TClient();
      cl2 = new TClient();

      try {

         assertTrue("false listener initial size", cl1.getListenerCount() == 0);
         assertTrue("false listener initial size", cl2.getListenerCount() == 0);
         
         // test we can add a single listener on many connections 
         ConnectionListener li1 = new ConListener();
         cl1.addListener(li1);
         cl2.addListener(li1);
         assertTrue("could not add connection listener (1)", cl1.getListenerCount() == 1);
         assertTrue("connection list entry failed (1)", cl1.getListeners().contains(li1));
         assertTrue("could not add connection listener (2)", cl2.getListenerCount() == 1);
         assertTrue("connection list entry failed (2)", cl2.getListeners().contains(li1));
         
         // test we can add more than one listener (and still keep what we've got)
         ConnectionListener li2 = new ConListener();
         cl1.addListener(li2);
         cl2.addListener(li2);
         assertTrue("could not add connection listener (12)", cl1.getListenerCount() == 2);
         assertTrue("connection list entry failed (12)", cl1.getListeners().contains(li2));
         assertTrue("connection list entry lost (1)", cl1.getListeners().contains(li1));
         assertTrue("could not add connection listener (22)", cl2.getListenerCount() == 2);
         assertTrue("connection list entry failed (22)", cl2.getListeners().contains(li2));
         assertTrue("connection list entry lost (2)", cl2.getListeners().contains(li1));
         
         // test we cannot add one listener more than once
         cl1.addListener(li1);
         assertTrue("falsely added one con-listener twice", cl1.getListenerCount() == 2);
         
         // test we can remove listeners correctly
         cl1.removeListener(li1);
         cl2.removeListener(li1);
         assertTrue("could not remove connection listener (1)", cl1.getListenerCount() == 1);
         assertTrue("could not remove connection listener (2)", cl2.getListenerCount() == 1);
         assertTrue("connection listener false entry removed (1)", cl1.getListeners().contains(li2));
         assertTrue("connection listener false entry removed (2)", cl2.getListeners().contains(li2));
         
         // test we can remove more than once (no-operation)
         cl1.removeListener(li1);
         assertTrue("false list condition after second remove attempt", cl1.getListenerCount() == 1);
         
         // neutral null test
         cl1.removeListener(null);
         assertTrue("false list condition after null remove", cl1.getListenerCount() == 1);
         cl1.addListener(null);
         assertTrue("false list condition after null adding", cl1.getListenerCount() == 1);
         
         
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST LISTENERS EXCEPTION");
      } finally {
         cl1.close();
         cl2.close();
      }
   }
   
   @Test
   public void test_minors () throws InterruptedException {
      Client cl1 = null, cl2 = null;
      ConnectionListener conListener = new EventReporter();
      cl1 = new Client();
      cl1.addListener(conListener);
      cl2 = new Client();

      UUID uuid = cl1.getUUID();
      byte[] shortID = cl1.getShortId();
      
      try {
         // initial minors
         assertFalse("not in closed state", cl1.isClosed());
         assertFalse("not in connected state", cl1.isConnected());
         assertFalse("in idle state", cl1.isIdle());
         assertFalse("not in transmitting state", cl1.isTransmitting());
         assertTrue("initial operation state", cl1.getOperationState() == ConnectionState.UNCONNECTED);
         assertNull("getName should be null", cl1.getName());
         assertNotNull("has UUID (initial)", cl1.getUUID());
         assertNotNull("has short-ID (initial)", cl1.getShortId());
         assertTrue("illegal short-ID length (initial)", cl1.getShortId().length == 4);
         assertNotNull("has Properties", cl1.getProperties());
         assertNull("has no local address (initial)", cl1.getLocalAddress());
         assertNull("has no remote address (initial)", cl1.getRemoteAddress());
         assertTrue("false initial transmission speed", cl1.getTransmissionSpeed() == -1);
         assertTrue("equals itself", cl1.equals(cl1));
         assertTrue("equals another null-defined connection", cl1.equals(cl2));
         assertTrue("hascode identical with another null-defined connection",  
                    cl1.hashCode() == cl2.hashCode()); 
         assertTrue("toString() makes a value", cl1.toString().length() > 10);

    	 // close unbound
         cl1.close();
         assertTrue("in closed state", cl1.isClosed());
         assertFalse("not in connected state", cl1.isConnected());
         assertFalse("in idle state", cl1.isIdle());
         assertFalse("not in transmitting state", cl1.isTransmitting());
         assertTrue("operation state CLOSED", cl1.getOperationState() == ConnectionState.CLOSED);

         // test after binding
         cl1 = new Client();
         cl1.addListener(conListener);
         cl1.bind(0);
         cl1.setUUID(uuid);
         assertFalse("not in closed state", cl1.isClosed());
         assertFalse("not in connected state", cl1.isConnected());
         assertFalse("in idle state", cl1.isIdle());
         assertTrue("false operation state", cl1.getOperationState() == ConnectionState.UNCONNECTED);
         assertNotNull("has local address (initial)", cl1.getLocalAddress());
         assertNull("has no remote address (initial)", cl1.getRemoteAddress());
         assertTrue("equals itself", cl1.equals(cl1));
         assertFalse("not equals another null-defined connection", cl1.equals(cl2));
         assertFalse("hascode not identical with null-defined connection",  
               cl1.hashCode() == cl2.hashCode()); 
         
         // test after connecting
         cl1.connect(0, "localhost", 4000);
         cl2.connect(0, "localhost", 4000);
         assertFalse("not in closed state", cl1.isClosed());
         assertFalse("not in closed state", cl2.isClosed());
         assertTrue("in connected state", cl1.isConnected());
         assertTrue("in connected state", cl2.isConnected());
         assertFalse("in idle state", cl1.isIdle());
         assertFalse("in idle state", cl2.isIdle());
         assertTrue("false operation state", cl1.getOperationState() == ConnectionState.CONNECTED);
         assertTrue("false operation state", cl2.getOperationState() == ConnectionState.CONNECTED);
         assertFalse("not in transmitting state", cl1.isTransmitting());
         assertNull("getName should be null", cl1.getName());
         assertNotNull("has UUID (initial)", cl1.getUUID());
         assertNotNull("has short-ID (initial)", cl1.getShortId());
         assertTrue("illegal short-ID length (initial)", cl1.getShortId().length == 4);
         assertNotNull("has Properties", cl1.getProperties());
         assertTrue("false transmission speed", cl1.getTransmissionSpeed() == -1);
         assertNotNull("has local address (initial)", cl1.getLocalAddress());
         assertNotNull("has remote address (initial)", cl1.getRemoteAddress());
         assertTrue("equals itself", cl1.equals(cl1));
         assertFalse("not equals another connection", cl1.equals(cl2));
         assertFalse("hascode not identical with connection",  
               cl1.hashCode() == cl2.hashCode()); 

         // separation tests
         assertFalse("has different Properties instance", cl1.getProperties() == cl2.getProperties());
         assertFalse("has different UUID", cl1.getUUID().equals(cl2.getUUID())); 
         assertFalse("has different Short-ID", Util.equalArrays(cl1.getShortId(), cl2.getShortId())); 
         assertFalse("has different local address", cl1.getLocalAddress().equals(cl2.getLocalAddress())); 
         assertTrue("has same remote address", cl1.getRemoteAddress().equals(cl2.getRemoteAddress())); 
         assertFalse("has different toString rendering", cl1.toString().equals(cl2.toString())); 

         // test set name
         String name = "Ohuwabohu";
         cl1.setName(name);
         cl2.setName("Oxenheimer");
         assertTrue("remembers name setting", name.equals(cl1.getName()));
         
         // test Properties
         cl1.getProperties().setProperty("key1", "Hello");
         cl2.getProperties().setProperty("key1", "NoNo");
         assertTrue("Properties remembers key-value pair", cl1.getProperties().
               getProperty("key1").equals("Hello"));
         assertTrue("Properties remembers key-value pair", cl2.getProperties().
               getProperty("key1").equals("NoNo"));
         
         // send action
         long timeNow = System.currentTimeMillis();
         cl1.sendObject("Hello Other End!");
         cl2.sendObject("My Fair Lady");
         Util.sleep(100);
         
         // test after sending
         long lastSendTime = cl1.getMonitor().lastSendTime;
         assertFalse("not in closed state", cl1.isClosed());
         assertTrue("in connected state", cl1.isConnected());
         assertFalse("in idle state", cl1.isIdle());
         assertTrue("in transmitting state", cl1.isTransmitting());
         assertTrue("false operation state", cl1.getOperationState() == ConnectionState.CONNECTED);
         assertTrue("has last-send-time", lastSendTime > 0);
         assertTrue("credible last-send time", lastSendTime - timeNow <= 100);
         assertTrue("has no last-receive-time", cl1.getMonitor().lastReceiveTime == 0);

         cl1.close();
         cl2.close();
         System.out.println(".. waiting for disconnect");
         cl1.waitForDisconnect(5000);
         cl2.waitForDisconnect(5000);
         Util.sleep(100);
         
         // test after closing
         assertTrue("in closed state", cl1.isClosed());
         assertFalse("still in connected state", cl1.isConnected());
         assertFalse("in idle state", cl1.isIdle());
         assertFalse("not in transmitting state", cl1.isTransmitting());
         assertTrue("false operation state", cl1.getOperationState() == ConnectionState.CLOSED);
         assertTrue("has last-send-time", cl1.getMonitor().lastSendTime > 0);
         assertTrue("has last-receive-time", cl1.getMonitor().lastReceiveTime != 0);
         
         assertTrue("name persists close", cl1.getName().equals(name));
         assertNotNull("has UUID (closed)", cl1.getUUID());
         assertTrue("same UUID (closed)", cl1.getUUID().equals(uuid));
         assertNotNull("has short-ID (closed)", cl1.getShortId());
         assertTrue("same short-ID (closed)", Util.equalArrays(cl1.getShortId(), shortID));
         assertNotNull("has Properties", cl1.getProperties());
         assertNotNull("has local address (closed)", cl1.getLocalAddress());
         assertNotNull("has remote address (closed)", cl1.getRemoteAddress());
         assertTrue("equals itself", cl1.equals(cl1));
         assertFalse("not equals another closed connection", cl1.equals(cl2));
         assertFalse("hashcode not identical with another closed connection",  
                    cl1.hashCode() == cl2.hashCode()); 
         assertTrue("toString() makes a value (closed)", cl1.toString().length() > 10);
         
      } catch (Exception e) {
         e.printStackTrace();
         fail("TEST MINORS EXCEPTION");
      } finally {
         cl1.closeAndWait(2000);
         cl2.closeAndWait(2000);
      }
   }
   
}
