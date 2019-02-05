package com.template.flows

import com.google.common.collect.ImmutableList
import com.template.Contract.LoanContract
import com.template.LoanRequestFlow
import com.template.State.EligibilityState
import com.template.State.LoanState
import com.template.flow.QueryHandler
import com.template.flow.SignHandler
import com.template.verifyCheckEligibilityFlow
import com.template.verifyEligibilityApprovalFlow
import com.template.verifyLoanApprovalFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals


class verifyLoanApprovalFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var nodeC: StartedMockNode
    private lateinit var oracleNode: StartedMockNode
    private lateinit var loanId: UniqueIdentifier
    private lateinit var eligibilityId: UniqueIdentifier

    @Before
    fun setup() {
        network = MockNetwork(ImmutableList.of("com.template"))
        nodeA = network.createPartyNode(null)
        nodeB = network.createPartyNode(null)
        nodeC = network.createPartyNode(null)
        oracleNode = network.createNode(legalName = CordaX500Name("Oracle", "New York","US"))

        oracleNode.registerInitiatedFlow(QueryHandler::class.java)
        oracleNode.registerInitiatedFlow(SignHandler::class.java)

        network.runNetwork()

        // Run the Loan Request Flow first
        val flow = LoanRequestFlow("Jhon", 99, "PANCARD", nodeB.info.legalIdentities[0])
        val loanRequestFlowFuture = nodeA.startFlow(flow)
        network.runNetwork()
        val results = loanRequestFlowFuture.getOrThrow()
        val newLoanState = results.tx.outputs.single().data as LoanState
        loanId = newLoanState.linearId

        // Run verify Check Eligibility Flow next
        val verifyCheckEligibilityFlow = verifyCheckEligibilityFlow(loanId, nodeC.info.legalIdentities[0])
        val verifyCheckEligibilityFlowFuture = nodeB.startFlow(verifyCheckEligibilityFlow)
        network.runNetwork()
        val CheckEligibilitySignedTransaction = verifyCheckEligibilityFlowFuture.get()
        val newEligibilityState = CheckEligibilitySignedTransaction.tx.outputsOfType(EligibilityState::class.java)[0]

        // Run verify Eligibility Approval Flow next
        val verifyEligibilityApprovalFlow = verifyEligibilityApprovalFlow(newEligibilityState.linearId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(verifyEligibilityApprovalFlow)
        network.runNetwork()
        val eligibilityApprovalSignedTransaction = verifyEligibilityApprovalFlowFuture.get()
        val newEligibilityStateAfterApproval = eligibilityApprovalSignedTransaction.tx.outputsOfType(EligibilityState::class.java)[0]
        eligibilityId = newEligibilityStateAfterApproval.linearId

        assertEquals(300, newEligibilityStateAfterApproval.cibilRating)
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowUsesTheCorrectNotary() {
        val loanApprovalFlow = verifyLoanApprovalFlow(eligibilityId, true)
        val verifyLoanApprovalFlowFuture = nodeB.startFlow(loanApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyLoanApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.outputStates.size.toLong())
        val output = signedTransaction.tx.outputs[0]

        assertEquals(network.notaryNodes[0].info.legalIdentities[0], output.notary)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasCorrectParameters() {
        val loanApprovalFlow = verifyLoanApprovalFlow(eligibilityId, true)
        val verifyLoanApprovalFlowFuture = nodeB.startFlow(loanApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyLoanApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.outputStates.size.toLong())
        val output = signedTransaction.tx.outputsOfType(LoanState::class.java)[0]

        assertEquals(nodeB.info.legalIdentities[0], output.bank)
        assertEquals(nodeA.info.legalIdentities[0], output.financeAgency)
        assertEquals("Jhon", output.name)
        assertEquals(99, output.amount)
        assertEquals(true, output.loanStatus)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneOutputUsingTheCorrectContract() {
        val loanApprovalFlow = verifyLoanApprovalFlow(eligibilityId, true)
        val verifyLoanApprovalFlowFuture = nodeB.startFlow(loanApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyLoanApprovalFlowFuture.get()
        assertEquals(1, signedTransaction.tx.outputStates.size.toLong())
        val (_, contract) = signedTransaction.tx.outputs[0]

        assertEquals("com.template.Contract.LoanContract", contract)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneLoanApprovalCommand() {
        val loanApprovalFlow = verifyLoanApprovalFlow(eligibilityId, true)
        val verifyLoanApprovalFlowFuture = nodeB.startFlow(loanApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyLoanApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.commands.size.toLong())
        val (value) = signedTransaction.tx.commands[0]

        assert(value is LoanContract.Commands.LoanApproval)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneCommandWithTheBankAndFinanceAgencyAsASigner() {
        val loanApprovalFlow = verifyLoanApprovalFlow(eligibilityId, true)
        val verifyLoanApprovalFlowFuture = nodeB.startFlow(loanApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyLoanApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.commands.size.toLong())
        val (_, signers) = signedTransaction.tx.commands[0]

        assertEquals(2, signers.size.toLong())
        assert(signers.containsAll(listOf(nodeA.info.legalIdentities[0].owningKey,nodeB.info.legalIdentities[0].owningKey)))
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneInputAndAttachmentsAndNoTimeWindows() {
        val loanApprovalFlow = verifyLoanApprovalFlow(eligibilityId, true)
        val verifyLoanApprovalFlowFuture = nodeB.startFlow(loanApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyLoanApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.inputs.size.toLong())
        // The single attachment is the contract attachment.
        assertEquals(1, signedTransaction.tx.attachments.size.toLong())
        assertEquals(null, signedTransaction.tx.timeWindow)
    }
}