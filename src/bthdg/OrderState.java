package bthdg;

public enum OrderState {
    NONE {
        @Override public void checkState(IterationContext iContext, SharedExchangeData shExchData, OrderData orderData) throws Exception {}
    },
    BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, SharedExchangeData shExchData, OrderData orderData) throws Exception {
//            log("OrderState.BRACKET_PLACED. check if order executed: " + orderData);
            trackLimitOrderExecution(iContext, shExchData, orderData);
        }
    },
    MARKET_PLACED {
        @Override public void checkState(IterationContext iContext, SharedExchangeData shExchData, OrderData orderData) throws Exception {
            log("OrderState.MARKET_PLACED. check if order executed: " + orderData);
            boolean executed = trackLimitOrderExecution(iContext, shExchData, orderData);
            if( executed ) {
                log(" OPEN MKT bracket order executed. we are fully OPENED " + orderData);
            } else {
                log(" MKT order not yet executed - move if needed");
            }
        }
    };

    private static boolean trackLimitOrderExecution(IterationContext iContext, SharedExchangeData shExchData, OrderData orderData) throws Exception {
        // actually order execution should be checked via getLiveOrdersState()
        LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(shExchData);
        // but for simulation we are checking via trades
        TradesData newTrades = iContext.getNewTradesData(shExchData);
        orderData.xCheckExecutedLimit(iContext, shExchData, orderData, newTrades);
        if (orderData.m_filled > 0) {
            if (orderData.m_status == OrderStatus.FILLED) {
                orderData.m_state = NONE;
                iContext.onOrderFilled(shExchData, orderData);
                return true;
            } else { // PARTIALLY FILLED
                log("PARTIALLY FILLED, just wait more");
            }
        }
        return false;
    }

    private static void log(String s) { Log.log(s); }

    public void checkState(IterationContext iContext, SharedExchangeData shExchData, OrderData orderData) throws Exception {
        log("checkState not implemented for OrderState." + this);
    }
} // OrderState
