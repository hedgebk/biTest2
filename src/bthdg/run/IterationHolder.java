package bthdg.run;

import bthdg.exch.Exchange;
import bthdg.exch.Pair;

public class IterationHolder {
    private final int m_count;
    private LiveOrdersMgr m_liveOrdersMgr;

    public IterationHolder(int count) {
        m_count = count;
    }

    public void queryLiveOrders( Exchange exchange, Pair pair, LiveOrdersMgr.ILiveOrdersCallback callback ) {
        LiveOrdersMgr liveOrdersMgr;
        synchronized (this) {
            if( m_liveOrdersMgr == null ) {
                m_liveOrdersMgr = new LiveOrdersMgr();
            }
            liveOrdersMgr = m_liveOrdersMgr;
        }
        liveOrdersMgr.queryLiveOrders( exchange, pair, callback );
    }

}
