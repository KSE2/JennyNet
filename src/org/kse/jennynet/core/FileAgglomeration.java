/*  File: FileAgglomeration.java
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import org.kse.jennynet.exception.FileInTransmissionException;
import org.kse.jennynet.exception.IllegalDestinationPathException;
import org.kse.jennynet.exception.InsufficientFileSpaceException;
import org.kse.jennynet.exception.ParcelOutOfSyncException;
import org.kse.jennynet.exception.ParcelProtocolErrorException;
import org.kse.jennynet.exception.ReceptionUndefinedException;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.TransmissionEventType;
import org.kse.jennynet.util.IO_Manager;
import org.kse.jennynet.util.Util;

/** Class to collect data parcels of the FILE channel in order to build up
 * a data file which is transmitted over the net. An instance of this class
 * takes reference to a <code>Connection</code> and an object header (<code>
 * IObjectHeader</code>), which gives the required technical parameters for the
 * transmission. These parameters are:
 * <p><b>Object-ID (= File-ID)</b> - a long integer identifying the transmission
 * <br><b>expected file length</b> - an long integer for the file length 
 * (Long.MAX_VALUE)
 * <br><b>expected number of parcels</b> - long integer
 * <br><b>target filepath (= PATH)</b> - String to identify an output path 
 * for the transmitted file. See the special convention for this variable
 * below.
 * <br><b>method</b> byte, serialisation method identifier
 * 
 * <p><b>FILE PATH and File Storage Convention</b>
 * <p>An incoming file transmission is first stored in a TEMPORARY file with 
 * name extension ".temp" allocated in the file's target (DESTINATION) directory. 
 * When transmission completes, the file is renamed to its final DESTINATION name 
 * and a transmission-event FILE_RECEIVED issued to the user with reference to this file.
 * The DESTINATION is defined using the PATH variable in transmission header.
 * 
 * <p><u>The following convention holds</u>: If the PATH variable is void, or the DESTINATION path
 * cannot be allocated, or the DESTINATION partition cannot hold the file size, the transmission
 * is aborted and the temporary file deleted. Otherwise the DESTINATION file is reported. 
 * If the PATH variable 
 * holds a file path (which may be noted absolute or relative), this path is made relative
 * to the connection's FILE_ROOT_DIRECTORY. If that directory is undefined or does not exist
 * or the resulting DESTINATION path is invalid, file allocation fails. A DESTINATION path
 * is valid if its canonical path starts with the FILE_ROOT_DIRECTORY and does not name a
 * directory. A DESTINATION path may contain path elements which don't exist and which are
 * realised when the file is created.
 */

class FileAgglomeration extends ParcelAgglomeration {
   // init data
   private long fileID;
   private String path;
   private long expectedFileLength;
   private long receivedFileLength;
   private long expectedNrOfParcels;
   private SendPriority priority;
   private int crc32;

   // operational
   private ConnectionImpl connection;
   private int nextParcelNr;
   private long startTime, duration;
   private boolean isReserved; 
   /** output file during data collection */
   private File file;
   /** remote indicated output file after transmission (may be null) */
   private File destination;
   private OutputStream fileOutput;
   
   /**
    * Creates a new parcel agglomeration device for an incoming file transmission.
    * 
    * @param connection Connection
    * @param fileID long object number to reference
    * @throws ReceptionUndefinedException if local resources cannot be allocated
    */
   public FileAgglomeration (ConnectionImpl connection, long fileID) throws ReceptionUndefinedException {
      super(connection);
      Objects.requireNonNull("connection is null");
      
      // control if FILE-ROOT-DIR is defined
      File conRootDir = connection.getParameters().getFileRootDir();
      if (conRootDir == null || !conRootDir.isDirectory()) {
    	  throw new ReceptionUndefinedException();
      }
      
      this.connection = connection;
      this.fileID = fileID;
   }

