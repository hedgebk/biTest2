package bthdg;

import java.util.Map;

public enum OrderState {
    NONE {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {}
    },
    BRACKET_PLACED {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
            trackLimitOrderExecution(iContext, exchange, orderData, listener, account, null);
        }
    },
    MARKET_PLACED {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
            boolean executed = trackLimitOrderExecution(iContext, exchange, orderData, listener, account, holder);
            if( executed ) {
                log(" OPEN MKT bracket order executed. we are fully OPENED " + orderData);
            } else {
                log(" MKT order not yet executed - move if needed");
            }
        }
    };

    private static boolean trackLimitOrderExecution(IIterationContext iContext, Exchange exchange,
                                                    OrderData orderData, IOrderExecListener listener,
                                                    AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
        // actually order execution should be checked via getLiveOrdersState()
        //LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(shExchData);
        // but for simulation we are checking via trades
        Map<Pair, TradesData> newTradesMap = iContext.getNewTradesData(exchange, holder);
        TradesData newTrades = newTradesMap.get(orderData.m_pair);
        orderData.xCheckExecutedLimit(iContext, exchange, newTrades, account);
        if (orderData.m_filled > 0) {
            if (orderData.m_status == OrderStatus.FILLED) {
                orderData.m_state = NONE;
                listener.onOrderFilled(iContext, exchange, orderData);
                return true;
            } else { // PARTIALLY FILLED
                log("PARTIALLY FILLED, just wait more");
            }
        }
        return false;
    }

    private static void log(String s) { Log.log(s); }

    public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                           AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
        log("checkState not implemented for OrderState. " + this);
    }

    public interface IOrderExecListener {
        void onOrderFilled(IIterationContext iContext, Exchange exchange, OrderData orderData);
    }
} // OrderState
