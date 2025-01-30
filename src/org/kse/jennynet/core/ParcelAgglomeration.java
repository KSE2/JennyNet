/*  File: ParcelAgglomeration.java
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

import java.util.concurrent.LinkedBlockingQueue;

import org.kse.jennynet.intfa.Connection;

/** This class defines a general purpose parcel collector. An instance 
 * harbours a thread which actively collects data parcels handed to it 
 * and makes them processed in a subclass. The class is a derivate of 
 * {@code LinkedBlockingQueue<TransmissionParcel>}, the received parcels are
 * stored in the instance itself, so an instance functions as a buffer
 * between parcel arrival and digestion activity.
 */
abstract class ParcelAgglomeration extends LinkedBlockingQueue<TransmissionParcel> {

   public static final String THREAD_BASENAME = "Parcel-Receptor at ";
   private Thread worker;
   private String name;
   private boolean terminate;
   
   public ParcelAgglomeration (Connection connection) {
      super(connection.getParameters().getParcelQueueCapacity());
      
      worker = new Thread(THREAD_BASENAME + connection.getLocalAddress().getPort()) {
         @Override
         public void run() {
            setPriority(connection.getParameters().getTransmitThreadPriority());
             
            while (!terminate) {
               try {
                  TransmissionParcel parcel = take();
                  processReceivedParcel(parcel);
                  
               } catch (InterruptedException e) {
                  ParcelAgglomeration.this.interrupted(e);
               } catch (Throwable e) {
                  exceptionThrown(e);
               } 
            }
         }
       };
       worker.start();
   }

   abstract protected void processReceivedParcel(TransmissionParcel parcel) throws Exception;

   /** Called when the receptor thread received an exception 
    * from the working method (<code>processReceivedParcel()</code>).
    * 
    * @param e InterruptedException
    */
   abstract protected void exceptionThrown (Throwable e);

   /** Called when the receptor thread has been interrupted.
    * 
    * @param e InterruptedException
    */
   protected void interrupted (InterruptedException e) {
   }
   
   /** Returns the  name for this agglomeration or null if none is defined.
    * 
    * @return String or null
    */
   public String getName () {return name;}

   /** Sets a name for this agglomeration.
    * Also serves for the thread name if a thread is activated.
    * 
    * @param name String 
    */
   public void setName (String name) {
      this.name = name;
      if (name != null) {
         worker.setName(THREAD_BASENAME.concat(name));
      }
   }

   public int getThreadPriority() {
      return worker.getPriority();
   }

   public void setThreadPriority(int threadPriority) {
      worker.setPriority(threadPriority);
   }

   /** Terminates the worker thread.
    */
   public void terminate () {
      terminate = true;
      worker.interrupt();
   }

   public boolean isTerminated () {return terminate;}
   
   @Override
   protected void finalize() throws Throwable {
      terminate();
      super.finalize();
   }
   
}
