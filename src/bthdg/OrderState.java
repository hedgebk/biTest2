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
            trackMktOrderExecution(iContext, exchange, orderData, listener, account);
        }
    };

    private static boolean trackMktOrderExecution(IIterationContext iContext, Exchange exchange,
                                                  OrderData orderData, IOrderExecListener listener,
                                                  AccountData account) throws Exception {
        if (Fetcher.SIMULATE_ORDER_EXECUTION) { // for simulation we are checking via top
            Pair pair = orderData.m_pair;
            TopData top = iContext.getTop(exchange, pair);
            orderData.xCheckExecutedMkt(exchange, top, account);
        } else {
            log("trackMktOrderExecution() orderData=" + orderData);
            checkOrderExecuted(iContext, exchange, orderData, account);
        }
        return notifyIfFilled(iContext, exchange, orderData, listener);
    }

    private static boolean trackLimitOrderExecution(IIterationContext iContext, Exchange exchange,
                                                    OrderData orderData, IOrderExecListener listener,
                                                    AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
        if (Fetcher.SIMULATE_ORDER_EXECUTION) {
            Map<Pair, TradesData> newTradesMap = iContext.getNewTradesData(exchange, holder);
            TradesData newTrades = newTradesMap.get(orderData.m_pair);
            orderData.xCheckExecutedLimit(iContext, exchange, newTrades, account);
        } else {
            log("trackLimitOrderExecution() orderData=" + orderData);
            checkOrderExecuted(iContext, exchange, orderData, account);
        }
        return notifyIfFilled(iContext, exchange, orderData, listener);
    }

    private static boolean notifyIfFilled(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener) {
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
        if (liveOrders != null) {
            String orderId = orderData.m_orderId;
            OrdersData.OrdData ordData = liveOrders.getOrderData(orderId);
            Pair pair = orderData.m_pair;
            double orderPrice = orderData.m_price;
            double remained = orderData.remained();
            String remainedStr = orderData.roundAmountStr(exchange, remained);
            if (ordData != null) {
                log(" order still alive - liveOrder[" + orderId + "]=" + ordData);
                double orderAmount = orderData.m_amount;
                double liveAmount = ordData.m_amount;
                double absAmountDelta = Math.abs(orderAmount - liveAmount);
                double minAmountStep = exchange.minAmountStep(pair);
                String minAmountStepStr = orderData.roundAmountStr(exchange, minAmountStep);
                if (absAmountDelta > minAmountStep) {
                    log("  amounts are not equals: " + orderData);
                    if (orderData.m_status == OrderStatus.PARTIALLY_FILLED) {
                        log("   looks not OK - reprocessing PARTIALLY_FILLED status: ");
                        new Exception("TRACE").printStackTrace();
                        log("   error");
                    }
                    double filled = orderData.m_filled;
                    log("   order: amount=" + orderData.roundAmountStr(exchange) + ", filled=" + orderData.roundAmountStr(exchange, filled) +
                            ", remained=" + remainedStr +
                            ";  liveOrder.amount=" + orderData.roundAmountStr(exchange, liveAmount));  // probably partial
                    double partial = remained - liveAmount;
                    log("    probably partial: " + orderData.roundAmountStr(exchange, partial));
                    if (partial > minAmountStep) {
                        orderData.addExecution(orderPrice, partial, exchange);
                        account.releaseTrade(pair, orderData.m_side, orderPrice, partial);
                    } else {
                        log("     skipped: < minAmountStep (" + minAmountStepStr + ")");
                    }
                } else {
                    log("  order " + orderId + " not executed.  absAmountDelta(" + absAmountDelta + ") < pair.minAmountStep(" + minAmountStepStr + ")");
                }
            } else {
                log(" liveOrder[" + orderId + "]=" + ordData + ";  no such liveOrder. EXECUTED");
                log("   orderPrice=" + orderData.roundPrice(exchange) + "; remained=" + remainedStr);
                orderData.addExecution(orderPrice, remained, exchange);
                account.releaseTrade(pair, orderData.m_side, orderPrice, remained);
                log("    order at result: " + orderData);
            }
        } else {
            log("  error loading liveOrder: " + liveOrders);
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
