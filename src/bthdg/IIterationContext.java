package bthdg;

import bthdg.exch.*;

import java.util.Map;

public interface IIterationContext {
    boolean acceptPriceSimulated();
    void acceptPriceSimulated(boolean b);
    Map<Pair, TradesData> fetchTrades(Exchange exchange) throws Exception;
    Map<Pair, TradesData> getNewTradesData(Exchange exchange, TradesData.ILastTradeTimeHolder holder) throws Exception;
    TopData getTop(Exchange exchange, Pair pair) throws Exception;
    OrdersData getLiveOrders(Exchange exchange) throws Exception;
}
