/*  File: MutableBoolean.java
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

import java.io.Serializable;

/** A boolean wrapping class which allows to modify the value.
 */
public class MutableBoolean implements Serializable {
	private static final long serialVersionUID = -24440972875667L;
	private boolean value;

	/** Creates a boolean wrapper instance with initial value <b>false</b>.
	 */
	public MutableBoolean () {
	}

	/** Creates a boolean wrapper instance with the given initial value.
	 * 
	 * @param initial boolean
	 */
	public MutableBoolean (boolean initial) {
		value = initial;
	}

	public boolean getValue () {
		return value;
	}
	
	public void setValue (boolean v) {
		value = v;
	}
	
}
