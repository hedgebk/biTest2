package bthdg;

import java.io.IOException;

public class AccountData {
    public final String m_name;
    public double m_usd;
    public double m_btc;
    public double m_fee;
                             // todo: serialize
    public double m_allocatedUsd;
    public double m_allocatedBtc;

    private static void log(String s) { Log.log(s); }

    public double availableUsd() { return m_usd; }
    public double availableBtc() { return m_btc; }

    public AccountData(String name, double usd, double btc, double fee) {
        m_name = name;
        m_usd = usd;
        m_btc = btc;
        m_fee = fee;
    }

    @Override public String toString() {
        return "AccountData{" +
                "name='" + m_name + '\'' +
                ", usd=" + m_usd +
                ", btc=" + m_btc +
                ((m_fee != Double.MAX_VALUE) ? ", fee=" + m_fee : "") +
                '}';
    }

    public void compare(AccountData other) {
        if (!m_name.equals(other.m_name)) {
            throw new RuntimeException("m_name");
        }
        if (m_usd != other.m_usd) {
            throw new RuntimeException("m_usd");
        }
        if (m_btc != other.m_btc) {
            throw new RuntimeException("m_btc");
        }
        if (m_fee != other.m_fee) {
            throw new RuntimeException("m_fee");
        }
    }

    public void serialize(StringBuilder sb) {
        sb.append("Acct[name=").append(m_name);
        sb.append("; usd=").append(m_usd);
        sb.append("; btc=").append(m_btc);
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
        return orderData.m_side.allocate(this, orderData);
    }

    public boolean allocateBtc(OrderData sellBtc) {
        double amountBtc = sellBtc.m_amount;
        if (amountBtc > m_btc) {
            log("Unable to allocate " + amountBtc + " btc. available=" + m_btc);
            return false;
        } else {
            m_btc -= amountBtc;
            m_allocatedBtc += amountBtc;
            return true;
        }
    }

    public boolean allocateUsd(OrderData buyBtc) {
        double amountBtc = buyBtc.m_amount;
        double price = buyBtc.m_price;
        double amountUsd = amountBtc * price;
        if (amountUsd > m_usd) {
            log("Unable to allocate " + amountUsd + " usd. available=" + m_usd);
            return false;
        } else {
            m_usd -= amountUsd;
            m_allocatedUsd += amountUsd;
            return true;
        }
    }

    public void releaseOrder(OrderData orderData) {
        orderData.m_side.release(this, orderData);
    }

    public void releaseUsd(OrderData orderData) {
        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
            log("special case - some portion is already executed. releasing only remained part="+orderData.remained());
        }
        double amountBtc = orderData.remained();
        double price = orderData.m_price;
        double amountUsd = amountBtc * price;
        m_usd += amountUsd;
        m_allocatedUsd -= amountUsd;
    }

    public void releaseBtc(OrderData orderData) {
        if(orderData.m_filled > 0) { // special case - some portion is already executed. releasing only remained part
            log("special case - some portion is already executed. releasing only remained part="+orderData.remained());
        }
        double amountBtc = orderData.remained();
        m_btc += amountBtc;
        m_allocatedBtc -= amountBtc;
    }

    public void releaseTrade(OrderSide orderSide, double price, double amount) {
        orderSide.releaseTrade(this, price, amount);
    }

    public void releaseUsd(double price, double btcAmount) {
        double amountUsd = btcAmount * price;
        m_allocatedUsd -= amountUsd;
        m_btc += btcAmount * (1 - m_fee);
    }

    public void releaseBtc(double price, double btcAmount) {
        m_allocatedBtc -= btcAmount;
        double amountUsd = btcAmount * price;
        m_usd += amountUsd * (1 - m_fee);
    }
}
