/*  File: SendPriority.java
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

/** A send-priority value which may be used as parameter for some
 * sending methods of the <code>Connection</code> interface.
 * Values are in ascending priority: BOTTOM, LOW, NORMAL,	HIGH, TOP;
 */
public enum SendPriority {
	BOTTOM, 
	LOW,
	NORMAL,
	HIGH,
	TOP;
	
	public static SendPriority valueOf (int ordinal) {
		SendPriority v;
		switch (ordinal) {
		   case 0 : v = SendPriority.BOTTOM; break;
		   case 1 : v = SendPriority.LOW; break;
		   case 2 : v = SendPriority.NORMAL; break;
		   case 3 : v = SendPriority.HIGH; break;
		   case 4 : v = SendPriority.TOP; break;
		   default: throw new IllegalArgumentException("undefined ordinal value: " + ordinal);
		   }
		   return v;
		}

	
}
