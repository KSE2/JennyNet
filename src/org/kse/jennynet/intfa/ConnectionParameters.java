/*  File: ConnectionParameters.java
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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.kse.jennynet.core.JennyNet.ThreadUsage;
import org.kse.jennynet.exception.SerialisationUnavailableException;

/** Connection-parameters are a tool to fine-tune the behaviour of a 
 * {@code Connection} through setting parameter values and/or activating
 * special services which the implementation is capable of. This interface
 * describes a set of parameters which have an initial (default) value and how
 * to retrieve and change them. Connection-parameter sets are available at
 * different places and abstraction levels. Each connection has its own set
 * of parameters which can be fully adjusted to special needs. Furthermore,
 * generic sets are placed at 1) {@code JennyNet} global class, and 2) each
 * {@code IServer} instance. {@code Client} instances take their default set
 * from {@code JennyNet} class, {@code ServerConnection} instances refer to
 * the set kept in their {@code Server}. 
 * 
 * <p><b>Famous Settings</b>
 * <p>Particular attention has to be directed to the setting of 
 * <b>FileRootDir</b>. This parameter is naming the directory in which 
 * incoming file transmissions will be located. By default this setting is 
 * void (null) with consequence that no file transmissions will be accepted.
 * In this case the sender receives an ABORTION event.
 * Next important values are <b>TransmissionParcelSize</b> and the two
 * settings for <b>thread priority</b> (Base and Transmit), however the 
 * standard should be expected to work well.
 * 
 * <p><b>Special Services</b>
 * <p><b><i>= ALIVE Signals</i></b>. ALIVE signals can be requested to be sent
 * from the remote station. By default NO ALIVE signals are requested or 
 * controlled. This is the case if parameter 'AlivePeriod' has the value zero.
 * In order to activate the service, a value greater zero has to be specified.
 * If the service is active and no signals arrive for a certain time, the 
 * connection is closed with an error code.
 * 
 * <p><b>= IDLE State</b>
 * <br>The IDLE-State controller checks whether a connection performs at least 
 * a given amount of data exchange with the remote station per minute. The 
 * IDLE-State has two values 'IDLE' and 'BUSY'. When the IDLE-State changes, 
 * a special event is dispatched to the listeners of the connection. In order 
 * to activate this service a value greater zero has to be set in parameter 
 * 'IdleThreshold'. Furthermore, in parameter 'IdleCheckPeriod' the frequency
 * of control can be adjusted.
 * 
 * <p><b>= Transmission Speed</b>
 * <br>The speed by which data is sent to remote station can be set to a 
 * maximum value (bytes per second). File and object transmissions are subject
 * to this limitation once given. The speed limit is executed by time delays
 * on the sending thread. The special value zero stops all data transmission 
 * without closing the connection (ATTENTION!). Note that the speed setting
 * also becomes valid for the sending of the remote station.
 * 
 */
public interface ConnectionParameters extends Cloneable, Serializable {

   /** Returns a deep clone of this set of settings.
    * 
    * @return {@code Object}
    */
   Object clone ();

   /** Whether all parameter values of the given set are equal to the
    * corresponding values of this set.
    * 
    * @param p {@code ConnectionParameters}
    * @return boolean true = all values equal, false = some values not equal
    */
   boolean equalValues (ConnectionParameters p);
   
   /** Returns the thread priority of the service threads involved in
    * data processing. This includes threads which
    * deliver received objects to the application or handle outgoing
    * objects handed to the connection. Defaults to Thread.NORM_PRIORITY.
    *  
    *  @return int thread priority
    */
   int getBaseThreadPriority ();

   /** Sets thread priority of the service threads involved in data processing. 
    * This concerns layer threads dealing with the user interface like
    * delivery of received objects. Defaults to Thread.NORM_PRIORITY.
    * <p><small>In general it is most efficient to set this value to the 
    * application's standard thread priority. In cases where a particular
    * high reactivity of the network layer is requested, this value should
    * be set higher.</small>
    *  
    * @param p int thread priority
    * @return int the value adopted, which may differ from the argument
    * @see #setTransmitThreadPriority(int)
    */
   int setBaseThreadPriority (int p);

