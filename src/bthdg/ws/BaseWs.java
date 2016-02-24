package bthdg.ws;

import bthdg.exch.Pair;

public abstract class BaseWs implements IWs {
    protected ITradesListener m_tradesListener;
    protected ITopListener m_topListener;
    protected IExecsListener m_execsListener;

    @Override public void subscribeExecs(Pair pair, IExecsListener listener) throws Exception {
        throw new RuntimeException("subscribeExecs not implemented for " + this);
    }
}
