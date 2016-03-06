package bthdg.exch;

import bthdg.Deserializer;
import bthdg.Fetcher;
import bthdg.Log;
import bthdg.util.Utils;

import java.io.IOException;
import java.util.*;

public class AccountData {
    public static final double FUND_DIFF_RATIO = 0.01;

    public final Exchange m_exchange;
    public final String m_name;
    public double m_fee;
                             // todo: serialize
    private final HashMap<Currency, Double> m_funds = new HashMap<Currency,Double>();
    private final HashMap<Currency, Double> m_allocatedFunds = new HashMap<Currency,Double>();
    public boolean m_gotFundDiff;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable e) { Log.err(s, e); }

    // shortcuts
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
        return Math.round(value * 1000000000d) / 1000000000d;
    }

    public AccountData(Exchange exchange, double fee) {
        m_exchange = exchange;
        m_name = exchange.m_name;
        m_fee = fee;
        if (fee > 1) {
            err("invalid AccountData fee = " + fee, new Exception("TRACE"));
        }
    }

    @Override public String toString() {
        return "AccountData{" +
                "name='" + m_name + "\' " +
                "funds=" + toString(m_funds) + "; " +
                "allocated=" + toString(m_allocatedFunds) +
                ((m_fee != Double.MAX_VALUE) ? ", fee=" + Utils.format5(m_fee) : "") +
                '}';
    }

    private String toString(HashMap<Currency, Double> funds) {
        if (funds.isEmpty()) { return "{}"; }
        Set<Currency> entries = funds.keySet();
        ArrayList<Currency> list = new ArrayList<Currency>(entries);
        Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (Currency currency : list) {
            Double value = funds.get(currency);
            if (Math.abs(value) > 0.0000000001) {
                sb.append(currency);
                sb.append('=');
                sb.append(Utils.format5(value));
                sb.append(',').append(' ');
            }
        }
        int length = sb.length();
        if (length > 2) {
            sb.setLength(length - 2);
        }
        return sb.append('}').toString();
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
        AccountData ret = new AccountData(Exchange.getExchange(name), fee);
        ret.setAvailable(Currency.USD, usd);
        ret.setAvailable(Currency.BTC, btc);
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

    // return true if error detected
    public boolean releaseOrder(OrderData orderData, Exchange exchange) {
        double amount = orderData.remained();
        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
            log("special case - some portion is already executed. releasing only remained part="+ amount);
        }
        return release(orderData.m_pair, orderData.m_side, orderData.m_price, amount, false, exchange);
    }

    // return true if error detected
    private boolean release(Pair pair, OrderSide orderSide, double price, double amount, boolean tradeHappens, Exchange exchange) {
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        log("release() pair: " + pair + "; side: " + orderSide + "; price=" + exchange.roundPriceStr(price, pair) +
                "; amount=" + exchange.roundAmountStr(amount, pair) + "   on " + this);
        boolean isBuy = orderSide.isBuy();
        Currency fromCurrency = isBuy ? pair.m_from : pair.m_to;
        double fromSize = isBuy ? amount * price : amount;
        String str = " fromCurrency " + fromCurrency + "; fromSize=" + Utils.format8(fromSize);

        double allocated = allocated(fromCurrency);
        double newAllocated = allocated - fromSize;
        setAllocated(fromCurrency, newAllocated);

        if (tradeHappens) {
            Currency toCurrency = isBuy ? pair.m_to : pair.m_from;
            double toSize = isBuy ? amount : amount * price;
            double fee = getFee(exchange, pair);
            double commission = toSize * fee;
            double rest = toSize - commission; // deduct commissions
            str += ";   toCurrency " + toCurrency + "; toSize=" + Utils.format8(toSize) +
                    "; fee=" + fee + "; commission=" + Utils.format8(commission) + "; rest=" + Utils.format8(rest);

            double available = available(toCurrency);
            setAvailable(toCurrency, available + rest);
            log(str);
            log("   result=" + this);
        } else {
            double available = available(fromCurrency);
            setAvailable(fromCurrency, available + fromSize);
            str += ";   result=" + this;
            log(str);
        }
        boolean error = (fromCurrency == Currency.CNH)
                            ? (newAllocated < -1)
                            : (newAllocated < 0);
        if (error) {
            log("ERROR: result allocated " + newAllocated + " " + fromCurrency + " is negative for " + this);
        }
        return error;
    }

    public void move(Currency currencyFrom, Currency currencyTo, double amountTo, TopsData topsData, boolean verbose) {
        if (verbose) {
            log("   move() currencyFrom=" + currencyFrom + "; currencyTo=" + currencyTo + "; amountTo=" + amountTo);
            log("    account in: " + this);
        }
        double amountFrom = topsData.convert(currencyTo, currencyFrom, amountTo, m_exchange);
        double availableFrom = available(currencyFrom);
        double newAvailableFrom = availableFrom - amountFrom;
        if (newAvailableFrom < 0) {
            throw new RuntimeException("Error account move. from=" + currencyFrom + "; to=" + currencyTo + "; amountTo=" + amountTo
                    + "; amountFrom=" + amountFrom + "; availableFrom=" + availableFrom + "; on " + this);
        }
        setAvailable(currencyFrom, newAvailableFrom);

        double availableTo = available(currencyTo);
        double newAvailableTo = availableTo + amountTo;
        if (newAvailableTo < 0) {
            throw new RuntimeException("Error account move. from=" + currencyFrom + "; to=" + currencyTo + "; amountTo=" + amountTo
                    + "; amountFrom=" + amountFrom + "; availableFrom=" + availableFrom + "; availableTo=" + availableTo + "; on " + this);
        }
        setAvailable(currencyTo, newAvailableTo);
        if (verbose) {
            log("    account out: " + this);
        }
    }

    public double getFee(Exchange exchange, Pair pair) {
        return exchange.getFee(pair, m_fee);
    }

    // return true if error detected
    public boolean releaseTrade(Pair pair, OrderSide orderSide, double price, double amount, Exchange exchnage) {
        return release(pair, orderSide, price, amount, true, exchnage);
    }

    public AccountData copy() {
        AccountData ret = new AccountData(m_exchange, m_fee);
        ret.m_funds.putAll(m_funds);
        ret.m_allocatedFunds.putAll(m_allocatedFunds);
        return ret;
    }

    public double evaluateEur(TopsData tops, Exchange exchange) {
        return evaluateAll(tops, Currency.EUR, exchange);
    }

    public double evaluateUsd(TopsData tops, Exchange exchange) {
        return evaluateAll(tops, Currency.USD, exchange);
    }

    public double evaluateAll(TopsData tops, Currency curr, Exchange exchange) {
        double allValue = 0;
        for (Currency currency : m_funds.keySet()) {
            double value = getAllValue(currency);
            if (value > 0.000000001) {
                Double rate = tops.rate(exchange, currency, curr);
                if(rate != null) { // if can convert
                    value = value / rate;
                    allValue += value;
                }
            }

        }
        return allValue;
    }

    public double getAllValue(Currency currency) {
        double allValue = 0;
        Double available = m_funds.get(currency);
        if (available != null) {
            allValue += available;
        }
        Double allocated = m_allocatedFunds.get(currency);
        if (allocated != null) {
            allValue += allocated;
        }
        return allValue;
    }

    public double midMul(AccountData account) {
        int funds = 0;
        double ratiosSum = 0.0;
        for (Map.Entry<Currency, Double> entry : m_funds.entrySet()) {
            Currency currency = entry.getKey();
            double value1 = getAllValue(currency);
            double value2 = account.getAllValue(currency);
            double ratio = value1 / value2;
            ratiosSum += ratio;
            funds++;
        }
        return ratiosSum / funds;
    }

    public void compareFunds(AccountData account) {
        StringBuilder sb = getFundsDiff(account);
        if (sb != null) {
            log("warning " + sb + "\n acct1=" + account + "\n acct2=" + this);
        }
    }

    private StringBuilder getFundsDiff(AccountData account) {
        StringBuilder sb = null;
        for (Map.Entry<Currency, Double> entry : m_funds.entrySet()) {
            Currency curr = entry.getKey();
            Double value = entry.getValue();
            if ((value != null) && (value != 0)) {
                Double other = account.m_funds.get(curr);
                if ((other != null) && (other != 0)) {
                    double diffAbs = Math.abs(value - other);
                    double maxAbs = Math.max(Math.abs(value), Math.abs(other));
                    double ratio = diffAbs / maxAbs;
                    if (ratio > FUND_DIFF_RATIO) { // log if more that 1%
                        if (sb == null) {
                            sb = new StringBuilder();
                        }
                        sb.append(" fund diff: " + curr + " " + value + " " + other + ", diffAbs=" + Utils.format8(diffAbs)
                                + ", diffRatio=" + ratio + ";");
                        m_gotFundDiff = true;
                    }
                }
            }
        }
        return sb;
    }

    public String cancelOrder(OrderData od) throws Exception {
        Pair pair = od.m_pair;
        String orderId = od.m_orderId;
        if(orderId != null) {
            CancelOrderData coData = Fetcher.cancelOrder(m_exchange, orderId, pair);
            String error = coData.m_error;
            if (error == null) {
                od.cancel();
                boolean cancelRet = releaseOrder(od, m_exchange);
                return cancelRet ? "error releasing order" : null;
            } else {
                return error;
            }
        } else {
            log("Warning: unable to cancel order without id: " + od);
            return null;
        }
    }

    public double calcNeedBuyTo(double directionAdjusted, Pair pair, TopsData topsData, Exchange exchange, boolean verbose) {
        Currency currencyFrom = pair.m_from; // cnh=from
        Currency currencyTo = pair.m_to;     // btc=to

        double valuateTo = evaluateAll(topsData, currencyTo, exchange);
        double valuateFrom = evaluateAll(topsData, currencyFrom, exchange);
        if (verbose) {
            log("  valuate" + currencyTo + "=" + Utils.format8(valuateTo) + " " + currencyTo + "; valuate" + currencyFrom + "=" + Utils.format8(valuateFrom) + " " + currencyFrom);
        }

        double haveTo = getAllValue(currencyTo);
        double haveFrom = getAllValue(currencyFrom);
        if (verbose) {
            log("  have" + currencyTo + "=" + Utils.format8(haveTo) + " " + currencyTo + "; have" + currencyFrom + "=" + Utils.format8(haveFrom) + " " + currencyFrom + "; on account=" + this);
        }

        double needTo = (1 + directionAdjusted) / 2 * valuateTo;
        double needFrom = (1 - directionAdjusted) / 2 * valuateFrom;
        if (verbose) {
            log("  need" + currencyTo + "=" + Utils.format8(needTo) + " " + currencyTo + "; need" + currencyFrom + "=" + Utils.format8(needFrom) + " " + currencyFrom);
        }

        double needBuyTo = needTo - haveTo;
        double needSellCnh = haveFrom - needFrom;
        if (verbose) {
            log("  directionAdjusted=" + Utils.format8(directionAdjusted) + "; needBuy" + currencyTo + "=" + Utils.format8(needBuyTo) + "; needSell" + currencyFrom + "=" + Utils.format8(needSellCnh));
        }
        return needBuyTo;
    }
}
