package bthdg.ws;

import bthdg.exch.Exchange;
import bthdg.exch.Pair;

public interface IWs {
    Exchange exchange();
    void subscribeTrades(Pair pair, ITradesListener listener) throws Exception;
    void subscribeTop(Pair pair, ITopListener listener) throws Exception;
    void stop();
}
