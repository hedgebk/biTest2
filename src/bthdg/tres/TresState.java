package bthdg.tres;

import bthdg.IIterationContext;
import bthdg.Log;
import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;

public enum TresState {
    NONE {
        @Override public TresState onDirection(TresExecutor executor) throws Exception {
            log("State.NONE.onDirection(direction=" + executor.getDirectionAdjusted() + ") on " + this + " *********************************************");
            int stateCode = executor.processDirection();
            return codeToState(stateCode);
        }
        @Override public TresState onStopRequested(TresExecutor executor) throws Exception {
            executor.cancelOrdersAndReset();
            return executor.parkAccount();
        }
        @Override public int toCode() { return BaseExecutor.STATE_NONE; }
    },
    ORDER {  // order placed - waiting execution
        @Override public TresState onDirection(TresExecutor executor) throws Exception {
            log("State.ORDER.onDirection(direction=" + executor.getDirectionAdjusted() + ") on " + this + " *********************************************");
            int stateCode = executor.processDirection();
            return codeToState(stateCode);
        }
        @Override public TresState onTrade(TresExecutor executor, TradeDataLight tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            log("State.ORDER.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            int stateCode = executor.processTrade(tData, iContext);
            return codeToState(stateCode);
        }
        @Override public void onTop(TresExecutor executor) throws Exception {
            log("State.ORDER.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
            executor.processTop();
        }
        @Override public TresState onStopRequested(TresExecutor executor) throws Exception {
            executor.cancelOrdersAndReset();
            return executor.parkAccount();
        }
        @Override public int toCode() { return BaseExecutor.STATE_ORDER; }
    },
    STOP {
        @Override public TresState onTrade(TresExecutor executor, TradeDataLight tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            log("State.STOP.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            int stateCode = executor.processTrade(tData, iContext);
            if (stateCode == BaseExecutor.STATE_NONE) {
                log(" park order finished. ");
                executor.onStopped();
                return NONE;
            }
            if (stateCode != BaseExecutor.STATE_NO_CHANGE) {
                log(" STOP state. onTrade. processTrade unexpected code " + stateCode);
                executor.cancelOrdersAndReset();
                return executor.parkAccount();
            }
            if (executor.tooOldStopOrder()) {
                log("     tooOldStopOrder - need reset");
                executor.cancelOrdersAndReset();
                return executor.parkAccount();
            }
            return null; // no change
        }
    },
    STOPPED,
    ERROR {
        @Override public TresState onTrade(TresExecutor executor, TradeDataLight tData, IIterationContext.BaseIterationContext iContext) throws Exception {
            log("State.ERROR.onTrade(tData=" + tData + ") on " + this + " *********************************************");
            executor.onError();
            return executor.onAfterError();
        }
        @Override public int toCode() { return BaseExecutor.STATE_ERROR; }
    };

    protected static void log(String s) { Log.log(s); }

    public void onTop(TresExecutor executor) throws Exception {
        log("State.onTop(buy=" + executor.m_buy + ", sell=" + executor.m_sell + ") on " + this + " *********************************************");
    }

    public TresState onTrade(TresExecutor executor, TradeDataLight tData, IIterationContext.BaseIterationContext iContext) throws Exception {
        log("State.onTrade(tData=" + tData + ") on " + this + " *********************************************");
        return null; // no change
    }

    public TresState onDirection(TresExecutor executor) throws Exception {
        log("State.onDirection(direction=" + executor.getDirectionAdjusted() + ") on " + this + " *********************************************");
        return null; // no change
    }

    public TresState onStopRequested(TresExecutor executor) throws Exception {
        log("State.onStopRequested() on " + this + " *********************************************");
        return null; // no change
    }

    public int toCode() { return 0; }

    public static TresState codeToState(int stateCode) {
        switch (stateCode) {
            case BaseExecutor.STATE_NO_CHANGE: return null;
            case BaseExecutor.STATE_NONE: return NONE;
            case BaseExecutor.STATE_ORDER: return ORDER;
            case BaseExecutor.STATE_ERROR: return ERROR;
        }
        return null;
    }

    public static int toCode(TresState state) {
        if(state == null) {
            return BaseExecutor.STATE_NO_CHANGE;
        }
        return state.toCode();
    }
}
