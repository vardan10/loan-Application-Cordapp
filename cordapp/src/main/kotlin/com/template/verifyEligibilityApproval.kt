package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.Contract.EligibilityContract
import com.template.State.EligibilityState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.function.Predicate

@InitiatingFlow
@StartableByRPC
class verifyEligibilityApprovalFlow(val eligibilityID: UniqueIdentifier
                                    ):FlowLogic<SignedTransaction>() {

    companion object {
        object SET_UP : ProgressTracker.Step("Initialising flow.")
        object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for Credit Rating.")
        object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
        object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
        object WE_SIGN : ProgressTracker.Step("signing transaction.")
        object ORACLE_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
        object OTHER_PARTY_SIGNS : ProgressTracker.Step("Requesting Other party signature.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(SET_UP, QUERYING_THE_ORACLE, BUILDING_THE_TX,
                VERIFYING_THE_TX, WE_SIGN, ORACLE_SIGNS, OTHER_PARTY_SIGNS, FINALISING)
    }

    override val progressTracker = tracker()
    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SET_UP
        // Get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()


        // Build the transaction
        // 1. Query loan state by linearId for input state
        val vaultQueryCriteria = QueryCriteria.LinearStateQueryCriteria(listOf(ourIdentity), listOf(eligibilityID))
        val inputState = serviceHub.vaultService.queryBy<EligibilityState>(vaultQueryCriteria).states.first()


        //get cibil rating from oracle
        val panCardNo=inputState.state.data.panCardNo

        val oracleName = CordaX500Name("Oracle", "New York","US")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                ?: throw IllegalArgumentException("Requested oracle $oracleName not found on network.")

        progressTracker.currentStep = QUERYING_THE_ORACLE
        val cibilRating = subFlow(QueryCreditRatingFlow(oracle,panCardNo))


        // Create the output state
        val outputState = inputState.state.data.copy(cibilRating = cibilRating)

        progressTracker.currentStep = BUILDING_THE_TX
        // Building the transaction
        val transactionBuilder = TransactionBuilder(notary).
                addInputState(inputState).
                addOutputState(outputState, EligibilityContract.ID).
                addCommand(EligibilityContract.Commands.GenerateRating(panCardNo, cibilRating), listOf(oracle.owningKey, ourIdentity.owningKey, outputState.bank.owningKey))

        progressTracker.currentStep = VERIFYING_THE_TX
        // Verify transaction Builder
        transactionBuilder.verify(serviceHub)

        progressTracker.currentStep = WE_SIGN
        //Sign initial transaction
        val ptx = serviceHub.signInitialTransaction(transactionBuilder)


        progressTracker.currentStep = ORACLE_SIGNS
        // For privacy reasons, we only want to expose to the oracle any commands of type `Prime.Create`
        // that require its signature.
        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> -> oracle.owningKey in it.signers && it.value is EligibilityContract.Commands.GenerateRating
                else -> false
            }
        })

        val oracleSignature = subFlow(SignCreditRatingFlow(oracle, ftx))
        val stx = ptx.withAdditionalSignature(oracleSignature)

        progressTracker.currentStep = OTHER_PARTY_SIGNS
        // Send transaction to the other node for signing
        val otherPartySession = initiateFlow(outputState.bank)
        val completelySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(otherPartySession)))


        progressTracker.currentStep = FINALISING
        // Notarize and commit
        return subFlow(FinalityFlow(completelySignedTransaction))
    }
}

@InitiatedBy(verifyEligibilityApprovalFlow::class)
class verifyEligibilityApprovalResponderFlow(val otherpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val flow = object : SignTransactionFlow(otherpartySession){
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an EligibilityState." using (output is EligibilityState)
                val eligibilityStateoutput = output as EligibilityState
                "CibilRating should not null" using (eligibilityStateoutput.cibilRating != null)
            }
        }
        return subFlow(flow)
    }

}