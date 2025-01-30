/*  File: ConnectionParametersImpl.java
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
import java.io.IOException;
import java.util.Objects;

import org.kse.jennynet.core.JennyNet.ThreadUsage;
import org.kse.jennynet.intfa.ConnectionParameters;

/**
 * This is the implementation class for interface {@code ConnectionParameters}.
 * It can store, retrieve and administer a set of parameters for a
 * <code>Connection</code>. By creating a new instance, parameter values are
 * set to the default values which are defined in the static class
 * {@code JennyNet}, however and notably, the default instance available at
 * {@code JennyNet} may harbour a different set of values as these can hold
 * modifications by the application.
 *   
 */
class ConnectionParametersImpl implements Cloneable, ConnectionParameters {
	
   private static final long serialVersionUID = 2344073475117L;

   private File fileRootDir;
   private ThreadUsage deliveryUsage = JennyNet.DEFAULT_THREAD_USAGE;
   private int baseThreadPriority = JennyNet.DEFAULT_BASE_PRIORITY;
   private int transmitThreadPriority = JennyNet.DEFAULT_TRANSMIT_PRIORITY;
   private int transmissionParcelSize = JennyNet.DEFAULT_TRANSMISSION_PARCEL_SIZE;
   private int parcelQueueCapacity = JennyNet.DEFAULT_PARCEL_QUEUE_CAPACITY;
   private int objectQueueCapacity = JennyNet.DEFAULT_QUEUE_CAPACITY;
   private int alivePeriod = JennyNet.DEFAULT_ALIVE_PERIOD;
   private int confirmTimeout = JennyNet.DEFAULT_CONFIRM_TIMEOUT;
   private int serialMethod = JennyNet.getDefaultSerialisationMethod();
   private int idleThreshold;
   private int idleCheckPeriod = JennyNet.DEFAULT_IDLE_CHECK_PERIOD;
   private int transmissionTempo = JennyNet.DEFAULT_TRANSMISSION_TEMPO;
   private int maxSerialiseSize = JennyNet.DEFAULT_MAX_SERIALISE_SIZE;
   private int deliverTolerance = JennyNet.DEFAULT_DELIVER_TOLERANCE;

   public ConnectionParametersImpl() {
   }
   
   @Override
   public Object clone () {
      try {
         return super.clone();
      } catch (CloneNotSupportedException e) {
         return null;
      }
   }
   
   @Override
   public int getBaseThreadPriority() {
      return baseThreadPriority;
   }

   @Override
   public int setBaseThreadPriority(int p) {
      baseThreadPriority = Math.min(Math.max(p, Thread.MIN_PRIORITY), Thread.MAX_PRIORITY);
      return baseThreadPriority;
   }

   @Override
   public int getTransmitThreadPriority() {
      return transmitThreadPriority;
   }

   @Override
   public int setTransmitThreadPriority (int p) {
      transmitThreadPriority = Math.min(Math.max(p, Thread.MIN_PRIORITY), Thread.MAX_PRIORITY);
      return transmitThreadPriority;
   }

	@Override
	public ThreadUsage getDeliveryThreadUsage () {
		return deliveryUsage;
	}

	@Override
	public void setDeliveryThreadUsage (ThreadUsage usage) {
		Objects.requireNonNull(usage);
		deliveryUsage = usage;
	}

   @Override
   public int getParcelQueueCapacity() {
      return parcelQueueCapacity;
   }

   @Override
   public void setParcelQueueCapacity (int parcelQueueCapacity) {
      if (parcelQueueCapacity < 10 | parcelQueueCapacity > 10000) 
         throw new IllegalArgumentException("queue capacity out of range (10..10000)");
      this.parcelQueueCapacity = parcelQueueCapacity;
   }

   @Override
   public int getObjectQueueCapacity() {
      return objectQueueCapacity;
   }

   @Override
   public void setObjectQueueCapacity(int objectQueueCapacity) {
      if (objectQueueCapacity < 1 | objectQueueCapacity > JennyNet.MAX_QUEUE_CAPACITY) 
          throw new IllegalArgumentException("queue capacity out of range (1..10000)");
      this.objectQueueCapacity = objectQueueCapacity;
   }

   @Override
   public int getAlivePeriod () {
      return alivePeriod;
   }
   
