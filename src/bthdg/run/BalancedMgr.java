package bthdg.run;

import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.exch.Currency;
import bthdg.util.Sync;
import bthdg.util.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BalancedMgr {
    public Set<Exchange> m_checkBalancedExchanges = new HashSet<Exchange>();
    public Map<Exchange, OrderData> m_balanceOrders = new HashMap<Exchange, OrderData>();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    protected abstract AccountData getAccount(Exchange exchange);
    protected abstract boolean hasLiveExchOrder(Exchange exch);

    public void сheckBalanced(IBalancedHelper ih) throws Exception {
        checkBalancedOrders(ih);
        checkForNewBalancedOrder(ih);
    }

    public interface IBalancedHelper {
        void queryLiveOrders(Exchange exchange, Pair pair, LiveOrdersMgr.ILiveOrdersCallback iLiveOrdersCallback) throws Exception;
        TopsData getTops(Exchange exchange) throws Exception;
        Map<Currency,Double> getRatioMap();
    }

    private void checkBalancedOrders(final IBalancedHelper ih) throws Exception {
        List<AtomicBoolean> syncList = null;
        final List<Exchange> toRemove = new ArrayList<Exchange>();
        for(Map.Entry<Exchange, OrderData> entry: m_balanceOrders.entrySet()) {
            final Exchange exchange = entry.getKey();
            final OrderData od = entry.getValue();
            Pair pair = od.m_pair;
            log(" queryLiveOrders: " + exchange + ", to check order state " + od);
            final AtomicBoolean sync = new AtomicBoolean(false);
            ih.queryLiveOrders(exchange, pair, new LiveOrdersMgr.ILiveOrdersCallback() {
                @Override public void onLiveOrders(final OrdersData ordersData) throws Exception {
                    log("onLiveOrders: " + exchange);
                    String orderId = od.m_orderId;
                    log(" orderId: " + orderId);
                    OrdersData.OrdData ordData = ordersData.m_ords.get(orderId);
                    log("  live order state: " + ordData);
                    AccountData accountData = getAccount(exchange);
                    try {
                        od.checkState(new IIterationContext.BaseIterationContext() {
                            @Override public OrdersData getLiveOrders(Exchange exchange) throws Exception {
                                return ordersData;
                            }
                        }, exchange, accountData, null, null);
                    } catch (Exception e) {
                        err("orderData.checkState error: " + e, e);
                    }
                    boolean done = checkBalancedOrderStatus(ih, accountData, od, exchange);
                    if (done) {
                        toRemove.add(exchange);
                    }
                    Sync.setAndNotify(sync);
                }
            });
            syncList = Sync.addSync(syncList, sync);
        }
        Sync.wait(syncList);

        if(!toRemove.isEmpty()) {
            log(" removing processed balanced order for exch: " + toRemove);
            for (Exchange exchange : toRemove) {
                m_balanceOrders.remove(exchange);
            }
        }
    }

    /** @return true if DONE and need to remove from balance map */
    private boolean checkBalancedOrderStatus(IBalancedHelper ih, AccountData accountData, OrderData od, Exchange exchange) throws Exception {
        if (od.m_status == OrderStatus.FILLED) {
            log("   balanced order FILLED: " + od);
        } else if ((od.m_status == OrderStatus.SUBMITTED) || (od.m_status == OrderStatus.PARTIALLY_FILLED)) {
            Pair pair = od.m_pair;
            TopsData tops = ih.getTops(exchange);
            TopData topData = tops.get(pair);
            double price = od.m_price;
            String baStr = "[b:" + topData.m_bid + "; a:" + topData.m_ask + "]";
            if (topData.isOutsideBibAsk(price)) {
                log("price " + price + " is outside of market " + baStr + " for order " + od);
                log(" cancelOrder: " + od);
                try {
                    String error = accountData.cancelOrder(od);
                    if (error == null) {
                        log("  cancelOrder OK");
                    } else {
                        log("  error in cancel order: " + error + "; " + od);
                        log("   leave the order in balance map - will check on next iteration");
                        return false;
                    }
                } catch (Exception e) {
                    err("  error in cancel order: " + e + "; " + od, e);
                }
            } else {
                log("   balanced order " + price + " is within market " + baStr + " - wait more: " + od);
                return false;
            }
        } else {
            log("unsupported status: " + od.m_status + " for order " + od);
        }
        return true;
    }

    private void checkForNewBalancedOrder(final IBalancedHelper ih) throws InterruptedException {
        if (!m_checkBalancedExchanges.isEmpty()) {
            log("checkForNewBalancedOrder for " + m_checkBalancedExchanges);
            List<Exchange> list = null;
            for (Exchange exch : m_checkBalancedExchanges) {
                OrderData od = m_balanceOrders.get(exch);
                if (od != null) {
                    log("exch " + exch + " skipped since has already balanced order: " + od);
                } else {
                    boolean has = hasLiveExchOrder(exch);
                    if (has) {
                        log("exch " + exch + " skipped since has live orders");
                    } else {
                        log("exch " + exch + " can be checked - NO live orders");
                        if (list == null) {
                            list = new ArrayList<Exchange>();
                        }
                        list.add(exch);
                    }
                }
            }
            if (list != null) {
                log(" check balanced for exchanges: " + list);
                Utils.doInParallel("сheckBalanced", list.toArray(new Exchange[list.size()]), new Utils.IExchangeRunnable() {
                    @Override public void run(Exchange exchange) throws Exception {
                        checkBalancedExch(ih, exchange);
                    }
                });
            }
        }
    }

    private void checkBalancedExch(IBalancedHelper ih, Exchange exchange) throws Exception {
        log(" check balanced for exchange: " + exchange + " -------------------------------");

        Map<Currency, Double> ratioMap = ih.getRatioMap();
        Set<Currency> keys = ratioMap.keySet();
        Currency[] currencies = keys.toArray(new Currency[keys.size()]);

        AccountData account = getAccount(exchange);
        TopsData tops = ih.getTops(exchange);
        OrderData od = FundMap.test(account, tops, exchange, ratioMap, currencies, 0.96);
        if (od == null) {
            log(" no need balance exch " + exchange);
            m_checkBalancedExchanges.remove(exchange);
            return;
        }

        Pair pair = od.m_pair;
        double minOrdSize = exchange.minOrderToCreate(pair);
        if (od.m_amount < minOrdSize) {
            log(" no need balance exch " + exchange + ", amount(" + exchange.roundAmountStr(od.m_amount, pair) + ") < minOrdSize(" + minOrdSize + ")");
            m_checkBalancedExchanges.remove(exchange);
            return;
        }
        log(" to sync we need place peg order on exch " + exchange + ": " + od);

        od.m_amount = minOrdSize;
        log("  order size capped to " + minOrdSize + ": " + od);

        OrderData.OrderPlaceStatus ops = BiAlgo.placeOrder(account, od, OrderState.LIMIT_PLACED);
        if (ops == OrderData.OrderPlaceStatus.OK) {
            m_balanceOrders.put(exchange, od);
            log("Balanced order placed fine: " + od + "; exch=" + exchange);
        } else if (ops == OrderData.OrderPlaceStatus.ERROR) {
            log("ERROR placing Balanced order: " + od + "; exch=" + exchange);
        } else  {
            log("ERROR: not supported OrderPlaceStatus: " + ops);
        }
    }

    public void logIterationEnd() {
        if (!m_balanceOrders.isEmpty()) {
            log(" balanceOrders: " + m_balanceOrders);
            for (Map.Entry<Exchange, OrderData> entry : m_balanceOrders.entrySet()) {
                Exchange exchange = entry.getKey();
                OrderData orderData = entry.getValue();
                log(" " + Utils.padRight(exchange.toString(), 12) + orderData);
            }
        }
    }

    public void cancelAll() throws Exception {
        if (!m_balanceOrders.isEmpty()) {
            log(" balanceOrders.cancelAll(): " + m_balanceOrders);
            for (Map.Entry<Exchange, OrderData> entry : m_balanceOrders.entrySet()) {
                Exchange exchange = entry.getKey();
                OrderData orderData = entry.getValue();
                log(" " + Utils.padRight(exchange.toString(), 12) + orderData);

                AccountData account = getAccount(exchange);
                String error = account.cancelOrder(orderData);
                if (error != null) {
                    log("error in cancel order: " + error + "; " + orderData);
                }
            }
        }
    }

    public void addCheckBalancedExchanges(Exchange exchange) {
        m_checkBalancedExchanges.add(exchange);
    }
}
