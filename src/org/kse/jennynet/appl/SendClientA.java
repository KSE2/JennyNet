/*
*  File: SendClientA.java
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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.kse.jennynet.util.Util;
    
/** Java.net testing tool for a client connection.
 * 
 */
   public class SendClientA {

//   static Timer timer = new Timer();
   
   public static void main(String[] args) throws IOException {
    
       if (args.length != 3) {
           System.err.println("Usage: <program> <server> <port number> <file>");
           System.err.println("       server = target (server) IP address\n       port number = " +
                 "target (server) port\n       file = receive-file");
           System.exit(1);
       }
    
       String serverName = args[0];
       int portNumber = Integer.parseInt(args[1]);
       File outFile = new File(args[2]).getAbsoluteFile();
       File rootDir = outFile.getParentFile();
       if (!rootDir.isDirectory()) {
          System.err.println("*** root-directory does not exist: ".concat(rootDir.getAbsolutePath()));
          System.exit(1);
       }

        try {
           // create client socket
           Socket client = new Socket();
           
           InetSocketAddress serverAddress = new InetSocketAddress(serverName, portNumber);
           client.connect(serverAddress, 5000);
           System.out.println("SEND-CLIENT-TEST A (receive a file), local = " + client.getLocalSocketAddress());
           System.out.println("--- CONNECTED TO SERVER : " + client.getRemoteSocketAddress());

           BufferedInputStream in = null;
           OutputStream out = null;
           
           try {
              // open input stream on client socket
              in = new BufferedInputStream(client.getInputStream(), 1024*16);
              
              // open output for file
              out = new BufferedOutputStream(new FileOutputStream(outFile), 1024*16);
              
              // receiving data (transmission)
              long startTime = System.currentTimeMillis();
              System.out.println("--- RECEIVING DATA from : " + client.getRemoteSocketAddress() + 
                    "\n    file = " + outFile.getAbsolutePath());
              Util.transferData(in, out, 1024*8);
              long duration = System.currentTimeMillis() - startTime;
              
              out.close();
              long flen = outFile.length();
              
              // report
              System.out.println("--- FILE RECEIVED (COMPLETED) from : " + client.getRemoteSocketAddress() +
                    "\n    duration = " + duration + ",  file-length = " + flen);
              
           } catch (IOException e) {
              System.out.println("*** TRANSMISSION ERROR");
              e.printStackTrace();
              
           } finally {
              try {
                 // close client socket and input file
                 client.close();
                 System.out.println("--- CONNECTION CLOSED to : " + client.getRemoteSocketAddress());
              } catch (IOException e1) {
                 System.out.println("*** TASK TERMINATION ERROR\n" + e1);
                 e1.printStackTrace();
              }
              System.out.println("terminating");
           }
           

        } catch (Exception e) {
            System.err.println("*** SOCKET CONNECTION ERROR to PORT " + portNumber);
            e.printStackTrace();
            System.out.println("terminating");
            System.exit(-1);
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
   
   