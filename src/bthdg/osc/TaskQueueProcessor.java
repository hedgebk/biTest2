package bthdg.osc;

import bthdg.util.Utils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

public class TaskQueueProcessor implements Runnable {
    protected final Object m_semafore = new Object();
    protected final LinkedList<BaseOrderTask> m_tasksQueue = new LinkedList<BaseOrderTask>();
    private Thread m_thread;
    private boolean m_run = true;
    Map<String,Utils.DoubleDoubleAverageCalculator> m_waitCalculators = new HashMap<String, Utils.DoubleDoubleAverageCalculator>();
    private BaseOrderTask m_processingTask;

    public void stop() {
        m_run = false;
    }

    public TaskQueueProcessor() {}

    public void addTaskFirst(BaseOrderTask task) {
        task.m_postTime = System.currentTimeMillis();
        synchronized (m_semafore) {
            LinkedList<BaseOrderTask> tasksQueue = getQueue(task);
            compareWithQueueTasks(task, tasksQueue);
            tasksQueue.addFirst(task);
            m_semafore.notify();
            startThreadIfNeeded();
        }
    }

    public void addTask(BaseOrderTask task) {
        task.m_postTime = System.currentTimeMillis();
        synchronized (m_semafore) {
            LinkedList<BaseOrderTask> tasksQueue = getQueue(task);
            addTaskInt(task, tasksQueue);
            m_semafore.notify();
            startThreadIfNeeded();
        }
    }

    protected LinkedList<BaseOrderTask> getQueue(BaseOrderTask task) {
        return m_tasksQueue;
    }

    private void addTaskInt(BaseOrderTask task, LinkedList<BaseOrderTask> tasksList) {
        Boolean addLast = compareWithQueueTasks(task, tasksList);
        if (addLast != null) {
            if (addLast) {
                tasksList.addLast(task);
            } else {
                tasksList.addFirst(task);
            }
        }
    }

    private Boolean compareWithQueueTasks(BaseOrderTask task, LinkedList<BaseOrderTask> tasksList) {
        Boolean addLast = Boolean.TRUE;
        for (ListIterator<BaseOrderTask> listIterator = tasksList.listIterator(); listIterator.hasNext(); ) {
            BaseOrderTask nextTask = listIterator.next();
            DuplicateAction duplicateAction = task.isDuplicate(nextTask);
            if (duplicateAction == DuplicateAction.REMOVE_ALL_AND_PUT_AS_LAST) {
                BaseExecutor.log(" replacing as LAST task " + nextTask.getClass().getSimpleName() + "; tasksList.size=" + tasksList.size() + "; processingTask=" + m_processingTask);
                task.m_postTime = nextTask.m_postTime;
                listIterator.remove();
            } else if (duplicateAction == DuplicateAction.REMOVE_ALL_AND_PUT_AS_FIRST) {
                BaseExecutor.log(" replacing as FIRST task " + nextTask.getClass().getSimpleName() + "; tasksList.size=" + tasksList.size() + "; processingTask=" + m_processingTask);
                task.m_postTime = nextTask.m_postTime;
                listIterator.remove();
                addLast = Boolean.FALSE;
            } else if (duplicateAction == DuplicateAction.DO_NOT_ADD) {
                BaseExecutor.log(" skipping task " + nextTask.getClass().getSimpleName() + "; tasksList.size=" + tasksList.size() + "; processingTask=" + m_processingTask);
                addLast = null; // do not add
                break;
            }
        }
        return addLast;
    }

    protected void startThreadIfNeeded() {
        if (m_thread == null) {
            m_thread = new Thread(this);
            m_thread.setName("TaskQueueProcessor");
            m_thread.start();
        }
    }

    @Override public void run() {
        BaseExecutor.log("TaskQueueProcessor.queue: started thread");
        while (m_run) {
            BaseOrderTask task = null;
            try {
                synchronized (m_semafore) {
                    task = poolTask();
                    if (task == null) {
                        m_semafore.wait();
                    }
                }
                if (task != null) {
                    m_processingTask = task;
                    task.m_processTime = System.currentTimeMillis();
                    task.process();
                    long waitTime = task.m_processTime - task.m_postTime;
                    registerWaitTime(task, waitTime);
                    m_processingTask = null;
                }
            } catch (Exception e) {
                BaseExecutor.log("error in TaskQueueProcessor: " + e + "; for task " + task);
                e.printStackTrace();
            }
        }
        BaseExecutor.log("TaskQueueProcessor.queue: thread finished");
    }

    protected BaseOrderTask poolTask() throws InterruptedException {
        return m_tasksQueue.pollFirst();
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
                buf.append(name).append("=").append(Utils.format3(average)).append("; ");
            }
        }
        return buf.toString();
    }

    public static abstract class BaseOrderTask {
        public long m_postTime;
        public long m_processTime;

        protected abstract void process() throws Exception;
        public abstract DuplicateAction isDuplicate(BaseOrderTask other);
    }

    public enum DuplicateAction {
        DO_NOT_ADD,
        REMOVE_ALL_AND_PUT_AS_LAST,
        REMOVE_ALL_AND_PUT_AS_FIRST,
    }

    public static abstract class SinglePresenceTask extends BaseOrderTask {
        @Override public DuplicateAction isDuplicate(BaseOrderTask other) {
            // single presence in queue task
            return getClass().equals(other.getClass()) ? DuplicateAction.REMOVE_ALL_AND_PUT_AS_LAST : null;
        }
    }
}