   /** Returns the thread priority of the data transmission threads
    *  involved in dealing with socket communication. Defaults to
    *  Thread.MAX_PRIORITY - 2.
    *  
    *  @return int thread priority
    */
   int getTransmitThreadPriority ();

   /** Sets thread priority of the data transmission threads involved in 
    * dealing with socket communication. Defaults to Thread.MAX_PRIORITY - 2.
    *  
    *  <p><small>Note: For minimised latency of net transmission it is 
    *  recommended to set this value above or equal to the base-thread-priority. 
    *  Setting it to Thread.MAX_PRIORITY is not expected to disturb an 
    *  application but ensures that network socket activity is immediately 
    *  performed when data is ready to send or available to deliver.</small> 
    *  
    * @param p int thread priority 
    * @return int the value adopted, which may differ from the argument
    * @see #setBaseThreadPriority(int)
    */
   int setTransmitThreadPriority (int p);

	/** Returns the setting for delivery thread usage of this connection.
	 * INDIVIDUAL makes object delivery occurring in a thread reserved only
	 * for this connection, while GLOBAL means delivery takes place in the
	 * context of a single global thread.  
	 * 
	 * @return ThreadUsage
	 */
	ThreadUsage getDeliveryThreadUsage ();

	/** Sets delivery thread usage of this connection. The setting can have
	 * one of two values.
	 * 'INDIVIDUAL' makes object and event delivery occurring in a thread 
	 * reserved exclusively for this connection, while 'GLOBAL' means delivery 
	 * takes place in the context of a single global thread. This setting is 
	 * only relevant for a multi-connection setup, e.g. for a Server 
	 * application. 
	 * <p>Object delivery of a single connection always takes place in a
	 * sequence. By large, this setting makes the difference between parallel
	 * and serial delivery processing in a multi-connection setup. The 
	 * default is serial, parallel processing can be interesting
	 * if there are possibly lengthy operations associated in the reception. 
	 * 
	 * @param usage JennyNet.ThreadUsage
	 */
	void setDeliveryThreadUsage (ThreadUsage usage);

	/** Returns the time delay tolerable for a single event delivery digestion
	 * in a user supplied {@code ConnectionListener}. Minimum of 1,000 and 
	 * defaults to 10,000.
	 * 
	 * @return int milliseconds
	 */
	int getDeliverTolerance ();
	
	/** Sets the time delay tolerable for a single event delivery digestion 
	 * in a user supplied {@code ConnectionListener}. Has a minimum of
	 * 1,000 and defaults to 10,000.
	 * 
	 * @param delay int milliseconds
	 */
	void setDeliverTolerance (int delay);
	
   /** Returns the value for capacity of queues handling with data parcels.
    * Defaults to 600.
    * 
    * @return int maximum number of parcels in a queue
    */
   int getParcelQueueCapacity ();

   /** Sets the value for capacity of queues handling with data parcels.
    * This value can only be set before a connection starts. The value
    * ranges within a minimum of 10 and a maximum of 10,000 and defaults to
    * 600. 
    * 
    * @param parcelQueueCapacity int
    * @throws IllegalArgumentException if parameter is out of range 
    * @throws IllegalStateException if a related Connection is connected
    */
   void setParcelQueueCapacity (int parcelQueueCapacity);

   /** Returns the maximum size of any serialisation of an object which is
    * to be sent over the network. The default value is 100 Mega.
    * <p><small>Encountering a size overflow during send or receive operations
    * causes the connection closed.</small>
    * 
    * @return int maximum object serialisation size
    */
   int getMaxSerialisationSize ();
   
