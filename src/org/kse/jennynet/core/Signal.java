/*  File: Signal.java
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

import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.util.Util;

class Signal extends TransmissionParcel {
   private SignalType sigType;
   private int info;
   private String text;
   
   public Signal (ConnectionImpl con, SignalType s, long objectID, int info, String text) {
      super(con, s, objectID, info, text);
      this.sigType = s;
      this.info = info;
      this.text = text;
   }

   public Signal (ConnectionImpl con, SignalType s, long objectID) {
      this(con, s, objectID, 0, null);
   }
   
   /** Creates a Signal from a signal parcel.
    * 
    * @param parcel TransmissionParcel
    * @throws IllegalArgumentException if argument is not in channel SIGNAL
    */
   public Signal (TransmissionParcel parcel) {
      super(parcel);
      
      if (parcel.getChannel() != TransmissionChannel.SIGNAL)
         throw new IllegalArgumentException("parcel not in SIGNAL channel");
    
      int serialNr = getParcelSequencelNr();
      this.sigType = SignalType.valueOf(serialNr & 0xFFFF);
      byte[] data = getData();
      this.text = data.length == 0 ? null : 
    	          data.length == 4 ? null : new String(data, 4, data.length-4, JennyNet.getCodingCharset());
      this.info = data.length == 0 ? 0 : Util.readInt(data, 0); 
   }

   public SignalType getSigType() {
      return sigType;
   }

   public int getInfo() {
      return info;
   }

   public String getText() {
      return text;
   }
   
   /** Creates a new BREAK transmission signal for a transmission object
    * and a reason.
    *   
    * @param con {@code ConnectionImpl}
    * @param objectID long ID of object which is broken
    * @param info int signal error value
    * @param text String cause for the break (may be null)
    * @return {@code Signal}
    */
   public static Signal newBreakSignal (ConnectionImpl con, long objectID, int info, String text) {
	  Signal s = new Signal(con, SignalType.BREAK, objectID, info, text);
	  s.setPriority(SendPriority.HIGH);
      return s;
   }
   
   public static Signal newPingSignal (ConnectionImpl con, long pingID) {
	  Signal s = new Signal(con, SignalType.PING, pingID);
	  s.setPriority(SendPriority.TOP);
      return s;
   }
   
   public static Signal newEchoSignal (ConnectionImpl con, long pingID) {
	  Signal s = new Signal(con, SignalType.ECHO, pingID);
	  s.setPriority(SendPriority.TOP);
      return s;
   }

   public static Signal newAliveSignal (ConnectionImpl con) {
	  Signal s = new Signal(con, SignalType.ALIVE, 0);
	  s.setPriority(SendPriority.BOTTOM);
      return s;
   }
   
   public static Signal newAliveRequestSignal (ConnectionImpl con, int period) {
	  Signal s = new Signal(con, SignalType.ALIVE_REQUEST, 0, period, null);
	  s.setPriority(SendPriority.BOTTOM);
      return s;
   }
   
   public static Signal newAliveConfirmSignal (ConnectionImpl con, int period) {
	  Signal s = new Signal(con, SignalType.ALIVE_CONFIRM, 0, period, null);
	  s.setPriority(SendPriority.BOTTOM);
      return s;
   }
   
   public static Signal newTempoSignal (ConnectionImpl con, int baud) {
	  Signal s = new Signal(con, SignalType.TEMPO, 0, baud, null);
	  s.setPriority(SendPriority.LOW);
      return s;
   }
   
   public static Signal newConfirmSignal (ConnectionImpl con, long objectID) {
      return new Signal(con, SignalType.CONFIRM, objectID);
   }
   
   public static Signal newClosedSignal (ConnectionImpl con, int info, String msg) {
      return new Signal(con, SignalType.CLOSED, 0, info, msg);
   }
	   
   public static Signal newFailSignal (ConnectionImpl con, long objectID, int info, String msg) {
      return new Signal(con, SignalType.FAIL, objectID, info, msg);
   }
   
   public static Signal newShutdownSignal (ConnectionImpl con, int info, String msg) {
	  return new Signal(con, SignalType.SHUTDOWN, 0, info, msg);
   }


   
}
