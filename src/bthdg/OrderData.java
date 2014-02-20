package bthdg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OrderData {
    public OrderStatus m_status = OrderStatus.NEW;
    public OrderState m_state = OrderState.NONE;
    public final OrderSide m_side;
    public double m_price;
    public final double m_amount;
    public double m_filled;
    public List<Execution> m_executions;

    @Override public String toString() {
        return "OrderData{" +
                "side=" + m_side +
                ", amount=" + m_amount +
                ", price=" + Fetcher.format(m_price) +
                ", status=" + m_status +
                ", state=" + m_state +
                ", filled=" + m_filled +
                '}';
    }

    public String priceStr() { return Fetcher.format(m_price); }

    public OrderData(OrderSide side, double price, double amount) {
        m_side = side;
        m_price = price;
        m_amount = amount;
    }

    public boolean isActive() { return m_status.isActive(); }

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
        }
    }

    private List<Execution> getExecutions() {
        if (m_executions == null) { // lazily create
            m_executions = new ArrayList<Execution>();
        }
        return m_executions;
    }

    public boolean xCheckExecutedLimit(IterationContext iContext, ExchangeData exchData, OrderData orderData, TradesData newTrades) {
        OrderSide orderSide = orderData.m_side;
        double orderAmount = orderData.m_amount;
        double price = orderData.m_price;
        for (TradesData.TradeData trade : newTrades.m_trades) {
            double mktPrice = trade.m_price; // ASK > BID

            boolean acceptPriceSimulated = false;
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (Fetcher.SIMULATE_ACCEPT_ORDER_PRICE && !iContext.m_acceptPriceSimulated
                    && new Random().nextBoolean() && new Random().nextBoolean()) {
                System.out.println("@@@@@@@@@@@@@@  !!!!!!!! SIMULATE ACCEPT_ORDER_PRICE mktPrice=" + Fetcher.format(mktPrice) + ", order=" + this);
                acceptPriceSimulated = true;
                iContext.m_acceptPriceSimulated = true; // one accept order price simulation per iteration
            }

            if (acceptPriceSimulated || orderData.acceptPrice(mktPrice)) {
                double tradeAmount = trade.m_amount;
                System.out.println("@@@@@@@@@@@@@@ we have LMT order " + orderSide + " " + orderAmount + " @ " + orderData.priceStr() +
                                  " on '" + exchData.exchName() + "' got matched trade=" + trade);

                if (orderAmount > tradeAmount) { // for now partial order execution it is complex to handle - todo: we may execute the rest by MKT price
                    System.out.println("@@@@@@@@@@@@@@  for now partial order execution it is complex to handle: " +
                            "orderAmount=" + orderAmount + ", tradeAmount=" + tradeAmount);
                }
                // here we pretend that the whole order was executed for now
                orderData.addExecution(price, orderAmount); // todo: add partial order execution support later.
                return true; // simulated that the whole order executed
            }
        }
        return false;
    }

    public boolean isFilled() {
        boolean statusOk = (m_status == OrderStatus.FILLED);
        boolean filledOk = (m_filled == m_amount);
        if (statusOk == filledOk) {
            return statusOk;
        }
        System.out.println("Error order state: status not matches filled qty: " + this);
        return false;
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
        OrderData ret = new OrderData(side, price, amount);

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
} // OrderData