   /** Sets the maximum size of any serialisation of an object which is
    * to be sent over the network. The default value is 100 Megabytes and a
    * minimum of 10,000 is guaranteed.
    * <p><small>Encountering a size overflow during send or receive operations
    * causes the connection to close.</small>
    * 
    * @param size int maximum object serialisation size
    */
   void setMaxSerialisationSize (int size);
   
   /** Returns the queue capacity for sending objects over the net. Attempts 
    * to send an object with a full send queue results in an exception thrown.
    * Defaults to 200.
    * 
    * @return int maximum number of objects in queue
    */
   int getObjectQueueCapacity ();

   /** Sets the queue capacity for sending objects over the net. 
    * This value can only be set before a connection starts. 
    * Has a minimum of 1 and a maximum of 10,000, defaults to 200.
    * 
    * @param objectQueueCapacity int maximum number of objects in queue
    * @throws IllegalArgumentException if value is out of range
    * @throws IllegalStateException if a related Connection is connected
    */
   void setObjectQueueCapacity (int objectQueueCapacity);

   /** The current value of ALIVE_PERIOD.
    * This value determines the time interval by which ALIVE signals are to be
    * sent from the remote station. A value of zero indicates that no ALIVE 
    * signals are sent. Defaults to zero.
    * 
    * @return int milliseconds 
    */
   int getAlivePeriod ();

   /** Sets the value of ALIVE_PERIOD.
    * This value, when above zero, allows to request ALIVE signals to be sent
    * from the remote station and controlled locally. If the signal
    * is missing out for a time, the connection is shut down with an error
    * condition. 
    * <p>The argument defines the interval by which the signals are sent.
    * Zero indicates that no ALIVE signals are sent and no timeout control 
    * is performed. Defaults to zero.
 
    * <p><small>The layer ensures a minimum of 5,000 milliseconds and a
    * maximum of 300,000, automatically correcting arguments except zero. 
    * </small>
    *       
    * @param period int milliseconds
    * @return int the value adopted
    */
   int setAlivePeriod (int period);

   /** The value of parameter CONFIRM_TIMEOUT.
    * This value determines the maximum time for the connection to wait for
    * an expected CONFIRM signal from remote. In consequence of failure
    * the connection will react depending on the case. Has a minimum of 
    * 1,000 milliseconds and defaults to 30,000.
    * 
    * @return int timeout in milliseconds
    */
   int getConfirmTimeout ();

   /** Sets the value for CONFIRM_TIMEOUT.
    * This value determines the maximum time for the connection to wait for
    * an expected CONFIRM signal from remote. In consequence of failure
    * the connection will react depending on the case. Defaults to 30,000. 
    * <p><small>The layer implements a minimum of 1,000 milliseconds
    * automatically correcting lower settings.</small>
    *  
    * @param timeout int milliseconds
    */
   void setConfirmTimeout (int timeout);

   /** Returns the code number of the object serialisation method applied by 
    * the connection. The code specifies a {@code Serialization} type. The
    * default is the <i>JennyNet</i> global default value.
    * <p>Legend: 0 = Java-Serialisation, 1 = Kryo-Serialisation. 
    * 
    * @return int method code
    * @see Serialization
    */
   int getSerialisationMethod ();

   /** Sets the method for object serialisation.
    * Possible values: 0 = Java, 1 = Kryo, 2 = custom.
    * 
    * @param method int serialisation method
    * @return boolean true = value has changed, false = value unchanged
    * @throws IllegalArgumentException if the given value is undefined
    */
   boolean setSerialisationMethod (int method);

   /** Returns the root directory (TRANSFER_ROOT_PATH) for incoming file 
    * transmissions. Defaults to null. 
    * <p><small>The location of an incoming file transmission is defined by 
    * the concatenation of TRANSFER_ROOT_PATH and the 'path' information
    * which was given by the sender in the send-file command.</small>
    * 
    * @return File directory or null
    */
   File getFileRootDir ();

