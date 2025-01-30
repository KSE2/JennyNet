/*  File: TransmissionChannel.java
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

/** The basic transmission channel for parcel sending, which can be 
 * SIGNAL, OBJECT, FILE, BLIND.
 * The channels are priorised for transmission on the network in the given
 * order with lower ordinal values sent first.
 */

enum TransmissionChannel {
   SIGNAL,
   OBJECT,
   FILE,
   FINAL
;

static TransmissionChannel valueOf (int ordinal) {
   TransmissionChannel sp;
   switch (ordinal) {
   case 0 : sp = TransmissionChannel.SIGNAL; break;
   case 1 : sp = TransmissionChannel.OBJECT; break;
   case 2 : sp = TransmissionChannel.FILE; break;
   case 3 : sp = TransmissionChannel.FINAL; break;
   default: throw new IllegalArgumentException("undefined ordinal value: " + ordinal);
   }
   return sp;
}
}

