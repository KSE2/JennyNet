/*  File: KryoSerialisation.java
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

import java.io.NotSerializableException;
import java.util.Objects;

import org.kse.jennynet.exception.SerialisationException;
import org.kse.jennynet.intfa.Serialization;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * {@code Serialisation} based on Kryo-4.0.3 package (c) 2023 by Esoteric 
 * Software. Reference: https://github.com/EsotericSoftware/kryo.
 * Proprietary Berkeley-like licence.
 * <p>This serialisation method works faster and more space-efficient than
 * the Java-serialisation. Its <i>JennyNet</i> method identifier is zero, 
 * which is the default setting in connection parameters. 
 */
public class KryoSerialisation extends AbstractSerialization {
   private static final int METHOD_ID = 1;
   
   Kryo kryo = createKryo();
   
   private static Kryo createKryo () {
	   Kryo kryo = new Kryo();
	   kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(
			   new SerializingInstantiatorStrategy()));
	   return kryo;
   }
   
   public KryoSerialisation () {
	   super();
   }
   
   @Override
   public Serialization copy() {
      // make a deep clone of this serialisation object
      KryoSerialisation c = (KryoSerialisation)super.clone();
	  c.kryo = createKryo();
	  for (Class<?> type : classMap.keySet()) {
	      c.kryo.register(type);
	  }
      return c;
   }

   
   @Override
   public void clear() {
	   super.clear();
	   kryo = createKryo();
   }

@Override
   public boolean registerClass(Class<?> c) throws NotSerializableException {
	   if (super.registerClass(c)) {
	       kryo.register(c);
	       return true;
	   }
	   return false;
   }

   @Override
   public byte[] serialiseObject (Object object) {
	  Objects.requireNonNull(object);
	  if (!isRegisteredClass(object.getClass()))
		  throw new IllegalArgumentException("not-registered object class: " + object.getClass().getName());
	  
      Output output = new Output(1024, -1);
      kryo.writeClassAndObject(output, object);
      output.close();
      return output.toBytes();
   }

   @Override
   public Object deserialiseObject (byte[] buffer) throws SerialisationException {
	  Objects.requireNonNull(buffer);
	  Object object;
	  try {
		  Input input = new Input(buffer);
		  object = kryo.readClassAndObject(input);
	  } catch (KryoException e) {
		  throw new SerialisationException(8, e);
	  }

	  // test class registration
	  if (!isRegisteredClass(object.getClass())) {
		  throw new SerialisationException(1, "unregistered object-class: " + object.getClass().getName());
	  }
      return object;
   }

   @Override
   public int getMethodID() {
      return METHOD_ID;
   }

   @Override
   public String getName () {
      return "Kryo-Serialisation";
   }

   @Override
   protected boolean verifyClass (Class<?> c) {
	   return c != null;
   }

}
