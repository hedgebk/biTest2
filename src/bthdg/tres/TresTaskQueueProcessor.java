package bthdg.tres;

import bthdg.osc.BaseExecutor;
import bthdg.osc.TaskQueueProcessor;

import java.util.LinkedList;

public class TresTaskQueueProcessor extends TaskQueueProcessor {
    protected final LinkedList<BaseOrderTask> m_tradeTasksQueue = new LinkedList<BaseOrderTask>();
    protected final LinkedList<BaseOrderTask> m_topTasksQueue = new LinkedList<BaseOrderTask>();

    @Override protected LinkedList<BaseOrderTask> getQueue(BaseOrderTask task) {
        if (task instanceof BaseExecutor.TradeTask) {
            return m_tradeTasksQueue;
        }
        if (task instanceof BaseExecutor.TopTask) {
            return m_topTasksQueue;
        }
        return super.getQueue(task);
    }

    @Override protected BaseOrderTask poolTask() throws InterruptedException {
        BaseOrderTask ret = m_tradeTasksQueue.pollFirst();
        if (ret == null) {
            ret = m_topTasksQueue.pollFirst();
            if (ret == null) {
                ret = super.poolTask();
            }
        }
        return ret;
    }
}
