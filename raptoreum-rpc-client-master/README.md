raptoreum-rpc-client
==================

This is a lightweight java raptoreumd JSON-RPC client binding. It does not require any external dependencies.


Configuration
=====
In order to know what RPC API to use, the library will look in the bitcoind configuration file (`<user home>/.raptoreumcore/raptoreum.conf`) and read the relevant configs:
- rpcconnect
- rpcport

Here is a sample raptoreum.conf that will setup bitcoind to run in regtest mode and in a way compatible with this library:

```
# Maintain full transaction index, used in lookups by the getrawtransaction call
txindex=1

# Run bitcoind in regtest mode
regtest=1

# Accept command line and JSON-RPC commands
server=1

# Tells bitcoind that the RPC API settings on the following lines apply to the regtest RPC API
[regtest]

# RPC API settings
rpcconnect=localhost
rpcport=9997
```

Note that the configuration does not contain any API credentials. The authentication is done via a temporary token stored in a cookie file by bitcoind (see [details](https://bitcoin.org/en/release/v0.12.0#rpc-random-cookie-rpc-authentication)). The approach of using rpcuser and rpcpassword is still supported, even though bitcoind considers it legacy. This works fine with raptoreumd.