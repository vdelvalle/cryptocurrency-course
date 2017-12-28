import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        Set<UTXO> utxosSet = new HashSet<>();
        List<Double> outputValues = new ArrayList<>();

        try {
            AtomicInteger inputIndex = new AtomicInteger(0);
            return tx.getInputs().stream().allMatch(
                input -> {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    Transaction.Output output = this.utxoPool.getTxOutput(utxo);
                    outputValues.add(output.value);

                    return utxoPool.contains(utxo) &&
                           Crypto.verifySignature(output.address, tx.getRawDataToSign(inputIndex.getAndIncrement()), input.signature) &&
                           utxosSet.add(utxo);
            })
            && tx.getOutputs().stream().mapToDouble(output -> output.value).sum() <= outputValues.stream().reduce(0.0, Double::sum)
            && tx.getOutputs().stream().allMatch(output -> output.value >= 0);
        }catch (NullPointerException e){
            return false;
        }
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        Set<Transaction> validateTxs = Arrays.stream(possibleTxs).filter(this::isValidTx).collect(Collectors.toSet());
        validateTxs.forEach(tx -> {
            AtomicInteger outputIndex = new AtomicInteger(0);
            tx.getInputs().forEach(input-> {
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                utxoPool.removeUTXO(utxo);
            });
            tx.getOutputs().forEach(output -> {
                UTXO utxo = new UTXO(tx.getHash(), outputIndex.getAndIncrement());
                utxoPool.addUTXO(utxo, output);
            });
        });
        Transaction[] validateTxsrray = new Transaction[validateTxs.size()];
        return validateTxs.toArray(validateTxsrray);
    }
}