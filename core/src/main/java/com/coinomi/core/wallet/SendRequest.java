/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.coinomi.core.wallet;


import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet.MissingSigsMode;
import com.google.bitcoin.wallet.CoinSelector;

import org.spongycastle.crypto.params.KeyParameter;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A SendRequest gives the wallet information about precisely how to send money to a recipient or set of recipients.
 * Static methods are provided to help you create SendRequests and there are a few helper methods on the wallet that
 * just simplify the most common use cases. You may wish to customize a SendRequest if you want to attach a fee or
 * modify the change address.
 */
public class SendRequest {
    /**
     * <p>A transaction, probably incomplete, that describes the outline of what you want to do. This typically will
     * mean it has some outputs to the intended destinations, but no inputs or change address (and therefore no
     * fees) - the wallet will calculate all that for you and update tx later.</p>
     *
     * <p>Be careful when adding outputs that you check the min output value
     * ({@link TransactionOutput#getMinNonDustValue(Coin)}) to avoid the whole transaction being rejected
     * because one output is dust.</p>
     *
     * <p>If there are already inputs to the transaction, make sure their out point has a connected output,
     * otherwise their value will be added to fee.  Also ensure they are either signed or are spendable by a wallet
     * key, otherwise the behavior of {@link Wallet#completeTx(Wallet.SendRequest)} is undefined (likely
     * RuntimeException).</p>
     */
    public Transaction tx;

    /**
     * When emptyWallet is set, all coins selected by the coin selector are sent to the first output in tx
     * (its value is ignored and set to {@link com.google.bitcoin.core.Wallet#getBalance()} - the fees required
     * for the transaction). Any additional outputs are removed.
     */
    public boolean emptyWallet = false;

    /**
     * "Change" means the difference between the value gathered by a transactions inputs (the size of which you
     * don't really control as it depends on who sent you money), and the value being sent somewhere else. The
     * change address should be selected from this wallet, normally. <b>If null this will be chosen for you.</b>
     */
    public Address changeAddress = null;

    /**
     * <p>A transaction can have a fee attached, which is defined as the difference between the input values
     * and output values. Any value taken in that is not provided to an output can be claimed by a miner. This
     * is how mining is incentivized in later years of the Bitcoin system when inflation drops. It also provides
     * a way for people to prioritize their transactions over others and is used as a way to make denial of service
     * attacks expensive.</p>
     *
     * <p>This is a constant fee (in satoshis) which will be added to the transaction. It is recommended that it be
     * at least {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE} if it is set, as default reference clients will
     * otherwise simply treat the transaction as if there were no fee at all.</p>
     *
     * <p>You might also consider adding a {@link SendRequest#feePerKb} to set the fee per kb of transaction size
     * (rounded down to the nearest kb) as that is how transactions are sorted when added to a block by miners.</p>
     */
    public Coin fee = null;

    /**
     * <p>A transaction can have a fee attached, which is defined as the difference between the input values
     * and output values. Any value taken in that is not provided to an output can be claimed by a miner. This
     * is how mining is incentivized in later years of the Bitcoin system when inflation drops. It also provides
     * a way for people to prioritize their transactions over others and is used as a way to make denial of service
     * attacks expensive.</p>
     *
     * <p>This is a dynamic fee (in satoshis) which will be added to the transaction for each kilobyte in size
     * including the first. This is useful as as miners usually sort pending transactions by their fee per unit size
     * when choosing which transactions to add to a block. Note that, to keep this equivalent to the reference
     * client definition, a kilobyte is defined as 1000 bytes, not 1024.</p>
     *
     * <p>You might also consider using a {@link SendRequest#fee} to set the fee added for the first kb of size.</p>
     */
    public Coin feePerKb = DEFAULT_FEE_PER_KB;

    /**
     * If you want to modify the default fee for your entire app without having to change each SendRequest you make,
     * you can do it here. This is primarily useful for unit tests.
     */
    public static Coin DEFAULT_FEE_PER_KB = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

