/*
*  File: SendServerA.java
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

package org.kse.jennynet.appl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import org.kse.jennynet.util.Util;
    
/** Java.net testing tool for a server-socket.
 * 
 */
   public class SendServerA {

   static List<Socket> clientList = new ArrayList<>();
   static int clientCounter;
   static Timer timer = new Timer();
   
   public static void main(String[] args) throws IOException {
    
       if (args.length != 2) {
           System.err.println("Usage: <program> <port number> <send-file>");
           System.exit(1);
       }
    
       File sendFile = new File(args[1]);
       if (!sendFile.isFile()) {
          System.err.println("*** cannot find send-file: ".concat(sendFile.getAbsolutePath()));
          System.out.println("terminating");
          System.exit(1);
       }

       ServerSocket server = null;
       int portNumber = Integer.parseInt(args[0]);
       boolean listening = true;

           try {
              server = new ServerSocket(portNumber);
              System.out.println("SEND-SERVER-TEST A, local = " + server.getLocalSocketAddress());
              
              while (listening) {
                 System.out.println("waiting for client connection ..");
                 Socket con = server.accept();
                 
                 System.out.println("--- CONNECTION ACCEPTED from : " + con.getRemoteSocketAddress());
                 clientList.add(con);
                 ServerTask task = new FileTransferTask(con, sendFile);
                 task.start();
                 
              }
              
           } catch (IOException e) {
               System.err.println("*** ERROR listening to PORT " + portNumber);
               System.exit(-1);
           } finally {
        	   if (server != null) {
        		   server.close();
        	   }
               System.out.println("terminating");
           }
       }

   private static class ServerTask extends Thread {
   }
   
   private static class FileTransferTask extends ServerTask {

      Socket socket;
      File sendFile;
      
      public FileTransferTask (Socket con, File file) {
         sendFile = file;
         socket = con;
      }

      @Override
      public void run () {
         BufferedInputStream in = null;
         OutputStream out;
         try {
            // open source file input stream
            in = new BufferedInputStream(new FileInputStream(sendFile), 1024*16);
            // open output socket
            out = socket.getOutputStream();
            
            // sending data (transmission)
            System.out.println("--- SENDING FILE to : " + socket.getRemoteSocketAddress() + 
                  "\n    file = " + sendFile.getAbsolutePath() +
                  "\n    length = " + sendFile.length());
            long startTime = System.currentTimeMillis();
            Util.transferData(in, out, 1024*4);
            long duration = System.currentTimeMillis() - startTime;
            
            // report
            System.out.println("--- TRANSMISSION COMPLETED to : " + socket.getRemoteSocketAddress() +
                  "\n    duration = " + duration);
            
         } catch (IOException e) {
            System.out.println("*** TRANSMISSION ERROR");
            e.printStackTrace();
            
         } finally {
            try {
               // close client socket and input file
               socket.close();
               System.out.println("--- CONNECTION CLOSED to : " + socket.getRemoteSocketAddress());
               if (in != null) {
                  in.close();
               }
            } catch (IOException e1) {
               System.out.println("*** TASK TERMINATION ERROR\n" + e1);
               e1.printStackTrace();
            }
         }
         
      }
      
      
   }
/*   
      public static class AbortFiletransferTask extends TimerTask {
         private Connection connection;
         private long fileId;
         
         /** A new file transfer cancel task.
          * 
         * @param c IConnection
          * @param fileId long file transfer
          * @param time int delay in seconds
          
         public AbortFiletransferTask (Connection c, long fileId, int time) {
            connection = c;
            this.fileId = fileId;
            timer.schedule(this, time * 1000);
         }
         
         @Override
         public void run() {
            System.out.println("TIMER-TASK: Cancelling incoming file transfer : " 
                  + connection.getRemoteAddress() + " FILE = " + fileId);
            connection.breakTransfer(fileId, 0);
         }
         
      }     
*/
}
   
   