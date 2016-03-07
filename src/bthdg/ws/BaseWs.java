package bthdg.ws;

import bthdg.exch.Pair;

public abstract class BaseWs implements IWs {
    protected ITradesListener m_tradesListener;
    protected ITopListener m_topListener;
    protected IExecsListener m_execsListener;
    protected IAcctListener m_acctListener;
    protected int m_disconnectCount;

    @Override public void subscribeExecs(Pair pair, IExecsListener listener) throws Exception {
        throw new RuntimeException("subscribeExecs not implemented for " + this);
    }

    @Override public void subscribeAcct(IAcctListener listener) throws Exception {
        throw new RuntimeException("subscribeAcct not implemented for " + this);
    }

    @Override public int disconnectCount() {
        return m_disconnectCount;
    }
}
