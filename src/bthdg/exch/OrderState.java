package bthdg.exch;

import bthdg.Fetcher;
import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.util.Utils;

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
    MKT_PLACED {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
            trackMktOrderExecution(iContext, exchange, orderData, listener, account);
        }
    },
    MARKET_PLACED {
        @Override public void checkState(IIterationContext iContext, Exchange exchange, OrderData orderData, IOrderExecListener listener,
                                         AccountData account, TradesData.ILastTradeTimeHolder holder) throws Exception {
            trackMarketOrderExecution(iContext, exchange, orderData, listener, account);
        }
    };

    private static boolean trackMarketOrderExecution(IIterationContext iContext, Exchange exchange, OrderData orderData,
                                                     IOrderExecListener listener, AccountData account) throws Exception {
        log("trackMarketOrderExecution() orderData=" + orderData);
        checkOrderExecuted(iContext, exchange, orderData, account);
        return notifyIfFilled(iContext, exchange, orderData, listener);
    }

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

    // return true if error detected
    private static boolean checkOrderExecuted(IIterationContext iContext, Exchange exchange, OrderData orderData, AccountData account) throws Exception {
        boolean error = false;
        log("checkOrderExecuted() orderData=" + orderData);
        OrdersData liveOrders = iContext.getLiveOrders(exchange);
        if ((liveOrders != null) && (liveOrders.m_error == null)) {
            String orderId = orderData.m_orderId;
            OrdersData.OrdData ordData = liveOrders.getOrderData(orderId);
            log(" ordData=" + ordData);
            Pair pair = orderData.m_pair;
            double orderPrice = orderData.roundPrice(exchange);
            double remainedAmountBefore = orderData.remained();
            double orderAmount = orderData.m_amount;
            String remainedStr = orderData.roundAmountStr(exchange, remainedAmountBefore);
            double volumeBefore = orderData.getFilledVolume();
            double avgFillPrice = (ordData == null) ? orderPrice : ordData.m_avgPrice;

            if ((ordData != null) && ((ordData.m_orderStatus == OrderStatus.SUBMITTED) || (ordData.m_orderStatus == OrderStatus.PARTIALLY_FILLED))) {
                log(" order still alive - liveOrder[" + orderId + "]=" + ordData);
                double remainedAmountNow = ordData.m_remainedAmount;
                double filledAmountNow = orderAmount - remainedAmountNow;
                double volumeNow = filledAmountNow * avgFillPrice;
                double partialVolume = volumeNow - volumeBefore;
                double partialSize = remainedAmountBefore - remainedAmountNow;
                double partialPrice = partialVolume/partialSize;
                double absAmountDelta = Math.abs(partialSize);
                double minAmountStep = exchange.minAmountStep(pair);
                String minAmountStepStr = orderData.roundAmountStr(exchange, minAmountStep);
                if (absAmountDelta > minAmountStep) {
                    log("  amounts are not equals: orderAmount=" + orderAmount + "; remainedAmountNow=" + remainedAmountNow + "; partialSize=" + partialSize +
                            "; filledAmountNow=" + filledAmountNow + "; volumeNow=" + volumeNow + "; partialVolume=" + partialVolume +
                            "; partialPrice=" + partialPrice + "; absAmountDelta=" + absAmountDelta + "; minAmountStep=" + minAmountStep + " :" + orderData);
//                    if (orderData.m_status == OrderStatus.PARTIALLY_FILLED) {
//                        log("   looks not OK - reprocessing PARTIALLY_FILLED status: ");
//                        new Exception("TRACE").printStackTrace();
//                        log("   error");
//                    }
                    double filled = orderData.m_filled;
                    log("   order: amount=" + orderData.roundAmountStr(exchange) + ", filled=" + orderData.roundAmountStr(exchange, filled) +
                            ", remained=" + remainedStr +
                            ";  liveOrder.amount=" + orderData.roundAmountStr(exchange, remainedAmountNow));  // probably partial
                    log("    probably partial: " + orderData.roundAmountStr(exchange, partialSize));
                    if (partialSize > minAmountStep) {
                        orderData.addExecution(partialPrice, partialSize, exchange);
                        account.releaseTrade(pair, orderData.m_side, orderPrice, partialSize, exchange);
                    } else {
                        log("     skipped: < minAmountStep (" + minAmountStepStr + ")");
                    }
                } else {
                    log("  order " + orderId + " not executed.  absAmountDelta(" + Utils.format8(absAmountDelta) + ") < pair.minAmountStep(" + minAmountStepStr + ")");
                }
            } else if ((ordData == null) || (ordData.m_orderStatus == OrderStatus.FILLED)) {
                log(" liveOrder[" + orderId + "]=" + ordData + ";  EXECUTED");
                log("   orderPrice=" + orderPrice + "; remainedAmountBefore=" + remainedAmountBefore + "; remainedStr=" + remainedStr);
                double orderVolume = orderAmount * avgFillPrice;
                log("   orderAmount=" + orderAmount + "; avgFillPrice=" + avgFillPrice + " -> orderVolume=" + orderVolume);
                double fillVolume = orderVolume - volumeBefore;
                double fillPrice = fillVolume / remainedAmountBefore;
                log("    volumeBefore=" + volumeBefore + "; fillVolume=" + fillVolume + "; fillPrice=" + fillPrice);
                if (remainedAmountBefore > 0) {
                    orderData.addExecution(fillPrice, remainedAmountBefore, exchange);
                    error = account.releaseTrade(pair, orderData.m_side, fillPrice, remainedAmountBefore, exchange);
                    if (error) {
                        orderData.m_status = OrderStatus.ERROR;
                        log("  error while releasing trade - setting OrderStatus.ERROR - need to reload all");
                    }
                } else {
                    log("     error: cant add execution - just set order as FILLED");
                    orderData.m_status = OrderStatus.FILLED;
                }
                log("    order at result: " + orderData);
                if ((orderData.m_type == OrderType.MARKET) && (ordData != null)) {
                    orderData.m_price = avgFillPrice; // update order price to avg filled
                    log("     MKT filled - update order price to avg filled: " + orderData);
                }
            } else {
                if (ordData.m_orderStatus == OrderStatus.CANCELING) {
                    orderData.m_status = OrderStatus.CANCELING;
                    log("  order still CANCELING. leave as is");
                } else if (ordData.m_orderStatus == OrderStatus.CANCELLED) {
                    orderData.m_status = OrderStatus.ERROR;
                    log("  order got cancelled. setting OrderStatus.ERROR - need to reload all");
                } else {
                    log("  error unexpected order status=" + orderData.m_status +  "; ordStatus=" + ordData.m_orderStatus
                            + "': orderData=" + orderData + "; ordData=" + ordData);
                    orderData.m_status = OrderStatus.ERROR;
                }
            }
        } else {
            // sometimes we having here  'invalid nonce parameter; on key:1397515806, you sent:1397515020'
            log("  error loading liveOrder: " + liveOrders);
        }
        return error;
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
