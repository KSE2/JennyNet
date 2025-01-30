/*  File: TransmissionParcel.java
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.kse.jennynet.exception.BadTransmissionParcelException;
import org.kse.jennynet.exception.StreamOutOfSyncException;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.util.CRC32;
import org.kse.jennynet.util.SchedulableTimerTask;
import org.kse.jennynet.util.Util;

/** The atomic network transmission unit of the <i>JennyNet</i> network layer.
 * Incoming and outgoing data-streams and all communication signals rely on 
 * this class as exchange unit. 
 * <p>NOTE: {@code Signal} is a subclass of {@code TransmissionParcel}.
 */

class TransmissionParcel extends JennyNetByteBuffer implements Comparable<TransmissionParcel> {
   
   public static final int PARCEL_MARK = JennyNet.PARCEL_MARKER;
   
   /** Returns an array of transmission parcels of the OBJECT channel 
    * converted from object serialisation data. It is assumed that 
    * serialisation data is created by the current send-serialisation method
    * of the connection.
    * 
    * @param con {@code ConnectionImpl} connection on which parcel is received
    * @param serObj byte[] serialisation data of an object (complete)
    * @param objectNr int object ID
    * @param priority sending priority within the channel
    * @param transmissionParcelSize int data segment size transmittable in 
    *        a single parcel
    * @return {@code TransmissionParcel[]} 
    */
   public static TransmissionParcel[] createParcelArray (
		 ConnectionImpl con,
         byte[] serObj, 
         long objectNr, 
         int method,
         SendPriority priority,
         int transmissionParcelSize ) {

      // calculate number of parcels
      int dataLen = serObj.length;
      int parcelSize = transmissionParcelSize;
      int nrOfParcels = dataLen / parcelSize;
      int lastBit = dataLen % parcelSize; 
      if (lastBit > 0) nrOfParcels++;
      
      // create a list of parcels
      List<TransmissionParcel> list = new ArrayList<>();
      for (int i = 0; i < nrOfParcels; i++) {
         int segmentSize = i < nrOfParcels-1 ? transmissionParcelSize : lastBit;
         TransmissionParcel p = new TransmissionParcel(con, objectNr, i, 
               serObj, i*transmissionParcelSize, segmentSize);
         p.setPriority(priority);
         list.add(p);
      }

      // set object header values on first transmission parcel
      ObjectHeader header = list.get(0).getObjectHeader();
      header.setSerialisationMethod(method);
      header.setTransmissionSize(serObj.length);
      header.setNrOfParcels(nrOfParcels);
      header.setPriority(priority);
      
      // return collected parcels as array
      return list.toArray(new TransmissionParcel[list.size()]);
   }

   // parcel header data
   private ObjectHeader header;
   private TransmissionChannel channel;
   private SendPriority priority = SendPriority.NORMAL;
   private SchedulableTimerTask timerTask;
   private long objectID;
   private int sequencelNr;
   
   // connection reference
   private ConnectionImpl connection;
   
   
   /** Creates a new transmission parcel for the OBJECT channel with the 
    * given data buffer and header information. (For other channels use
    * the <code>setChannel()</code> method.)  
    * This fully defines the parcel.
    * 
    * @param con {@code ConnectionImpl} connection on which parcel is sent
    * @param objectNr int the transmission object number
    * @param parcelNr int the parcel serial number
    * @param buffer byte[] object data segment
    * @param start int data start offset in buffer
    * @param length int data length in buffer 
    */
   public TransmissionParcel (ConnectionImpl con,
		   					  long objectNr, 
		   					  int parcelNr, 
		   					  byte[] buffer, 
		   					  int start, 
		   					  int length) {
      super(buffer, start, length);
      Objects.requireNonNull(con, "connection is null");

      connection = con;
      objectID = objectNr;
      sequencelNr = parcelNr;
      channel = TransmissionChannel.OBJECT;
      
      // parcel 0 has extended header information
      if (parcelNr == 0) {
         header = new ObjectHeader(objectNr);
      }
   }

   
   /** Creates a new transmission parcel for the OBJECT channel 
    * with the given data buffer and header information. 
    * This fully defines the parcel.
    * 
    * @param con {@code ConnectionImpl} connection on which parcel is sent
    * @param objectNr int the transmission object number
    * @param parcelNr int the parcel serial number
    * @param buffer byte[] object data segment
    */
   public TransmissionParcel (ConnectionImpl con, long objectNr, int parcelNr, byte[] buffer) {
      this(con, objectNr, parcelNr, buffer, 0, buffer.length);
   }
   
