package bthdg.util;

import bthdg.Log;

import java.util.LinkedList;

public abstract class Queue<Item> extends Thread {
    private final LinkedList<Item> m_queue = new LinkedList<Item>();
    private boolean m_run = true;

    public Queue(String name) {
        setName(name);
    }

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    protected abstract void processItem(Item tData);

    @Override public void run() {
        while (m_run) {
            Item item = null;
            try {
                synchronized (m_queue) {
                    item = m_queue.pollFirst();
                    if (item == null) {
                        m_queue.wait();
                        item = m_queue.pollFirst();
                    }
                }
                if (item != null) {
                    processItem(item);
                }
            } catch (Exception e) {
                err("error processing Queue[" + getName() + "] item=" + item + ". err=" + e, e);
            }
        }
        log("Queue[" + getName() + "] thread finished");
    }

    public void addItem(Item item) {
        synchronized (m_queue) {
            m_queue.addLast(item);
            m_queue.notify();
        }
    }

    public void stopQueue() {
        synchronized (m_queue) {
            m_run = false;
            m_queue.clear();
            m_queue.notify();
        }
    }
}
