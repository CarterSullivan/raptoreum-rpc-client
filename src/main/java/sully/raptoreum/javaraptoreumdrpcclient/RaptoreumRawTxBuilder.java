/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package sully.raptoreum.javaraptoreumdrpcclient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author azazar
 */
public class RaptoreumRawTxBuilder {

  public final RaptoreumdRpcClient bitcoin;

  public RaptoreumRawTxBuilder(RaptoreumdRpcClient bitcoin) {
    this.bitcoin = bitcoin;
  }
  public Set<RaptoreumdRpcClient.TxInput> inputs = new LinkedHashSet<>();
  public List<RaptoreumdRpcClient.TxOutput> outputs = new ArrayList<>();
  public List<String> privateKeys;

  @SuppressWarnings("serial")
  private class Input extends RaptoreumdRpcClient.BasicTxInput {

    public Input(String txid, Integer vout) {
      super(txid, vout);
    }

    public Input(RaptoreumdRpcClient.TxInput copy) {
      this(copy.txid(), copy.vout());
    }

    @Override
    public int hashCode() {
      return txid.hashCode() + vout;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null)
        return false;
      if (!(obj instanceof RaptoreumdRpcClient.TxInput))
        return false;
      RaptoreumdRpcClient.TxInput other = (RaptoreumdRpcClient.TxInput) obj;
      return vout == other.vout() && txid.equals(other.txid());
    }

  }

  public RaptoreumRawTxBuilder in(RaptoreumdRpcClient.TxInput in) {
    inputs.add(new Input(in.txid(), in.vout()));
    return this;
  }

  public RaptoreumRawTxBuilder in(String txid, int vout) {
    in(new RaptoreumdRpcClient.BasicTxInput(txid, vout));
    return this;
  }

  public RaptoreumRawTxBuilder out(String address, BigDecimal amount) {
    return out(address, amount, null);
  }

  public RaptoreumRawTxBuilder out(String address, BigDecimal amount, byte[] data) {
    outputs.add(new RaptoreumdRpcClient.BasicTxOutput(address, amount, data));
    return this;
  }

  public RaptoreumRawTxBuilder in(BigDecimal value) throws GenericRpcException {
    return in(value, 6);
  }

  public RaptoreumRawTxBuilder in(BigDecimal value, int minConf) throws GenericRpcException {
    List<RaptoreumdRpcClient.Unspent> unspent = bitcoin.listUnspent(minConf);
    BigDecimal v = value;
    for (RaptoreumdRpcClient.Unspent o : unspent) {
      if (!inputs.contains(new Input(o))) {
        in(o);
        v = v.subtract(o.amount());
      }
      if (v.compareTo(BigDecimal.ZERO) < 0)
        break;
    }
    if (BigDecimal.ZERO.compareTo(v) < 0)
      throw new GenericRpcException("Not enough bitcoins (" + v + "/" + value + ")");
    return this;
  }

  private HashMap<String, RaptoreumdRpcClient.RawTransaction> txCache = new HashMap<>();

  private RaptoreumdRpcClient.RawTransaction tx(String txId) throws GenericRpcException {
    RaptoreumdRpcClient.RawTransaction tx = txCache.get(txId);
    if (tx != null)
      return tx;
    tx = bitcoin.getRawTransaction(txId);
    txCache.put(txId, tx);
    return tx;
  }

  public RaptoreumRawTxBuilder outChange(String address) throws GenericRpcException {
    return outChange(address, BigDecimal.ZERO);
  }

  public RaptoreumRawTxBuilder outChange(String address, BigDecimal fee) throws GenericRpcException {
    BigDecimal is = BigDecimal.ZERO;
    for (RaptoreumdRpcClient.TxInput i : inputs)
      is = is.add(tx(i.txid()).vOut().get(i.vout()).value());
    BigDecimal os = fee;
    for (RaptoreumdRpcClient.TxOutput o : outputs)
      os = os.add(o.amount());
    if (os.compareTo(is) < 0)
      out(address, is.subtract(os));
    return this;
  }
  
  public RaptoreumRawTxBuilder addPrivateKey(String privateKey)
  {
	  if ( privateKeys == null )
		  privateKeys = new ArrayList<String>();
	  privateKeys.add(privateKey);
	  return this;
  }

  public String create() throws GenericRpcException {
    return bitcoin.createRawTransaction(new ArrayList<>(inputs), outputs);
  }

	/**
	 * @deprecated Underlying client call not supported anymore, use instead
	 *             {@link RaptoreumdRpcClient#signRawTransactionWithKey(String, List, List, SignatureHashType)}
	 */
  @Deprecated
  public String sign() throws GenericRpcException {
    return bitcoin.signRawTransaction(create(), null, privateKeys);
  }

	/**
	 * @deprecated Relies on call to deprecated {@link #sign()}. Instead, call
	 *             {@link RaptoreumdRpcClient#sendRawTransaction(String)} on a signed
	 *             transaction
	 */
  @Deprecated
  public String send() throws GenericRpcException {
    return bitcoin.sendRawTransaction(sign());
  }

}
