/*  File: CRC32.java
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

/** A 32-bit CRC value based on java.util.zip.Adler32.
 */
public class CRC32 extends java.util.zip.Adler32 {
   
   public CRC32() {
      super();
   }

   /** Update one byte.
    * @param b byte value
    */
   public void update (byte b) {
      super.update(b & 0xFF);
   }

   /** Update one integer value (4 bytes).
    * @param b int (all bytes used)
    */
   @Override
   public void update (int b) {
       update((byte)((b >>> 24) & 0xFF));
       update((byte)((b >>> 16) & 0xFF));
       update((byte)((b >>> 8) & 0xFF));
       update((byte)(b & 0xFF));
   }

   /** Update one long value (8 bytes).
    * @param b long (all bytes used) 
    */
   public void update (long b) {
       update((int)((b >>> 32) & 0xFFFFFFFF));
       update((int)((b & 0xFFFFFFFF)));
   }

//  ******** RETURNS *************

   /** Returns a 4-byte array of the checksum value. 
    * @return byte[4]
    */
   public byte[] getByteArray() {
       long val = getValue();
       return new byte[] {
        (byte)((val>>24) & 0xff),
        (byte)((val>>16) & 0xff),
        (byte)((val>>8) & 0xff),
        (byte)(val & 0xff) };
   }

   /**
    * Returns the value of the checksum as an integer.
    * @return int
    */
   public int getIntValue() {
       return (int)getValue();
   }

   /** Returns a hexadecimal representation of the 4-byte checksum value.
    * @return String 8 hexadecimal characters
    */
   @Override
   public String toString() {
       return Util.bytesToHex(getByteArray());
   }

// ******** STATIC FUNCTIONS *************

   /** Returns an array of 4 bytes containing the CRC32 over
    * parameter data buffer.
    *  
    * @param buffer byte[] input data
    * @return byte[] CRC32 (4 bytes)
    */
   public static byte[] bytesToCrc32 (byte[] buffer) {
      CRC32 crc = new CRC32();
      crc.update(buffer);
      return crc.getByteArray();
   }
}
