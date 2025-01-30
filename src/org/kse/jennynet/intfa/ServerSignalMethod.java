/*  File: ServerSignalMethod.java
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

/** Identifies a {@code Server} method for signalling incoming connection 
 * requests from remote <i>JennyNet</i> transport layers to the user 
 * application: LISTENER or ACCEPT.
 */
public enum ServerSignalMethod {
	
	/** Application listens to events issued by the server. */
	LISTENER, 

	/** Application polls actively for the next connection request. */
	ACCEPT;
}