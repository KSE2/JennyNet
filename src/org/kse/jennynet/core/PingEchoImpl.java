/*  File: PingEchoImpl.java
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

import java.util.Date;
import java.util.Objects;

import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.PingEcho;
import org.kse.jennynet.util.Util;

/** Class to contain immutable information about a single PING-to-its-ECHO
 * relation concerning a specific connection (<code>IConnection</code>).
 *  
 */
final class PingEchoImpl implements PingEcho {
   
   private Connection connection;
   private long pingID;
   private long time_sent;
   private int duration;
   
   public static PingEcho create (Connection con, long pingId, long sendTime, int duration) {
      // validate
	  Objects.requireNonNull(con, "connection is null");
      if (pingId <= 0)
         throw new IllegalArgumentException("pingId <= 0");
      if (sendTime <= 0)
         throw new IllegalArgumentException("sendTime <= 0");
      if (duration < 0)
         throw new IllegalArgumentException("duration < 0");

      PingEchoImpl pe = new PingEchoImpl();
      pe.pingID = pingId;
      pe.connection = con;
      pe.time_sent = sendTime;
      pe.duration = duration;
      return pe;
}
   
   @Override
   public Connection getConnection () {
      return connection;
   }

   @Override
   public long time_sent () {
      return time_sent;
   }

   @Override
   public int duration () {
      return duration;
   }
   
   @Override
   public long pingId () {
      return pingID;
   }
   
   @Override
   public String toString () {
      String timestr = new Date(time_sent).toLocaleString();
      return Util.bytesToHex(connection.getShortId()) + " PING: " + pingID + 
             ", " + timestr + ", Duration = " + duration + " ms";
   }
}
