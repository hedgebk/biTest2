package bthdg.util;

import bthdg.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sync {
    public static void waitInThreadIfNeeded(final List<AtomicBoolean> sync, final Runnable callback) {
        // check if anything is pending first
        for (AtomicBoolean obj : sync) {
            synchronized (obj) {
                boolean flag = obj.get();
                if (!flag) {
                    waitInThread(sync, callback);
                    return;
                }
            }
        }
        callback.run(); // nothing to wait
        callback.run(); // nothing to wait
    }

    public static void waitInThread(final List<AtomicBoolean> sync, final Runnable callback) {
        new Thread() {
            @Override public void run() {
                for (AtomicBoolean obj : sync) {
                    synchronized (obj) {
                        boolean flag = obj.get();
                        if (!flag) {
                            try {
                                obj.wait();
                            } catch (InterruptedException e) {
                                Log.err("interrupted: " + e, e);
                            }
                        }
                    }
                }
                callback.run();
            }
        }.start();
    }

    public static void wait(List<AtomicBoolean> ret) {
        if (ret != null) {
            for (AtomicBoolean sync : ret) {
                synchronized (sync) {
                    boolean flag = sync.get();
                    if (!flag) {
                        try {
                            sync.wait();
                        } catch (InterruptedException e) {
                            Log.err("interrupted: " + e, e);
                        }
                    }
                }
            }
        }
    }

    public static List<AtomicBoolean> addSync(List<AtomicBoolean> in, List<AtomicBoolean> stat) {
        List<AtomicBoolean> ret = in;
        if (stat != null) {
            if (ret == null) {
                ret = stat;
            } else {
                ret.addAll(stat);
            }
        }
        return ret;
    }

    public static List<AtomicBoolean> addSync(List<AtomicBoolean> in, AtomicBoolean stat) {
        List<AtomicBoolean> ret = in;
        if (stat != null) {
            if (ret == null) {
                ret = new ArrayList<AtomicBoolean>();
            }
            ret.add(stat);
        }
        return ret;
    }

    public static void setAndNotify(AtomicBoolean sync) {
        synchronized (sync) {
            sync.set(true);
            sync.notifyAll();
        }
    }
}
