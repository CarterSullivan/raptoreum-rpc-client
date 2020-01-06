package wf.bitcoin.javabitcoindrpcclient.integration;

import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import wf.bitcoin.javabitcoindrpcclient.BitcoinRawTxBuilder;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.ExtendedTxInput;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.MultiSig;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.RawTransactionSigningOrVerificationError;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.SignedRawTransaction;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.TxInput;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.Unspent;

/**
 * Integration tests for the Raw Transactions command group
 * 
 * @see <a href="https://bitcoincore.org/en/doc/0.18.0/rpc/">Bitcoin Core RPC documentation</a>
 */
public class RawTransactionsTest extends IntegrationTestBase
{
	@Test(expected = Test.None.class) // no exception expected
	public void signRawTransactionWithKeyTest_P2SH_MultiSig()
	{
		LOGGER.info("Testing scenario: signRawTransactionWithKey ( P2SH-multiSigAddr(2-of-2, [addr1, addr2]) -> addr4 )");
		
		///////////////////////////////////////////
		// Prepare transaction 1 (addr3 -> multisig)
		///////////////////////////////////////////
		LOGGER.info("Preparing tx1 (addr3 -> multisig)");
		
		String addr1 = client.getNewAddress();
		LOGGER.info("Created address addr1: " + addr1);

		String addr2 = client.getNewAddress();
		LOGGER.info("Created address addr2: " + addr2);

		// Create P2SH multisig address that wallet can sign
		// Command also adds it to wallet, allowing us to track and spend payments received by that address
		// See https://bitcoin.stackexchange.com/questions/36053/difference-between-createmultisig-and-addmultisigaddress
		MultiSig p2shMultiSig = client.addMultiSigAddress(2, Arrays.asList(addr1, addr2));
		String p2shMultiSigAddr = p2shMultiSig.address();
		LOGGER.info("Created and added to wallet the P2SH-multiSigAddr(2-of-2, [addr1, addr2]) : " + p2shMultiSigAddr);

		String addr3 = client.getNewAddress();
		LOGGER.info("Created address addr3: " + addr3);
		
		List<String> generatedBlocksHashes = client.generateToAddress(110, addr3);
		LOGGER.info("Generated " + generatedBlocksHashes.size() + " blocks for addr3");
		
		List<Unspent> availableUtxosForTx1 = client.listUnspent(0, Integer.MAX_VALUE, addr3);
		LOGGER.info("Found " + availableUtxosForTx1.size() + " UTXOs (unspent transaction outputs) belonging to addr3");

		TxInput selectedUtxoForTx1 = availableUtxosForTx1.get(0);
		LOGGER.info("Selected UTXO which will be used in tx1 (addr3 -> P2SH-multiSigAddr) : " + selectedUtxoForTx1);

		// Fire off transaction 1 (addr3 -> multisig)
		String tx1ID = client.sendToAddress(p2shMultiSigAddr, selectedUtxoForTx1.amount());
		LOGGER.info("UTXO sent to P2SH-multiSigAddr, tx1 ID: " + tx1ID);
		
		///////////////////////////////////////////
		// Prepare transaction 2 (multisig -> addr4)
		///////////////////////////////////////////
		LOGGER.info("Preparing tx2 (multisig -> addr4)");
		
		String addr4 = client.getNewAddress();
		LOGGER.info("Created address addr4: " + addr4);
		
		List<Unspent> availableUtxosForTx2 = client.listUnspent(0, 999, p2shMultiSigAddr);
		LOGGER.info("Found " + availableUtxosForTx2.size() + " UTXOs (unspent transaction outputs) belonging to P2SH-multiSigAddr");
		
		Unspent selectedUtxoForTx2 = availableUtxosForTx2.get(0);
		LOGGER.info("Selected UTXO which will be used in tx2 (P2SH-multiSigAddr -> addr4) : " + selectedUtxoForTx2);
		
		ExtendedTxInput inputP2SH = new ExtendedTxInput(
				selectedUtxoForTx2.txid(),
				selectedUtxoForTx2.vout(),
				selectedUtxoForTx2.scriptPubKey(),
				selectedUtxoForTx2.amount(),
				selectedUtxoForTx2.redeemScript(),
				selectedUtxoForTx2.witnessScript());
		
		LOGGER.info("inputP2SH txid: " + 			inputP2SH.txid());
		LOGGER.info("inputP2SH vout: " + 			inputP2SH.vout());
		LOGGER.info("inputP2SH scriptPubKey: " + 	inputP2SH.scriptPubKey());
		LOGGER.info("inputP2SH amount: " + 			inputP2SH.amount());
		
		BitcoinRawTxBuilder rawTxBuilder = new BitcoinRawTxBuilder(client);
		rawTxBuilder.in(inputP2SH);
		
		// Found no other reliable way to estimate the fee in a test
		// Therefore, setting the fee for this tx to 1/1000 of the tx amount
		BigDecimal estimatedFee = selectedUtxoForTx2.amount().divide(BigDecimal.valueOf(1000));
		BigDecimal txToAddr4Amount = selectedUtxoForTx2.amount().subtract(estimatedFee);
		rawTxBuilder.out(addr4, txToAddr4Amount);
		
		String unsignedRawMultiSigToAddr4TxHex = rawTxBuilder.create();
		LOGGER.info("Created unsignedRawTx from P2SH-multiSigAddr(2-of-2, [addr1, addr2]) to addr4: " + unsignedRawMultiSigToAddr4TxHex);
		
		// Sign multi-sig transaction
		SignedRawTransaction srTx = client.signRawTransactionWithKey(
				unsignedRawMultiSigToAddr4TxHex,
				Arrays.asList(client.dumpPrivKey(addr1), client.dumpPrivKey(addr2)), // Using private keys of addr1 and addr2 (multisig 2-of-2)
				Arrays.asList(inputP2SH),
				null);
		LOGGER.info("signedRawTx hex: " + srTx.hex());
		LOGGER.info("signedRawTx complete: " + srTx.complete());
		
		List<RawTransactionSigningOrVerificationError> errors = srTx.errors();
		if (errors != null)
		{
			LOGGER.severe("Found errors when signing");

			for (RawTransactionSigningOrVerificationError error : errors)
			{
				LOGGER.severe("Error: " + error);
			}
		}
		
		// Transaction 2 : multisig -> addr4
		String sentRawTransactionID = client.sendRawTransaction(srTx.hex());
		LOGGER.info("Sent signedRawTx (txID): " + sentRawTransactionID);

		assertNull("Client reported errors when signing unsignedRawTx", srTx.errors());
	}
	
