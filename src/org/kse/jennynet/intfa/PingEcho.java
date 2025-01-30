/*  File: PingEcho.java
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

package org.kse.jennynet.intfa;

/**
 * Interface for a PING-ECHO. A PING-ECHO is released in a connection event 
 * to <code>ConnectionListener</code> implementations.
 * It gives details about the connection involved, when the PING was started 
 * and its run-time (complete run) in milliseconds. The PING-ID makes a PING
 * traceable after sending. The ID name-space is unique for each connection
 * and separate from OBJECT-IDs. 
 * 
 * @see ConnectionListener
 * @see Connection
 */

public interface PingEcho {

   /** The connection involved in the PING / PING-ECHO action.
    * 
    * @return <code>Connection</code>
    */
   Connection getConnection();

   /** Returns the time point when the PING was sent.
    * 
    * @return long "epoch" time value in milliseconds
    */
   long time_sent();

   /** Returns the time (duration) of the complete PING run from the 
    * time-point of sending to the time-point of reception of the ECHO 
    * in the sending layer.
    * 
    * @return int PING run time in milliseconds
    */
   int duration();

   /** The PING-ID number for this PING-ECHO.
    * <p>PING IDs are relative to their connections and cover a name-space
    * separate from OBJECT-IDs.
    * 
    * @return long PING-Id
    */
   long pingId();

   /**
    * Human readable representation of this PING-ECHO detailing its basic 
    * facts, including a short-ID of the connection involved.
    * 
    * @return String
    */
   @Override
   String toString();

}