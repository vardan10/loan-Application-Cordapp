package com.template

import com.google.common.collect.ImmutableList
import com.template.Contract.EligibilityContract
import com.template.State.EligibilityState
import com.template.State.LoanState
import com.template.flow.QueryHandler
import com.template.flow.SignHandler
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.*
import kotlin.test.assertEquals

class VerifyEligibilityApprovalFlowTests {
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
        val flow = LoanRequestFlow("Jhon",99 , "PANCARD" ,nodeB.info.legalIdentities[0])
        val loanRequestFlowFuture = nodeA.startFlow(flow)
        network.runNetwork()
        val results = loanRequestFlowFuture.getOrThrow()
        val newLoanState = results.tx.outputs.single().data as LoanState
        loanId = newLoanState.linearId

        // Run verify Check Eligibility Flow next
        val verifyCheckEligibilityFlow = verifyCheckEligibilityFlow(loanId,nodeC.info.legalIdentities[0])
        val verifyEligibilityApprovalFlowFuture = nodeB.startFlow(verifyCheckEligibilityFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()
        val newEligibilityState = signedTransaction.tx.outputsOfType(EligibilityState::class.java)[0]
        eligibilityId = newEligibilityState.linearId
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowUsesTheCorrectNotary() {
        val eligibilityApprovalFlow = verifyEligibilityApprovalFlow(eligibilityId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(eligibilityApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.outputStates.size.toLong())
        val output = signedTransaction.tx.outputs[0]

        assertEquals(network.notaryNodes[0].info.legalIdentities[0], output.notary)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasCorrectParameters() {
        val eligibilityApprovalFlow = verifyEligibilityApprovalFlow(eligibilityId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(eligibilityApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.outputStates.size.toLong())
        val output = signedTransaction.tx.outputsOfType(EligibilityState::class.java)[0]

        assertEquals(nodeB.info.legalIdentities[0], output.bank)
        assertEquals(nodeC.info.legalIdentities[0], output.creditRatingAgency)
        assertEquals("Jhon", output.name)
        assertEquals(300, output.cibilRating)
        assertEquals(loanId,output.loanId)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneOutputUsingTheCorrectContract() {
        val eligibilityApprovalFlow = verifyEligibilityApprovalFlow(eligibilityId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(eligibilityApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.outputStates.size.toLong())
        val (_, contract) = signedTransaction.tx.outputs[0]

        assertEquals("com.template.Contract.EligibilityContract", contract)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneCheckEligibilityCommand() {
        val eligibilityApprovalFlow = verifyEligibilityApprovalFlow(eligibilityId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(eligibilityApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.commands.size.toLong())
        val (value) = signedTransaction.tx.commands[0]

        assert(value is EligibilityContract.Commands.GenerateRating)
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneCommandWithTheCreditRatingAgencyAsASigner() {
        val eligibilityApprovalFlow = verifyEligibilityApprovalFlow(eligibilityId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(eligibilityApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.commands.size.toLong())
        val (_, signers) = signedTransaction.tx.commands[0]

        assertEquals(2, signers.size.toLong())
        assert(signers.containsAll(listOf(oracleNode.info.legalIdentities[0].owningKey,nodeC.info.legalIdentities[0].owningKey)))
    }

    @Test
    @Throws(Exception::class)
    fun transactionConstructedByFlowHasOneInputNoAttachmentsOrTimeWindows() {
        val eligibilityApprovalFlow = verifyEligibilityApprovalFlow(eligibilityId)
        val verifyEligibilityApprovalFlowFuture = nodeC.startFlow(eligibilityApprovalFlow)
        network.runNetwork()
        val signedTransaction = verifyEligibilityApprovalFlowFuture.get()

        assertEquals(1, signedTransaction.tx.inputs.size.toLong())
        // The single attachment is the contract attachment.
        assertEquals(1, signedTransaction.tx.attachments.size.toLong())
        assertEquals(null, signedTransaction.tx.timeWindow)
    }
}