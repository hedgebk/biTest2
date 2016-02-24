package bthdg.ws;

import bthdg.exch.Exchange;
import bthdg.exch.Pair;

public interface IWs {
    Exchange exchange();
    void connect(Runnable runnable);
    void subscribeTrades(Pair pair, ITradesListener listener) throws Exception;
    void subscribeTop(Pair pair, ITopListener listener) throws Exception;
    void subscribeExecs(Pair pair, IExecsListener listener) throws Exception;
    void stop();
    String getPropPrefix();
    void reconnect();
}