   private void init (ObjectHeader header) throws IOException {
      Objects.requireNonNull("header is null");
      
      // technical
      fileID = header.getObjectID();
      path = header.getPath();
      crc32 = header.getCrc32();
      priority = header.getPriority();
      expectedNrOfParcels = header.getNumberOfParcels();
      expectedFileLength = header.getTransmissionSize();
      startTime = System.currentTimeMillis();
      
      // break condition: invalid file target information
      if (path == null || path.isEmpty()) {
          throw new IllegalDestinationPathException("no path setup");
      }
      
      // break condition: reception (root-path) undefined
      File conRootDir = connection.getParameters().getFileRootDir();
      if (conRootDir == null || !conRootDir.isDirectory()) {
          throw new ReceptionUndefinedException();
      }
      
      // set up a thread name 
      String name = "FILE-AGGLO for " + path;
      setName(name);

      // create file path for the destination file
	  conRootDir = conRootDir.getCanonicalFile();
      File test = new File(conRootDir, path).getCanonicalFile();
      if (JennyNet.debug) {
    	  connection.prot("(FileAgglomeration.init) testing file destination: " + test);
      }

      // accept remote indicated destination path only if it 
      // falls under connection's file root directory
      // (must do this because ".." elements in PATH can lead to irritating results!)
      if (test.getPath().startsWith(conRootDir.getPath())) {
          if (JennyNet.debug) {
        	  connection.prot("(FileAgglomeration.init) destination confirmed: " + test);
          }
         destination = test;
      } else {
          if (JennyNet.debug) {
        	  connection.prot("(FileAgglomeration.init) *** FILE DESTINATION FAILED: " + test);
          }
          throw new IllegalDestinationPathException("illegal: ".concat(path));
      }

      // test for overrun events (file currently written)
      if (!IO_Manager.get().enterActiveFile(destination, ComDirection.OUTGOING)) {
    	  throw new FileInTransmissionException("file output overrun");
      }
      isReserved = true;
      
      // create operational output file (temporary name)
      file = new File(destination.getPath().concat(".temp"));
//      file = Util.getTempFile(connection.getParameters().getTempDirectory());
      
      // verify storage space
      if (conRootDir.getFreeSpace() < expectedFileLength + 32000) {
         throw new InsufficientFileSpaceException("required size = " + expectedFileLength + 
               " on " + conRootDir);
      }
      
      // create output stream
      file.getParentFile().mkdirs();
      fileOutput = new FileOutputStream(file);

      // inform user about NEW FILE INCOMING
      TransmissionEventImpl event = new TransmissionEventImpl (connection,
            TransmissionEventType.FILE_INCOMING, ComDirection.INCOMING, 
            priority, fileID, file, path);
      event.setExpectedLength(expectedFileLength);
      connection.fireTransmissionEvent(event);
   }
   
   /**
    * This method adds a FILE data parcel to the data field of the reception file.
    * The parcel serial number must be in sequence of its predecessor, so that only
    * a  contiguous series of parcels will be permitted to build up the file. 
    * 
    * @param parcel TransmissionParcel
    * @throws ReceptionUndefinedException if no file-root-dir is defined for connection
    * @throws IllegalDestinationPathException if the file destination is either undefined or syntactically incorrect 
    * @throws FileInTransmissionException if the destination file is currently written 
    * @throws ParcelProtocolErrorException if header data is missing on first parcel
    * @throws ParcelOutOfSyncException if parcel is out of sequence
    * @throws IllegalArgumentException if the parcel has false addressing
    * @throws IllegalStateException if object is already done
    * @throws IOException
    */
   @Override
   protected void processReceivedParcel (TransmissionParcel parcel) throws Exception {
	  int parcelNr = parcel.getParcelSequencelNr();
	   
      // check the parcel CHANNEL
      if (parcel.getChannel() != TransmissionChannel.FILE)  
         throw new IllegalArgumentException("false channel");
    
      // check the object number
      if (parcel.getObjectID() != fileID)  
         throw new IllegalArgumentException("false object (gush!)");
    
      // test for expected parcel sequence number 
      if (parcelNr != nextParcelNr) {
         throw new ParcelOutOfSyncException("FILE TRANSFER: object-ID = " + fileID + 
               ", parcel-nr = " + parcelNr + ", expected = " + nextParcelNr +
               "\n" + path);
      }
      
	  // check object state
	  if (isCompleted())
		  throw new IllegalStateException("illegal parcel entry, file object already complete");
	   
      // initialise Agglomeration on HEADER parcel (parcel == 0)
      if (parcelNr == 0) {
         ObjectHeader header = parcel.getObjectHeader();
         if (header == null) {
            // PROTOCOL ERROR
            throw new ParcelProtocolErrorException("NO OBJECT HEADER on new FILE transfer, obj=" +
                  fileID + ", parcel=0");
         }
         try {
        	 init(header);
         } catch (IOException e) {
        	 dropTransfer(102, 1, e);
         }
      }
      
      // testing function: failure on parcel-nr
      else if (parcelNr == connection.getProcessingTestError(ComDirection.INCOMING)) {
    	  throw new IOException("TESTING IO-Error (file-reception)"); 
      }
      
      // write parcel data to file
      if (parcel.getLength() > 0 && fileOutput != null) {
    	 synchronized(fileOutput) {
    		 byte[] data = parcel.getData();
    		 fileOutput.write(data);
    		 receivedFileLength += data.length;
    	 }
      }
      
      // promote expected parcel number
      nextParcelNr++;
      if (isCompleted()) {
         finishFileOutput();
      }
   }

