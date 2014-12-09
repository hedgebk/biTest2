package bthdg.ws;

import bthdg.exch.Pair;

public interface IWs {
    void subscribeTrades(Pair pair, ITradesListener listener);
}
