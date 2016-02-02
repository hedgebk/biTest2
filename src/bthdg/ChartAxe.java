package bthdg;

import bthdg.util.Utils;

public class ChartAxe {
    public final int m_size;
    public double m_min;
    public double m_max;
    public double m_scale;
    public int m_offset;

    public ChartAxe(Utils.DoubleMinMaxCalculator calculator, int size) {
        this(calculator.m_minValue, calculator.m_maxValue, size);
    }

    public ChartAxe(double min, double max, int size) {
        m_size = size;
        m_min = min;
        m_max = max;
        updateScale();
    }

    protected void updateScale() {
        m_scale = (m_max - m_min) / m_size;
    }

    public void updateBounds(double min, double max) {
        m_min = Math.min(m_min, min);
        m_max = Math.max(m_max, max);
        updateScale();
    }

    public int getPoint(double value) {
        int pointInt = getPointInt(value);
        int point = m_offset + pointInt;
        return point;
    }

    private int getPointInt(double value) {
        double offset = value - m_min;
        int getPointInt = (int) (offset / m_scale);
        return getPointInt;
    }


    public int getPointReverse(double value) {
        int point = getPointInt(value);
        return m_offset + m_size - 1 - point;
    }

    private double getValue(int pointInt) {
        double offset = pointInt * m_scale;
        double value = offset + m_min;
        return value;
    }

    public double getValueFromPoint(int point) {
        int pointInt = point - m_offset;
        double value = getValue(pointInt);
        return value;
    }
}