   /** Returns the total number of parcel this agglomeration requires.
    * 
    * @return long
    */
   public long getExpectedNrParcels () {
	   return expectedNrOfParcels;
   }
   
   /** Returns the number of parcel already received.
    * 
    * @return int
    */
   public int getNrParcels () {
	   return nextParcelNr;
   }

   /** Whether this agglomeration has received all required data parcels.
    * 
    * @return boolean
    */
   private boolean isCompleted () {
	   return startTime > 0 && nextParcelNr == expectedNrOfParcels;
   }
   
   /** Returns the expected length of the transmission object. 
    * 
    * @return long
    */
   public long getExpectedLength () {
	   return expectedFileLength;
   }
   
   /** Attempts a regular transfer termination (reception) after the last 
    * parcel was received. Failure in realising the destination file may 
    * occur, e.g. when space is limited.
    * 
    * @throws IOException
    */
   private void finishFileOutput () throws IOException {
      if (fileOutput == null || connection.isConfirmSuppressed()) return;
	  if (JennyNet.debug) {
		  connection.prot("(FileAgglomeration.finishFileOutput) finishing reception of transmission (ID " 
				  	+ fileID + ") " + path);
	  }
      cancelTransfer();
      int info = 0;
      String text = null;
      
      // verify file destination
      if (destination == null || destination.isDirectory()) {
         // cannot realise destination file (environment reason)
    	 if (JennyNet.debug) {
    		  connection.prot("(FileAgglomeration.finishFileOutput)  object " + fileID 
    				  + ", unable to realise file destination: " + destination);
    	 }
         info = 102;
         text = "destination assignment error";
      }
      
      // CRC control of resulting file data
      if (info == 0 && crc32 != 0) {
    	  int crc = Util.CRC32_of(file);
    	  if (crc != crc32) {
    	      info = 118;
    	      text = "CRC failure on target file";
    	  }
      }
      
      // attempt rename of temp-file to destination file   
      if (info == 0) {
    	  // rename temp-file
    	  destination.delete();
	      if (file.renameTo(destination)) {
              // let 'file' assume the destination (successful completed transfer)
	    	  if (JennyNet.debug) {
	    		  connection.prot("(FileAgglomeration.finishFileOutput) SUCCESS: object " 
	    				  + fileID + ", completed file destination: " + destination);
	    	  }
		      file = destination;
		  } else {
	    	  if (JennyNet.debug) {
	    		  connection.prot("(FileAgglomeration.finishFileOutput) *** ERROR: object " + fileID + ", unable to rename to file destination: " + destination);
	    	  }
			  info = 102;
			  text = "destination storage error";
		  }
      }

      // signal transfer success or failure to remote station
      // (failure prevails if a file destination could not be realised)
      Signal signal = info == 0 ? Signal.newConfirmSignal(connection, fileID) : 
                      Signal.newFailSignal(connection, fileID, (info == 118 ? 3 : 1), text);
      connection.sendSignal(signal);
      
      // inform the user about file-received or transmission failed (event)
      boolean success = info == 0;
      TransmissionEventImpl event = new TransmissionEventImpl(connection, 
            success ? TransmissionEventType.FILE_RECEIVED : TransmissionEventType.FILE_ABORTED,
       		ComDirection.INCOMING, priority, fileID, file, path);
      event.setTransmissionLength(receivedFileLength);
      event.setExpectedLength(expectedFileLength);
      event.setDuration(getDuration());
      if (!success) {
    	  event.setInfo(info);
      }
      connection.fireTransmissionEvent(event);
   }