   @Override
   public int setAlivePeriod (int period) {
      if (period < JennyNet.MIN_ALIVE_PERIOD & period != 0) { 
         period = JennyNet.MIN_ALIVE_PERIOD;
      } else if (period > JennyNet.MAX_ALIVE_PERIOD) {
    	 period = JennyNet.MAX_ALIVE_PERIOD;
      }
      alivePeriod = period;
      return period;
   }
   
   @Override
   public int getConfirmTimeout() {
      return confirmTimeout;
   }

   @Override
   public void setConfirmTimeout (int timeout) {
      confirmTimeout = Math.max(JennyNet.MIN_CONFIRM_TIMEOUT, timeout);
   }

   @Override
   public int getSerialisationMethod() {
      return serialMethod;
   }

   @Override
   public boolean setSerialisationMethod (int method) {
	  if (method < 0 | method >= JennyNet.MAX_SERIAL_DEVICE)
		  throw new IllegalArgumentException("illegal method number: " + method);
	  
	  boolean changed = serialMethod != method;
      serialMethod = method;
      return changed;
   }

   @Override
   public File getFileRootDir() {
      return fileRootDir;
   }

   @Override
   public void setFileRootDir (File dir) throws IOException {
      if (dir == null) {
         fileRootDir = null;
      } else {
    	 dir = dir.getCanonicalFile();
         if (!dir.isDirectory()) {
            throw new IllegalArgumentException("parameter is not a directory");
         }
         fileRootDir = dir;
      }
   }

   @Override
   public int getTransmissionParcelSize () {
      return transmissionParcelSize;
   }

   @Override
   public int setTransmissionParcelSize (int size) {
      transmissionParcelSize = Math.min( Math.max(size, 
            JennyNet.MIN_TRANSMISSION_PARCEL_SIZE), JennyNet.MAX_TRANSMISSION_PARCEL_SIZE );
      return transmissionParcelSize;
   }
   
   @Override
   public void setIdleThreshold (int idleThreshold) {
      this.idleThreshold = Math.max(idleThreshold, 0);
   }

   @Override
   public int getIdleThreshold () {
      return idleThreshold;
   }

   @Override
   public int getIdleCheckPeriod () {
      return idleCheckPeriod;
   }

   @Override
   public void setIdleCheckPeriod (int period) {
      idleCheckPeriod = Math.max(period, JennyNet.MIN_IDLE_CHECK_PERIOD);
   }

	@Override
	public int getTransmissionSpeed() {
		return transmissionTempo;
	}
	
	@Override
	public void setTransmissionSpeed(int tempo) {
		transmissionTempo = Math.max(-1, tempo);
	}

	@Override
	public int getMaxSerialisationSize() {
		return maxSerialiseSize;
	}

	@Override
	public void setMaxSerialisationSize(int size) {
		maxSerialiseSize = Math.max(size, JennyNet.MIN_MAX_SERIALISE_SIZE);
	}

	@Override
	public int getDeliverTolerance() {
		return deliverTolerance;
	}

	@Override
	public void setDeliverTolerance (int delay) {
		deliverTolerance = Math.max(delay, JennyNet.MIN_DELIVER_TOLERANCE);
	}

	@Override
	public boolean equalValues (ConnectionParameters p) {
		ConnectionParametersImpl par = (ConnectionParametersImpl) p;
		boolean ok = alivePeriod == par.alivePeriod &&
				baseThreadPriority == par.baseThreadPriority &&
				confirmTimeout == par.confirmTimeout &&
				deliverTolerance == par.deliverTolerance &&
				deliveryUsage.equals(par.deliveryUsage) &&
				fileRootDir == null ? par.fileRootDir == null : 
					(par.fileRootDir != null && fileRootDir.equals(par.fileRootDir)) &&
				idleCheckPeriod == par.idleCheckPeriod &&
				idleThreshold == par.idleThreshold &&
				maxSerialiseSize == par.maxSerialiseSize &&
				objectQueueCapacity == par.objectQueueCapacity &&
				parcelQueueCapacity == par.parcelQueueCapacity &&
				serialMethod == par.serialMethod &&
				transmissionParcelSize == par.transmissionParcelSize &&
				transmissionTempo == par.transmissionTempo &&
				transmitThreadPriority == par.transmitThreadPriority;
		return ok;
	}
}
