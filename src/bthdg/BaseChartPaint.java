package bthdg;

import java.awt.*;
import java.awt.geom.Rectangle2D;

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

    static void paintLeftAxeLabels(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe, Graphics2D g, int priceStep, int priceStart) {
        g.setFont(g.getFont().deriveFont(20.0f));
        for (int price = priceStart; price < maxPrice; price += priceStep) {
            if (price >= minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawString(Integer.toString(price), 2, y - 1);
            }
        }
    }

    static void paintRightAxeLabels(double minDif, double maxDif, PaintChart.ChartAxe difAxe, Graphics2D g, int width, int priceDifStep) {
        int priceDifStart = ((int) minDif) / priceDifStep * priceDifStep;
        System.out.println("priceDifStart=" + priceDifStart);
        g.setFont(g.getFont().deriveFont(20.0f));
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

}