   @Override
   protected void exceptionThrown(Throwable e) {
      e.printStackTrace();
      dropTransfer(110, 1, e);
   }

   /** Terminates this file agglomeration by shutting down the thread
    * and removing it from the file-receptor map. Silent operation, no signals
    * sent or events reported! Reception file remains untouched.
    */
   private void cancelTransfer () {
      // terminate thread specific resources (and the collection thread itself)
      connection.removeFileReceptor(fileID);
      duration = System.currentTimeMillis() - startTime;
      
      // close file-output-stream
      if (fileOutput != null) {
	  	  synchronized(fileOutput) {
		      try {
		         fileOutput.close();
		         fileOutput = null;
		      } catch (IOException e1) {
		         e1.printStackTrace();
		      }
	  	  }
      }
         
      // take down global file protection
      if (isReserved) {
	      try {
			  IO_Manager.get().removeActiveFile(destination, ComDirection.OUTGOING);
	      } catch (IOException e) {
			 if (JennyNet.debug) {
				System.err.println("(FileAgglomeration.cancelTransfer) *** unable to remove file-lock in JennyNet"
						+ "\n" + e);
				System.err.println(destination);
			 }
	      }
      }
      
      // terminate the worker thread
      super.terminate();
   }

   /** Drops this file agglomeration and removes its reception file (TEMP);
    * optionally a signal is sent to remote and a layer event issued to
    * connection listeners.
    * 
    * @param eventInfo int if != 0 a transmission event will be issued 
    * @param signalInfo int if != 0 a BREAK signal will be sent to remote 
    * @param e Throwable, optional error information, may be null
    */
   public void dropTransfer (int eventInfo, int signalInfo, Throwable e) {
      cancelTransfer();
      
      if (ConnectionImpl.debug) { 
   	     connection.prot("-- dropping incoming file transfer ID " + fileID + ", rem " + connection.getRemoteAddress());
   	     connection.prot("   signal to remote: BREAK " + signalInfo + ", FILE_ABORTED event " + eventInfo);
      }
      
      // if opted, signal remote about transmission break
      if (signalInfo != 0 && connection.isConnected()) {
         String text = e == null ? null : e.toString();
         connection.sendSignal(Signal.newBreakSignal(connection, fileID, signalInfo, text));
      }

      // if opted, issue a transmission event (abortion)
      if (eventInfo != 0) {
         TransmissionEventImpl event = new TransmissionEventImpl(connection,
              TransmissionEventType.FILE_ABORTED, ComDirection.INCOMING,
              priority, fileID, eventInfo, e );
         event.setPath(path);
         event.setFile(file);
         event.setDuration(getDuration());
         event.setTransmissionLength(receivedFileLength);
         event.setExpectedLength(expectedFileLength);
         connection.fireTransmissionEvent(event);
      }
   }

   @Override
   public void terminate() {
	  // this is for the finalize() method of super
	  if (!isTerminated()) {
		  super.terminate();
		  cancelTransfer();
	  }
   }
   
   /** The destination PATH information as given by the sender.
    * 
    * @return String
    */
   public String getPath() {return path;}

   /** Time in milliseconds the transmission took to be realised. Measurement
    * starts with the arrival of the first data parcel and ends with abortion
    * or arrival of the last parcel.
    * 
    * @return long milliseconds
    */
   public long getDuration() {return duration;}

   /** Returns the file of latest validity. After the transmission has been 
    * completed, this is the destination file. If the transmission has been
    * aborted or is still ongoing, this is the temporary file which assembled
    * transmission data.
    * 
    * @return File transmission datafile
    */
   public File getFile() {return file;}

}
