package bthdg.duplet;

import bthdg.Log;

public enum ForkState {
    NONE {
        @Override public boolean needQueryTrades() { return false; }
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.NONE. queryAccountsData");
            //iContext.queryAccountsData(forkData);
            forkData.setState(OPENING_CROSS);
            iContext.delay(0);
        }
    },
    OPENING_CROSS {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.OPENING_CROSS. try to place open crosses orders");
            forkData.placeOpenCross(iContext);
        }
    },
    OPEN_CROSS_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.OPEN_CROSS_PLACED checkState()");
            if (forkData.m_openCross.m_state == CrossState.BOTH_BRACKETS_EXECUTED) { // open cross executed ?
                log(" open cross state is BOTH_BRACKETS_EXECUTED. try to place close crosses orders");
                forkData.setState(ForkState.OPEN_CROSS_EXECUTED);
                // do nothing more here - pairExchange will check do we need to start close cross
            }
        }
    },
    OPEN_CROSS_EXECUTED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.OPEN_CROSS_EXECUTED checkState() do nothing");
            // do nothing more here - pairExchange will check do we need to start close cross
        }
    },
//    CLOSING_CROSS {
//        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
//            log("ForkState.CLOSING_CROSS. try to place close crosses orders");
//            forkData.placeCloseCross(iContext);
//        }
//    },
    CLOSE_CROSS_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.CLOSE_CROSS_PLACED checkState()");
            if (forkData.m_closeCross.m_state == CrossState.BOTH_BRACKETS_EXECUTED) {
                log(" close cross state is BOTH_BRACKETS_EXECUTED. all done");
                forkData.evaluateGain();
            }
        }
    },

    START {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.START. try to place open bracket orders");
            forkData.placeOpenBrackets(iContext);
        }
    },
    WAITING_OPEN_BRACKETS {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            // orders are placed just instantaneously - code here just in case and double check
            log("ForkState.OPEN_BRACKETS_WAITING");
            // todo: check for order status here - can be complex here - some order can be already placed and partially or fully executed
            // can become SUBMITTED, REJECTED
            if(!forkData.waitingForAllBracketsOpen()) {
                forkData.setState(ForkState.OPEN_BRACKETS_PLACED); // actually some order may have already another state
                iContext.delay(0); // no wait
            }
        }
    },
    OPEN_BRACKETS_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.OPEN_BRACKETS_PLACED checkState()");
            if( forkData.checkBothBracketsExecutedOnExch(iContext)) {
                log(" Both Brackets on some exchange Executed !!!");
                forkData.setState(ForkState.END);
            } else if( forkData.checkAnyBracketExecuted()) {
                log(" Bracket on some exchange Executed !!!");
                forkData.setState(OPENING_OTHER_SIDE_AT_MKT);
                forkData.openOtherSideAtMarket(iContext);
            } else {
                forkData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    OPENING_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.OPENING_OTHER_SIDE_AT_MKT checkState()");
            forkData.openOtherSideAtMarket(iContext);
        }
    },
    WAITING_OPEN_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.WAITING_OPEN_OTHER_SIDE_AT_MKT checkState()");
            if (forkData.waitingOtherSideAtMarket(iContext)) {
                log("  placing CloseBrackets");
                forkData.setState(BOTH_SIDES_OPENED);
                forkData.placeCloseBrackets(iContext);
            } else {
                forkData.moveMarketsIfNeeded(iContext);
            }
        }
    },
    BOTH_SIDES_OPENED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.BOTH_SIDES_OPENED will need to place close brackets");
            forkData.placeCloseBrackets(iContext);
        }
    },
    CLOSE_BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.CLOSE_BRACKET_PLACED monitor order...");
            if (forkData.checkAnyBracketExecuted()) {
                log(" Bracket on some exchange Executed !!!");
                forkData.setState(CLOSING_OTHER_SIDE_AT_MKT);
                forkData.closeOtherSideAtMarket(iContext);
            } else {
                forkData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    CLOSING_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.CLOSING_OTHER_SIDE_AT_MKT checkState()");
            forkData.closeOtherSideAtMarket(iContext);
        }
    },
    WAITING_CLOSE_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.WAITING_CLOSE_OTHER_SIDE_AT_MKT checkState()");
            if (forkData.waitingOtherSideAtMarket(iContext)) {
                forkData.endThisRun();
            }
        }
    },
    END {
        @Override public boolean preCheckState(IterationContext iContext, ForkData forkData) {
            log("ForkState.END preCheckState()");
            return true; // finish execution
        }
    },
    ERROR {
        @Override public boolean preCheckState(IterationContext iContext, ForkData forkData) {
            log("ForkState.ERROR preCheckState() set all internal as ERROR...");
            forkData.setAllAsError();
            return false; // todo: this not exactly correct - closing orders should be quick, but moving to 50-50 can take time
        }

        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.ERROR checkState() all should be closed at this point");
            // todo: make 50-50 balances
            forkData.setState(STOP);
        }
    },
    STOP {
        @Override public boolean preCheckState(IterationContext iContext, ForkData forkData) {
            log("ForkState.STOP preCheckState()");
            return forkData.allStopped();
        }

        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.STOP checkState()");
            forkData.stop();
        }
    },
    STOPPING {  // internal forkStop requested
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.STOPPING checkState()");
        }
    },
    RESTART {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.RESTART checkState()");
            forkData.stop();
            log(" stopped. restarting");
            forkData.setState(START);
        }
    },
    FIX_BALANCE { // make 50-50 balance
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            log("ForkState.FIX_BALANCE checkState()");
        }
    };

    private static void log(String s) { Log.log(s); }

    // todo: we need preCheck to properly handle ERROR state
    public boolean preCheckState(IterationContext iContext, ForkData forkData) { return false; }

    public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
        log("checkState not implemented for " + this);
    }

    public boolean needQueryTrades() { return true; }
}