   /** Creates a transmission parcel for a transmission signal (channel SIGNAL).
    * 
    * @param con {@code ConnectionImpl} connection on which parcel is sent
    * @param signal SignalType type of signal
    * @param object long referenced transmission object or 0
    * @param info int signal operational info
    * @param text String signal associated text (human readable additional info
    *         e.g. a cause for issuing the signal)
    */
   public TransmissionParcel (ConnectionImpl con, SignalType signal, long object, int info, String text) {
	  Objects.requireNonNull(con, "connection is null");
	  connection = con;
      channel = TransmissionChannel.SIGNAL;
      objectID = object;
      sequencelNr = signal.ordinal();
      
  	  byte[] textData = text == null ? null : text.getBytes(JennyNet.getCodingCharset());
  	  if (textData != null || info != 0) {
  	  	  int dataLen = (textData == null ? 0 : textData.length) + 4;
  		  byte[] data = new byte[dataLen];
  		  Util.writeInt(data, 0, info);
  		  if (textData != null) {
  			  System.arraycopy(textData, 0, data, 4, textData.length);
  		  }
          setData(data);
  	  }
   }
   
   /** Creates an empty and invalid transmission parcel.
    */
   protected TransmissionParcel () {
   }

   
   /** Creates an empty transmission parcel of the FINAL Channel the given 
    * sub-type and the priority NORMAL.
    * 
    * @param con {@code ConnectionImpl}
    * @param type int sub-type of FINAL
    */
   public TransmissionParcel (ConnectionImpl con, int type) {
	   Objects.requireNonNull(con, "connection is null");
	   connection = con;
	   channel = TransmissionChannel.FINAL;
	   sequencelNr = type;
   }

   /** Creates a parcel from an existing other parcel (identical settings).
    * The data-block is the same reference.
    * 
    * @param p {@code TransmissionParcel}
    */
   protected TransmissionParcel (TransmissionParcel p) {
      channel = p.channel;
      priority = p.priority;
      objectID = p.objectID;
      sequencelNr = p.sequencelNr;
      header = p.header;
      setData(p.getData());
      crc32 = p.crc32;
      connection = p.connection;
   }

   /** Writes the transmit parcel data to the given output stream.
    * 
    * @param output OutputStream data sink
    * @throws IOException 
    */
   public void writeObject (OutputStream output) throws IOException {
      DataOutputStream out = new DataOutputStream(output);

      // ensure CRC is calculated
      getCRC();
      
      // prevent sending invalid parcels
      if ( !verify() ) {
         throw new IOException("invalid send parcel: object=" + objectID + ", parcel=" + sequencelNr);
      }
      
      // write basic parcel information
      out.writeInt( PARCEL_MARK );
      out.write( channel.ordinal() );
      out.writeByte( priority.ordinal() );
      out.writeLong( objectID );
      out.writeInt( sequencelNr );
      out.writeInt( getLength() );
      out.writeInt( crc32 );

      // for parcel number 0 we write extended header information
      if (sequencelNr == 0 & (channel == TransmissionChannel.OBJECT |
            channel == TransmissionChannel.FILE) ) {
         header.writeObject(out);
      }

      // write serial buffer if supplied
      if (getLength() > 0) {
         out.write(getData());
      }
   }

   /** Reads the content of this parcel from the given input-stream.
    * 
    * @param con {@code ConnectionImpl}
    * @param socketInput {@code InputStream}
    * @throws IOException
    */
   public void readObject(ConnectionImpl con, InputStream socketInput) throws IOException {
      DataInputStream in = new DataInputStream(socketInput);

      int mark = in.readInt();
      if (mark != PARCEL_MARK) {
         throw new StreamOutOfSyncException("bad parcel mark");
      }
      
      // read basic parcel information
      connection = con;
      channel = TransmissionChannel.valueOf(in.read());
      priority = SendPriority.valueOf(in.readByte());
      objectID = in.readLong();
      sequencelNr = in.readInt();
      int dataLength = in.readInt();
      int crc = in.readInt();
      
      // for parcel number 0 we read extended header information
      if (sequencelNr == 0 & (channel == TransmissionChannel.OBJECT |
            channel == TransmissionChannel.FILE) ) {
         header = new ObjectHeader(objectID);
         header.readObject(in);
      }

      // read the serial buffer if it is supplied
      if (dataLength > 0) {
         byte[] buffer = new byte[dataLength];
         in.readFully(buffer);
         setData(buffer);
      }
      
      // check CRC value of the parcel
      if (crc != getCRC()) {
         throw new BadTransmissionParcelException("bad CRC value");
      }
   }
   
   @Override
   public void setData(byte[] block) {
      super.setData(block);
      crc32 = 0;
   }

   /** Returns the object header data record if available.
    * On each parcel number 0 the transmittable object's header
    * data is available, null otherwise.
    * 
    * @return <code>ObjectHeader</code> or null
    */
   public ObjectHeader getObjectHeader () {
      return header;
   }

