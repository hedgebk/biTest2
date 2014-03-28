package bthdg;

import bthdg.exch.OrdersData;
import bthdg.exch.TopData;
import bthdg.exch.TradesData;

import java.util.Map;

public enum OrderState {
    NONE {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {}
    },
    LIMIT_PLACED {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
            trackLimitOrderExecution(iContext, exchange, orderData, listener, account, holder);
        }
    },
    MARKET_PLACED {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
            boolean executed = trackMktOrderExecution(iContext, exchange, orderData, listener, account);
            if( executed ) {
                log(" OPEN MKT bracket order executed. we are fully OPENED " + orderData);
            } else {
                log(" MKT order not yet executed - move if needed");
            }
        }
    };

    private static boolean trackMktOrderExecution(IIterationContext iContext, Exchange exchange,
                                                  OrderData orderData, IOrderExecListener listener,
                                                  AccountData account) throws Exception {
        if( Fetcher.SIMULATE_ORDER_EXECUTION ) {
            // but for simulation we are checking via top
            Pair pair = orderData.m_pair;
            TopData top = iContext.getTop(exchange, pair);
            orderData.xCheckExecutedMkt(exchange, top, account);
        } else {
            log("trackMktOrderExecution() orderData=" + orderData);
            checkOrderExecuted(iContext, exchange, orderData, account);
        }

        if (orderData.m_filled > 0) {
            if (orderData.m_status == OrderStatus.FILLED) {
                orderData.m_state = NONE;
                if(listener != null) {
                    listener.onOrderFilled(iContext, exchange, orderData);
                }
                return true;
            } else { // PARTIALLY FILLED
                log("PARTIALLY FILLED, just wait more / split?");
            }
        }
        return false;
    }

    private static boolean trackLimitOrderExecution(IIterationContext iContext, Exchange exchange,
                                                    OrderData orderData, IOrderExecListener listener,
                                                    AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
        if( Fetcher.SIMULATE_ORDER_EXECUTION ) {
            Map<Pair, TradesData> newTradesMap = iContext.getNewTradesData(exchange, holder);
            TradesData newTrades = newTradesMap.get(orderData.m_pair);
            orderData.xCheckExecutedLimit(iContext, exchange, newTrades, account);
        } else {
            log("trackLimitOrderExecution() orderData=" + orderData);
            checkOrderExecuted(iContext, exchange, orderData, account);
        }

        if (orderData.m_filled > 0) {
            if (orderData.m_status == OrderStatus.FILLED) {
                orderData.m_state = NONE;
                if(listener != null) {
                    listener.onOrderFilled(iContext, exchange, orderData);
                }
                return true;
            } else { // PARTIALLY FILLED
                log("PARTIALLY FILLED, just wait more / split?");
            }
        }
        return false;
    }

    private static void checkOrderExecuted(IIterationContext iContext, Exchange exchange, OrderData orderData, AccountData account) throws Exception {
        OrdersData liveOrders = iContext.getLiveOrders(exchange);
        log(" liveOrders=" + liveOrders);

        if (liveOrders != null) {
            String orderId = orderData.m_orderId;
            double orderAmount = orderData.m_amount;
            OrdersData.OrdData ordData = liveOrders.getOrderData(orderId);
            if (ordData != null) {
                double amount = ordData.m_amount;
                if (amount != orderAmount) {
                    double filled = orderData.m_filled;
                    double remained = orderData.remained();
                    log("  amounts are not equals: orderAmount=" + orderAmount + "; filled=" + filled + "; remained=" + remained + ";  liveOrder.amount=" + amount);  // probably partial
                    double partial = remained - amount;
                    log("   probably partial=" + partial);
                    if (partial > 0) {
                        double price = orderData.m_price;
                        orderData.addExecution(price, partial, exchange);
                        account.releaseTrade(orderData.m_pair, orderData.m_side, price, partial);
                    }
                } else {
                    log("  order " + orderId + " not executed");
                }
            } else {
                log("  no such liveOrder. EXECUTED");
                double orderPrice = orderData.m_price;
                double remained = orderData.remained();
                log("   orderPrice=" + orderPrice + "; remained=" + remained);
                orderData.addExecution(orderPrice, remained, exchange);
                account.releaseTrade(orderData.m_pair, orderData.m_side, orderPrice, remained);
                log("    order at result: " + orderData);
            }
        } else {
            log("  error loading liveOrder");
        }
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
