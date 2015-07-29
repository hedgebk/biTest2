package bthdg.osc;

import java.util.LinkedList;
import java.util.ListIterator;

public class TaskQueueProcessor implements Runnable {
    private final LinkedList<IOrderTask> m_tasksQueue = new LinkedList<IOrderTask>();
    private Thread m_thread;
    private boolean m_run = true;

    public void stop() {
        m_run = false;
    }

    public void addTask(IOrderTask task) {
        synchronized (m_tasksQueue) {
            for (ListIterator<IOrderTask> listIterator = m_tasksQueue.listIterator(); listIterator.hasNext(); ) {
                IOrderTask nextTask = listIterator.next();
                if (task.isDuplicate(nextTask)) {
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
            IOrderTask task = null;
            try {
                synchronized (m_tasksQueue) {
                    if (m_tasksQueue.isEmpty()) {
                        m_tasksQueue.wait();
                    }
                    task = m_tasksQueue.pollFirst();
                }
                if (task != null) {
                    task.process();
                }
            } catch (Exception e) {
                BaseExecutor.log("error in TaskQueueProcessor: " + e + "; for task " + task);
                e.printStackTrace();
            }
        }
        BaseExecutor.log("TaskQueueProcessor.queue: thread finished");
    }

    public interface IOrderTask {
        void process() throws Exception;
        boolean isDuplicate(IOrderTask other);
    }

    public static abstract class BaseOrderTask implements IOrderTask {
        @Override public boolean isDuplicate(IOrderTask other) {
            // single presence in queue task
            return getClass().equals(other.getClass());
        }
    }
}
