# Basic RESTful API for money transfer between accounts

In this implementation the API manage the following scenario:

* In memory database to persist accounts information and transactions
* No external service to manage the accounts

The database is implemented using an HashMap and synchronized block is used to manage a single transaction.
This solution purely for the simplicity of the test, better solutions can be implemented.

# How to run

In the root folder of the project:
```
$sbt clean compile test
```

# Endpoints
### POST /account
Create a new account for an user. \
Response codes:
* 200 =\> Account created
* 400 =\> If invalid username, initial balance not valid (negative)

Body:
```
{
  "userName": "Alice",
  "initialBalance": 345
}
```
Response:
```
{
  "id": "4438322a-86b3-4b7f-996c-2f39c917d344",
  "userName": "Alice",
  "balance": 345
}
```
### GET /account/{id}
Fetch an already existing account.\
Response codes: 
* 200 =\> Fetched account with account info in the body
* 404 =\> Account not found

Response:
```
{
  "id": "111",
  "userName": "bob",
  "balance": 200
}
```
### POST /account/{id}/withdraw
Withdraw money from a specific account\
Response codes:
* 200 =\> Success Withdraw with the transaction id and the new balance in the body response
* 400 =\> if insufficient fund with error message
* 400 =\> if amount not valid, negative with error message
* 404 =\> if account not found with error message

Body:
```
{
  "amount": 50
}
```
Response:
```
{
  "id": "bdebe740-cdfc-4bbc-9372-dd0672822e19",
  "balance": 150
}
```
### POST /account/{id}/transfer
Transfer money from the current account {id} to the another account, from the request body\
Response codes:
* 200 =\> Successful transfer and transaction id and new balance in the body response
* 404 =\> If account not found with error message
* 400 =\> If insufficient fund with error message
* 400 =\> If amount not valid with error message

Request:
```
{
  "to": "987",
  "amount": 50
}
```

Response;
```
{
  "id": "ddde9f86-0c9c-4ae4-bcb4-b82132e0f909",
  "balance": 150
}
```

### POST /account/{id}/deposit
Deposit money in the account {id}
Response codes:
* 200 =\> Successful deposit with transaction id and new balance in the response body\
* 404 =\> If account not found with error message
* 400 =\> I amount not valid (negative) with error message

Request:
```
{
  "amount": 50
}
```
Response:
```
{
  "id": "74367c8d-6904-4587-89b4-877c78d5ec83",
  "balance": 250
}
```

### GET /account/{account_id}/tx/{transaction_id}
Fetch a specific transaction {transaction_id} for the account {account_id}
Response codes:
* 200 =\> Success with transaction info in the body response
* 404 =\> For account or transaction not found with error message

```
{
  "transactionId": "7290ef7e-0039-41f0-a390-a4fbe43c706d",
  "accountId": "123",
  "balance": 87,
  "time": "2017-10-17T22:16:16.820+01:00"
}
```

### GET /account/{id}/txs
Fetch all the transactions for the account {id}\
Response codes:
* 200 =\> Success with transaction list in the body response
* 404 =\> If the account is not found

Response:
```
[{
  "transactionId": "7dab9f85-23de-41e7-970e-ffabe3a9759b",
  "accountId": "123",
  "balance": 87,
  "time": "2017-10-17T22:24:57.521+01:00"
}, {
  "transactionId": "6aa5db4d-25d4-4765-a6f3-d0ec09b3dbc6",
  "accountId": "123",
  "balance": null,
  "time": "2017-10-17T22:24:57.521+01:00"
}, {
  "transactionId": "a499f742-e1b1-49e6-bc67-4d133b5f2e7f",
  "accountId": "123",
  "balance": 67,
  "time": "2017-10-17T22:24:57.521+01:00"
}]
```

# Examples
Create users
```\\
curl -H 'Content-Type: application/json' -X POST \
-d '{"userName": "Alice","initialBalance": 345}' \
http://localhost:8080/account 
=>
{
  "id": "6153bb08-7f92-48dd-884e-fe29566ab92f",
  "userName": "Alice",
  "balance": 345
}
```
```
curl -H 'Content-Type: application/json' -X POST \
-d '{"userName": "Maccio","initialBalance": 290}' \
http://localhost:8080/account
=>
{
  "id": "fc6f669e-77c4-471d-b3f8-b408d10e5cb9",
  "userName": "Maccio",
  "balance": 290
}
```
Transfer from Alice to Maccio
```
curl -H 'Content-Type: application/json' -X POST \
-d '{"to": "fc6f669e-77c4-471d-b3f8-b408d10e5cb9","amount": 125}' \
http://localhost:8080/account/6153bb08-7f92-48dd-884e-fe29566ab92f/transfer
=>
{
  "id": "6e025089-892b-4d57-a64d-e29a03bbbba7",
  "balance": 220
}
```
Fetch transactions
```
curl -H 'Content-Type: application/json' -X GET \
-d '{"to": "fc6f669e-77c4-471d-b3f8-b408d10e5cb9","amount": 125}' \
http://localhost:8080/account/6153bb08-7f92-48dd-884e-fe29566ab92f/txs
=>
[
  {
    "transactionId": "6e025089-892b-4d57-a64d-e29a03bbbba7",
    "accountId": "6153bb08-7f92-48dd-884e-fe29566ab92f",
    "balance": 220,
    "time": "2017-10-18T12:32:19.337+01:00"
  }
]
```