package bthdg.tres;

import bthdg.Log;

public class ChartPoint {
    public final long m_millis;
    public final double m_value;

    private static void log(String s) { Log.log(s); }

    public ChartPoint(long millis, double value) {
        m_millis = millis;
        if (Double.isNaN(value)) {
            log("ChartPoint. value=" + value);
        }
        m_value = value;
    }

    @Override public String toString() {
        return "ChartPoint{" +
                "millis=" + m_millis +
                ", value=" + m_value +
                '}';
    }
}
