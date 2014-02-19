package bthdg;

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
} // Execution