   /** Sets the root directory (TRANSFER_ROOT_PATH) for incoming file 
    * transmissions. If not null, the parameter must be an existing directory.
    * The default value is null.
    * <p><small>Null implies that no file transmissions are possible in the
    * INCOMING direction; the setting of this property is not required for 
    * OUTGOING transmissions. If the receiving station is not set up for 
    * receiving files, the sending station will issue a FILE_ABORTED event. 
    * </small>
    * 
    * @param dir File directory or null
    * @throws IllegalArgumentException if parameter is not a directory
    * @throws IOException if the path cannot be verified (canonical name)
    */
   void setFileRootDir (File dir) throws IOException;

   /** Returns the setting for TRANSMISSION_PARCEL_SIZE in the connection.
    * This is the data transport capacity that a single transmission parcel
    * may assume in maximum. Defaults to 65,536 (0x10000).
    * 
    * @return int maximum data size of a transmission parcel 
    */
   int getTransmissionParcelSize ();

   /** Sets the value for TRANSMISSION_PARCEL_SIZE for the connection.
    * This is the data transport capacity that a single transmission parcel
    * may assume in maximum.  The value has a range of 1k to 262k (0x400 .. 
    * 0x40000) and defaults to 65,536 (0x10000).
    * 
    * @param size int maximum capacity of transmission parcels (bytes)
    * @return int adopted value
    */
   int setTransmissionParcelSize (int size);

   /** Returns the transmission speed setting for the connection in bytes 
    * per second. Value -1 indicates no speed restrictions are imposed;
    * value zero means that all data transmission is stopped.
    * Defaults to -1.
    * 
    * @return int tempo in bytes per second
    */
   int getTransmissionSpeed ();
   
   /** Sets the top transmission speed for the connection in bytes 
    * per second. Value -1 indicates no speed restriction is imposed.
    * Value 0 stops all data transmission without closing the connection.
    * Transmission resumes when a value above zero or -1 is supplied.
    * Defaults to -1.
    * 
    * @param tempo int bytes per second
    */
   void setTransmissionSpeed (int tempo);
   
   /** Sets the IDLE_THRESHOLD for a connection. The IDLE_THRESHOLD states an
    * amount of bytes exchanged with the remote station per minute (incoming or
    * outgoing). This threshold determines whether a connection qualifies for
    * BUSY or IDLE status, which is indicated via connection events once a 
    * threshold is defined. Defaults to 0 (undefined).
    * <p>A value zero switches off the IDLE checking and no more events are
    * issued. A state of BUSY is assumed.
    *  
    * @param idleThreshold int bytes per minute
    */
   void setIdleThreshold (int idleThreshold);

   /** Returns the IDLE_THRESHOLD for a connection. The IDLE_THRESHOLD states
    * an amount of bytes exchanged with the remote end per minute (incoming or
    * outgoing). This threshold determines whether a connection qualifies for
    * BUSY or IDLE status, which is indicated via connection events once a 
    * threshold is defined. Defaults to 0 (undefined). 
    *  
    *  @return int threshold: bytes per minute
    */
   int getIdleThreshold ();

   /** Returns the period by which the IDLE-State of the connection is checked
    * and determined. On every change of the IDLE-State (IDLE or BUSY) an event
    * is issued to the listeners of the connection. Defaults to 60,000;  
    * minimum is 5,000.
    * 
    * @return int check period in milliseconds
    */
   int getIdleCheckPeriod ();
   
   /** Sets the period by which the IDLE-State of the connection is checked
    * and determined. On every change of the IDLE-State a special event is 
    * issued to listeners of the connection. Defaults to 60,000; a minimum of 
    * 5,000 is ensured.
    * <p><small>Caution is to be applied for low values because the controlled
    * time-space is identical with the check period, i.e. with the value given.
    * For instance if a period of 10 seconds is defined, if within 10 seconds
    * there is no data exchange, then the state IDLE is assumed.</small>
    * 
    * @param period int checking period in milliseconds
    */
   void setIdleCheckPeriod (int period);
   
}