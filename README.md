# gemini-coin-mixer

This is a humble attempt at a mixer implementation for a fictitious Jobcoin cryptocurrency.

Clients wishing to mix their jobcoins send a set of addresses to the mixer and are given an address to which they send their coins.

The mixer will then deposit the value of those coins minus a fee to the set of addresses which were provided by the client.


Instructions to run:

1. brew install telnet
2. git clone git@github.com:davidlusg/gemini-coin-mixer.git
3. start the server - run https://github.com/davidlusg/gemini-coin-mixer/blob/master/src/main/scala/server/Main.scala
4. telnet https://github.com/davidlusg/gemini-coin-mixer/blob/master/src/main/resources/application.conf#L3
5. Send a request for a house address and provide client addresses.
`{"user": "davidlu", "addresses": ["address1", "address2"]}`
6. To send a mixing request, you must send coins to the address received in step 5 like this:
`{"user": "davidlu", "houseAddress": "<house address from (step 5)>", "amount": 5.99999999}`

Here is an actual run:
Within a terminal:
`
 [dlu@mbp ~]$ telnet 127.0.0.1 9281
 Trying 127.0.0.1...
 Connected to localhost.
 Escape character is '^]'.
 `
 
 
 `{"user": "davidlu", "addresses": ["address1", "address2"]}`
 
 `Coins sent to 1ed54015-aa80-4768-8c4d-e6a583101e61 will be mixed and sent to "address1","address2"`
 
 
 `{"user": "davidlu", "houseAddress": "1ed54015-aa80-4768-8c4d-e6a583101e61", "amount": 5.99999999}`
 
 `3.00000000 sent to "address1",2.99999999 sent to "address2"`
`

You will see in your Main (e.g. Intellij console) something like this where each line is printed 1 second apart demonstrating the deposits over time.:

`Sending transaction 3.00000000 "address1"  to P2P`

`Sending transaction 2.99999999 "address2"  to P2P`

`Finished my work - going away`
