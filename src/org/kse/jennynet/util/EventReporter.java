/*  File: EventReporter.java
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

package org.kse.jennynet.util;

import java.io.File;
import java.io.PrintStream;
import java.util.Objects;

import org.kse.jennynet.core.JennyNetByteBuffer;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerListener;
import org.kse.jennynet.intfa.TransmissionEvent;

/** This reporter class issues short text output on the console for the
 * methods called in interfaces {@code ConnectionListener} and 
 * {@code ServerListener}. It can be added as listener to a {@code Connection}
 * or a {@code Server} and will document events occurring..
 */
public class EventReporter implements ConnectionListener, ServerListener {

   private final PrintStream out;
   
   /** Creates an event reporter which writes to the <i>System.out</i> 
    * print stream.
    */
   public EventReporter () {
	   out = System.out;
   }
   
   /** Creates an event reporter which writes to the given print stream.
    * 
    * @param output {@code PrintStream}
    */
   public EventReporter (PrintStream output) {
	  Objects.requireNonNull(output);
      out = output;
   }
   
   private void thisStatement (Connection con) {
      String idstr = Util.bytesToHex(con.getShortId());
      String contype = con instanceof ServerConnection ? "SV " : "CL ";
      out.println("    THIS-" + contype + ": " + idstr + ", " + con.getLocalAddress());
   }
   
   @Override
   public void connected (Connection con) {
      out.println("+++ Event: CONNECTED TO: " + con.getRemoteAddress() + 
            "  ");
      thisStatement(con);
   }

   @Override
   public void closed (Connection con, int cause, String message) {
      out.println("+++ Event: CONNECTION CLOSED: " + con.getRemoteAddress() + 
            ", Reason = " + cause);
      if (message != null) {
         out.println("    MSG: ".concat(message));
      }
      thisStatement(con);
   }

	@Override
	public void shutdown (Connection con, int cause, String message) {
	    out.println("+++ Event: CONNECTION SHUTDOWN, Info = " + cause);
	      if (message != null) {
	         out.println("    MSG: ".concat(message));
	      }
	      thisStatement(con);
		
	}
	
   @Override
   public void idleChanged (Connection con, boolean idle, int exchange) {
      out.println("+++ Event: NEW IDLE STATE (" + (idle ? "IDLE" : "BUSY") 
    		  + "), baud = " + exchange + " : " + con.getRemoteAddress());
      thisStatement(con);
   }

   @Override
   public void objectReceived (Connection con, SendPriority priority, long objectNr, Object object) {
      out.println("+++ Event: O B J E C T (" + objectNr + ") Priority " + priority + ", RECEIVED FROM: " + con.getRemoteAddress());
      thisStatement(con);
      out.println("    Class: " + object.getClass());
      if (object instanceof String) {
         out.println("    \"" + (String)object + "\"");
      }
      if (object instanceof JennyNetByteBuffer) {
         JennyNetByteBuffer buffer = (JennyNetByteBuffer)object;
         byte[] trunk = new byte[Math.min(buffer.getLength(), 500)];
         System.arraycopy(buffer.getData(), 0, trunk, 0, trunk.length);
         out.println("    JENNY-BUFFER: " + Util.bytesToHex(trunk) );
         out.println("    received block CRC = " + buffer.getCRC());
      }
   }

   @Override
   public void objectAborted (Connection con, long objectNr, Object object, int info, String msg) {
      out.println("+++ Event: OBJECT ABORTED (" + objectNr + ") RECEIVED FROM: " + con.getRemoteAddress() + 
              	", cause = " + info);
      if (msg != null) {
    	  out.println("    msg = " + msg);
      }
      out.println("    Class: " + object.getClass());
      thisStatement(con);
   }

   @Override
   public void transmissionEventOccurred(TransmissionEvent event) {
      File file = event.getFile();
      SendPriority priority = event.getPriority();
      String path = event.getPath();
      long objectNr = event.getObjectID();
      long expectedLength = event.getExpectedLength();
      long transmitLength = event.getTransmissionLength();
      long duration = event.getDuration();
      int info = event.getInfo();
      
      out.println("+++ Event: TRANSMISSION EVENT OCCURRED, object = " + objectNr + ", remote = " +
            event.getConnection().getRemoteAddress());
      out.println("    TYPE = " + event.getType() + "  (info " + info + ")");
      thisStatement(event.getConnection());

      switch (event.getType()) {
  	  case FILE_SENDING:
          out.println("--- STARTING OUTGOING FILE (" + objectNr + "), destination = " + path);
          out.println("    File = " + file);
          out.println("    Priority " + priority + ", size = " + expectedLength);
		break;
		
      case FILE_INCOMING:
         out.println("--- FILE TRANSFER INCOMING (" + objectNr + "), path = " + path);
         out.println("    storing file = " + file);
         out.println("    Priority " + priority + ", size = " + expectedLength);
         break;

      case FILE_RECEIVED:
         out.println("--- FILE RECEIVED (" + objectNr + "), path = " + path);
         out.println("    Priority " + priority + ", size = " + expectedLength + ", duration=" + duration);
         out.println("    File = " + file);
         if (transmitLength < expectedLength) {
        	 out.println("    ** WARNING ** received file length does not match definition");
         }
         out.println("    local file length is: " + (file.exists() ? file.length() : "- NIL -"));
         break;
         
      case FILE_CONFIRMED:
         out.println("--- FILE TRANSFER CONFIRMED (" + objectNr + "), path = " + path + ", duration=" + duration);
         if ( file != null ) {
            out.println("    File = " + file);
            out.println("    local file length is: " + (file.exists() ? file.length() : "- NIL -"));
         }
         break;
         
      case FILE_ABORTED:
         out.println("--- FILE TRANSFER ABORTED (" + objectNr + "), info = " + info + ", path = " + path);
         out.println("    Priority " + priority + ", size = " + expectedLength);
         if ( file != null ) {
            out.println("    File = " + file);
            out.println("    local file length is: " + (file.exists() ? file.length() : "- NIL -"));
         }
         if (event.getException() != null) {
       	  	 out.println("    Error = " + event.getException());
         }
         break;
         
	default:
		break;
      }

   }

   @Override
   public void pingEchoReceived (PingEcho pingEcho) {
      Connection con = pingEcho.getConnection();
      out.println("+++ Event: PING-ECHO RECEIVED FROM: " + con.getRemoteAddress() + "  ");
      thisStatement(con);
      out.println("    ECHO: ".concat(pingEcho.toString()));
   }

// ------------  SERVER - LISTENER  -----------------
   
@Override
public void connectionAvailable (IServer server, ServerConnection con) {
	out.println("++ Server " + server.getSocketAddress() + ":  NEW CONNECTION AVAILABLE = " + con.getRemoteAddress());
}

@Override
public void connectionAdded (IServer server, Connection con) {
	out.println("++ Server " + server.getSocketAddress() + ":  CONNECTION REGISTERED = " + con.getRemoteAddress());
}

@Override
public void connectionRemoved(IServer server, Connection con) {
	out.println("++ Server " + server.getSocketAddress() + ":  CONNECTION REMOVED = " + con.getRemoteAddress());
}

@Override
public void serverClosed (IServer server) {
	out.println("++ Server " + server.getSocketAddress() + ":  IS CLOSED");
}

@Override
public void errorOccurred (IServer server, Connection con, int transAction, Throwable e) {
	out.println("++ Server " + server.getSocketAddress() + ":  TRANSACTION ERROR (" + transAction + "), remote = " +  con.getRemoteAddress());
    out.println("       Throwable: " + e);	
}

}
