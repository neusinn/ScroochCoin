import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
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
        if (tx == null) return false;

        Set<UTXO> validatedAndClaimedUTXO = new HashSet<UTXO>();
        double inputValueSum = 0;
        double outputValueSum = 0;

        for(int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) all outputs claimed by transaction are in the current UTXO pool
            if (! utxoPool.contains(utxo)) {
                return false;
            };

            Transaction.Output utxoOutput = utxoPool.getTxOutput(utxo);

            // (2) the signatures on each input of transaction are valid
            if ( ! Crypto.verifySignature(utxoOutput.address,
                    tx.getRawDataToSign(i),
                    input.signature)) {
                return false;
            }

            // (3) no UTXO is claimed multiple times by transaction
            if (validatedAndClaimedUTXO.contains(utxo)) {
                //the utxo was already claimed. This is double spending.
                return false;
            } else {
                validatedAndClaimedUTXO.add(utxo);
            }

            // (5) keep track of the sum of the input values
            inputValueSum += utxoOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            // (4) all of transaction output values are non-negative
            if (output.value < 0) {
                return false;
            }
            outputValueSum += output.value;
        }

        // (5) the sum of transactions input values is greater than or equal to the sum of its output values;
        if (inputValueSum < outputValueSum) {
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        ArrayList<Transaction> pendingTxs = new ArrayList<>(Arrays.asList(possibleTxs));
        ArrayList<Transaction> validTxs = new ArrayList<Transaction>();

        int count = 0;
        do {
            ArrayList<Transaction> toRemove = new ArrayList<Transaction>();
            // 1. process tx that are independently valid
            for (int i = 0; i < pendingTxs.size(); i++) {
                Transaction tx = pendingTxs.get(i);
                // (1) Only valid transactions
                if (isValidTx(tx)) {
                    validTxs.add(tx);
                    toRemove.add(tx);
                    updateUTXOPool(tx);
                }
            }
            for (Transaction tx : toRemove) {
                pendingTxs.remove(tx);
            }
            count = toRemove.size();
            // 2. process tx unitl there are no dependently valid transactiosn that are independently valid
        } while (count != 0 && pendingTxs.size() != 0);

        return validTxs.toArray(new Transaction[0]);
    }

    private void updateUTXOPool(Transaction tx)
    {
        // Remove each utxo that matches the inputs of the tx
        for(Transaction.Input input : tx.getInputs()) {
            UTXO inputUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(inputUTXO);
        }

        // Add utxo for each output
        for(int i = 0; i < tx.numOutputs(); i++)
        {
            UTXO outputUTXO = new UTXO(tx.getHash(), i);
            utxoPool.addUTXO(outputUTXO, tx.getOutput(i));
        }
    }
}
