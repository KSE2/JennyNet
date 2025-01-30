
/*  File: Serialization.java
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

import java.io.NotSerializableException;
import java.util.List;

import org.kse.jennynet.exception.SerialisationException;

/** Interface for a device to serialise objects of registered classes.
 * Allows to register Java classes for the serialisation / de-serialisation
 * functions realised by this device. Performs serialisation and de-serialisation
 * of objects which have to be instances of registered classes.
 * 
 * <p>Each <code>Serialization</code> instance bears a code name for the 
 * serialisation method it realises. In the system there may exist more 
 * than one instances with the same method name. Devices assume individuality 
 * by registering of different sets of classes for serialisation. There is no
 * restriction for the multiplicity of devices and identical clones may exist. 
 * 
 * <p>IMPORTANT!! Depending on the implementation, the order of registering 
 * of classes at this interface may matter for the proper functioning of
 * serialisation and de-serialisation. Therefore the removal of singular
 * registry entries is not supported in this interface.
 * 
 * <p>A reference to the <code>Connection</code> for which a serialisation
 * instance will be working can be associated optionally or may be left void.
 * If the reference is given, operation limitations defined in the parameter
 * set of the connection may become active. 
 */

public interface Serialization {

	/** Returns a complete clone of this serialisation object. 
	 * 
	 * @return {@code Serialization}
	 */
	Serialization copy ();

   /** Removes all class registrations from this serialisation. A reference
    * to the connection remains intact.
    */
   void clear ();

	/** Whether the given class is registered in this serialisation device.
	 * 
	 * @param c <code>Class</code> class to investigate
	 * @return boolean true if and only if class is registered
	 */
   boolean isRegisteredClass (Class<?> c);
	
   /** Registers the given class for serialisation in this device.
    * It is assumed that registering a class twice does not modify this
    * serialisation and does not constitute an error.
    * 
    * @param c <code>Class</code> class to register for serialisation
    * @return boolean true = new entry, false = already contained
    * @throws NotSerializableException if the class does not qualify to be
    *         serialised
    */
   boolean registerClass (Class<?> c) throws NotSerializableException;

   /** Returns a data block with serialisation of the given object.
    * 
    * @param object <code>Object</code> object to serialise
    * @return byte array holding the object serialisation
    *         (and nothing but the object serialisation)
    * @throws SerialisationException if the given object cannot be serialised
    */
   byte[] serialiseObject (Object object) throws SerialisationException;

   /** De-serialises a data block and returns the de-serialised object.
    *  
    * @param buffer byte[] serialisation data block
    * @return <code>Object</code> de-serialised object
    * @throws SerialisationException if the given data is inconsistent or
    *         does not match the serialisation method
    */
   Object deserialiseObject (byte[] buffer) throws SerialisationException;

   /** A code name for the serialisation method performed by this
    * <code>Serialisation</code> device.
    * 
    * @return int serialisation method code
    */
   int getMethodID ();

   /** Returns the name of this serialisation. The name consists of a
    * serialisation type name plus a short-ID of the <code>Connection</code>
    * in case there is one associated with this serialisation.
    * 
    * @return String serialisation name
    */
   String getName ();

   /** Returns the number of registered classes.
    * 
    * @return int list size
    */
   int getRegisteredSize ();
   
   /** Renders the name of this serialisation (<code>getName()</code>)
    * and the amount of registered classes.
    *  
    * @return String 
    */
   @Override
   String toString ();

   /** Returns a list of the classes registered.
    * 
    * @return {@code List<Class<?>>}
    */
   List<Class<?>> getRegisteredClasses ();
}
