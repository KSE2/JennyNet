/*  File: IO_Manager.java
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.intfa.ComDirection;

/** Singleton class to manage potential file access problems by exclusion.
 * The class reserves path-strings of files in order to avoid conflicting
 * access. It discriminates writing and reading access.
 */

public class IO_Manager {

	private static IO_Manager instance = new IO_Manager();
	
	public static IO_Manager get () {return instance;}
	
	private Map<String, String> outMap = new HashMap<>(); 
	private Map<String, Integer> inMap = new HashMap<>(); 
	
	private IO_Manager () {
	}

	/** Registers the given file for a communication direction
	 * (reading or writing) and creates a reservation for it if required.
	 * The canonical form of the argument is used.
	 * <p>Parameter <i>direction</i> is used to determine whether reading
	 * (incoming) or writing (outgoing) use of the file is requested.
	 * 
	 * @param file File file definition
	 * @param direction {@code ComDirection} reading (incoming) or writing
	 *                  (outgoing)
	 * @return boolean true = entry was accepted, false = entry is denied 
	 * @throws IOException
	 */
	public synchronized boolean enterActiveFile (File file, ComDirection direction) throws IOException {
		Objects.requireNonNull(direction, "direction is null");
		String entry = file.getCanonicalPath();
		boolean ok;
		
		// writing direction
		if (direction == ComDirection.OUTGOING) {
			ok = !inMap.containsKey(entry) && !outMap.containsKey(entry);
			if (ok) {
				outMap.put(entry, entry);
				if (JennyNet.debug) {
					System.out.println("-- (IO_Manager) entering WRITE protection for " + entry);
				}
			} else {
				if (JennyNet.debug) {
					System.out.println("-- (IO_Manager) WRITE protection DENIED for " + entry);
				}
			}

	    // reading direction
		} else {
			ok = !outMap.containsKey(entry);
			if (ok) {
				Integer i = inMap.get(entry);
				if (i == null) {
					i = 0;
				}
				i++;
				inMap.put(entry, i);
				if (JennyNet.debug) {
					System.out.println("-- (IO_Manager) entering READ protection for " + entry);
				}
			} else {
				if (JennyNet.debug) {
					System.out.println("-- (IO_Manager) READ protection DENIED for " + entry);
				}
			}
		}
		return ok;
	}
	
	/** Enters the given filepath as occupied (reserved) in this
	 * IO_Manager and returns whether it is not already contained. 
	 * The canonical form of the argument is used.
	 * 
	 * @param path String filepath
	 * @param direction {@code ComDirection} reading (incoming) or writing
	 *                  (outgoing)
	 * @return boolean true = entry was accepted, false = entry is denied 
	 * @throws IOException
	 */
	public boolean enterActiveFile (String path, ComDirection direction) throws IOException {
		return enterActiveFile(new File(path), direction);
	}
	
	/** Whether the given file definition is accessible for the given 
	 * IO-direction, according to the active-file-registry.
	 * The canonical form of the argument is used.
	 * 
	 * @param file File file to test
	 * @param direction {@code ComDirection} reading (incoming) or writing
	 *                  (outgoing)
	 * @return boolean true = accessible, false = occupied in IO
	 * @throws IOException
	 */
	public synchronized boolean canAccessFile (File file, ComDirection direction) throws IOException {
		Objects.requireNonNull(direction, "direction is null");
		boolean ok;
		String entry = file.getCanonicalPath();
		
		if (direction == ComDirection.OUTGOING) {
			ok = !inMap.containsKey(entry) && !outMap.containsKey(entry);
		} else {
			ok = !outMap.containsKey(entry);
		}
		return ok;
	}
	

	/** Whether the file named by the given filepath is accessible for the 
	 * given IO-direction, according to the active-file-registry.
	 * The canonical form of the argument is used.
	 * 
	 * @param path String filepath
	 * @param direction {@code ComDirection} reading (incoming) or writing
	 *                  (outgoing)
	 * @return boolean true = occupied, false = free
	 * @throws IOException
	 */
	public boolean canAccessFile (String path, ComDirection direction) throws IOException {
		return canAccessFile(new File(path), direction);
	}
	
	/** Removes the given file from registration in this IO-Manager
	 * for the given communication direction. 
	 * The canonical form of the argument is used.
	 * 
	 * @param file File
	 * @param direction {@code ComDirection} reading (incoming) or writing
	 *                  (outgoing)
	 * @return boolean true = entry removed, false = entry not found
	 * @throws IOException
	 */
	public synchronized boolean removeActiveFile (File file, ComDirection direction) throws IOException {
		Objects.requireNonNull(direction, "direction is null");
		String entry = file.getCanonicalPath();
		boolean done = false;
		
		if (direction == ComDirection.OUTGOING) {
			done = outMap.remove(entry) != null;
			if (JennyNet.debug & done) {
				System.out.println("-- (IO_Manager) removing WRITE protection for " + entry);
			}
			
		} else {
			Integer i = inMap.get(entry);
			if (i != null) {
				i--;
				if (i == 0) {
					inMap.remove(entry);
				} else {
					inMap.put(entry, i);
				}
				done = true;
				if (JennyNet.debug) {
					System.out.println("-- (IO_Manager) removing READ protection for " + entry);
				}
			}
		}
		return done;
	}
	
	/** Removes the given filepath from registration in this IO-Manager
	 * for the given communication direction. 
	 * The canonical form of the argument is used.
	 * 
	 * @param path String filepath
	 * @param direction {@code ComDirection} reading (incoming) or writing
	 *                  (outgoing)
	 * @return boolean true = entry removed, false = entry not found
	 * @throws IOException
	 */
	public boolean removeActiveFile (String path, ComDirection direction) throws IOException {
		return removeActiveFile(new File(path), direction);
	}
	
	
}
