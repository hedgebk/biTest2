package bthdg.duplet;

import bthdg.Deserializer;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.util.Utils;

import java.io.IOException;

public class SharedExchangeData implements TradesData.ILastTradeTimeHolder {
    final Exchange m_exchange;
    public final Utils.AverageCounter m_averageCounter;
    // to calc average diff between bid and ask on exchange
    public final Utils.DoubleAverageCalculator<Double> m_bidAskDiffCalculator;
    //bitstamp avgBidAskDiff1=2.6743, btc-e avgBidAskDiff2=1.3724
    //bitstamp avgBidAskDiff1=2.1741, btc-e avgBidAskDiff2=1.2498
    //bitstamp avgBidAskDiff1=1.9107, btc-e avgBidAskDiff2=1.3497

    private long m_lastProcessedTradesTime;
    TopData m_lastTop;
    public AccountData m_account;

    public double midPrice() { return (m_lastTop == null) ? 0 : m_lastTop.getMid(); }
    public double midCommissionAmount() { return midPrice() * getFee(); }
    public double roundPrice(double price) { return m_exchange.roundPrice(price, Pair.BTC_USD); }

    private static void log(String s) { Log.log(s); }

    @Override public String toString() {
        return "SharedExchangeData{" +
                "exchange=" + m_exchange +
                '}';
    }

    private static Utils.DoubleAverageCalculator<Double> mkBidAskDiffCalculator() {
        return new Utils.DoubleAverageCalculator<Double>() {
            @Override public double getDoubleValue(Double tick) { return tick; } // ASK > BID
        };
    }

    public SharedExchangeData(Exchange exchange) {
        this(exchange, new Utils.AverageCounter(Fetcher.MOVING_AVERAGE), mkBidAskDiffCalculator());
    }

    private SharedExchangeData(Exchange exchange, Utils.AverageCounter averageCounter,
                               Utils.DoubleAverageCalculator<Double> calculator) {
        m_exchange = exchange;
        m_averageCounter = averageCounter;
        m_bidAskDiffCalculator = calculator;
    }

    @Override public long lastProcessedTradesTime() { return m_lastProcessedTradesTime; }
    @Override public void lastProcessedTradesTime(long lastProcessedTradesTime) { m_lastProcessedTradesTime = lastProcessedTradesTime; }

    public TopData fetchTopOnce() throws Exception {
        TopData top = Fetcher.fetchTopOnce(m_exchange);
        if( top != null ) { // we got fresh top data
            m_lastTop = top; // update top
            m_averageCounter.justAdd(m_lastTop.getMid());
            double bidAskDiff = m_lastTop.getBidAskDiff();
            m_bidAskDiffCalculator.addValue(bidAskDiff);
        } else {
            if(m_lastTop != null) {
                m_lastTop.setObsolete();
            }
        }
        return m_lastTop;
    }

    public double calcAmountToOpen() {
        // could be different depending on exchange since the price is different
        return 1.0 /*BTC*/ ;  // todo: get this based on both exch account info
    }

    public void serialize(StringBuilder sb) {
        sb.append("ShExh[exch=").append(m_exchange.toString());
        sb.append("; lastTrdTm=").append(m_lastProcessedTradesTime);
        sb.append("; lastTop=");
        if (m_lastTop != null) {
            m_lastTop.serialize(sb);
        }
        sb.append("; avgCntr=");
        m_averageCounter.serialize(sb);
        sb.append("; account=");
        if (m_account != null) {
            m_account.serialize(sb);
        }
        sb.append("; bidAskDiffClcltr=");
        m_bidAskDiffCalculator.serialize(sb);
        sb.append("]");
    }

    public static SharedExchangeData deserialize(Deserializer deserializer) throws IOException {
        deserializer.readObjectStart("ShExh");
        deserializer.readPropStart("exch");
        String exchStr = deserializer.readTill("; ");
        deserializer.readPropStart("lastTrdTm");
        String lastTrdTm = deserializer.readTill("; ");

        deserializer.readPropStart("lastTop");
        TopData lastTop = TopData.deserialize(deserializer);

        deserializer.readPropStart("avgCntr");
        Utils.AverageCounter avgCntr = Utils.AverageCounter.deserialize(deserializer);

        deserializer.readPropStart("account");
        AccountData account = AccountData.deserialize(deserializer);

        deserializer.readPropStart("bidAskDiffClcltr");
        Utils.DoubleAverageCalculator<Double> calculator = mkBidAskDiffCalculator();
        calculator.deserialize(deserializer);

        deserializer.readObjectEnd();

        Exchange exchange = Exchange.valueOf(exchStr);
        SharedExchangeData ret = new SharedExchangeData(exchange, avgCntr, calculator);
        ret.m_lastProcessedTradesTime = Long.parseLong(lastTrdTm);
        ret.m_lastTop = lastTop;
        ret.m_account = account;
        return ret;
    }

    public void compare(SharedExchangeData data) {
        if (m_exchange != data.m_exchange) {
            throw new RuntimeException("m_exchange");
        }
        m_averageCounter.compare(data.m_averageCounter);
        m_bidAskDiffCalculator.compare(data.m_bidAskDiffCalculator);
        if (m_lastProcessedTradesTime != data.m_lastProcessedTradesTime) {
            throw new RuntimeException("m_lastProcessedTradesTime");
        }
        if (Utils.compareAndNotNulls(m_lastTop, data.m_lastTop)) {
            m_lastTop.compare(data.m_lastTop);
        }
        if (Utils.compareAndNotNulls(m_account, data.m_account)) {
            m_account.compare(data.m_account);
        }
    }

    public void queryAccountData() throws Exception {
        AccountData account = Fetcher.fetchAccount(m_exchange);
        log("queryAccountData() account=" + account);
        m_account = account;
        // todo: handle if query unsuccessfull
    }

    // TODO: can be exchnage-pair dependent (e.g. btce-rur-usd)
    public double getFee() {
        if (m_account != null) {
            double fee = m_account.m_fee;
            if (fee != Double.MAX_VALUE) {
                return fee;
            }
        }
        return m_exchange.m_baseFee;
    }

    public boolean placeOrderBracket(OrderData orderData) {
        return placeOrder(orderData, OrderState.LIMIT_PLACED);
    }

    public boolean placeOrder(OrderData orderData, OrderState state) {
        // todo: implement
        log("placeOrder(" + m_exchange.m_name + ") not implemented yet: " + orderData);

        boolean success = m_account.allocateOrder(orderData);
        if(success) {
            // todo: pass to exch.baseExch if needed
            orderData.m_status = OrderStatus.SUBMITTED;
            orderData.m_state = state;
        } else {
            log("account allocateOrder unsuccessful: " + orderData + ", account: " + m_account);
        }
        return success;
    }

    public boolean cancelOrder(OrderData orderData) {
        if (orderData != null) {
            if(orderData.canCancel()) {
                // todo: implement
                orderData.cancel();
                m_account.releaseOrder(orderData, m_exchange);
                return true;
            } else {
                log("error: can not cancel order: " + orderData);
            }
        }
        return false;
    }

    public OrdersData fetchLiveOrders() {
        // todo: implement
//        log("fetchLiveOrders() not implemented yet");
        return new OrdersData();
    }
} // SharedExchangeData
