
//
// ThreadPool.java
//

/*
VisAD system for interactive analysis and visualization of numerical
data.  Copyright (C) 1996 - 1999 Bill Hibbard, Curtis Rueden, Tom
Rink and Dave Glowacki.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 1, or (at your option)
any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License in file NOTICE for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package visad.util;

import java.util.Vector;

/** A pool of threads (with minimum and maximum limits on the number
 *  of threads) which can be used to execute any Runnable tasks.
 */
public class ThreadPool
{
  private static final int DEFAULT_MIN_THREADS = 1;
  private static final int DEFAULT_MAX_THREADS = 10;

  // maximum number of tasks which can be queued before a new thread is created
  private int maxQueuedTasks = 3;

  // minimum and maximum number of threads to create
  private int minThreads;
  private int maxThreads;

  // generic lock object
  private Object threadLock = new Object();

  // 'true' if all threads should exit
  private boolean terminateThread = false;

  // list of threads in the pool
  private Vector threads = new Vector();

  // list of queued tasks
  private Vector tasks = new Vector();

  private class ThreadMinnow
      extends Thread
  {
    private ThreadPool parent = null;

    public ThreadMinnow(ThreadPool p)
    {
      parent = p;
      start();
    }

    public void run()
    {
      while (true) {
        // try to find something to do...
        Runnable r = parent.getTask();
        if (r != null) {
          r.run();
        } else {

          // if we're supposed to stop, break out of the infinite loop
          if (terminateThread) {
            return;
          }

          // NOTE:
          //   the 'terminateThread' check only happens when there's no
          //   work to be done.  This is to ensure that all outstanding
          //   tasks are completed.


          // wait until there's work to be done
          try {
            synchronized (threadLock) {
              threadLock.wait();
            }
          } catch (InterruptedException e) {
            // ignore interrupts ...
          }
        }
      }
    }
  }

  /** Build a thread pool with the default minimum and maximum
   *  numbers of threads
   */
  public ThreadPool()
        throws Exception
  {
    this(DEFAULT_MIN_THREADS, DEFAULT_MAX_THREADS);
  }

  /** Build a thread pool with the specified maximum number of
   *  threads, and the default minimum number of threads
   */
  public ThreadPool(int max)
        throws Exception
  {
    this(DEFAULT_MIN_THREADS, max);
  }

  /** Build a thread pool with the specified minimum and maximum
   *  numbers of threads
   */
  public ThreadPool(int min, int max)
        throws Exception
  {
    minThreads = min;
    maxThreads = max;

    if (minThreads > maxThreads) {
      throw new Exception("Maximum number of threads (" + maxThreads +
                          ") is less than minimum number of threads (" +
                          minThreads + ")");
    }

    for (int i = 0; i < minThreads; i++) {
      threads.addElement(new ThreadMinnow(this));
    }
  }

  /** Add a task to the queue; tasks are executed as soon as a thread
   *  is available, in the order in which they are submitted
   */
  public void queue(Runnable r)
  {
    // don't queue new tasks after the pool has been shut down
    if (terminateThread) {
      throw new Error("Task queued after threads stopped");
    }

    // add this task to the queue
    int numTasks = 0;
    synchronized (tasks) {
      tasks.addElement(r);
      numTasks = tasks.size();
    }

    // make sure one or more threads are told to deal with the new task
    synchronized (threadLock) {
      // if all the threads appear to be busy...
      if (numTasks > maxQueuedTasks) {

        // ...and we haven't created too many threads...
        if (threads.size() < maxThreads) {

          // ...spawn a new thread and tell it to deal with this
          Thread t = new ThreadMinnow(this);
          threads.addElement(t);
          threadLock.notify();
        } else {

          // try to wake up all waiting threads to deal with the backlog
          threadLock.notifyAll();
        }
      } else {

        // not all threads are busy; notify one of the waiting threads
        threadLock.notify();
      }
    }
  }

  /** Get the next task on the queue.<BR>
   *  This method is intended only for the use of client threads and
   *  should never be called by external objects.
   */
  Runnable getTask()
  {
    Runnable thisTask = null;

    synchronized (tasks) {
      if (tasks.size() > 0) {
        thisTask = (Runnable )tasks.elementAt(0);
        tasks.removeElementAt(0);
      }
    }

    return thisTask;
  }

  public void setThreadMaximum(int num)
        throws Exception
  {
    if (num < maxThreads) {
      throw new Exception("Cannot decrease maximum number of threads");
    }
    maxThreads = num;
  }

  /** Stop all threads as soon as all queued tasks are completed */
  public void stopThreads()
  {
    terminateThread = true;
    synchronized (threadLock) {
      threadLock.notifyAll();
    }

    for (int i = threads.size() - 1; i >= 0; i--) {
      Thread t = (Thread )threads.elementAt(i);
      while (true) {
        try {
          t.join();
          break;
        } catch (InterruptedException e) {
        }
      }
    }
  }
}

/*
 * Here's a simple test program for the ThreadPool code.  Save it to
 * 'SimpleTask.java':
 *
 * import java.util.Random;
 * 
 * import visad.util.ThreadPool;
 * 
 * public class SimpleTask implements Runnable {
 *   private static Random rand = new Random();
 * 
 *   private int count = 0;
 * 
 *   public SimpleTask() { }
 * 
 *   public void run()
 *   {
 *     count++;
 *     try { Thread.sleep((rand.nextInt() % 10), 0); } catch (Throwable t) { }
 *   }
 * 
 *   public int getCount() { return count; }
 * 
 *   public static void main(String[] args)
 *   {
 *     ThreadPool pool;
 *     try {
 *       pool = new ThreadPool();
 *     } catch (Exception e) {
 *       System.err.println("Couldn't build ThreadPool: " + e.getMessage());
 *       System.exit(1);
 *       return;
 *     }
 * 
 *     // give threads a chance to start up
 *     try { Thread.sleep(100, 0); } catch (Throwable t) { }
 * 
 *     SimpleTask[] task = new SimpleTask[4];
 *     for (int i = 0; i < task.length; i++) {
 *       task[i] = new SimpleTask();
 *     }
 * 
 *     for (int i = 0; i < 10; i++) {
 *       for (int j = 0; j < task.length; j++) {
 *         pool.queue(task[j]);
 *       }
 *       try { Thread.sleep(10, 0); } catch (Throwable t) { }
 *     }
 * 
 *     pool.stopThreads();
 * 
 *     boolean success = true;
 *     for (int i = 0; i < task.length; i++) {
 *       int c = task[i].getCount();
 *       if (c != 10) {
 *         System.err.println("Got " + c + " for task#" + i + ", expected 10");
 *         success = false;
 *       }
 *     }
 * 
 *     if (success) System.out.println("Success!");
 *   }
 * }
 * 
 */
