/*  File: SchedulableTimerTask.java
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

import java.util.Timer;
import java.util.TimerTask;

/** A java.util.TimerTask that can be scheduled at a Timer with the pre-defined
 * values for delay and period parameters.
 * 
 */
public abstract class SchedulableTimerTask extends TimerTask {
   private final int delay;
   private final int period;

   /** Creates a new schedulable timer task.
    * 
    * @param delay int milliseconds to delay task once scheduled
    * @param period int milliseconds of period for task repetition 
    *               (0 for no repetition) 
    * @param text String debugging text for this timer task (may be null)              
    */
   public SchedulableTimerTask (int delay, int period, String text) {
      this.delay = delay;
      this.period = period;
   }

   /** Creates a new schedulable timer task for one-time execution.
    * 
    * @param delay int milliseconds to delay task once scheduled
    * @param text String debugging text for this timer task (may be null)              
    */
   public SchedulableTimerTask (int delay, String text) {
      this.delay = delay;
      this.period = 0;
   }
   
   /** Schedules this timer-task according to its defined scheduling
    * parameters at the given timer instance.
    * 
    * @param timer java.util.Timer
    */
   public void schedule (Timer timer) {
      if (period > 0) {
         timer.schedule(this, delay, period);
      } else { 
         timer.schedule(this, delay);
      }
   }

   public int getDelay() {
      return delay;
   }

   public int getPeriod() {
      return period;
   }
   
}
