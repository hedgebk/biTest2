package bthdg;

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
} // OrderData
