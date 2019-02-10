package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.Contract.EligibilityContract
import com.template.State.EligibilityState
import com.template.State.LoanState
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class verifyCheckEligibilityFlow(val loanID: UniqueIdentifier,
                                 val creditRatingAgency : Party):FlowLogic<SignedTransaction>() {

    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flow.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object OTHER_PARTY_SIGNS : ProgressTracker.Step("Requesting Other party signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, OTHER_PARTY_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SET_UP
        // Get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()


        // Build the transaction
        // 1. Query loan state by linearId for input state
        val vaultQueryCriteria = QueryCriteria.LinearStateQueryCriteria(listOf(ourIdentity), listOf(loanID))
        val inputStateData = serviceHub.vaultService.queryBy<LoanState>(vaultQueryCriteria).states.first().state.data

        // Create the output state
        val outputState = EligibilityState(inputStateData.name, inputStateData.bank, inputStateData.panCardNo, creditRatingAgency, null, loanID, UniqueIdentifier())

        progressTracker.currentStep = BUILDING_THE_TX
        // Building the transaction
        val transactionBuilder = TransactionBuilder(notary).
                addOutputState(outputState, EligibilityContract.ID).
                addCommand(EligibilityContract.Commands.CheckEligibility(), ourIdentity.owningKey, outputState.creditRatingAgency.owningKey)

        progressTracker.currentStep = VERIFYING_THE_TX
        // Verify transaction Builder
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        // Sign the transaction
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = OTHER_PARTY_SIGNS
        // Send transaction to the other node for signing
        val otherPartySession = initiateFlow(outputState.creditRatingAgency)
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))

        progressTracker.currentStep = FINALISING
        // Notarize and commit
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}

@InitiatedBy(verifyCheckEligibilityFlow::class)
class verifyCheckEligibilityResponderFlow(val otherpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val flow = object : SignTransactionFlow(otherpartySession){
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an EligibilityState." using (output is EligibilityState)
                val eligibilityStateoutput = output as EligibilityState
                "CibilRating should be null" using (eligibilityStateoutput.cibilRating == null)
            }
        }
        return subFlow(flow)
    }

}
