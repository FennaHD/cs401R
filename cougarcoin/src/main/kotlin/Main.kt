import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

private const val TIMESTAMP_FORMAT = "YYYY-mm-dd hh:mm:ss"
private const val DEFAULT_RECIPIENT = "371c20fb2e9899338ce5e99908e64fd30b789313"
private const val GENESIS_PREV = "0000000000000000000000000000000000000000000000000000000000000000"
private const val GENESIS_TIMESTAMP = "2022-02-03 16:15:00"
private const val GENESIS_NONCE = 65281174
private const val DEFAULT_TARGET = "000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

fun main() {
    var blockchain = ""

    val genesis = Block.getGenesis()

    blockchain += genesis

    var prev = genesis.header.singleHashString()
    for (i in 0..8) {
        val block = Block.getBlock(prev)
        prev = block.mine()
        blockchain += block
    }

    File("src/main/kotlin/blockchain.txt").writeText(blockchain)
}

/**
 * Contains useful methods to hash our classes the way CougarCoin does. 
 */
interface CCHash {
    fun singleHashBytes(): ByteArray = MessageDigest
        .getInstance("SHA-256")
        .digest(toString().encodeToByteArray())

    fun doubleHashBytes(): ByteArray = MessageDigest
        .getInstance("SHA-256")
        .digest(singleHashBytes())

    fun singleHashString(): String = singleHashBytes().hexString()

    fun doubleHashString(): String = doubleHashBytes().hexString()
}

/**
 * For this project we are assuming the block will have only the coinbase transaction.
 * Thus, we only need the hash of the previous block and we're in business. Technically we'd also need
 * the recipient address, but we're defaulting that to the address the professor had.
 */
data class Block(
    val header: Header,
    val merkle: Merkle,
    val transaction: Transaction
): CCHash {
    override fun toString(): String = listOf(
            "BLOCK\n",
            header.toString(),
            merkle.toString(),
            transaction.toString()).joinToString("")

    override fun singleHashString(): String = header.singleHashString()

    private fun increaseNonce() = run { header.nonce += 1 }

    /**
     * Will increase the nonce by 1 until we get a hash that is below the target,
     * then returns the single hash of the header.
     */
    fun mine(): String {
        while (!header.isBelowTarget()) {
            println(header.nonce)
            increaseNonce()
        }
        return header.singleHashString()
    }

    companion object {
        fun getBlock(prev: String, transaction: Transaction = Transaction.getCoinbase()): Block {
            val merkle = Merkle(transaction)
            val header = Header(prev=prev, root=merkle.getRoot())
            return Block(header, merkle, transaction)
        }

        fun getGenesis(): Block {
            Transaction.getCoinbase().let { coinbase ->
                val merkle = Merkle(coinbase)
                val header = Header(GENESIS_TIMESTAMP, GENESIS_PREV, merkle.getRoot(), nonce=GENESIS_NONCE)
                return Block(header, merkle, coinbase)
            }
        }
    }
}

/**
 * The header contains a timestamp, so our header hash won't match the professor's hash perfectly.
 * For debugging purposes, I'm overriding the timestamp string to match the professors'.
 */
data class Header(
    val timestamp: String? = SimpleDateFormat(TIMESTAMP_FORMAT).format(Date()),
    val prev: String?,
    val root: String?,
    val target: String? = DEFAULT_TARGET,
    var nonce: Int = 0
): CCHash {
    override fun toString(): String = listOf(
            "header:",
            "timestamp:".joinBySpaceIfNotEmpty(timestamp),
            "prev:".joinBySpaceIfNotEmpty(prev?.hexStr()),
            "root:".joinBySpaceIfNotEmpty(root?.hexStr()),
            "target:".joinBySpaceIfNotEmpty(target?.hexStr()),
            "nonce:".joinBySpaceIfNotEmpty(nonce.toString()),
            "\n").joinToString("\n")

    fun isBelowTarget(): Boolean = singleHashString().take(6) == "000000"
}

/**
 * Right now this class only handles one transaction, but we can easily make it handle more.
 * What is the logic though?
 * What does the tree look like if there are 6 transactions?
 * What about 5 transactions?
 */
data class Merkle(
    val transaction: Transaction
) {
    override fun toString(): String = listOf(
            "merkle",
            transaction.doubleHashString().hexStr(),
            transaction.doubleHashString().hexStr(),
            "\n"
        ).joinToString("\n")

    // Will return as "0x1234..."
    fun getRoot(): String = transaction.doubleHashBytes().let { it + it }.ccHash().hexStringEndIn0x()
}

data class Transaction(
    val inputs: String? = null,
    val txId: String? = null,
    val index: Int? = null,
    val unlock: String? = null,
    val outputs: String? = null,
    val amount: Int? = null,
    val lock: String? = null
): CCHash {
    companion object {
        fun getCoinbase(recipient: String = DEFAULT_RECIPIENT) = Transaction(
            amount = 1000,
            lock = "OP_DUP OP_HASH160 <$recipient> OP_EQUALVERIFY OP_CHECKSIG")
    }

    override fun toString(): String = listOf(
            "transaction:",
            "inputs:".joinBySpaceIfNotEmpty(inputs),
            "txid:".joinBySpaceIfNotEmpty(txId),
            "index:".joinBySpaceIfNotEmpty(index?.toString()),
            "unlock:".joinBySpaceIfNotEmpty(unlock),
            "outputs:".joinBySpaceIfNotEmpty(outputs),
            "amount:".joinBySpaceIfNotEmpty(amount?.toString()),
            "lock:".joinBySpaceIfNotEmpty(lock),
            "\n").joinToString("\n")
}

fun String.joinBySpaceIfNotEmpty(str: String?) = listOfNotNull(this, str.takeUnless { isNullOrEmpty() })
    .joinToString(" ")

fun String.hexStr(): String = if (take(2) == "0x") this else "0x$this"

fun ByteArray.ccHash(): ByteArray = MessageDigest.getInstance("SHA-256").run {
    digest(digest(this@ccHash))
}

fun String.endIn0x() = "0x$this"

fun ByteArray.hexString() = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun ByteArray.hexStringEndIn0x() = hexString().endIn0x()
