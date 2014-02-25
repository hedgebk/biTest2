package bthdg;

import java.io.IOException;

public class AccountData {
    public final String m_name;
    public double m_usd;
    public double m_btc;
    public double m_fee;

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
}
