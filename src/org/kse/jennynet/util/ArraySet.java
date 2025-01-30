/*  File: ArraySet.java
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

package org.kse.jennynet.util;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/** A {@code Set} implementation based on an {@code ArrayList}.
 * 
 */
public class ArraySet<E> extends AbstractSet<E> implements Serializable, Cloneable {
   private static final long serialVersionUID = 9327502872481L;

   private ArrayList<E> list = new ArrayList<E>();
   
   public ArraySet () {
   }

   public ArraySet ( Collection<E> c ) {
      addAll(c);
   }
   
   public ArraySet ( E[] c ) {
      addAll(c);
   }

   /** Adds all elements of the given array into this set iff they have a
    * value other than <b>null</b>.
    * 
    * @param c E[], may be null
    */
   public void addAll (E[] c) {
	   if (c != null) {
		   for (E e : c) {
			   if (e != null) {
				   add(e);
			   }
		   }
	   }
   }

@Override
   public Iterator<E> iterator() {
      return list.iterator();
   }

   @Override
   public int size() {
      return list.size();
   }

   @Override
   public boolean add (E e) {
      if ( list.contains(e) ) {
         return false;
      }
      list.add(e);
      return true;
   }

   @Override
   public boolean contains(Object o) {
      return list.contains(o);
   }

   @Override
   public boolean remove(Object o) {
      return list.remove(o);
   }

   @Override
   public void clear() {
      list.clear();
   }

   @SuppressWarnings("unchecked")
   @Override
   public Object clone() {
      try {
         ArraySet<E> copy = (ArraySet<E>)super.clone();
         copy.list = (ArrayList<E>)list.clone();
         return copy;
      } catch (CloneNotSupportedException e) {
         return null;
      }
   }
   
   
}
