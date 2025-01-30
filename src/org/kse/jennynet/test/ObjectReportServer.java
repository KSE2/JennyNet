/*  File: ObjectReportServer.java
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

import java.io.IOException;
import java.net.SocketAddress;

import org.kse.jennynet.core.DefaultConnectionListener;
import org.kse.jennynet.core.DefaultServerListener;
import org.kse.jennynet.core.Server;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.TransmissionEvent;

public class ObjectReportServer extends Server {

   
   public ObjectReportServer() throws IOException {
   }

   public ObjectReportServer(SocketAddress address) throws IOException {
      super(address);
   }

   public ObjectReportServer(int port) throws IOException {
      super(port);
   }


//  ************ INNER CLASSES *************
   
private static class SvListener extends DefaultServerListener {
   ConnectionListener conListener = new ServerConListener();
   
   public SvListener () {
   }
   
   @Override
   public void connectionAvailable (IServer server, ServerConnection connection) {
      try {
         connection.addListener(conListener);
         connection.start();
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



private static class ServerConListener extends DefaultConnectionListener {

   @Override
   public void objectReceived (Connection connection, SendPriority priority, long objectNr, Object object) {
      if (object instanceof String) {
         System.out.println("-- RECEIVED STRING == [" + (String)object + "]");
      }
   }

   @Override
   public void closed (Connection connection, int cause, String message) {
   }

   @Override
   public void transmissionEventOccurred (TransmissionEvent event) {
   }

}

   
   
}
