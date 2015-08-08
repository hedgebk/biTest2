package bthdg.osc;

import bthdg.util.Utils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

public class TaskQueueProcessor implements Runnable {
    private final LinkedList<BaseOrderTask> m_tasksQueue = new LinkedList<BaseOrderTask>();
    private Thread m_thread;
    private boolean m_run = true;
    Map<String,Utils.DoubleDoubleAverageCalculator> m_waitCalculators = new HashMap<String, Utils.DoubleDoubleAverageCalculator>();

    public void stop() {
        m_run = false;
    }

    public TaskQueueProcessor() {}

    public void addTask(BaseOrderTask task) {
        task.m_postTime = System.currentTimeMillis();
        synchronized (m_tasksQueue) {
            for (ListIterator<BaseOrderTask> listIterator = m_tasksQueue.listIterator(); listIterator.hasNext(); ) {
                BaseOrderTask nextTask = listIterator.next();
                if (task.isDuplicate(nextTask)) {
                    task.m_postTime = nextTask.m_postTime;
                    listIterator.remove();
                }
            }
            m_tasksQueue.addLast(task);
            m_tasksQueue.notify();
            if (m_thread == null) {
                m_thread = new Thread(this);
                m_thread.setName("TaskQueueProcessor");
                m_thread.start();
            }
        }
    }

    @Override public void run() {
        BaseExecutor.log("TaskQueueProcessor.queue: started thread");
        while (m_run) {
            BaseOrderTask task = null;
            try {
                synchronized (m_tasksQueue) {
                    if (m_tasksQueue.isEmpty()) {
                        m_tasksQueue.wait();
                    }
                    task = m_tasksQueue.pollFirst();
                }
                if (task != null) {
                    task.m_processTime = System.currentTimeMillis();
                    task.process();
                    long waitTime = task.m_processTime - task.m_postTime;
                    registerWaitTime(task, waitTime);
                }
            } catch (Exception e) {
                BaseExecutor.log("error in TaskQueueProcessor: " + e + "; for task " + task);
                e.printStackTrace();
            }
        }
        BaseExecutor.log("TaskQueueProcessor.queue: thread finished");
    }

    private void registerWaitTime(BaseOrderTask task, long waitTime) {
        String name = task.getClass().getSimpleName();
        Utils.DoubleDoubleAverageCalculator calculator;
        synchronized (m_waitCalculators) {
            calculator = m_waitCalculators.get(name);
            if (calculator == null) {
                calculator = new Utils.DoubleDoubleAverageCalculator();
                m_waitCalculators.put(name, calculator);
            }
        }
        calculator.addValue((double) waitTime);
    }

    public String dumpWaitTime() {
        StringBuilder buf = new StringBuilder();
        synchronized (m_waitCalculators) {
            for(Map.Entry<String, Utils.DoubleDoubleAverageCalculator> entry : m_waitCalculators.entrySet()) {
                String name = entry.getKey();
                Utils.DoubleDoubleAverageCalculator calculator = entry.getValue();
                double average = calculator.getAverage();
                buf.append(name).append("=").append(average).append("; ");
            }
        }
        return buf.toString();
    }

    public static abstract class BaseOrderTask {
        public long m_postTime;
        public long m_processTime;

        abstract void process() throws Exception;
        abstract boolean isDuplicate(BaseOrderTask other);
    }

    public static abstract class SinglePresenceTask extends BaseOrderTask {
        @Override public boolean isDuplicate(BaseOrderTask other) {
            // single presence in queue task
            return getClass().equals(other.getClass());
        }
    }
}
