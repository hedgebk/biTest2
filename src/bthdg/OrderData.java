package bthdg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OrderData {
    public static final double MIN_ORDER_QTY = 0.01;

    public OrderStatus m_status = OrderStatus.NEW;
    public OrderState m_state = OrderState.NONE;
    public final OrderSide m_side;
    public double m_price;
    public double m_amount;
    public double m_filled;
    public List<Execution> m_executions;
    // todo: add to serialize
    public long m_time;
    public final Pair m_pair;

    public OrderData(Pair pair, OrderSide side, double price, double amount) {
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        m_pair = pair; // like Pair.BTC_USD
        m_side = side; // like OrderSide.BUY
        m_price = price;
        m_amount = amount;
    }

    public boolean isActive() { return m_status.isActive(); }
    public long time() { return m_time; }
    public String priceStr() { return Fetcher.format(m_price); }
    public double remained() { return m_amount - m_filled; }

    public boolean acceptPrice(double mktPrice) {
        return m_side.acceptPrice(m_price, mktPrice);
    }

    public void addExecution(double price, double amount) {
        List<Execution> executions = getExecutions();
        executions.add(new Execution(price, amount));
        m_filled += amount;
        if (m_amount == m_filled) { // todo: add check for very small diff like 0.00001
            m_status = OrderStatus.FILLED;
        } else if (executions.size() == 1) { // just got the very first execution
            m_status = OrderStatus.PARTIALLY_FILLED;
            m_time = System.currentTimeMillis();
        }
    }

    private List<Execution> getExecutions() {
        if (m_executions == null) { // lazily create
            m_executions = new ArrayList<Execution>();
        }
        return m_executions;
    }

    public void xCheckExecutedLimit(IIterationContext iContext, Exchange exchange, TradesData newTrades, AccountData account) {
        for (TradeData trade : newTrades.m_trades) {
            if (trade.m_amount == 0) {
                continue; // this execution is already processed
            }
            double tradePrice = trade.m_price; // ASK > BID

            boolean acceptPriceSimulated = false;
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (Fetcher.SIMULATE_ACCEPT_ORDER_PRICE
                    && !iContext.acceptPriceSimulated() // not yet accept simulated this run
                    && (new Random().nextDouble() < Fetcher.SIMULATE_ACCEPT_ORDER_PRICE_RATE)) {
                log("@@@@@@@@@@@@@@  !!!!!!!! SIMULATE ACCEPT_ORDER_PRICE tradePrice=" + Fetcher.format(tradePrice) + ", order=" + this);
                acceptPriceSimulated = true;
                iContext.acceptPriceSimulated(true); // one accept order price simulation per iteration
            }

            //noinspection ConstantConditions
            if (acceptPrice(tradePrice) || acceptPriceSimulated) {
                double tradeAmount = trade.m_amount;
                log("@@@@@@@@@@@@@@ we have LMT order " + m_side + " " + m_amount + " " + m_pair + " @ " + priceStr() +
                        " on '" + exchange.m_name + "' got matched trade=" + trade);

                double remained = remained();
                double extra = tradeAmount - remained;
                double amount;
                if (extra > 0) { // to much trade to fill the order - split the trade
                    amount = remained;
                    trade.m_amount = extra;
                } else {
                    amount = tradeAmount;
                    trade.m_amount = 0;
                }

                addExecution(m_price, amount);
                account.releaseTrade(m_pair, m_side, m_price, amount);
                if (isFilled()) {
                    return; // the whole order executed
                }
            }
        }
    }

    public boolean isFilled() {
        boolean statusOk = (m_status == OrderStatus.FILLED);
        boolean filledOk = (m_filled == m_amount) && (m_filled > 0);
        if (statusOk == filledOk) {
            return statusOk;
        }
        throw new RuntimeException("Error order state: status not matches filled qty: " + this);
    }

    public boolean isPartiallyFilled() {
        boolean statusOk = (m_status == OrderStatus.PARTIALLY_FILLED) || (m_status == OrderStatus.CANCELLED);
        boolean filledOk = (m_filled != m_amount) && (m_filled > 0);
        if (statusOk == filledOk) {
            return statusOk;
        }
        throw new RuntimeException("Error order state: status not matches partially filled qty: " + this);
    }

    @Override public String toString() {
        return "OrderData{" +
                "side=" + m_side +
                ", amount=" + m_amount +
                ", price=" + Fetcher.format(m_price) +
                ", status=" + m_status +
                ", pair=" + m_pair +
                ", state=" + m_state +
                ", filled=" + m_filled +
                '}';
    }

    public void serialize(StringBuilder sb) {
        sb.append("Order[status=").append(m_status.toString());
        sb.append("; state=").append(m_state.toString());
        sb.append("; side=").append(m_side.toString());
        sb.append("; price=").append(m_price);
        sb.append("; amount=").append(m_amount);
        sb.append("; filled=").append(m_filled);
        sb.append("; executions=");
        if(m_executions != null) {
            sb.append("[");
            for(Execution execution: m_executions) {
                execution.serialize(sb);
                sb.append("; ");
            }
            sb.append("]");
        }
        sb.append("]");
    }

    public static OrderData deserialize(Deserializer deserializer) throws IOException {
        if( deserializer.readIf("; ")) {
            return null;
        }
        deserializer.readObjectStart("Order");
        deserializer.readPropStart("status");
        String statusStr = deserializer.readTill("; ");
        deserializer.readPropStart("state");
        String stateStr = deserializer.readTill("; ");
        deserializer.readPropStart("side");
        String sideStr = deserializer.readTill("; ");
        deserializer.readPropStart("price");
        String priceStr = deserializer.readTill("; ");
        deserializer.readPropStart("amount");
        String amountStr = deserializer.readTill("; ");
        deserializer.readPropStart("filled");
        String filledStr = deserializer.readTill("; ");
        deserializer.readPropStart("executions");
        List<Execution> executions = readExecutions(deserializer);
        deserializer.readObjectEnd();
        deserializer.readStr("; ");

        OrderSide side = OrderSide.valueOf(sideStr);
        Double price = Double.parseDouble(priceStr);
        Double amount = Double.parseDouble(amountStr);
        OrderData ret = new OrderData(null, // todo
                side, price, amount);

        ret.m_status = OrderStatus.valueOf(statusStr);
        ret.m_state = OrderState.valueOf(stateStr);
        ret.m_filled = Double.parseDouble(filledStr);
        ret.m_executions = executions;

        return ret;
    }

    private static List<Execution> readExecutions(Deserializer deserializer) throws IOException {
        if( deserializer.readIf("[") ) {
            // [[Exec]; [Exec]; [Exec]; ]
            List<Execution> ret = new ArrayList<Execution>();
            while(true) {
                Execution exec = Execution.deserialize(deserializer);
                ret.add(exec);
                deserializer.readStr("; ");
                if(deserializer.readIf("]")) {
                    return ret;
                }
            }
        }
        return null;
    }

    public void compare(OrderData other) {
        if (m_status != other.m_status) {
            throw new RuntimeException("m_status");
        }
        if (m_state != other.m_state) {
            throw new RuntimeException("m_state");
        }
        if (m_side != other.m_side) {
            throw new RuntimeException("m_side");
        }
        if (m_price != other.m_price) {
            throw new RuntimeException("m_price");
        }
        if (m_amount != other.m_amount) {
            throw new RuntimeException("m_amount");
        }
        if (m_filled != other.m_filled) {
            throw new RuntimeException("m_filled");
        }
        compareExecutions(m_executions, other.m_executions);
    }

    private void compareExecutions(List<Execution> executions, List<Execution> other) {
        if(Utils.compareAndNotNulls(executions, other)) {
            int size = executions.size();
            if(size != other.size()) {
                throw new RuntimeException("executions.size");
            }
            for (int i = 0; i < size; i++) {
                Execution exec1 = executions.get(i);
                Execution exec2 = other.get(i);
                if(Utils.compareAndNotNulls(executions, other)) {
                    exec1.compare(exec2);
                }
            }
        }
    }

    private void log(String s) {
        Log.log(s);
    }

    public OrderData fork(double qty) {
        double amount2 = m_amount - qty;
        OrderData ret = new OrderData(m_pair, m_side, m_price, amount2);

        double filled2;
        OrderStatus status2;
        OrderState state2;
        if(qty <= m_filled) { // need to split filled
            filled2 = m_filled - qty;
            status2 = m_status;
            state2 = m_state;
            m_filled = 0;
            if( m_executions != null ) {
                List<Execution> executions = m_executions;
                m_executions = null;
                for (Execution execution: executions) {
                    double amount = execution.m_amount;
                    double price = execution.m_price;
                    double nonFilled = qty - m_filled;
                    if( nonFilled > 0 ) {
                        double split = amount - nonFilled;
                        if(split > 0) {
                            addExecution(price, nonFilled);
                            ret.addExecution(price, split);
                        } else {
                            addExecution(price, amount);
                        }
                    } else {
                        ret.addExecution(price, amount);
                    }
                }
            }
            m_state = OrderState.NONE;
            m_status = OrderStatus.FILLED;
        } else {
            filled2 = 0;
            status2 = m_status;
            state2 = m_state;
        }
        m_amount = qty;

        ret.m_status = status2;
        ret.m_state = state2;
        ret.m_filled = filled2;

        return ret;
    }

    public void checkState(IIterationContext iContext, Exchange exchange, AccountData account,
                           OrderState.IOrderExecListener listener, TradesData.ILastTradeTimeHolder holder) throws Exception {
        m_state.checkState(iContext, exchange, this, listener, account, holder);
    }

    public void cancel() {
        if ((m_status == OrderStatus.SUBMITTED) || (m_status == OrderStatus.PARTIALLY_FILLED)) {
            log("cancelOrder() not implemented yet: " + this);
        } else {
            log("cancelOrder() no need to cancel oder in state: " + this);
        }
        m_status = OrderStatus.CANCELLED;
        m_state = OrderState.NONE;
    }

    public void xCheckExecutedMkt(Exchange exchange, TopData top, AccountData account) {
        double mktPrice = m_side.mktPrice(top);
        if (acceptPrice(mktPrice)) {
            log("@@@@@@@@@@@@@@ we have MKT order " + m_side + " " + m_amount + " " + m_pair + " @ " + priceStr() +
                    " on '" + exchange.m_name + "' have matched TOP price=" + mktPrice + "; top=" + top);

            double remained = remained();
            addExecution(m_price, remained);
            account.releaseTrade(m_pair, m_side, m_price, remained);
        }
    }
} // OrderData
