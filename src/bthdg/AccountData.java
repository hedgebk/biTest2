package bthdg;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AccountData {
    public static final DecimalFormat X_YYYYY = new DecimalFormat("0.00000");

    public final String m_name;
    public double m_fee;
                             // todo: serialize
    private final HashMap<Currency, Double> m_funds = new HashMap<Currency,Double>();
    private final HashMap<Currency, Double> m_allocatedFunds = new HashMap<Currency,Double>();

    private static void log(String s) { Log.log(s); }

    public double availableUsd() { return available(Currency.USD); }
    public double availableBtc() { return available(Currency.BTC); }
    public double allocatedUsd() { return allocated(Currency.USD); }
    public double allocatedBtc() { return allocated(Currency.BTC); }

    private double notNull(Double aDouble) { return aDouble == null ? 0 : aDouble.doubleValue(); }

    public double available(Currency currency) { return notNull(m_funds.get(currency)); }
    public double allocated(Currency currency) { return notNull(m_allocatedFunds.get(currency)); }

    public void setAvailable(Currency currency, double value) { m_funds.put(currency, round(value)); }
    public void setAllocated(Currency currency, double value) { m_allocatedFunds.put(currency, round(value)); }

    private Double round(double value) {
        return Math.round(value * 10000000000d) / 10000000000d;
    }

    public AccountData(String name, double usd, double btc, double fee) {
        m_name = name;
        setAvailable(Currency.USD, usd);
        setAvailable(Currency.BTC, btc);
        m_fee = fee;
    }

    @Override public String toString() {
        return "AccountData{" +
                "name='" + m_name + "\' " +
                "funds=" + toString(m_funds) + "; " +
                "allocated=" + toString(m_allocatedFunds) + " " +
                ((m_fee != Double.MAX_VALUE) ? ", fee=" + m_fee : "") +
                '}';
    }

    private String toString(HashMap<Currency, Double> funds) {
        Iterator<Map.Entry<Currency, Double>> i = funds.entrySet().iterator();
        if (!i.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (; ; ) {
            Map.Entry<Currency, Double> e = i.next();
            Currency key = e.getKey();
            Double value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(X_YYYYY.format(value));
            if (!i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    public void compare(AccountData other) {
        if (!m_name.equals(other.m_name)) {
            throw new RuntimeException("m_name");
        }
        if (m_fee != other.m_fee) {
            throw new RuntimeException("m_fee");
        }
    }

    public void serialize(StringBuilder sb) {
        sb.append("Acct[name=").append(m_name);
        sb.append("; fee=").append((m_fee == Double.MAX_VALUE) ? "" : Double.toString(m_fee));
        sb.append("]");
    }

    public static AccountData deserialize(Deserializer deserializer) throws IOException {
        if( deserializer.readIf("; ")) {
            return null;
        }
        deserializer.readObjectStart("Acct");
        deserializer.readPropStart("name");
        String name = deserializer.readTill("; ");
        deserializer.readPropStart("usd");
        String usdStr = deserializer.readTill("; ");
        deserializer.readPropStart("btc");
        String btcStr = deserializer.readTill("; ");
        deserializer.readPropStart("fee");
        String feeStr = deserializer.readTill("]; ");

        double usd = Double.parseDouble(usdStr);
        double btc = Double.parseDouble(btcStr);
        double fee = (feeStr.length() == 0) ? Double.MAX_VALUE: Double.parseDouble(feeStr);
        AccountData ret = new AccountData(name, usd, btc, fee);
        return ret;
    }

    public boolean allocateOrder(OrderData orderData) {
        Pair pair = orderData.m_pair;
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        OrderSide orderSide = orderData.m_side;
        boolean isBuy = orderSide.isBuy();
        Currency currency = isBuy ? pair.m_from : pair.m_to;
        double price = orderData.m_price;
        double amount = isBuy ? orderData.m_amount * price : orderData.m_amount;

        log("allocateOrder() pair: " + pair + "; side: " + orderSide + "; price=" + Fetcher.format(price) +
            "; amount=" + amount + "; currency=" + currency + "   on " + this);

        double available = available(currency);
        if (amount > available) {
            log("Unable to allocate " + amount + " " + currency + ". available=" + available);
            return false;
        }
        setAvailable(currency, available - amount);
        double allocated = allocated(currency);
        setAllocated(currency, allocated + amount);
        log("   result=" + this);
        return true;
    }

    public void releaseOrder(OrderData orderData) {
        double amount = orderData.remained();
        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
            log("special case - some portion is already executed. releasing only remained part="+ amount);
        }
        release(orderData.m_pair, orderData.m_side, orderData.m_price, amount, false);
    }

    private void release(Pair pair, OrderSide orderSide, double price, double amount, boolean tradeHappens) {
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        log("release() pair: " + pair + "; side: " + orderSide + "; price=" + Fetcher.format(price) +
                "; amount=" + amount + "   on " + this);
        boolean isBuy = orderSide.isBuy();
        Currency fromCurrency = isBuy ? pair.m_from : pair.m_to;
        double fromSize = isBuy ? amount * price : amount;
        log(" fromCurrency " + fromCurrency + "; fromSize=" + fromSize);

        double allocated = allocated(fromCurrency);
        setAllocated(fromCurrency, allocated - fromSize);

        if (tradeHappens) {
            Currency toCurrency = isBuy ? pair.m_to : pair.m_from;
            double toSize = isBuy ? amount : amount * price;
            double commission = toSize * m_fee;
            double rest = toSize - commission; // deduct commissions
            log(" toCurrency " + toCurrency + "; toSize=" + toSize + "; commission=" + commission + "; rest=" + rest);

            double available = available(toCurrency);
            setAvailable(toCurrency, available + rest);
        } else {
            double available = available(fromCurrency);
            setAvailable(fromCurrency, available + fromSize);
        }
        log("   result=" + this);
    }

    public void releaseTrade(Pair pair, OrderSide orderSide, double price, double amount) {
        release(pair, orderSide, price, amount, true);
    }
}
