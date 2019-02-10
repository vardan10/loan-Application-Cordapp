package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.Contract.LoanContract
import com.template.State.LoanState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class LoanRequestFlow(val name: String,
                      val amount: Int,
                      val panCardNo: String,
                      val bank: Party):FlowLogic<SignedTransaction>() {

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

        // Create the output state
        val outputState = LoanState(name, amount, panCardNo, ourIdentity, bank, null, null, UniqueIdentifier())

        progressTracker.currentStep = BUILDING_THE_TX
        // Building the transaction
        val transactionBuilder = TransactionBuilder(notary).
                addOutputState(outputState, LoanContract.ID).
                addCommand(LoanContract.Commands.LoanRequest(), ourIdentity.owningKey, outputState.bank.owningKey)

        progressTracker.currentStep = VERIFYING_THE_TX
        // Verify transaction Builder
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        // Sign the transaction
        val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = OTHER_PARTY_SIGNS
        // Send transaction to the other node for signing
        val otherPartySession = initiateFlow(outputState.bank)
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(otherPartySession)))

        progressTracker.currentStep = FINALISING
        // Notarize and commit
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}


@InitiatedBy(LoanRequestFlow::class)
class LoanRequestResponderFlow(val otherpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val flow = object : SignTransactionFlow(otherpartySession){
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an LoanState." using (output is LoanState)
                val loanStateoutput = output as LoanState
                "Loan amount should be positive" using (loanStateoutput.amount > 0)
                "CibilRating should be null" using (loanStateoutput.cibilRating == null)
                "Loan Status should be null" using (loanStateoutput.loanStatus == null)
            }
        }
        return subFlow(flow)
    }

}
