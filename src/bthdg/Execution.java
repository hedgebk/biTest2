package bthdg;

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
        sb.append("]");
    }
} // Execution
