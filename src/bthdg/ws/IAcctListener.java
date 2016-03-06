package bthdg.ws;

import bthdg.exch.AccountData;

public interface IAcctListener {
    void onAccount(AccountData accountData);
}
