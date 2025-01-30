/*  File: SemaphorLock.java
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

package org.kse.jennynet.test;

/** This is a counter based semaphore implementation. Any number of threads
 *  can wait on this semaphore until the counter becomes zero or optionally
 *  an amount of time has elapsed. On the falling flank of the counter
 *  becoming zero all waiting threads are awakened.
 *
 *  <p>Note: the "lock_wait" method is stabilised against Java's 
 *  "spurious" thread wakeup.
 */
public class SemaphorLock {

	private Object lock = new Object();
	private int counter;

	/** Creates a new <code>SemaphorLock</code> with an initial counter 
	 * value of zero.
	 */
	public SemaphorLock () {
	}
	
	/** Creates a new <code>SemaphorLock</code> with an initial counter 
	 * value as given by the parameter.
	 * 
	 * @param count int initial counter value
	 */
	public SemaphorLock (int count) {
		if (count < 0) 
			throw new IllegalArgumentException("illegal negative value");
		counter = count;
	}
	
	/** Increases the counter of this semaphore by 1.
	 */
	public void inc () {
		counter++;
	}
	
	/** Decreases the counter of this semaphore by 1 if its value is
	 * above zero. If the counter becomes zero on the falling flank in the 
	 * course of this action, the semaphore is released.
	 */
	public void dec () {
		if (counter > 0) {
		   counter--;
		}
		check_release();
	}

	private void check_release () {
	   if (counter == 0) {
		   release();
	   }
	}
	
	/** Returns the current counter state. 0 means the semaphore is unlocked.
	 * 
	 * @return int counter value
	 */
	public int getCounter () {
		return counter;
	}
	
	/** Sets the counter value on this semaphore lock. The value zero will
	 * release the lock.
	 * 
	 * @param value int counter value (0..n)
	 */
	public void setCounter (int value) {
		if (value < 0) 
			throw new IllegalArgumentException("negative value");
		counter = value;
		check_release();
	}
	
	/** Releases all threads currently waiting on the lock of this semaphore
	 * and sets the counter to zero. 
	 */
	public void release () {
		synchronized (lock) {
			lock.notifyAll();
			counter = 0;
		}
	}

	/** Lets the calling thread wait for the given amount of time on this 
	 * semaphore's lock to open if and only if the counter's value is above 
	 * zero. The lock opens if method "release" is called or if the counter
	 * value becomes zero.
	 *  
	 * @param time long milliseconds to wait; 0 for endless wait
	 * @throws InterruptedException
	 */
	public void lock_wait (long time) throws InterruptedException {
		if (counter > 0 && time > -1) {
			long elapsed = 0;
			long start = System.currentTimeMillis();
			
			synchronized(lock) {
				// we have to make all this effort bc. of the socalled "spurious" thread resumption
				while (elapsed-1 < time && counter > 0) {
					lock.wait(time-elapsed);
					elapsed = System.currentTimeMillis() - start;
				}
			}
		}
	}

}
