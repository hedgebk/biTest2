public enum ExchangesState {
    NONE {
        @Override public boolean queryTrades() { return false; }
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.NONE. queryAccountsData");
            exchangesData.queryAccountsData();
            exchangesData.setState(START);
            iContext.delay(0);
        }
    },
    START {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.START. try to place open bracket orders");
            exchangesData.placeOpenBrackets(iContext);
        }
    },
    WAITING_OPEN_BRACKETS {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            // orders are placed just instantaneously - code here just in case and double check
            System.out.println("ExchangesState.OPEN_BRACKETS_WAITING");
            // todo: check for order status here - can be complex here - some order can be already placed and partially or fully executed
            // can become SUBMITTED, REJECTED
            if(!exchangesData.waitingForAllBracketsOpen()) {
                exchangesData.setState(ExchangesState.OPEN_BRACKETS_PLACED); // actually some order may have already another state
                iContext.delay(0); // no wait
            }
        }
    },
    OPEN_BRACKETS_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.OPEN_BRACKETS_PLACED checkState()");
            if( exchangesData.hasAnyBracketExecuted()) {
                System.out.println(" Bracket on some exchange Executed !!!");
                exchangesData.setState(OPENING_OTHER_SIDE_AT_MKT);
                exchangesData.openOtherSideAtMarket(iContext);
            } else {
                exchangesData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    OPENING_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.OPENING_OTHER_SIDE_AT_MKT checkState()");
            exchangesData.openOtherSideAtMarket(iContext);
        }
    },
    WAITING_OPEN_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.WAITING_OPEN_OTHER_SIDE_AT_MKT checkState()");
            if(exchangesData.waitingOtherSideAtMarket(iContext)) {
                System.out.println("  placing CloseBrackets");
                exchangesData.setState(BOTH_SIDES_OPENED);
                exchangesData.placeCloseBrackets(iContext);
            }
        }
    },
    BOTH_SIDES_OPENED {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.BOTH_SIDES_OPENED will need to place close brackets");
            exchangesData.placeCloseBrackets(iContext);
        }
    },
    CLOSE_BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.CLOSE_BRACKET_PLACED monitor order...");
            if( exchangesData.hasAnyBracketExecuted()) {
                System.out.println(" Bracket on some exchange Executed !!!");
                exchangesData.setState(CLOSING_OTHER_SIDE_AT_MKT);
                exchangesData.closeOtherSideAtMarket(iContext);
            } else {
                exchangesData.moveBracketsIfNeeded(iContext);
            }
        }
    },
    CLOSING_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.CLOSING_OTHER_SIDE_AT_MKT checkState()");
            exchangesData.closeOtherSideAtMarket(iContext);
        }
    },
    WAITING_CLOSE_OTHER_SIDE_AT_MKT {
        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.WAITING_CLOSE_OTHER_SIDE_AT_MKT checkState()");
            if(exchangesData.waitingOtherSideAtMarket(iContext)) {
                exchangesData.endThisRun();
            }
        }
    },
    END  {
        @Override public boolean preCheckState(IterationContext iContext, ExchangesData exchangesData) {
            System.out.println("ExchangesState.END preCheckState()");
            return true; // finish execution
        }
    },
    ERROR  {
        @Override public boolean preCheckState(IterationContext iContext, ExchangesData exchangesData) {
            System.out.println("ExchangesState.ERROR preCheckState() set all internal as ERROR...");
            exchangesData.setAllAsError();
            return false;
        }

        @Override public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
            System.out.println("ExchangesState.ERROR checkState() all should be closed at thois point");
            // todo: make 1-1 balances
        }
    },
    ;

    public boolean preCheckState(IterationContext iContext, ExchangesData exchangesData) { return false; }

    public void checkState(IterationContext iContext, ExchangesData exchangesData) throws Exception {
        System.out.println("checkState not implemented for " + this);
    }

    public boolean queryTrades() { return true; }
}
