import java.util.*;
import java.security.PublicKey;


public class TxHandler {

    UTXOPool utxopool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxopool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // (1) Check if all outputs claimed in tx are in the current UTXO pool.
        for (Transaction.Input input : tx.getInputs())
            if (!utxopool.contains(new UTXO(input.prevTxHash, input.outputIndex)))
                return false;
        // (2) the signatures on each input of {@code tx} are valid.
        for (int i = 0; i < tx.numInputs(); ++i) {
            Transaction.Input input = tx.getInput(i);
            PublicKey pubKey = utxopool.getTxOutput(new UTXO(input.prevTxHash, input.outputIndex)).address;
            if (!Crypto.verifySignature(pubKey, tx.getRawDataToSign(i), input.signature))
                return false;
        }
        // (3) no UTXO is claimed multiple times by {@code tx}.
        HashSet<UTXO> claimedOutputs = new HashSet();
        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (claimedOutputs.contains(utxo))
                return false;
            claimedOutputs.add(utxo);
        }
        // (4) all of {@code tx}s output values are non-negative
        for (Transaction.Output output : tx.getOutputs())
            if (output.value < 0)
                return false;
        // (5) the sum of tx input values is greater than or equal to the sum of its output values
        double sum_inputs = 0;
        for (Transaction.Input input : tx.getInputs())
            sum_inputs += utxopool.getTxOutput(new UTXO(input.prevTxHash, input.outputIndex)).value;

        double sum_outputs = 0;
        for (Transaction.Output output : tx.getOutputs())
            sum_outputs += output.value;

        return sum_inputs >= sum_outputs;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> validTxs = new ArrayList<>();
        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                validTxs.add(tx);
                for (Transaction.Input input : tx.getInputs())
                    utxopool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
                for (int i = 0; i < tx.numOutputs(); ++i)
                    utxopool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));
            }
        }
        Transaction[] txs = new Transaction[validTxs.size()];
        return validTxs.toArray(txs);
    }
}
