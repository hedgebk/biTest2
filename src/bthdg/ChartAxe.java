package bthdg;

import bthdg.util.Utils;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;

public class ChartAxe {
    public static final int AXE_MARKER_WIDTH = 10;

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

    public int paintYAxe(Graphics g, int right, Color color) {
        g.setColor(color);

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;

        int maxLabelsCount = m_size * 3 / fontHeight / 4;
        double diff = m_max - m_min;
        double maxLabelsStep = diff / maxLabelsCount;
        double log = Math.log10(maxLabelsStep);
        int floor = (int) Math.floor(log);
        int points = Math.max(0, -floor);
        double pow = Math.pow(10, floor);
        double mant = maxLabelsStep / pow;
        int stepMant;
        if (mant == 1) {
            stepMant = 1;
        } else if (mant <= 2) {
            stepMant = 2;
        } else if (mant <= 5) {
            stepMant = 5;
        } else {
            stepMant = 1;
            floor++;
            pow = Math.pow(10, floor);
        }
        double step = stepMant * pow;

        double minLabel = Math.floor(m_min / step) * step;
        double maxLabel = Math.ceil(m_max / step) * step;

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(points);
        nf.setMinimumFractionDigits(points);

        FontMetrics fontMetrics = g.getFontMetrics();
        int maxWidth = 10;
        for (double y = minLabel; y <= maxLabel; y += step) {
            String str = nf.format(y);
            Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
            int stringWidth = (int) bounds.getWidth();
            maxWidth = Math.max(maxWidth, stringWidth);
        }

        int x = right - maxWidth;

        for (double val = minLabel; val <= maxLabel; val += step) {
            String str = nf.format(val);
            int y = getPointReverse(val);
            g.drawString(str, x, y + halfFontHeight);
            g.drawLine(x - 2, y, x - AXE_MARKER_WIDTH, y);
        }

//        g.drawString("h" + height, x, fontHeight * 2);
//        g.drawString("m" + maxLabelsCount, x, fontHeight * 3);
//        g.drawString("d" + diff, x, fontHeight * 4);
//        g.drawString("m" + maxLabelsStep, x, fontHeight * 5);
//        g.drawString("l" + log, x, fontHeight * 6);
//        g.drawString("f" + floor, x, fontHeight * 7);
//        g.drawString("p" + pow, x, fontHeight * 8);
//        g.drawString("m" + mant, x, fontHeight * 9);
//        g.drawString("s" + stepMant, x, fontHeight * 11);
//        g.drawString("f" + floor, x, fontHeight * 12);
//        g.drawString("p" + pow, x, fontHeight * 13);
//        g.drawString("s" + step, x, fontHeight * 14);
//        g.drawString("p" + points, x, fontHeight * 15);
//
//        g.drawString("ma" + maxLabel, x, fontHeight * 17);
//        g.drawString("mi" + minLabel, x, fontHeight * 18);

        return maxWidth + AXE_MARKER_WIDTH + 2;
    }

}
