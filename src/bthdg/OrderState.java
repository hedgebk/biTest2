package bthdg;

public enum OrderState {
    NONE,
    BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
            log("OrderState.BRACKET_PLACED. check if order executed");
            trackLimitOrderExecution(iContext, exchData, orderData);
        }
    },
    MARKET_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
            log("OrderState.MARKET_PLACED. check if order executed");
            boolean executed = trackLimitOrderExecution(iContext, exchData, orderData);
            if( executed ) {
                log(" OPEN MKT bracket order executed. we are fully OPENED");
            } else {
                log(" MKT order not yet executed - move if needed");
            }
        }
    };

    private static boolean trackLimitOrderExecution(IterationContext iContext, ExchangeData exchData, OrderData orderData) throws Exception {
        // actually order execution should be checked via getLiveOrdersState()
        LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);
        // but for simulation we are checking via trades
        TradesData newTrades = iContext.getNewTradesData(exchData.m_shExchData);
        orderData.xCheckExecutedLimit(iContext, exchData, orderData, newTrades);
        if (orderData.m_filled > 0) {
            if (orderData.m_status == OrderStatus.FILLED) {
                orderData.m_state = NONE;
                return true;
            } else { // PARTIALLY FILLED
                log("PARTIALLY FILLED, not supported yet - just wait more");
            }
        }
        return false;
    }

    private static void log(String s) { Log.log(s); }

    public void checkState(IterationContext iContext, ExchangeData exchangeData, OrderData orderData) throws Exception {
        log("checkState not implemented for OrderState." + this);
    }
} // OrderState
