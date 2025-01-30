/*  File: JennyNetByteBuffer.java
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

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.kse.jennynet.util.CRC32;

/** 
 * Class for sending byte data over the net. This can be used to perform
 * transmissions of user defined object serialisations. 
 * 
 * <p>This class is by default globally registered for transmission over
 * the net. 
 *
 */
public class JennyNetByteBuffer implements Serializable {
   private static long serialVersionUID = 834209742878217L;
   
   protected byte[] data;
   protected transient int crc32;
   
   /** Creates a new byte buffer instance from a section of a given 
    * data buffer.
    * 
    * @param buffer byte[] data buffer
    * @param start int offset
    * @param length int data length
    * @throws IllegalArgumentException if data addressing is wrong
    */
   public JennyNetByteBuffer (byte[] buffer, int start, int length) {
      // validity testing
	  Objects.requireNonNull(buffer, "buffer is null");
      if (start < 0 | length < 0 | start+length > buffer.length)
         throw new IllegalArgumentException("illegal start/length setting for byte data");
      
      data = new byte[length];
      System.arraycopy(buffer, start, data, 0, length);
   }

   /** Creates a new byte buffer instance from the given data buffer.
    *  
    * @param buffer byte[] transfer data 
    */
   public JennyNetByteBuffer (byte[] buffer) {
	  Objects.requireNonNull(buffer, "buffer is null");
      data = buffer.clone();
   }

   /** Creates an byte buffer with an empty data array.
    */
   protected JennyNetByteBuffer () {
	   data = new byte[0];
   }
   
   /** Returns the stored data buffer.
    * 
    * @return byte[] data buffer
    */
   public byte[] getData () {
      return data;
   }
   
   public ByteBuffer getByteBuffer () {
	   return ByteBuffer.wrap(data);
   }
   
   /** Defines the content of this byte-buffer.
    * 
    * @param block byte[], may be null
    */
   public void setData (byte[] block) {
      data = block;
      crc32 = 0;
   }
   
   /** Returns the length of the stored data.
    * 
    * @return int data length
    */
   public int getLength () {
      return data == null ? 0 : data.length;
   }
   
   /** Returns a CRC32 value for the contained buffer data. Returns zero
    * if the data reference is void (null).
    * 
    * @return long CRC32 value or zero
    */
   public int getCRC () {
      if (data == null) return 0;
      if (crc32 == 0) {
         CRC32 crc = new CRC32();
         crc.update(data);
         crc32 = crc.getIntValue();
      }
      return crc32;
   }
}
