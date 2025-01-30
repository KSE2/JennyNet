/*  File: TestServer.java
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerSignalMethod;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.intfa.TransmissionEventType;
import org.kse.jennynet.util.EventReporter;
    
   public class TestServer {

      static int clientCounter;
      static List<Connection> clientList = new ArrayList<>();
      static Timer timer = new Timer();
      
      public static void main(String[] args) throws IOException {
    
       if (args.length != 1) {
           System.err.println("Usage: <program> <port number>");
           System.exit(1);
       }
    
           JennyNet.getDefaultParameters().setFileRootDir(new File("test"));
          
//           ClientListener clistener = new ClientListener();
           EventReporter reporter = new EventReporter();
           int portNumber = Integer.parseInt(args[0]);
           boolean listening = true;
            
           try {
              Server server = new Server();
              server.bind(portNumber);
              server.setSignalMethod(ServerSignalMethod.ACCEPT);
              server.setAcceptQueueCapacity(500);
              server.start();
              
              while (listening) {
                 ServerConnection con = server.accept(0);
                 con.addListener(reporter);
                 con.getParameters().setAlivePeriod(0);
//                 con.addListener(clistener);
                 clientList.add(con);
                 con.start();
                 
                 System.out.println("--- CONNECTION ACCEPTED from : " + con.getRemoteAddress() );
                 con.sendPing();
                 
//                  new ServerConnectionThread(server.accept()).start();
              }
           } catch (IOException e) {
               System.err.println("Could not listen on port " + portNumber);
               System.out.println("terminating");
               System.exit(-1);
           } catch (InterruptedException e) {
               e.printStackTrace();
           }
       }

      public static class AbortFiletransferTask extends TimerTask {
         private Connection connection;
         private long fileId;
         
         /** A new file transfer cancel task.
          * 
          * @param c IConnection
          * @param fileId long file transfer
          * @param time int delay in seconds
          */
         public AbortFiletransferTask (Connection c, long fileId, int time) {
            connection = c;
            this.fileId = fileId;
            timer.schedule(this, time * 1000);
         }
         
         @Override
         public void run() {
            System.out.println("TIMER-TASK: Cancelling incoming file transfer : " 
                  + connection.getRemoteAddress() + " FILE = " + fileId);
            connection.breakTransfer(fileId, ComDirection.INCOMING);
         }
         
      }
      
      public static class ClientListener extends DefaultConnectionListener {

         @Override
         public void objectReceived(Connection connection, SendPriority priority, long objectNr, Object object) {
         }

         @SuppressWarnings("unused")
         @Override
         public void transmissionEventOccurred (TransmissionEvent event) {
            if (event.getType() == TransmissionEventType.FILE_INCOMING) {
               new AbortFiletransferTask(event.getConnection(), 
                     event.getObjectID(), 3);
            }
         }

         @Override
         public void closed(Connection connection, int info, String msg) {
            System.out.println("+++ Connection TERMINATED (" + connection.getUUID() + ") Address: " 
                          + connection.getRemoteAddress());
            if (info != 0) {
               System.out.print("    Reason = " + info + ",  ");
            }
            if (msg != null) {
               System.out.println("MSG = ".concat(msg));
            }
         }
         
      }
/*      
      public static class ServerConnectionThread extends Thread {
         private int id = ++clientCounter; 
         private Connection connection = null;
      
         public ServerConnectionThread(Connection con) {
             super("KKMultiServerThread");
             connection = con;
         }
          
         public void run() {
            InputStream socketInput;
            boolean closed = false;
            
            System.out.println("++++++ Connection +++++ (" + id + ") thread started: Address: " + socket.getRemoteSocketAddress() );
            
            connection.sendPing();
            connection.sendObject("Hello here is Testserver! What is your name?");

            try {
                 while (!closed) {
                    try {
                       // reads and reports all parcels sent from the client
//                       TransmissionParcel parcel = TransmissionParcel.readParcel(in);
//                       parcel.report(System.out);
                       
//                       if (parcel.getChannel() == TransmissionChannel.OBJECT) {
//                          Object obj = JennyNet.getGlobalSerialisation().deserialiseObject(parcel.getData());
//                          System.out.println(obj);
//                       }
                       
                    } catch (Exception e) {
                       closed = true;
                       if (!(e instanceof EOFException)) {
                          e.printStackTrace();
                       }
                    }
                 }
                 System.out.println("++ Connection TERMINATED (" + id + ") Address: " + socket.getRemoteSocketAddress() );
                 
             } catch (Exception e) {
                 System.out.println("++ Connection (" + id + ") EXCEPTION: " + e );
                 System.out.println();
                 e.printStackTrace();
             }
         }
     }
*/     
   }
   
   