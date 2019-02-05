package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.Contract.LoanContract
import com.template.State.LoanState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
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
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, FINALISING)
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
                addCommand(LoanContract.Commands.LoanRequest(), ourIdentity.owningKey)

        progressTracker.currentStep = VERIFYING_THE_TX
        // Verify transaction Builder
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        // Sign the transaction
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = FINALISING
        // Notarize and commit
        return subFlow(FinalityFlow(signedTransaction))
    }
}