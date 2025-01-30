package org.kse.jennynet.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.Objects;

import org.kse.jennynet.exception.SerialisationException;
import org.kse.jennynet.intfa.Serialization;
import org.kse.jennynet.util.Util;

/**
 * Java-Serialisation uses the Java-built-in mechanics to serialise objects
 * which are descendants of the {@code Serializable} interface. The classes of
 * objects to be translated need to be registered before serialisation can take 
 * place. Qualifying classes need to be tagged with the {@code Serializable} 
 * interface, own a preferably static {@code long serialVersionUID} member
 * with a unique value and may not contain any structure in its data tree 
 * which is not serialisable and not transient.
 * <p>The serialisation METHOD identifier of this class is 1.
 * <p>The Java-Serialisation should be chosen if other more efficient methods 
 * don't render satisfying results. Java-Serialisation is reliable and offers 
 * a clear knowledge of what can be serialised and what not. It is slower than
 * other methods, and the choice when to prefer it may also rest on the 
 * transfer load the channel has to master.   
 */
public class JavaSerialisation extends AbstractSerialization {
	private static final int METHOD_ID = 0;

	public JavaSerialisation() {
	}

	@Override
	public Serialization copy() {
	    JavaSerialisation c = (JavaSerialisation)super.clone();
		return c;
	}

	@Override
	public byte[] serialiseObject (Object object) throws SerialisationException {
		  Objects.requireNonNull(object);
		  if (!isRegisteredClass(object.getClass()))
			  throw new SerialisationException(1, "unregistered object-class: " + object.getClass().getName());
		  
		  ByteArrayOutputStream out = new ByteArrayOutputStream(128);
		  ObjectOutputStream oos = null;
		  try {
			  oos = new ObjectOutputStream(out);
	    	  oos.writeObject(object);
	    	  oos.close();
	    	  byte[] result = out.toByteArray();
	    	  return result;
	      } catch (NotSerializableException e) {
		      throw new SerialisationException(3, "(serialise) object not serialisable", e);
		  } catch (IOException e) {
			  throw new SerialisationException(6, "writing error", e);
		  }
	}

	@Override
	public Object deserialiseObject (byte[] buffer) throws SerialisationException {
		Objects.requireNonNull(buffer);
		InputStream input = new ByteArrayInputStream(buffer);
		Object object;
		try {
			ObjectInputStream ois = new ObjectInputStream(input);
			object = ois.readObject();
			ois.close();
		} catch (ClassNotFoundException | InvalidClassException e) {
		    throw new SerialisationException(4, "(deserialise) object class faulty or not available", e);
		} catch (StreamCorruptedException e) {
			throw new SerialisationException(7, "data integrity error", e);
		} catch (IOException e) {
			throw new SerialisationException(5, "reading error", e);
		}
		
		// test class registration
		if (!isRegisteredClass(object.getClass())) {
			throw new SerialisationException(1, "unregistered object-class: " + object.getClass().getName());
		}
		return object;
	}

	@Override
	public int getMethodID() {return METHOD_ID;}

	@Override
	public String getName () {return "Java-Serialisation";}

	@Override
	protected boolean verifyClass (Class<?> c) {
		try {
			c.asSubclass(Serializable.class);
			return true;
		} catch (ClassCastException e) {
			return false;
		}
	}

}
