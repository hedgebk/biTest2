package bthdg;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class BaseChartPaint extends DbReady{
    static void paintLeftAxeAndGrid(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe,
                                    Graphics2D g, int priceStep, int priceStart, int width) {
        g.setPaint(new Color(128, 128, 128, 128)); // Color.gray
        final float dash1[] = {10.0f};
        BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash1, 0.0f);
        Stroke oldStroke = g.getStroke();
        g.setStroke(dashedStroke);
        for (int price = priceStart; price < maxPrice; price += priceStep) {
            if (price > minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawLine(0, y, width - 1, y);
            }
        }
        g.setStroke(oldStroke);
    }

    static void paintLeftAxeLabels(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe, Graphics2D g, int priceStep, int priceStart, float xFactor) {
        g.setFont(g.getFont().deriveFont(20.0f * xFactor));
        for (int price = priceStart; price < maxPrice; price += priceStep) {
            if (price >= minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawString(Integer.toString(price), 2, y - 1);
            }
        }
    }

    static void paintRightAxeLabels(double minDif, double maxDif, PaintChart.ChartAxe difAxe, Graphics2D g, int width, int priceDifStep, float xFactor) {
        int priceDifStart = ((int) minDif) / priceDifStep * priceDifStep;
//        System.out.println("priceDifStart=" + priceDifStart);
        g.setFont(g.getFont().deriveFont(20.0f * xFactor));
        FontMetrics fontMetrics = g.getFontMetrics();
        for (int price = priceDifStart; price < maxDif; price += priceDifStep) {
            if (price >= minDif) {
                int y = difAxe.getPointReverse(price);
                g.drawLine(width - 20, y, width - 1, y);
                String str = Integer.toString(price);
                Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
                g.drawString(str, (float) (width - 20 - bounds.getWidth()), y - 1);
            }
        }
    }

    public static void paintTimeAxeLabels(long minTimestamp, long maxTimestamp, PaintChart.ChartAxe timeAxe, Graphics2D g, int height, float xFactor) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(minTimestamp);
        Utils.setToDayStart(cal);

        long timePeriod = maxTimestamp - minTimestamp;
        int timePeriodDays = (int) (timePeriod / Utils.ONE_DAY_IN_MILLIS);

        // paint DAYS
        g.setFont(g.getFont().deriveFont(30.0f * xFactor));
        SimpleDateFormat format = new SimpleDateFormat("d MMM");
        while (true) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            long millis = cal.getTimeInMillis();
            if (millis >= minTimestamp) {
                if (millis > maxTimestamp) {
                    break;
                }
                String label = format.format(cal.getTime());
                int x = timeAxe.getPoint(millis);
                g.drawLine(x, height - 10, x, height - 1);
                g.drawString(label, x + 2, height - 8);
            }
        }

        // paint HOURS
        if (timePeriodDays <= 15) {
            cal.setTimeInMillis(minTimestamp);
            Utils.setToDayStart(cal);

            int hourPlus = (timePeriodDays > 6) ? 6 : ((timePeriodDays > 1) ? 2 : 1);

            g.setFont(g.getFont().deriveFont(20.0f * xFactor));
            format = new SimpleDateFormat("HH:mm");
            while (true) {
                cal.add(Calendar.HOUR_OF_DAY, hourPlus);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                if (hour != 0) {
                    long millis = cal.getTimeInMillis();
                    if (millis >= minTimestamp) {
                        if (millis > maxTimestamp) {
                            break;
                        }
                        String label = format.format(cal.getTime());
                        int x = timeAxe.getPoint(millis);
                        g.drawLine(x, height - 10, x, height - 1);
                        g.drawString(label, x + 2, height - 4);
                    }
                }
            }
        }
    }
}
