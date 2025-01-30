/*  File: SignalType.java
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

/** Enum listing the available SIGNAL types of the <i>JennyNet</i> layer.
 * <br>These signals serve communication between the two ends of a connection.
 */
enum SignalType {
   
   ALIVE,
   ALIVE_REQUEST,
   ALIVE_CONFIRM,
   TEMPO,
   CONFIRM,
   FAIL,
   BREAK,
   SHUTDOWN,
   CLOSED,
   PING,
   ECHO
;

   public static SignalType valueOf (int ordinal) {
      SignalType sp;
      switch (ordinal) {
      case 0 : sp = SignalType.ALIVE; break;
      case 1 : sp = SignalType.ALIVE_REQUEST; break;
      case 2 : sp = SignalType.ALIVE_CONFIRM; break;
      case 3 : sp = SignalType.TEMPO; break;
      case 4 : sp = SignalType.CONFIRM; break;
      case 5 : sp = SignalType.FAIL; break;
      case 6 : sp = SignalType.BREAK; break;
      case 7 : sp = SignalType.SHUTDOWN; break;
      case 8 : sp = SignalType.CLOSED; break;
      case 9 : sp = SignalType.PING; break;
      case 10: sp = SignalType.ECHO; break;
      default: throw new IllegalArgumentException("undefined ordinal value: " + ordinal);
      }
      return sp;
   }   

}
