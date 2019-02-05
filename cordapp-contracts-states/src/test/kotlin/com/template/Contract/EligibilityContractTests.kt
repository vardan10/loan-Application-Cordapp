package com.template.Contract

import com.template.State.EligibilityState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class EligibilityContractTests {
    private val alice = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val bob = TestIdentity(CordaX500Name("Bob", "", "GB"))
    private val ledgerServices = MockServices(TestIdentity(CordaX500Name("TestId", "", "GB")))
    private val eligibilityStatecheck = EligibilityState("Jack",alice.party, bob.party, null, UniqueIdentifier())
    private val eligibilityStateapproval = EligibilityState("Jack",alice.party, bob.party, 600, UniqueIdentifier())

    @Test
    fun eligibilityContractImplementsContract() {
        assert((EligibilityContract() is Contract))
    }
    @Test
    fun eligibilityContractRequiresZeroInputsInTheTransactionForCheckEligibility() {
        ledgerServices.ledger {
            transaction {
                // Has an input, will fail.
                input(EligibilityContract.ID, eligibilityStatecheck)
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                fails()
            }
            transaction{
                // Has no input, will verify.
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                verifies()
            }
        }
    }
    @Test
    fun eligibilityContractRequiresOneInputInTheTransactionForEligibilityApproval() {
        ledgerServices.ledger {
            transaction {
                // Has an input, will fail.
                input(EligibilityContract.ID, eligibilityStateapproval)
                output(EligibilityContract.ID, eligibilityStateapproval)
                command(listOf(alice.publicKey,bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                verifies()
            }
            transaction{
                // Has no input, will verify.
                output(EligibilityContract.ID, eligibilityStateapproval)
                command(listOf(alice.publicKey,bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                fails()
            }
        }
    }
    @Test
    fun EligibilityContractRequiresOneOutputInTheTransaction() {
        ledgerServices.ledger {
            transaction {
                // Has two outputs, will fail.
                output(EligibilityContract.ID, eligibilityStatecheck)
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                fails()
            }
            transaction {
                // Has one output, will verify.
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                verifies()
            }
            transaction {
                // Has two outputs, will fail.
                output(EligibilityContract.ID, eligibilityStateapproval)
                output(EligibilityContract.ID, eligibilityStateapproval)
                command(listOf(alice.publicKey,bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                fails()
            }
            transaction {
                // Has one output, will verify.
                input(EligibilityContract.ID, eligibilityStatecheck)
                output(EligibilityContract.ID, eligibilityStateapproval)
                command(listOf(alice.publicKey,bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                verifies()
            }
        }
    }
    @Test
    fun EligibilityContractRequiresOneCommandInTheTransaction() {

        ledgerServices.ledger {
            transaction {
                output(EligibilityContract.ID, eligibilityStateapproval)
                // Has two commands, will fail.
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                command(listOf(alice.publicKey,bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                fails()
            }
            transaction {
                input(EligibilityContract.ID, eligibilityStatecheck)
                output(EligibilityContract.ID, eligibilityStateapproval)
                // Has one command, will verify.
                command(listOf(alice.publicKey,bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                verifies()
            }
            transaction {
                output(EligibilityContract.ID, eligibilityStatecheck)
                // Has one command, will verify.
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                verifies()
            }
        }
    }
    @Test
    fun EligibilityContractRequiresTheTransactionsOutputToBeAEligibilityState() {
        ledgerServices.ledger {
            transaction {
                // Has wrong output type, will fail.
                output(EligibilityContract.ID, DummyState())
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                fails()
            }
            transaction {
                // Has correct output type, will verify.
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                verifies()
            }
        }
    }
    @Test
    fun EligibilityContractRequiresTheTransactionsCommandToBeAnCheckEligibilityOrEligibilityApprovalCommand() {
        ledgerServices.ledger {
            transaction {
                // Has wrong command type, will fail.
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, DummyCommandData)
                fails()
            }
            transaction {
                // Has correct command type, will verify.
                output(EligibilityContract.ID, eligibilityStatecheck)
                command(alice.publicKey, EligibilityContract.Commands.CheckEligibility())
                verifies()

            }
            transaction {
                // Has correct command type, will verify.
                input(EligibilityContract.ID, eligibilityStatecheck)
                output(EligibilityContract.ID, eligibilityStateapproval)
                command(listOf(alice.publicKey, bob.publicKey), EligibilityContract.Commands.EligibilityApproval())
                verifies()

            }
        }
    }
}