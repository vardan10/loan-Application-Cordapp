<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Loan Application Cordapp - Kotlin

## Architecture
![Solution Architecture](https://raw.githubusercontent.com/vardan10/loan-Application-Cordapp/master/Architecture%20docs/Design%20Document.png)

## Instructions for setting up

1. `git clone https://github.com/vardan10/loan-Application-Cordapp.git`
2. `cd loan-Application-Cordapp`
3. `./gradlew deployNodes` - building may take upto a minute (it's much quicker if you already have the Corda binaries).
4. `./build/nodes/runnodes`
5. In a new terminal - run partyA API Server
    ```./gradlew runPartyAServer```
6. In a new terminal - run partyB API Server
    ```./gradlew runPartyBServer```
7. In a new terminal - run partyC API Server
    ```./gradlew runPartyCServer```

At this point you will have a notary node running as well as three other nodes. The nodes take about 20-30 seconds to finish booting up.There should be 4 console windows in total. (Plus 3 terminal for API)

## Using the CorDapp via the console:
1. Start the Loan Application
In PartyA Console type:
```
start LoanRequestFlow name: "Prasanna", amount: 30000, panCardNo: "FLFPK1672D", bank: "PartyB"
```

2. Get Linear Id of Loan Application
InParty B console type:
```
run vaultQuery contractStateType: com.template.State.LoanState
```

3. Send loan application to credit rating Agency
InParty B console type:
```
start verifyCheckEligibilityFlow loanID: "<LOAN_LINEAR_ID>", creditRatingAgency: "PartyC"
```

4. Get Linear Id of Loan Eligibility
InParty B console type:
```
run vaultQuery contractStateType: com.template.State.EligibilityState
```

5. Create a CIBIL Rating
InParty C console type:
```
start verifyEligibilityApprovalFlow eligibilityID: "<ELIGIBILITY_LINEAR_ID>"
```

6. Approve/Reject Loan Application
InParty B console type:
```
start verifyLoanApprovalFlow eligibilityID: "<ELIGIBILITY_LINEAR_ID>", loanstatus: true
```

## Using the CorDapp via the Spring Boot APIs:
1. Start the Loan Application
```
curl -X POST \
  http://localhost:8080/loan/LoanRequest \
  -F panCardNo=FLFPK1672D \
  -F name=Vardan \
  -F amount=40000 \
  -F bank=PartyB
```

2. Get Linear Id of Loan Application
```
curl -X GET http://localhost:8080/loan/GetLoans
```

3. Send loan application to credit rating Agency
```
curl -X POST \
  http://localhost:8081/eligibility/CheckEligibility \
  -F loanID=<LOAN_LINEAR_ID> \
  -F creditRatingAgency=PartyC
```

4. Get Linear Id of Loan Eligibility
```
curl -X GET http://localhost:8081/eligibility/GetEligibilities
```

5. Create a CIBIL Rating
InParty C console type:
```
curl -X POST \
  http://localhost:8082/eligibility/VerifyEligibility \
  -F eligibilityID=<ELIGIBILITY_LINEAR_ID>
```

6. Approve/Reject Loan Application
InParty B console type:
```
curl -X POST \
  http://localhost:8081/loan/LoanApproval \
  -F eligibilityID=<ELIGIBILITY_LINEAR_ID> \
  -F loanstatus=true
```

7. Get Approved Loans
InParty A console type:
```
curl -X GET http://localhost:8080/loan/GetApprovedLoans
```
