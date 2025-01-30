/*  File: DefaultServerListener.java
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

import java.io.IOException;

import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.IServer;
import org.kse.jennynet.intfa.ServerConnection;
import org.kse.jennynet.intfa.ServerListener;

/** A <code>ServerListener</code> which immediately starts all incoming 
 * connections and otherwise does nothing.
 */
public class DefaultServerListener implements ServerListener {

   @Override
   public void connectionAvailable (IServer server, ServerConnection connection) {
	   try {
		   connection.start();
	   } catch (IOException e) {
		   e.printStackTrace();
	   }
   }

   @Override
   public void connectionAdded (IServer server, Connection connection) {
   }

   @Override
   public void connectionRemoved (IServer server, Connection connection) {
   }

   @Override
   public void serverClosed (IServer server) {
   }

   @Override
   public void errorOccurred (IServer server, Connection con, int transAction, Throwable e) {
   }

}
