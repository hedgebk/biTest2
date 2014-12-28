package bthdg.ws;

import bthdg.exch.TradeData;

public interface ITradesListener {
    void onTrade(TradeData tdata);
}