	/**
	 * Signing a transaction to a P2SH-P2WPKH address (Pay-to-Witness-Public-Key-Hash)
	 */
	@Test(expected = Test.None.class) // no exception expected
	public void signRawTransactionWithKeyTest_P2SH_P2WPKH()
	{
		LOGGER.info("Testing scenario: signRawTransactionWithKey (addr1 -> addr2)");
		
		String addr1 = client.getNewAddress();
		LOGGER.info("Created address addr1: " + addr1);

		String addr2 = client.getNewAddress();
		LOGGER.info("Created address addr2: " + addr2);

		List<String> generatedBlocksHashes = client.generateToAddress(110, addr1);
		LOGGER.info("Generated " + generatedBlocksHashes.size() + " blocks for addr1");
		
		List<Unspent> utxos = client.listUnspent(0, Integer.MAX_VALUE, addr1);
		LOGGER.info("Found " + utxos.size() + " UTXOs (unspent transaction outputs) belonging to addr1");

		Unspent selectedUtxo = utxos.get(0);
		LOGGER.info("Selected UTXO which will be sent from addr1 to addr2: " + selectedUtxo);
		
		ExtendedTxInput inputP2SH_P2WPKH = new ExtendedTxInput(
				selectedUtxo.txid(),
				selectedUtxo.vout(),
				selectedUtxo.scriptPubKey(),
				selectedUtxo.amount(),
				selectedUtxo.redeemScript(),
				selectedUtxo.witnessScript());
		LOGGER.info("inputP2SH_P2WPKH txid: " + 			inputP2SH_P2WPKH.txid());
		LOGGER.info("inputP2SH_P2WPKH vout: " + 			inputP2SH_P2WPKH.vout());
		LOGGER.info("inputP2SH_P2WPKH scriptPubKey: " + 	inputP2SH_P2WPKH.scriptPubKey());
		LOGGER.info("inputP2SH_P2WPKH redeemScript: " + 	inputP2SH_P2WPKH.redeemScript());
		LOGGER.info("inputP2SH_P2WPKH witnessScript: " + 	inputP2SH_P2WPKH.witnessScript());
		LOGGER.info("inputP2SH_P2WPKH amount: " + 			inputP2SH_P2WPKH.amount());

		BitcoinRawTxBuilder rawTxBuilder = new BitcoinRawTxBuilder(client);
		rawTxBuilder.in(inputP2SH_P2WPKH);

		// Found no other reliable way to estimate the fee in a test
		// Therefore, setting the fee for this tx to 1/1000 of the tx amount
		BigDecimal estimatedFee = selectedUtxo.amount().divide(BigDecimal.valueOf(1000));
		BigDecimal txToAddr2Amount = selectedUtxo.amount().subtract(estimatedFee);
		rawTxBuilder.out(addr2, txToAddr2Amount);
		
		LOGGER.info("unsignedRawTx in amount: " + selectedUtxo.amount());
		LOGGER.info("unsignedRawTx out amount: " + txToAddr2Amount);

		String unsignedRawTxHex = rawTxBuilder.create();
		LOGGER.info("Created unsignedRawTx from addr1 to addr2: " + unsignedRawTxHex);
		
		// Sign tx
		SignedRawTransaction srTx = client.signRawTransactionWithKey(
				unsignedRawTxHex,
				Arrays.asList(client.dumpPrivKey(addr1)), // addr1 is sending, so we need to sign with the private key of addr1
				Arrays.asList(inputP2SH_P2WPKH),
				null);
		LOGGER.info("signedRawTx hex: " + srTx.hex());
		LOGGER.info("signedRawTx complete: " + srTx.complete());

		List<RawTransactionSigningOrVerificationError> errors = srTx.errors();
		if (errors != null)
		{
			LOGGER.severe("Found errors when signing");

			for (RawTransactionSigningOrVerificationError error : errors)
			{
				LOGGER.severe("Error: " + error);
			}
		}
		
		String sentRawTransactionID = client.sendRawTransaction(srTx.hex());
		LOGGER.info("Sent signedRawTx (txID): " + sentRawTransactionID);

		assertNull("Client reported errors when signing unsignedRawTx", srTx.errors());
	}
}
