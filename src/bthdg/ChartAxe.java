package bthdg;

import bthdg.util.Utils;

public class ChartAxe {
    public final double m_min;
    public final double m_max;
    public final int m_size;
    public final double m_scale;
    public int m_offset;

    public ChartAxe(Utils.DoubleMinMaxCalculator calculator, int size) {
        this(calculator.m_minValue, calculator.m_maxValue, size);
    }

    public ChartAxe(double min, double max, int size) {
        m_min = min;
        m_max = max;
        m_size = size;
        double diff = max - min;
        m_scale = diff / size;
    }

    public int getPoint(double value) {
        return m_offset + getPointInt(value);
    }

    private int getPointInt(double value) {
        double offset = value - m_min;
        return (int) (offset / m_scale);
    }

    public int getPointReverse(double value) {
        int point = getPointInt(value);
        return m_offset + m_size - 1 - point;
    }
}
