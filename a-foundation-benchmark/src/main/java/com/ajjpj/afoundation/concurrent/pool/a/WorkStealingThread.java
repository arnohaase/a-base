package com.ajjpj.afoundation.concurrent.pool.a;

import com.ajjpj.afoundation.collection.immutable.AList;
import com.ajjpj.afoundation.concurrent.pool.a.WorkStealingPoolImpl.ASubmittable;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;


/**
 * @author arno
 */
class WorkStealingThread extends Thread {
    final WorkStealingLocalQueue queue = new WorkStealingLocalQueue (true);
    final WorkStealingPoolImpl pool;

    @SuppressWarnings ("unused") // is written with Unsafe.putOrderedObject
    private volatile ASubmittable wakeUpTask;

    private final int skipLocalQueueFrequency = 100; //TODO make configurable
    private final int pollNanosBeforePark = 100; //TODO make configurable

    //TODO optimization: is a lazySet sufficient for in-thread access as long as other threads use a volatile read? Is there a 'lazy CAS'?

    public WorkStealingThread (WorkStealingPoolImpl pool) {
        this.pool = pool;
    }

    @Override public void run () {
        int msgCount = 0;

        while (true) {
            msgCount += 1;

            try {
                ASubmittable newTask;

                final boolean pollGlobalQueueFirst = msgCount % skipLocalQueueFrequency == 0;

                // polling the global queue first once in a while avoids starvation of work from the global queue. This is
                //  important in systems where locally produced work can saturate the pool, e.g. in actor-based systems.
                if (pollGlobalQueueFirst && (newTask = pool.globalQueue.poll ()) != null) {
                    newTask.run ();
                    continue;
                }

                if ((newTask = queue.nextLocalTask ()) != null) {
                    newTask.run ();
                    continue;
                }

                // this is the 'normal case' search in the global queue: *after* looking in this thread's local queue
                if (! pollGlobalQueueFirst && (newTask = pool.globalQueue.poll ()) != null) {
                    newTask.run ();
                    continue;
                }

                //TODO work stealing
                waitForWork ();
            }
            catch (WorkStealingShutdownException exc) {
                // this exception signals that the thread pool was shut down

                //noinspection finally
                try {
                    pool.onThreadFinished (this);
                }
                catch (Throwable exc2) {
                    exc2.printStackTrace ();
                }
                finally {
                    //noinspection ReturnInsideFinallyBlock
                    return;
                }
            }
            catch (Exception exc) {
                exc.printStackTrace (); //TODO exception handling
            }
        }
    }

    private void waitForWork () {
        ASubmittable newTask;// There is currently no work available for this thread. That means that there is currently not enough work for all
        //  worker threads, i.e. the pool is in a 'low load' situation.
        //
        // Now we want to park this thread until work becomes available. There are basically two ways of doing that, and they
        //  have different trade-offs: Pushing work from the producing thread (i.e. calling 'unpark' from the producing thread
        //  when work becomes available), or polling from this thread (i.e. waiting some time and checking for work, without
        //  involving any producing threads in this thread's scheduling).
        //
        // Unparking a thread incurs a significant overhead *for the caller of 'unpark'*. In the 'push' approach, a producer
        //  thread is burdened with this overhead, which can severely limit throughput for high-frequency producers. Polling
        //  on the other hand causes each idling thread to place an ongoing load on the system.
        //
        // The following code compromises, starting out by polling and then parking itself, waiting to be awakened by a
        //  'push' operation when work becomes available.

        //TODO configurable iterations - including 'poll only'
        // wait a little while and look again before really going to sleep
        LockSupport.parkNanos (pollNanosBeforePark);
        if ((newTask = pool.globalQueue.poll ()) != null) {
            newTask.run ();
            return;
        }

        preparePark ();

        do {
            queue.checkShutdown ();
            LockSupport.park (); //TODO exception handling
            newTask = wakeUpTask;

            //TODO delay 'stealing' of work produced by other threads

            if (newTask == null) {
                // for other cases, shutdown is checked after the task is run anyway
                queue.checkShutdown ();
            }
        }
        while (newTask == null);
        U.putOrderedObject (this, WAKE_UP_TASK, null); //TODO replace with U.compareAndSwap? --> does that have volatile read semantics? Is that even faster

        newTask.run ();
    }

    void wakeUpWith (ASubmittable task) {
        //TODO is 'putOrderedObject' guaranteed to work on a volatile field?
        U.putOrderedObject (this, WAKE_UP_TASK, task); // is read with volatile semantics after wake-up

        LockSupport.unpark (this);
    }

    private void preparePark() {
        // removal from the stack of 'waiting workers' happens in the pool
        AList<WorkStealingThread> before;
        do {
            before = pool.waitingWorkers.get ();
        }
        while (! pool.waitingWorkers.compareAndSet (before, before.cons (this)));
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long WAKE_UP_TASK;
    static {
        try {
            final Field f = sun.misc.Unsafe.class.getDeclaredField ("theUnsafe");
            f.setAccessible (true);
            U = (Unsafe) f.get (null);

            Class<?> k = WorkStealingThread.class;
            WAKE_UP_TASK = U.objectFieldOffset (k.getDeclaredField("wakeUpTask"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
