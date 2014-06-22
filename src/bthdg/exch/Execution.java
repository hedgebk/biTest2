package bthdg.exch;

import bthdg.Deserializer;

import java.io.IOException;

public class Execution {
    public final double m_price;
    public final double m_amount;

    public Execution(double price, double amount) {
        m_price = price;
        m_amount = amount;
    }

    public void serialize(StringBuilder sb) {
        sb.append("Exec[price=").append(m_price);
        sb.append("; amount=").append(m_amount);
        sb.append("; ]");
    }

    public static Execution deserialize(Deserializer deserializer) throws IOException {
        deserializer.readObjectStart("Exec");
        deserializer.readPropStart("price");
        String priceStr = deserializer.readTill("; ");
        deserializer.readPropStart("amount");
        String amountStr = deserializer.readTill("; ");
        deserializer.readObjectEnd();

        Double price = Double.parseDouble(priceStr);
        Double amount = Double.parseDouble(amountStr);

        return new Execution(price, amount);
    }

    public void compare(Execution other) {
        if (m_price != other.m_price) {
            throw new RuntimeException("m_price");
        }
        if (m_amount != other.m_amount) {
            throw new RuntimeException("m_amount");
        }
    }
} // Execution
