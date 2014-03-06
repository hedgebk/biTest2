package bthdg;

import java.awt.*;

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
}
