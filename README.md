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

###/account?id={id}

###/transaction/withdraw
Body:
```
{
  "from": "ffeaab63-ab9f-4e03-b58b-dc2a6479ebf9",
  "amount": 50
}
```
Response:
```$xslt
{
  "id": "07f1b279-8b6b-4d5d-8c14-25689f7a234d",
  "reason": "Account not found"
}
```
###/transaction/transfer

Request:
```

```

Response;
```
{
  "id": "0924d6a4-b8ca-4514-baae-ceaa905fd10d",
  "balance": 150
}
```

###/transaction/deposit
Request:
```

```
Response:
```

```

###/transaction/tx/{id}
Request:
```

```
Response:
```

```