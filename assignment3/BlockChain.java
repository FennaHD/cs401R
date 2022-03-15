// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    public final Block genesisBlock;
    // Global pool shared by all leaf blocks
    private final TransactionPool transactionPool;
    // Keep here for easy/fast access
    private Node maxHeightNode;
    // Keep all nodes (at one point I'll prob have to trim the list but for now keep all).
    // Right now this is just the genesis node;
    // Note: this isn't actually doing anything right now, tests passed without needing it. 
    private ArrayList<Node> rootNodes;


    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        this.genesisBlock = genesisBlock;
        this.transactionPool = new TransactionPool();
        Node genesisNode = new Node(genesisBlock, null);
        this.maxHeightNode = genesisNode;
        this.rootNodes = new ArrayList<Node>() {{
            add(genesisNode);
        }};
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.txHandler.getUTXOPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block.getPrevBlockHash() == null)
            return false;
        Node parentNode = nodeWithHash(block.getPrevBlockHash());
        //Reject blocks pretending to be the genesis block.
        if (parentNode == null)
            return false;
        // (1) all transactions should be valid
        for (Transaction tx : block.getTransactions()) {
            // "Test 3: Process a block with many valid transactions" is failing right here.
            // This may be because the block may have transactions that refer to other transactions
            // in the same block.
            if (!parentNode.txHandler.isValidTx(tx))
                return false;
            System.out.println("b3t");
        }
        // (2) block should be at height > (maxHeight - CUT_OFF_AGE)
        if (parentNode.height < maxHeightNode.height - CUT_OFF_AGE)
            return false;
        // From here on we know the block is valid, update pools and stuff.
        Node newNode = new Node(block, parentNode);
        newNode.appendToParent();
        syncMaxHeightNode(newNode);
        updateGlobalPool(newNode.block);
        return true;
    }

    // We only update the max height node if we have to, otherwise we do nothing.
    private void syncMaxHeightNode(Node node) {
        if (node.height > maxHeightNode.height)
            maxHeightNode = node;
    }

    // When we create a new block we remove those transactions from the global transaction pool.
    private void updateGlobalPool(Block newBlock) {
        for (Transaction tx : newBlock.getTransactions()) {
            transactionPool.removeTransaction(tx.getHash());
        }
    }

    private Node nodeWithHash(byte[] hash) {
        for (Node node : rootNodes) {
            Node nodeResult = node.nodeWithHash(hash);
            if (nodeResult != null)
                return nodeResult;
        }

        return null;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }

    private class Node {
        public Block block;
        public Node parent;
        public ArrayList<Node> children;
        public int height;
        public TxHandler txHandler;

        public Node(Block block, Node parent) {
            this.block = block;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.height = parent == null ? 0 : parent.height + 1;
            this.txHandler = txHandlerCopy(parent == null ? null : parent.txHandler);

            addCoinbaseUTXO();
        }

        public Node nodeWithHash(byte[] hash) {
            if (Arrays.equals(block.getHash(), hash))
                return this;

            if (children.size() == 0)
                return null;

            for (Node node : children) {
                Node childResult = node.nodeWithHash(hash);
                if (childResult != null)
                    return childResult;
            }

            return null;
        }

        private TxHandler txHandlerCopy(TxHandler txHandlerOriginal) {
            // Genesis block won't have a parent.
            UTXOPool utxoPool = txHandlerOriginal == null ?
                    new UTXOPool() : utxoPoolCopy(txHandlerOriginal.getUTXOPool());

            TxHandler txHandlerCopy = new TxHandler(utxoPool);
            txHandlerCopy.handleTxs(block.getTransactions().toArray(new Transaction[0]));

            return txHandlerCopy;
        }

        private UTXOPool utxoPoolCopy(UTXOPool utxoPoolOriginal) {
            UTXOPool copy = new UTXOPool();
            for (UTXO utxo : utxoPoolOriginal.getAllUTXO())
                copy.addUTXO(utxo, utxoPoolOriginal.getTxOutput(utxo));
            return copy;
        }

        private void addCoinbaseUTXO() {
            for (int i = 0; i < block.getCoinbase().numOutputs(); ++i)
                txHandler.getUTXOPool()
                        .addUTXO(new UTXO(block.getCoinbase().getHash(), i), block.getCoinbase().getOutput(i));
        }

        private void appendToParent() {
            if (parent != null)
                parent.children.add(this);
        }
    }
}