/*
*  File: ReflectServer.java
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

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerSignalMethod;
import org.kse.jennynet.intfa.TransmissionEvent;
import org.kse.jennynet.util.EventReporter;

/** JennyNet testing tool for a server reflecting objects sent by a client.
 * 
 */
public class ReflectServer {
   public static final int BUILD_VERSION = 4;

   int serverPort;
   int hours = 1;
   String rootPath;
   Thread activity;

   public ReflectServer (String[] args) {
      
      if (setup_parameters (args)) {
         try {
            activity = new Thread(new SimpleReflectServer(serverPort, hours*JennyNet.HOUR, rootPath));
            activity.start();
         } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);         
         }
      }
   }
   
   private boolean setup_parameters (String[] args) {
      if (args.length < 1) {
         printUsage("Missing arguments. ");
         return false;
      }

      try { 
         serverPort = Integer.parseInt(args[0]);
      } catch (Exception e) {
         printUsage("Illegal characters in argument 0; must be numeric!");
         return false;
      }
      
      if (args.length > 1) {
         try { 
            hours = Integer.parseInt(args[1]);
         } catch (Exception e) {
            printUsage("Illegal characters in argument 1; must be numeric!");
            return false;
         }
      }

      if (args.length > 2) {
         try {
            String path = args[2];
            if (new File(path).isDirectory()) {
               rootPath = path;
            } else {
               printUsage("directory not found: ".concat(path));
               return false;
            }
         } catch (Exception e) {
            printUsage("Illegal characters in argument 2; must be a valid file path!");
            return false;
         }
      }
      return true;
   }

   /**
    * @param args
    */
   @SuppressWarnings("unused")
public static void main (String[] args) {
      
      new ReflectServer(args);
      
   }

   /** Prints a parameter usage declaration together with an optional
    * error message.
    * 
    * @param errorMessage Message to print before usage; (null to ignore)
    */
   private static void printUsage (String errorMessage) {
      String outputMessage = "Usage Parameters: <serverport> [<hours> [<root-directory>]] ";

      if (errorMessage != null) {
         // log.info("Error: " + errorMessage);
         outputMessage = "JennyNet ReflectTest *** Error: " + errorMessage + "\n\n" 
                         + outputMessage;
      }

      System.out.println(outputMessage);
   }

//  **************  INNER CLASSES  ***********
   
public static class SimpleReflectServer implements Runnable {
   Server server = new Server();
   ConnectionListener connectionListener = new SimpleConnectionListener();
   ConnectionParameters params = JennyNet.getConnectionParameters();
   int duration = 30*60*1000;
   String rootPath;
   
   public SimpleReflectServer () throws IOException {
      server.setSignalMethod(ServerSignalMethod.LISTENER);
      server.setName("SIMPLE_REFLECT_SERVER");
      defineParameters();
      server.setParameters(params);
      
      server.addListener(new EventReporter());
      server.addListener(new DefaultServerListener() {

         @Override
         public void serverClosed (IServer server) {
            System.out.println("Server " + server.getName() + " has been closed");
         }
         
         @Override
         public void connectionAvailable (IServer server, ServerConnection con) {
            con.addListener(connectionListener);
            con.setName("Simple Reflect Connection");
            try {
               con.addListener(new EventReporter());
               con.start();
               con.sendPing();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
      });
   }

   private void defineParameters () throws IOException {
	   params.setTransmissionParcelSize(32*1024);
	   File dir = new File("test/reflect/server");
	   dir.mkdirs();
	   params.setFileRootDir(dir);
}

/**
    * 
    * @param port int port number
    * @param duration int milliseconds of server ALIVE time
    * @param rootPath String the (existing) root directory for incoming file transmissions
    * @throws IOException if binding the port fails
    */
   public SimpleReflectServer (int port, int duration, String rootPath) throws IOException {
      this();
      this.duration = duration;
      this.rootPath = rootPath;
//      this.duration = 6000;
      server.bind(port);
      server.setName(server.getName() + " (" + port + ")");
      if (rootPath != null) {
         JennyNet.getDefaultParameters().setFileRootDir(new File(rootPath));
      }
   }
   
   @Override
   public void run () {
	  long finis = System.currentTimeMillis() + duration;
	  String hstr = new Date(finis).toLocaleString();
      System.out.println(server.getName() + " Build " + BUILD_VERSION + " started operating until " + hstr);
      server.start();
      try {
         Thread.sleep(duration);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      server.close();
      System.exit(0);
   }
   
   private class SimpleConnectionListener extends DefaultConnectionListener {

      @Override
      public void connected (Connection con) {
         System.out.println(con.toString().concat(" has been connected"));
      }

      @Override
      public void closed (Connection con, int info, String message) {
         String explain =  "reason : info=" + info + 
               (message != null ? ", ".concat(message) : "");
         System.out.println(con.toString() + " has been disconnected \n" + explain);
      }

      @Override
      public void objectReceived (Connection con, SendPriority priority, long objectNr, Object object) {
         String msg = "-- object received from " + con.getRemoteAddress()  
               + "\nID=" + objectNr + ", type=" + object.getClass().getSimpleName();
         System.out.println(msg);
         if (object instanceof String) {
             System.out.println((String)object);
         }
         
         try { 
            con.sendObject(object); 
            msg = "++ object reflected";
         } catch (Throwable e) {
            msg = "-- unable to reflect object; ERROR = ".concat(e.toString());
         }
         System.out.println(msg);
      }

      @Override
      public void pingEchoReceived (PingEcho echo) {
         Connection con = echo.getConnection();
         String msg = "-- PING-ECHO received from " + con.getRemoteAddress()  
               + ", ser=" + echo.pingId() + ", ms-total=" + echo.duration();
               
         System.out.println(msg);
      }

      @Override
      public void transmissionEventOccurred (TransmissionEvent evt) {
         String msg = "-- transmission event (ignored)";
         Connection con = evt.getConnection();
         
         switch (evt.getType()) {
         case FILE_INCOMING:
            msg = "-- file transfer incoming: obj=" + evt.getObjectID() +
                  ", length=" + evt.getExpectedLength() + ", buffer=" +
                  evt.getFile();
            break;
         case FILE_RECEIVED: 
            msg = "-- file received: obj=" + evt.getObjectID() +
                  ", length=" + evt.getTransmissionLength() + ", duration=" +
                  evt.getDuration() + "\n   storage=" + evt.getFile();
            try {
               if (evt.getPriority() != SendPriority.BOTTOM) {	
            	  con.sendFile(evt.getFile(), evt.getPath());
            	  msg = msg.concat("\n+++ started file reflection to " + con.getRemoteAddress());
               }
            } catch (IOException e1) {
               msg = msg.concat("\n** unable to reflect file, reason: " + e1);
            }
            break;
         case FILE_ABORTED:
            Throwable e = evt.getException();
            msg = "*** file transfer aborted: file = " + evt.getObjectID() +
            ", info " + evt.getInfo() + ", exception: " + e;
            break;
         case FILE_CONFIRMED:
        	 msg = "-- file reflection completed and confirmed: " + evt.getFile() 
        	       + "\n   duration: " + evt.getDuration();
        	 break;
         }
         System.out.println(msg);
      }
   }

}

}
