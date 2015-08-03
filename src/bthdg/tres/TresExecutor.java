package bthdg.tres;

import bthdg.IIterationContext;
import bthdg.exch.OrderData;
import bthdg.exch.OrderSide;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.osc.BaseExecutor;
import bthdg.ws.IWs;

import java.util.List;

public class TresExecutor extends BaseExecutor{
    private static final long MIN_ORDER_LIVE_TIME = 6000;
    private static final double OUT_OF_MARKET_THRESHOLD = 0.5;

    @Override protected long minOrderLiveTime() { return MIN_ORDER_LIVE_TIME; }
    @Override protected double outOfMarketThreshold() { return OUT_OF_MARKET_THRESHOLD; }

    public TresExecutor(IWs ws, Pair pair) {
        super(ws, pair);
        Thread thread = new Thread(this);
        thread.setName("TresExecutor");
        thread.start();
    }

    @Override protected void gotTop() throws Exception {
        throw new Exception("not implemented");
    }

    @Override protected void gotTrade(TradeData tradeData) throws Exception {
        throw new Exception("not implemented");
    }

    @Override protected void cancelAllOrders() throws Exception {
        throw new Exception("not implemented");
    }

    @Override protected void recheckDirection() throws Exception {
        throw new Exception("not implemented");
    }

    @Override protected List<OrderData> getAllOrders() {
        throw new RuntimeException("not implemented");
//        return null;
    }

    @Override protected IIterationContext.BaseIterationContext checkLiveOrders() throws Exception {
        throw new Exception("not implemented");
//        return null;
    }

    @Override protected double getDirectionAdjusted() {
        throw new RuntimeException("not implemented");
//        return 0;
    }

    @Override protected double checkAgainstExistingOrders(OrderSide needOrderSide, double orderSize) {
        throw new RuntimeException("not implemented");
//        return 0;
    }

    @Override protected int cancelOrderIfPresent() throws Exception {
        throw new Exception("not implemented");
//        return 0;
    }

    @Override protected double minOrderSizeToCreate() {
        throw new RuntimeException("not implemented");
//        return 0;
    }

    @Override protected boolean cancelOtherOrdersIfNeeded(OrderSide needOrderSide, double notEnough) throws Exception {
        throw new Exception("not implemented");
//        return false;
    }

    @Override protected void onOrderPlace(OrderData placeOrder) {
        throw new RuntimeException("not implemented");
    }

    @Override protected boolean checkNoOpenOrders() {
        throw new RuntimeException("not implemented");
//        return false;
    }

    @Override protected boolean hasOrdersWithMatchedPrice(double tradePrice) {
        throw new RuntimeException("not implemented");
//        return false;
    }

    @Override protected void checkOrdersOutOfMarket() throws Exception {
        throw new Exception("not implemented");
    }

    @Override protected int checkOrdersState(IIterationContext.BaseIterationContext iContext) throws Exception {
        throw new Exception("not implemented");
//        return 0;
    }

    @Override protected boolean haveNotFilledOrder() {
        throw new RuntimeException("not implemented");
//        return false;
    }

    @Override protected void processTopInt() throws Exception {
        throw new Exception("not implemented");
    }
}
