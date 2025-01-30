package org.kse.jennynet.core;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.kse.jennynet.exception.SerialisationUnavailableException;
import org.kse.jennynet.intfa.Connection;
import org.kse.jennynet.intfa.Serialization;

/** Foundation class for {@code Serialization} implementations.
 * This public class gives developers opportunity to create their own 
 * serialisation devices which can be used with {@code Connection} instances.
 */
public abstract class AbstractSerialization implements Cloneable, Serialization {

	protected LinkedHashMap<Class<?>, Class<?>> classMap = new LinkedHashMap<>();

	public AbstractSerialization() {
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Object clone() {
		AbstractSerialization ser = null;
		try {
			ser = (AbstractSerialization) super.clone();
	        ser.classMap = (LinkedHashMap<Class<?>, Class<?>>) classMap.clone();
		} catch (CloneNotSupportedException e) {
		}
		return ser;
	}
	
	@Override
	public boolean isRegisteredClass(Class<?> c) {
	    return classMap.containsKey(c);
	}

	@Override
	public boolean registerClass (Class<?> c) throws NotSerializableException {
		Objects.requireNonNull(c);
	    if (classMap.containsKey(c)) return false;
	    if (!verifyClass(c)) {
	    	throw new NotSerializableException(c.getName());
	    }
	    
	    classMap.put(c, null);
	    return true;
	}

	/** Whether the given class qualifies to be serialised. This is a 
	 * categorical property of the class and has nothing to do with being 
	 * registered. 
	 * 
	 * @param c {@code Class<?>}
	 * @return boolean true = serialisable, false = not serialisable
	 */
	protected abstract boolean verifyClass (Class<?> c);

	@Override
	public List<Class<?>> getRegisteredClasses() {
	   return new ArrayList<Class<?>>(classMap.keySet());
    }

	@Override
	public String toString() {
        return getName() + ", method = " + getMethodID() + ", (" + classMap.size() + " classes)";
    }

	@Override
	public int getRegisteredSize() {
	    return classMap.size();
	}

	@Override
	public void clear() {
		classMap.clear();
	}

	
}