package bthdg;

public enum ForkState {
    NONE {
        @Override public boolean needQueryTrades() { return false; }
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.NONE. queryAccountsData");
            forkData.queryAccountsData();
            forkData.setState(START);
            iContext.delay(0);
        }
    },
    START {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.START. try to place open bracket orders");
            forkData.placeOpenBrackets(iContext);
        }
    },
    WAITING_OPEN_BRACKETS {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            // orders are placed just instantaneously - code here just in case and double check
            System.out.println("ForkState.OPEN_BRACKETS_WAITING");
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
            System.out.println("ForkState.OPEN_BRACKETS_PLACED checkState()");
            if( forkData.checkBothBracketsExecutedOnExch(iContext)) {
                System.out.println(" Both Brackets on some exchange Executed !!!");
                forkData.setState(ForkState.END);
            } else if( forkData.checkAnyBracketExecuted()) {
                System.out.println(" Bracket on some exchange Executed !!!");
                forkData.setState(OPENING_OTHER_SIDE_AT_MKT);
                forkData.openOtherSideAtMarket(iContext);
            } else {
                forkData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    OPENING_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.OPENING_OTHER_SIDE_AT_MKT checkState()");
            forkData.openOtherSideAtMarket(iContext);
        }
    },
    WAITING_OPEN_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.WAITING_OPEN_OTHER_SIDE_AT_MKT checkState()");
            if (forkData.waitingOtherSideAtMarket(iContext)) {
                System.out.println("  placing CloseBrackets");
                forkData.setState(BOTH_SIDES_OPENED);
                forkData.placeCloseBrackets(iContext);
            } else {
                forkData.moveMarketsIfNeeded(iContext);
            }
        }
    },
    BOTH_SIDES_OPENED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.BOTH_SIDES_OPENED will need to place close brackets");
            forkData.placeCloseBrackets(iContext);
        }
    },
    CLOSE_BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.CLOSE_BRACKET_PLACED monitor order...");
            if (forkData.checkAnyBracketExecuted()) {
                System.out.println(" Bracket on some exchange Executed !!!");
                forkData.setState(CLOSING_OTHER_SIDE_AT_MKT);
                forkData.closeOtherSideAtMarket(iContext);
            } else {
                forkData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    CLOSING_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.CLOSING_OTHER_SIDE_AT_MKT checkState()");
            forkData.closeOtherSideAtMarket(iContext);
        }
    },
    WAITING_CLOSE_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.WAITING_CLOSE_OTHER_SIDE_AT_MKT checkState()");
            if (forkData.waitingOtherSideAtMarket(iContext)) {
                forkData.endThisRun();
            }
        }
    },
    END {
        @Override public boolean preCheckState(IterationContext iContext, ForkData forkData) {
            System.out.println("ForkState.END preCheckState()");
            return true; // finish execution
        }
    },
    ERROR {
        @Override public boolean preCheckState(IterationContext iContext, ForkData forkData) {
            System.out.println("ForkState.ERROR preCheckState() set all internal as ERROR...");
            forkData.setAllAsError();
            return false; // todo: this not exactly correct - closing orders should be quick, but moving to 50-50 can take time
        }

        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.ERROR checkState() all should be closed at this point");
            // todo: make 50-50 balances
            forkData.setState(STOP);
        }
    },
    STOP {
        @Override public boolean preCheckState(IterationContext iContext, ForkData forkData) {
            System.out.println("ForkState.STOP preCheckState()");
            return forkData.allStopped(); // todo: maybe this not exactly correct - closing orders should be quick, but moving to 50-50 can take time
        }

        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.STOP checkState()");
            forkData.stop();
        }
    },
    RESTART {
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.RESTART checkState()");
            forkData.stop();
            System.out.println(" stopped. restarting");
            forkData.setState(START);
        }
    },
    FIX_BALANCE { // make 50-50 balance
        @Override public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
            System.out.println("ForkState.FIX_BALANCE checkState()");
        }
    },
    ;

    // todo: we need preCheck to properly handle ERROR state
    public boolean preCheckState(IterationContext iContext, ForkData forkData) { return false; }

    public void checkState(IterationContext iContext, ForkData forkData) throws Exception {
        System.out.println("checkState not implemented for " + this);
    }

    public boolean needQueryTrades() { return true; }
}
