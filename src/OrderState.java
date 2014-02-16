public enum OrderState {
    NONE,
    BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, Fetcher.ExchangeData exchData, OrderData orderData) throws Exception {
            System.out.println("OrderState.BRACKET_PLACED. check if order executed");
            trackLimitOrderExecution(iContext, exchData, orderData);
        }
    },
    MARKET_PLACED {
        @Override public void checkState(IterationContext iContext, Fetcher.ExchangeData exchData, OrderData orderData) throws Exception {
            System.out.println("OrderState.MARKET_PLACED. check if order executed");
            boolean executed = trackLimitOrderExecution(iContext, exchData, orderData);
            if( executed ) {
                System.out.println(" OPEN MKT bracket order executed. we are fully OPENED");
            } else {
                System.out.println(" MKT order not yet executed - check and move ef needed");
            }
        }
    };

    private static boolean trackLimitOrderExecution(IterationContext iContext, Fetcher.ExchangeData exchData, OrderData orderData) throws Exception {
        // actually order execution should be checked via getLiveOrdersState()
        Fetcher.LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);
        // but for simulation we are checking via trades
        TradesData newTrades = iContext.getNewTradesData(exchData);
        orderData.xCheckExecutedLimit(iContext, exchData, orderData, newTrades);
        if (orderData.m_filled > 0) {
            if (orderData.m_status == OrderStatus.FILLED) {
                orderData.m_state = NONE;
                return true;
            } else { // PARTIALLY FILLED
                System.out.println("PARTIALLY FILLED, not supported yet - just wait more");
            }
        }
        return false;
    }

    public void checkState(IterationContext iContext, Fetcher.ExchangeData exchangeData, OrderData orderData) throws Exception {
        System.out.println("checkState not implemented for OrderState." + this);
    }
} // OrderState
