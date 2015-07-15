package bthdg.util;

import bthdg.Log;

public class Snoozer implements Runnable {
    private final long m_delay;
    private long m_wakeTime;
    private Thread m_thread;
    private boolean m_stopped;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    protected void wakeUp() {}

    public Snoozer(long delay) {
        m_delay = delay;
    }

    public void update() {
log("Snoozer.update");
        synchronized (this) {
            if (!m_stopped) {
                if (m_wakeTime == 0) {
                    m_wakeTime = System.currentTimeMillis() + m_delay;
log(" scheduling at " + m_wakeTime);
                    if (m_thread == null) {
                        m_thread = new Thread(this);
                        m_thread.start();
                    } else {
                        notify();
                    }
                } else {
log(" already scheduled at " + m_wakeTime);
                }
            } else {
                log("ignored update - snoozer stopped");
            }
        }
    }

    @Override public void run() {
        log("Snoozer thread started");
        try {
            while (m_thread != null) {
                Long sleepTime;
                synchronized (this) {
                    if (m_wakeTime == 0) {
log("Snoozer: not scheduled - wait...");
                        this.wait();
                    }
                    if (m_wakeTime != 0) {
                        sleepTime = m_wakeTime - System.currentTimeMillis();
                        m_wakeTime = 0;
                    } else {
                        sleepTime = null;
                    }
                }
                // do outside of synchronized() block
                if (sleepTime != null) {
                    if (sleepTime > 0) {
log("Snoozer: sleeping " + sleepTime + " ms");
                        Thread.sleep(sleepTime);
log("Snoozer: sleeping done");
                    } else {
log("Snoozer: no need to sleep. sleepTime=" + sleepTime + " ms");
                    }
                    try {
                        wakeUp();
                    } catch (Exception e) {
                        err("Snoozer error in wakeUp processing: " + e, e);
                    }
                }
            }
        } catch (InterruptedException e) {
            err("Snoozer interrupted: " + e, e);
        }
        log("Snoozer thread finished");
    }

    public void stop() {
        log("stopping Snoozer");
        synchronized (this) {
            m_wakeTime = 0;
            if (m_thread != null) {
                m_thread = null;
                notify();
            }
            m_stopped = true;
        }
    }
}
