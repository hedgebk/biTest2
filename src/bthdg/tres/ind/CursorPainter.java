package bthdg.tres.ind;

import bthdg.BaseChartPaint;
import bthdg.ChartAxe;
import bthdg.tres.ChartPoint;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.awt.*;

//import bthdg.tres.alg.Interpolator;

public class CursorPainter {
    private final TresIndicator m_indicator;
    private final long m_barSizeMillis;
//    private final Interpolator m_interpolator = new Interpolator();
    private final SplineInterpolator m_spline = new SplineInterpolator();
    private final int m_points;
    private final long[] m_paintTime;
    private final int[] m_paintTimeX;
    private final ChartPoint[] m_paintChartPoint;
    private final double[] m_interpolateX;
    private final double[] m_interpolateY;

    public CursorPainter(TresIndicator indicator) {
        this(indicator, indicator.m_algo.m_tresExchData.m_tres.m_barSizeMillis, 3);
    }

    public CursorPainter(TresIndicator indicator, long size, int points) {
        m_indicator = indicator;
        m_barSizeMillis = size;
        m_points = points;
        m_paintTime = new long[points];
        m_paintTimeX = new int[points];
        m_paintChartPoint = new ChartPoint[points];
        m_interpolateX = new double[points];
        m_interpolateY = new double[points];
    }

    public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        if (m_indicator.doPaint() && (m_indicator.m_yAxe != null)) {
            if (cursorPoint != null) {
                int cursorPointX = (int) cursorPoint.getX();

                int xMin = xTimeAxe.m_offset;
                int xMax = xMin + xTimeAxe.m_size;
                if((cursorPointX > xMin) && (cursorPointX < xMax)) {
                    long timeRight = (long) xTimeAxe.getValueFromPoint(cursorPointX);
//                    long timeMid = timeRight - m_barSizeMillis;
                    long timeLeft = timeRight - m_barSizeMillis * (m_points - 1);

                    long pointTime = timeLeft;
                    for(int i = 0; i < m_points; i++) {
                        m_paintTime[i] = pointTime;
                        m_paintTimeX[i] = xTimeAxe.getPoint(pointTime);
                        m_paintChartPoint[i] = findClosest(pointTime);
                        pointTime += m_barSizeMillis;
                    }

//                    int xRight = xTimeAxe.getPoint(timeRight);
//                    int xMid = xTimeAxe.getPoint(timeMid);
//                    int xLeft = xTimeAxe.getPoint(timeLeft);

                    int xRightRight = xTimeAxe.getPoint(timeRight + m_barSizeMillis);
                    int xLeftLeft = xTimeAxe.getPoint(timeLeft - m_barSizeMillis);

//                    int xRightRight = xRight + (xRight - xMid);
//                    int xLeftLeft = xLeft - (xMid - xLeft);

                    double yMin = yPriceAxe.m_offset;
                    double yMax = yMin + yPriceAxe.m_size;
                    double yMid = (yMax - yMin) / 2;

                    int yTop = (int) (yMid - 100);
                    int yBottom = (int) (yMid + 100);

                    for(int i = 0; i < m_points; i++) {
                        int x = m_paintTimeX[i];
                        g.drawLine(x, yTop, x, yBottom);
                        drawX(g, xTimeAxe, m_paintChartPoint[i]);
                    }
//                    g.drawLine(xRight, yTop, xRight, yBottom);
//                    g.drawLine(xMid, yTop, xMid, yBottom);
//                    g.drawLine(xLeft, yTop, xLeft, yBottom);

//                    ChartPoint right = findClosest(timeRight);
//                    ChartPoint mid = findClosest(timeMid);
//                    ChartPoint left = findClosest(timeLeft);

//                    drawX(g, xTimeAxe, right);
//                    drawX(g, xTimeAxe, mid);
//                    drawX(g, xTimeAxe, left);

                    boolean linearlyIncreased = true;
                    for (int i = 0; i < m_points; i++) {
                        ChartPoint chartPoint = m_paintChartPoint[i];
                        m_interpolateX[i] = chartPoint.m_millis;
                        m_interpolateY[i] = chartPoint.m_value;
                        if (i > 0) {
                            ChartPoint chartPointLeft = m_paintChartPoint[i - 1];
                            if (chartPointLeft.m_millis >= chartPoint.m_millis) { // should linearly increase
                                linearlyIncreased = false;
                            }
                        }
                    }

//                    long minMillis = left.m_millis;
//                    long midMillis = mid.m_millis;
//                    long maxMillis = right.m_millis;
//                    if( (minMillis < midMillis) && (midMillis<maxMillis)) { // should linearly increase
                    if( linearlyIncreased ) { // should linearly increase
                        PolynomialSplineFunction polynomialFunc = null;
                        try {
//                            polynomialFunc = m_interpolator.interpolate(minMillis, left.m_value, midMillis, mid.m_value, maxMillis, right.m_value);
                            polynomialFunc = m_spline.interpolate(m_interpolateX, m_interpolateY);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }

                        PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();

                        int lastX = Integer.MAX_VALUE;
                        int lastY = Integer.MAX_VALUE;
                        for (int x = xLeftLeft; x <= xRightRight; x++) {
                            long time = (long) xTimeAxe.getValueFromPoint(x);

                            int polyIndex = m_points - 2;
                            for (int i = 1; i < m_points; i++) {
                                long pt = m_paintTime[i];
                                if (time < pt) {
                                    polyIndex = i - 1;
                                    break;
                                }
                            }

                            PolynomialFunction polynomial = polynomials[polyIndex];
//                              UnivariateFunction derivative = polynomial.derivative();
                            long offset = time - m_paintTime[polyIndex];
                            double value = polynomial.value(offset);
                            int yy = m_indicator.m_yAxe.getPointReverse(value);
                            if (lastX != Integer.MAX_VALUE) {
                                g.drawLine(lastX, lastY, x, yy);
                            }
                            lastX = x;
                            lastY = yy;


//                            if (true/*(time >= minMillis) && (time <= maxMillis)*/) {
////                                    double value = polynomialFunc.value(time);
//////                                    int xx = xTimeAxe.getPoint(time);
////                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
////                                    if (lastX != Integer.MAX_VALUE) {
////                                        g.drawLine(lastX, lastY, x, yy);
////                                    }
////                                    lastX = x;
////                                    lastY = yy;
//
//                                if(x < xMid) {
//                                    PolynomialFunction polynomial = polynomials[0];
////                    UnivariateFunction derivative = polynomial.derivative();
//                                    long offset = time - minMillis;
//                                    double value = polynomial.value(offset);
//                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
//                                    if (lastX != Integer.MAX_VALUE) {
//                                        g.drawLine(lastX, lastY, x, yy);
//                                    }
//                                    lastX = x;
//                                    lastY = yy;
//                                } else {
//                                    PolynomialFunction polynomial = polynomials[1];
//                                    long offset = time - midMillis;
//                                    double value = polynomial.value(offset);
//                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
//                                    if (lastX != Integer.MAX_VALUE) {
//                                        g.drawLine(lastX, lastY, x, yy);
//                                    }
//                                    lastX = x;
//                                    lastY = yy;
//                                }
//                            }
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
