package bthdg.osc;

import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.TradeData;

enum OscState {
    NONE { // no order placed
        @Override public OscState onDirection(OscExecutor executor) throws Exception {
            BaseExecutor.log("State.NONE.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
            int stateCode = executor.processDirection();
            return codeToState(stateCode);
        }
        public int toCode() { return BaseExecutor.STATE_NONE; }
    },
    ORDER { // order placed - waiting
        @Override public OscState onDirection(OscExecutor executor) throws Exception {
            BaseExecutor.log("State.ORDER.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
            int stateCode = executor.processDirection();
            return codeToState(stateCode);
        }
        @Override public OscState onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            BaseExecutor.log("State.ORDER.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            int stateCode = executor.processTrade(tData, iContext);
            return codeToState(stateCode);
        }
        @Override public void onTop(OscExecutor executor) throws Exception {
            BaseExecutor.log("State.ORDER.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
            executor.processTop();
        }
        public int toCode() { return BaseExecutor.STATE_ORDER; }
    },
    ERROR {
        @Override public OscState onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            BaseExecutor.log("State.ERROR.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            executor.onError();
            return NONE;
        }
        public int toCode() { return BaseExecutor.STATE_ERROR; }
    };

    protected static void log(String s) { Log.log(s); }

    public void onTop(OscExecutor executor) throws Exception {
        log("State.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
    }

    public OscState onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
        log("State.onTrade(tData=" + tData + ") on " + this + " *********************************************");
        return this;
    }

    public OscState onDirection(OscExecutor executor) throws Exception {
        log("State.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
        return this;
    }

    public int toCode() { return 0; }

    private static OscState codeToState(int stateCode) {
        switch (stateCode) {
            case BaseExecutor.STATE_NO_CHANGE: return null;
            case BaseExecutor.STATE_NONE: return NONE;
            case BaseExecutor.STATE_ORDER: return ORDER;
            case BaseExecutor.STATE_ERROR: return ERROR;
        }
        return null;
    }

    public static int toCode(OscState oscState) {
        if(oscState == null) {
            return BaseExecutor.STATE_NO_CHANGE;
        }
        return oscState.toCode();
    }
}
