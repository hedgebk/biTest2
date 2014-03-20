package bthdg;

import java.io.IOException;
import java.util.HashMap;

public class AccountData {
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

    private void set(Currency currency, double value) { m_funds.put(currency, value); }
    private void setAllocated(Currency currency, double value) { m_allocatedFunds.put(currency, value); }

    public AccountData(String name, double usd, double btc, double fee) {
        m_name = name;
        set(Currency.USD, usd);
        set(Currency.BTC, btc);
        m_fee = fee;
    }

    @Override public String toString() {
        return "AccountData{" +
                "name='" + m_name + '\'' +
                ((m_fee != Double.MAX_VALUE) ? ", fee=" + m_fee : "") +
                '}';
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
        boolean isBuy = orderData.m_side.isBuy();
        Currency currency = isBuy ? pair.m_from : pair.m_to;
        double amount = isBuy ? orderData.m_amount * orderData.m_price : orderData.m_amount;

        double available = available(currency);
        if (amount > available) {
            log("Unable to allocate " + amount + " " + currency + ". available=" + available);
            return false;
        }
        set(currency, available - amount);
        double allocated = allocated(currency);
        setAllocated(currency, allocated + amount);
        return true;
    }

//    public boolean allocateBtc(OrderData sellBtc) {
//        double amountBtc = sellBtc.m_amount;
//        double aBtc = availableBtc();
//        if (amountBtc > aBtc) {
//            log("Unable to allocate " + amountBtc + " btc. available=" + aBtc);
//            return false;
//        } else {
//            set(Currency.USD, aBtc - amountBtc);
//            double allocatedBtc = allocated(Currency.BTC);
//            setAllocated(Currency.BTC, allocatedBtc + amountBtc);
//            return true;
//        }
//    }

//    public boolean allocateUsd(OrderData buyBtc) {
//        double amountBtc = buyBtc.m_amount;
//        double price = buyBtc.m_price;
//        double amountUsd = amountBtc * price;
//        if (amountUsd > m_usd) {
//            log("Unable to allocate " + amountUsd + " usd. available=" + m_usd);
//            return false;
//        } else {
//            m_usd -= amountUsd;
//            m_allocatedUsd += amountUsd;
//            return true;
//        }
//    }

    public void releaseOrder(OrderData orderData) {
        double amount = orderData.remained();
        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
            log("special case - some portion is already executed. releasing only remained part="+ amount);
        }
        Pair pair = orderData.m_pair;
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        OrderSide orderSide = orderData.m_side;
        double price = orderData.m_price;

        release(pair, orderSide, price, amount);
    }

    private void release(Pair pair, OrderSide orderSide, double price, double amount) {
        boolean isBuy = orderSide.isBuy();
        Currency fromCurrency = isBuy ? pair.m_from : pair.m_to;
        Currency toCurrency = isBuy ? pair.m_to : pair.m_from;
        double size = isBuy ? amount * price : amount;

        double allocated = allocated(fromCurrency);
        setAllocated(fromCurrency, allocated - size);

        double available = available(toCurrency);
        setAllocated(toCurrency, available + size);
    }

//    public void releaseUsd(OrderData orderData) {
//        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
//            log("special case - some portion is already executed. releasing only remained part="+orderData.remained());
//        }
//        double amountBtc = orderData.remained();
//        double price = orderData.m_price;
//        double amountUsd = amountBtc * price;
//        m_usd += amountUsd;
//        m_allocatedUsd -= amountUsd;
//    }
//
//    public void releaseBtc(OrderData orderData) {
//        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
//            log("special case - some portion is already executed. releasing only remained part="+orderData.remained());
//        }
//        double amountBtc = orderData.remained();
//        m_btc += amountBtc;
//        m_allocatedBtc -= amountBtc;
//    }

    public void releaseTrade(Pair pair, OrderSide orderSide, double price, double amount) {
        release(pair, orderSide, price, amount);
    }

//    public void releaseUsd(double price, double btcAmount) {
//        double amountUsd = btcAmount * price;
//        m_allocatedUsd -= amountUsd;
//        m_btc += btcAmount * (1 - m_fee);
//    }
//
//    public void releaseBtc(double price, double btcAmount) {
//        m_allocatedBtc -= btcAmount;
//        double amountUsd = btcAmount * price;
//        m_usd += amountUsd * (1 - m_fee);
//    }
}
