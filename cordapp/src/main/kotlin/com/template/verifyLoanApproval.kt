package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.Contract.LoanContract
import com.template.State.EligibilityState
import com.template.State.LoanState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class verifyLoanApprovalFlow(val eligibilityID: UniqueIdentifier, val loanstatus: Boolean):FlowLogic<SignedTransaction>() {
    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flow.")
        object QUERYING_THE_VAULT : ProgressTracker.Step("Querying the Vault for input states.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object OTHER_PARTY_SIGNS : ProgressTracker.Step("Requesting Other party signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, QUERYING_THE_VAULT, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, OTHER_PARTY_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()
    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SET_UP
        // Get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()


        progressTracker.currentStep = QUERYING_THE_VAULT
        // Build the transaction
        // 1. Query loan state by linearId for input state
        val vaultEligibilityQueryCriteria = QueryCriteria.LinearStateQueryCriteria(listOf(ourIdentity), listOf(eligibilityID))
        val eligibilityStateData = serviceHub.vaultService.queryBy<EligibilityState>(vaultEligibilityQueryCriteria).states.first().state.data

        // 1. Query loan state by linearId for input state
        val vaultLoanQueryCriteria = QueryCriteria.LinearStateQueryCriteria(listOf(ourIdentity), listOf(eligibilityStateData.loanId))
        val inputState = serviceHub.vaultService.queryBy<LoanState>(vaultLoanQueryCriteria).states.first()

        // Create the output state
        val outputState = inputState.state.data.copy(loanStatus=loanstatus,cibilRating = eligibilityStateData.cibilRating)

        progressTracker.currentStep = BUILDING_THE_TX
        // Building the transaction
        val transactionBuilder = TransactionBuilder(notary).
                addInputState(inputState).
                addOutputState(outputState, LoanContract.ID).
                addCommand(LoanContract.Commands.LoanApproval(), ourIdentity.owningKey, outputState.financeAgency.owningKey)

        progressTracker.currentStep = VERIFYING_THE_TX
        // Verify transaction Builder
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        // Sign the transaction
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = OTHER_PARTY_SIGNS
        // Send transaction to the seller node for signing
        val otherPartySession = initiateFlow(outputState.financeAgency)
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))

        progressTracker.currentStep = FINALISING
        // Notarize and commit
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}

@InitiatedBy(verifyLoanApprovalFlow::class)
class InvoiceSettlementResponderFlow(val otherpartySession: FlowSession): FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        val flow = object : SignTransactionFlow(otherpartySession){
            override fun checkTransaction(stx: SignedTransaction) {
                // Any sanity checks on this transaction
            }
        }
        subFlow(flow)
    }
}
