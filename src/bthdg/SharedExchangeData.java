package bthdg;

import java.io.IOException;

public class SharedExchangeData {
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

    public double midCommissionAmount() { return ((m_lastTop == null) ? 0 : m_lastTop.getMid()) * getFee(); }
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

    public TradesData filterOnlyNewTrades(TradesData trades) {
        TradesData newTrades = trades.newTrades(m_lastProcessedTradesTime);
        for (TradesData.TradeData trade : newTrades.m_trades) {
            long timestamp = trade.m_timestamp;
            if (timestamp > m_lastProcessedTradesTime) {
                m_lastProcessedTradesTime = timestamp;
            }
        }
        return newTrades;
    }

    public TopData fetchTopOnce() throws Exception {
        TopData top = Fetcher.fetchTopOnce(m_exchange);
        if( top != null ) { // we got fresh top data
            m_lastTop = top; // update top
            m_averageCounter.add(System.currentTimeMillis(), m_lastTop.getMid());
            double bidAskDiff = (m_lastTop.m_ask - m_lastTop.m_bid);
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
        return placeOrder(orderData, OrderState.BRACKET_PLACED);
    }

    public boolean placeOrder(OrderData orderData, OrderState state) {
        // todo: implement
        log("placeOrder("+m_exchange.m_name+") not implemented yet: " + orderData);
        // todo: pass to exch.baseExch if needed
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.m_state = state;

        return true;
    }

    public LiveOrdersData fetchLiveOrders() {
        // todo: implement
        log("fetchLiveOrders() not implemented yet");
        return new LiveOrdersData();
    }
} // SharedExchangeData
