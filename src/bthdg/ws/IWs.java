package bthdg.ws;

import bthdg.exch.Exchange;
import bthdg.exch.Pair;

public interface IWs {
    Exchange exchange();
    void subscribeTrades(Pair pair, ITradesListener listener);
    void subscribeTop(Pair pair, ITopListener listener);
}