    /**
     * <p>Requires that there be enough fee for a default reference client to at least relay the transaction.
     * (ie ensure the transaction will not be outright rejected by the network). Defaults to true, you should
     * only set this to false if you know what you're doing.</p>
     *
     * <p>Note that this does not enforce certain fee rules that only apply to transactions which are larger than
     * 26,000 bytes. If you get a transaction which is that large, you should set a fee and feePerKb of at least
     * {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE}.</p>
     */
    public boolean ensureMinRequiredFee = true;

    /**
     * If true (the default), the inputs will be signed.
     */
    public boolean signInputs = true;

    /**
     * The AES key to use to decrypt the private keys before signing.
     * If null then no decryption will be performed and if decryption is required an exception will be thrown.
     * You can get this from a password by doing wallet.getKeyCrypter().deriveKey(password).
     */
    public KeyParameter aesKey = null;

    /**
     * If not null, the {@link com.google.bitcoin.wallet.CoinSelector} to use instead of the wallets default. Coin selectors are
     * responsible for choosing which transaction outputs (coins) in a wallet to use given the desired send value
     * amount.
     */
    public CoinSelector coinSelector = null;

    /**
     * If true (the default), the outputs will be shuffled during completion to randomize the location of the change
     * output, if any. This is normally what you want for privacy reasons but in unit tests it can be annoying
     * so it can be disabled here.
     */
    public boolean shuffleOutputs = true;

    /**
     * Specifies what to do with missing signatures left after completing this request. Default strategy is to
     * throw an exception on missing signature ({@link MissingSigsMode#THROW}).
     * @see MissingSigsMode
     */
    public MissingSigsMode missingSigsMode = MissingSigsMode.THROW;


    // Tracks if this has been passed to wallet.completeTx already: just a safety check.
    boolean completed;

    private SendRequest() {}

    /**
     * <p>Creates a new SendRequest to the given address for the given value.</p>
     *
     * <p>Be very careful when value is smaller than {@link Transaction#MIN_NONDUST_OUTPUT} as the transaction will
     * likely be rejected by the network in this case.</p>
     */
    public static SendRequest to(Address destination, Coin value) {
        SendRequest req = new SendRequest();
        final NetworkParameters parameters = destination.getParameters();
        checkNotNull(parameters, "Address is for an unknown network");
        req.tx = new Transaction(parameters);
        req.tx.addOutput(value, destination);
        return req;
    }

    /**
     * <p>Creates a new SendRequest to the given pubkey for the given value.</p>
     *
     * <p>Be careful to check the output's value is reasonable using
     * {@link TransactionOutput#getMinNonDustValue(Coin)} afterwards or you risk having the transaction
     * rejected by the network. Note that using {@link SendRequest#to(Address, Coin)} will result
     * in a smaller output, and thus the ability to use a smaller output value without rejection.</p>
     */
    public static SendRequest to(NetworkParameters params, ECKey destination, Coin value) {
        SendRequest req = new SendRequest();
        req.tx = new Transaction(params);
        req.tx.addOutput(value, destination);
        return req;
    }

    /** Simply wraps a pre-built incomplete transaction provided by you. */
    public static SendRequest forTx(Transaction tx) {
        SendRequest req = new SendRequest();
        req.tx = tx;
        return req;
    }

    public static SendRequest emptyWallet(Address destination) {
        SendRequest req = new SendRequest();
        final NetworkParameters parameters = destination.getParameters();
        checkNotNull(parameters, "Address is for an unknown network");
        req.tx = new Transaction(parameters);
        req.tx.addOutput(Coin.ZERO, destination);
        req.emptyWallet = true;
        return req;
    }

    @Override
    public String toString() {
        // print only the user-settable fields
        ToStringHelper helper = Objects.toStringHelper(this).omitNullValues();
        helper.add("emptyWallet", emptyWallet);
        helper.add("changeAddress", changeAddress);
        helper.add("fee", fee);
        helper.add("feePerKb", feePerKb);
        helper.add("ensureMinRequiredFee", ensureMinRequiredFee);
        helper.add("signInputs", signInputs);
        helper.add("aesKey", aesKey != null ? "set" : null); // careful to not leak the key
        helper.add("coinSelector", coinSelector);
        helper.add("shuffleOutputs", shuffleOutputs);
        return helper.toString();
    }
}