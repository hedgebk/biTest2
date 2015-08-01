package bthdg.osc;

import bthdg.IIterationContext;
import bthdg.exch.TradeData;

enum OscState {
    NONE { // no order placed
        @Override public OscState onDirection(OscExecutor executor) throws Exception {
            BaseExecutor.log("State.NONE.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
            int stateCode = executor.processDirection();
            return codeToState(stateCode);
        }
    },
    ORDER { // order placed - waiting
        @Override public OscState onDirection(OscExecutor executor) throws Exception {
            BaseExecutor.log("State.ORDER.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
            int stateCode = executor.processDirection();
            return codeToState(stateCode);
        }
        @Override public OscState onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            BaseExecutor.log("State.ORDER.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            return executor.processTrade(tData, iContext);
        }
        @Override public void onTop(OscExecutor executor) throws Exception {
            BaseExecutor.log("State.ORDER.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
            executor.processTop();
        }
    },
    ERROR {
        @Override public OscState onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            BaseExecutor.log("State.ERROR.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            executor.onError();
            return NONE;
        }
    };

    private static OscState codeToState(int stateCode) {
        switch (stateCode) {
            case BaseExecutor.STATE_NO_CHANGE: return null;
            case BaseExecutor.STATE_NONE: return NONE;
            case BaseExecutor.STATE_ORDER: return ORDER;
            case BaseExecutor.STATE_ERROR: return ERROR;
        }
        return null;
    }

    public void onTop(OscExecutor executor) throws Exception {
        BaseExecutor.log("State.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
    }

    public OscState onTrade(OscExecutor executor, TradeData tData, IIterationContext.BaseIterationContext iContext) throws Exception {
        BaseExecutor.log("State.onTrade(tData=" + tData + ") on " + this + " *********************************************");
        return this;
    }

    public OscState onDirection(OscExecutor executor) throws Exception {
        BaseExecutor.log("State.onDirection(direction=" + executor.m_direction + ") on " + this + " *********************************************");
        return this;
    }
}