   /** Returns a timer task that has been added to this parcel
    * and will be scheduled at the time of its sending.
    *  
    * @return TimerTask or null if undefined
    */
   public SchedulableTimerTask getTimerTask() {
      return timerTask;
   }


   /** Attributes a timer task to this parcel
    * that will be scheduled at the time of parcel sending. There can be only
    * one such attribute.
    * 
    * @param timerTask TimerTask (may be null)
    */
   public void setTimerTask (SchedulableTimerTask timerTask) {
      this.timerTask = timerTask;
   }

   /** Returns whether this parcel is set up correctly 
    * an is ready for transmission or other reception.
    * 
    * @return boolean parcel validity 
    */
   public boolean verify () {
      boolean ok = channel != null;
      if (ok) {
         ok &= sequencelNr > -1 & objectID > -1;
         if (channel == TransmissionChannel.OBJECT || channel == TransmissionChannel.FILE) {
            ok &= objectID > 0;
            if (sequencelNr == 0) {
               ok &= header != null && header.verify();
            }
         }
      }
      return ok;
   }
   
   /** Returns a CRC64 value for all information in this parcel.
    * This comprises buffer and header data.
    * 
    * @return long CRC64 value
    */
   @Override
   public int getCRC () {
      if (crc32 == 0) {
         CRC32 crc = new CRC32();
         if (getLength() > 0) {
            crc.update(getData());
         }
         crc.update(objectID);
         crc.update(sequencelNr);
         crc.update((byte)channel.ordinal());
         crc32 = (int)crc.getValue();
      }
      return crc32;
   }
   
   public void report (int io, PrintStream out) {
	  String hstr = this instanceof Signal ? (" " + ((Signal)this).getSigType()) : "";
      prot("++ " + (io==0 ? "REC":"SND") + "-PARCEL: obj=" + objectID + ", ser=" + sequencelNr 
    		  + ", " + channel + hstr + ", data=" + getLength() + ", rem=" + getConnection().getRemoteAddress().getPort(), out);
      
      if (header != null) {
         prot("              HEAD: parcels=" + header.getNumberOfParcels() + ", size=" 
               + header.getTransmissionSize() + ", mt=" + header.getSerialisationMethod(), out);
         if (header.getPath() != null ) {
            prot("              path=" + header.getPath(), out);
         }
      }
   }

   /** Prints the given protocol text to the console.
    * 
    * @param text String 
    */
   private void prot (String text, PrintStream out) {
	   String hs = text;
	   if (connection != null) {
		   hs = "(" + connection.getLocalAddress().getPort() + ") " + text;
	   }
	   out.println(hs);
   }
   

   public static TransmissionParcel readParcel(ConnectionImpl con, InputStream in) throws IOException {
      TransmissionParcel p = new TransmissionParcel();
      p.readObject(con, in);
      return p;
   }

   public ConnectionImpl getConnection () {
	   return connection;
   }

   public long getObjectID() {
      return objectID;
   }


   public int getParcelSequencelNr() {
      return sequencelNr;
   }

   /** Returns the total length of this parcel's transmission data. This 
    * consists of transfer-data and administration data.  
    * 
    * @return int
    */
   public int getSerialisedLength () {
      return getLength() + 26 + (header != null ? header.getHeaderLength() : 0);
   }
   
   
   public TransmissionChannel getChannel() {
      return channel;
   }
   
   public void setChannel (TransmissionChannel channel) {
      this.channel = channel;
   }


	public SendPriority getPriority() {
		return priority;
	}


	public void setPriority(SendPriority priority) {
		this.priority = priority;
	}

	@Override
	public int compareTo (TransmissionParcel obj) {
		if (obj == null)
			throw new NullPointerException();
		
		if (channel.ordinal() < obj.channel.ordinal()) return -1;
		if (channel.ordinal() > obj.channel.ordinal()) return +1;
		if (priority.ordinal() > obj.priority.ordinal()) return -1;
		if (priority.ordinal() < obj.priority.ordinal()) return +1;
		if (objectID < obj.objectID) return -1;
		if (objectID > obj.objectID) return +1;
		if (sequencelNr < obj.sequencelNr) return -1;
		if (sequencelNr > obj.sequencelNr) return +1;
		return 0;
	}


	@Override
	public boolean equals (Object obj) {
		if (obj == null || !(obj instanceof TransmissionParcel))
			return false;
		return compareTo((TransmissionParcel)obj) == 0;
	}

	@Override
	public int hashCode() {
		int code = ((channel.hashCode() + priority.hashCode()) << 16) + 
					((int)objectID << 8) + sequencelNr;  
		return code;
	}


	public boolean isSignal() {
		return channel == TransmissionChannel.SIGNAL;
	}
   
}
