package bthdg.tres.ind;

import bthdg.BaseChartPaint;
import bthdg.ChartAxe;
import bthdg.tres.ChartPoint;
import bthdg.tres.alg.Interpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.awt.*;

public class CursorPainter {
    private final TresIndicator m_indicator;
    private final long m_barSizeMillis;
    private final Interpolator m_interpolator = new Interpolator();

    public CursorPainter(TresIndicator indicator) {
        this(indicator, indicator.m_algo.m_tresExchData.m_tres.m_barSizeMillis);
    }

    public CursorPainter(TresIndicator indicator, long size) {
        m_indicator = indicator;
        m_barSizeMillis = size;
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        if (m_indicator.doPaint() && (m_indicator.m_yAxe != null)) {
            if (cursorPoint != null) {
                int cursorPointX = (int) cursorPoint.getX();

                int xMin = xTimeAxe.m_offset;
                int xMax = xMin + xTimeAxe.m_size;
                if((cursorPointX > xMin) && (cursorPointX < xMax)) {
                    long timeRight = (long) xTimeAxe.getValueFromPoint(cursorPointX);
                    long timeMid = timeRight - m_barSizeMillis;
                    long timeLeft = timeMid - m_barSizeMillis;

                    int xRight = xTimeAxe.getPoint(timeRight);
                    int xMid = xTimeAxe.getPoint(timeMid);
                    int xLeft = xTimeAxe.getPoint(timeLeft);

                    int xRightRight = xRight + (xRight - xMid);
                    int xLeftLeft = xLeft - (xMid - xLeft);

                    double yMin = yPriceAxe.m_offset;
                    double yMax = yMin + yPriceAxe.m_size;
                    double yMid = (yMax - yMin) / 2;

                    int yTop = (int) (yMid - 100);
                    int yBottom = (int) (yMid + 100);

                    g.drawLine(xRight, yTop, xRight, yBottom);
                    g.drawLine(xMid, yTop, xMid, yBottom);
                    g.drawLine(xLeft, yTop, xLeft, yBottom);

                    ChartPoint right = findClosest(timeRight);
                    ChartPoint mid = findClosest(timeMid);
                    ChartPoint left = findClosest(timeLeft);

                    drawX(g, xTimeAxe, right);
                    drawX(g, xTimeAxe, mid);
                    drawX(g, xTimeAxe, left);

                    long minMillis = left.m_millis;
                    long midMillis = mid.m_millis;
                    long maxMillis = right.m_millis;
                    if( (minMillis < midMillis) && (midMillis<maxMillis)) { // should linearly increase
                        PolynomialSplineFunction polynomialFunc = null;
                        try {
                            polynomialFunc = m_interpolator.interpolate(minMillis, left.m_value, midMillis, mid.m_value, maxMillis, right.m_value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();

                        int lastX = Integer.MAX_VALUE;
                        int lastY = Integer.MAX_VALUE;
                        for (int x = xLeftLeft; x <= xRightRight; x++) {
                            long time = (long) xTimeAxe.getValueFromPoint(x);
                            if (true/*(time >= minMillis) && (time <= maxMillis)*/) {
//                                    double value = polynomialFunc.value(time);
////                                    int xx = xTimeAxe.getPoint(time);
//                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
//                                    if (lastX != Integer.MAX_VALUE) {
//                                        g.drawLine(lastX, lastY, x, yy);
//                                    }
//                                    lastX = x;
//                                    lastY = yy;

                                if(x < xMid) {
                                    PolynomialFunction polynomial = polynomials[0];
//                    UnivariateFunction derivative = polynomial.derivative();
                                    long offset = time - minMillis;
                                    double value = polynomial.value(offset);
                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
                                    if (lastX != Integer.MAX_VALUE) {
                                        g.drawLine(lastX, lastY, x, yy);
                                    }
                                    lastX = x;
                                    lastY = yy;
                                } else {
                                    PolynomialFunction polynomial = polynomials[1];
                                    long offset = time - midMillis;
                                    double value = polynomial.value(offset);
                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
                                    if (lastX != Integer.MAX_VALUE) {
                                        g.drawLine(lastX, lastY, x, yy);
                                    }
                                    lastX = x;
                                    lastY = yy;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected void drawX(Graphics g, ChartAxe xTimeAxe, ChartPoint cp) {
        long time = cp.m_millis;
        int xx = xTimeAxe.getPoint(time);
        double value = cp.m_value;
        int yy = m_indicator.m_yAxe.getPointReverse(value);
        BaseChartPaint.drawX(g, xx, yy, 5);
    }

    protected ChartPoint findClosest(long time) {
        long minDiff = Long.MAX_VALUE;
        ChartPoint closest = null;
        for (ChartPoint cp : m_indicator.m_avgPaintPoints) {
            long millis = cp.m_millis;
            long diffAbs = Math.abs(millis - time);
            if(diffAbs < minDiff) {
                minDiff = diffAbs;
                closest = cp;
            }
        }
        return closest;
    }
}
