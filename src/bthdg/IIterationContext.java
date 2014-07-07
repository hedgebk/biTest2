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

    public static abstract class BaseIterationContext implements IIterationContext {
        @Override public boolean acceptPriceSimulated() {
            throw new RuntimeException("BaseIterationContext.acceptPriceSimulated not implemented");
        }
        @Override public void acceptPriceSimulated(boolean b) {
            throw new RuntimeException("BaseIterationContext.acceptPriceSimulated not implemented");
        }
        @Override public Map<Pair, TradesData> fetchTrades(Exchange exchange) throws Exception {
            throw new RuntimeException("BaseIterationContext.fetchTrades not implemented");
        }
        @Override public Map<Pair, TradesData> getNewTradesData(Exchange exchange, TradesData.ILastTradeTimeHolder holder) throws Exception {
            throw new RuntimeException("BaseIterationContext.getNewTradesData not implemented");
        }
        @Override public TopData getTop(Exchange exchange, Pair pair) throws Exception {
            throw new RuntimeException("BaseIterationContext.getTop not implemented");
        }
        @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception {
            throw new RuntimeException("BaseIterationContext.getLiveOrders not implemented");
        }
    }
}
