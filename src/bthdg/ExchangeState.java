package bthdg;

import bthdg.ExchangeData;
import bthdg.IterationContext;
import bthdg.LiveOrdersData;

public enum ExchangeState {
    NONE {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.NONE(" + exchData.exchName() + ").");
        }
    },
    OPEN_BRACKETS_WAITING {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) {
            System.out.println("ExchangeState.OPEN_BRACKETS_WAITING(" + exchData.exchName() + "). check orders status");
            // todo: check orders status here, can be submitted/queued, placed, rejected, and even filled/partially-filled
            LiveOrdersData liveOrdersState = iContext.getLiveOrdersState(exchData);

            // actually one can be placed and another not - should be handled separately
            { // pretend that both orders are placed fine
                exchData.xAllBracketsPlaced(iContext); // start listen for order changes
            }
        }
    },
    OPEN_BRACKETS_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.OPEN_BRACKETS_PLACED(" + exchData.exchName() + "). check if some order executed");
            exchData.checkSomeBracketExecuted(iContext);
        }
    },
    ONE_OPEN_BRACKET_EXECUTED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.ONE_OPEN_BRACKET_EXECUTED(" + exchData.exchName() + "). do nothing");
        }
    },
    OPEN_AT_MKT_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.OPEN_AT_MKT_PLACED(" + exchData.exchName() + "). check if MKT order executed");
            exchData.checkMktBracketExecuted(iContext);
        }
    },
    OPEN_AT_MKT_EXECUTED {

    },
    CLOSE_BRACKET_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.CLOSE_BRACKET_PLACED(" + exchData.exchName() + "). check if some order executed");
            exchData.checkSomeBracketExecuted(iContext);
        }
    },
    CLOSE_BRACKET_EXECUTED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.CLOSE_BRACKET_EXECUTED(" + exchData.exchName() + "). do nothing");
        }
    },
    CLOSE_AT_MKT_PLACED {
        @Override public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
            System.out.println("ExchangeState.CLOSE_AT_MKT_PLACED(" + exchData.exchName() + "). check if MKT order executed");
            exchData.checkMktBracketExecuted(iContext);
        }
    },
    CLOSE_AT_MKT_EXECUTED{

    },
    ERROR,
    ;

    public void checkState(IterationContext iContext, ExchangeData exchData) throws Exception {
        System.out.println("checkState not implemented for ExchangeState." + this);
    }
} // ExchangeData