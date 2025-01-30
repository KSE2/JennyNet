/*
*  File: PlainReflectServer.java
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** Java.net testing tool implementing a server which reflects
 * incoming data transmissions.
 * 
 */
public class PlainReflectServer extends Thread {


   protected ServerSocket server;
   protected List<Socket> clientList = new ArrayList<Socket>();
   protected int clientCounter;
   protected boolean listening = true;
   

   public PlainReflectServer () throws IOException {
      this(0);
   }   
   
   public PlainReflectServer (int port) throws IOException {
      server = new ServerSocket(port);
      System.out.println("PlainReflectServer started at = " + server.getLocalSocketAddress());
   }

   public void run () {

      try {
         System.out.println("PlainReflectServer -- listening at " + server.getLocalSocketAddress());
         
         while (listening) {
            Socket con = server.accept();
            
            System.out.println("--- CONNECTION ACCEPTED from : " + con.getRemoteSocketAddress());
            clientList.add(con);
            ServerTask task = new ServerTask(con);
            task.start();
            
         }
      } catch (IOException e) {
         if (!listening) {
            System.out.println("*** PLAIN-REFELECT-SERVER Terminated (" + server.getLocalPort() + ")");
         } else {
            System.err.println("*** PlainReflectServer : ERROR listening to PORT " + server.getLocalPort());
            e.printStackTrace();
         }
      }
  }
  
   public int getPort () {
      return server == null ? -1 : server.getLocalPort();
   }
   
   public void terminate () {
      listening = false;
      try {
         server.close();
      } catch (IOException e) {
      }
   }
   
   public static void main(String[] args) throws IOException {
      int port = args.length == 0 ? 0 : Integer.parseInt(args[0]);
      new PlainReflectServer( port ).start();
   }

   
   private static class ServerTask extends Thread {
      Socket socket;
      
      public ServerTask (Socket con) {
         socket = con;
      }

      @Override
      public void run () {
         OutputStream out;
         InputStream in;
         
         try {
            // open output socket
            out = socket.getOutputStream();
            in = socket.getInputStream();
            byte[] buffer = new byte[2024];
            int rlen, dataLen = 0;
            
            // reading / writing
            while ( (rlen = in.read(buffer)) > -1 ) {
               if (rlen > 0) {
                  out.write(buffer, 0, rlen);
                  out.flush();
                  dataLen += rlen;
               }
            }
            
            // report
            System.out.println("--- ReflectServer ServerTask completed: " + socket.getRemoteSocketAddress()
                  + ", data length = " + dataLen);
            
         } catch (Exception e) {
            System.err.println("*** PLAIN-REFELECT-SERVER TRANSMISSION ERROR ***");
            System.err.println("*** Remote Address: " + socket.getRemoteSocketAddress());
            e.printStackTrace();
            
         } finally {
            try {
               // close client socket and input file
               socket.close();
               System.out.println("--- Reflect-Server CONNECTION CLOSED to : " + socket.getRemoteSocketAddress());
            } catch (IOException e1) {
               System.err.println("*** TASK TERMINATION ERROR");
               e1.printStackTrace();
            }
         }
   }
   }
}

