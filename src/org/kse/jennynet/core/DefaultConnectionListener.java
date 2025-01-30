/*  File: DefaultConnectionListener.java
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

package org.kse.jennynet.core;

import org.kse.jennynet.core.ConnectionImpl.ErrorObject;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.ConnectionListener;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEvent;

/**
 * A default implementation of the <code>ConnectionListener</code> interface.
 * Methods of this class do nothing except 'objectAborted()' which reacts
 * to conditions 207 and 209 (serialisation errors) with connection closure
 * code 11. By overwriting the application has an option to react differently
 * to this kind of transmission errors. 
 */
public class DefaultConnectionListener implements ConnectionListener {

   @Override
   public void connected(Connection connection) {
   }

   @Override
   public void closed(Connection connection, int cause, String message) {
   }

   @Override
   public void shutdown(Connection connection, int cause, String message) {
   }

   @Override
   public void idleChanged(Connection connection, boolean idle, int exchange) {
   }

   @Override
   public void objectReceived(Connection connection, SendPriority priority, 
		   long objectNr, Object object) {
   }

   @Override
   public void transmissionEventOccurred(TransmissionEvent event) {
   }

   @Override
   public void pingEchoReceived(PingEcho pingEcho) {
   }

   @Override
   public void objectAborted(Connection connection, long objectNr, Object object, 
		   	int info, String msg) {
	   if (info == 207 | info == 209) {
		   ((ConnectionImpl)connection).closeShutdown(new ErrorObject(11, "serialisation error " + info));
	   }
   }

}
