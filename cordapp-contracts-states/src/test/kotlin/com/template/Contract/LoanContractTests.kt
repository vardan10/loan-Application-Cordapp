package com.template.Contract

import com.template.State.LoanState
import net.corda.core.contracts.Contract
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class TokenContractTests {
    private val alice = TestIdentity(CordaX500Name("Alice", "", "GB"))
    private val bob = TestIdentity(CordaX500Name("Bob", "", "GB"))
    private val ledgerServices = MockServices(TestIdentity(CordaX500Name("TestId", "", "GB")))
    private val loanStateforLoanRequest = LoanState("Jack",5, "PANCARD", alice.party, bob.party, null, null)
    private val loanStateforLoanApproval = LoanState("Jack",5, "PANCARD", alice.party, bob.party, 400, null)

    @Test
    fun loanContractImplementsContract() {
        assert((LoanContract() is Contract))
    }
    @Test
    fun loanContractRequiresZeroInputsInTheTransaction() {
        ledgerServices.ledger {
            transaction {
                // Has an input, will fail.
                input(LoanContract.ID, loanStateforLoanRequest)
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                fails()
            }
            transaction{
                // Has no input, will verify.
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                verifies()
            }
        }
    }
    @Test
    fun loanContractRequiresOneOutputInTheTransaction() {
        ledgerServices.ledger {
            transaction {
                // Has two outputs, will fail.
                output(LoanContract.ID, loanStateforLoanRequest)
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                fails()
            }
            transaction {
                // Has one output, will verify.
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                verifies()
            }
        }
    }
    @Test
    fun loanContractRequiresOneCommandInTheTransaction() {

        ledgerServices.ledger {
            transaction {
                output(LoanContract.ID, loanStateforLoanRequest)
                // Has two commands, will fail.
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                fails()
            }
            transaction {
                output(LoanContract.ID, loanStateforLoanRequest)
                // Has one command, will verify.
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                verifies()
            }
        }
    }
    @Test
    fun loanContractRequiresTheTransactionsOutputToBeALoanState() {
        ledgerServices.ledger {
            transaction {
                // Has wrong output type, will fail.
                output(LoanContract.ID, DummyState())
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                fails()
            }
            transaction {
                // Has correct output type, will verify.
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                verifies()
            }
        }
    }
    @Test
    fun loanContractRequiresTheTransactionsOutputToHaveAPositiveAmount() {
        val zeroTokenState = LoanState("Jack",-1, "PANCARD", alice.party, bob.party, null, null)
        val negativeTokenState = LoanState("Jack",0, "PANCARD", alice.party, bob.party, null, null)
        val positiveTokenState = LoanState("Jack",6, "PANCARD", alice.party, bob.party, null, null)

        ledgerServices.ledger {
            transaction {
                // Has zero-amount TokenState, will fail.
                output(LoanContract.ID, zeroTokenState)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                fails()
            }
            transaction {
                // Has negative-amount TokenState, will fail.
                output(LoanContract.ID, negativeTokenState)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                fails()
            }
            transaction {
                // Also has positive-amount TokenState, will verify.
                output(LoanContract.ID, positiveTokenState)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                verifies()
            }
        }
    }
    @Test
    fun loanContractRequiresTheTransactionsCommandToBeAnLoanRequestOrLoanApprovalCommand() {
        ledgerServices.ledger {
            transaction {
                // Has wrong command type, will fail.
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), DummyCommandData)
                fails()
            }
            transaction {
                // Has correct command type, will verify.
                output(LoanContract.ID, loanStateforLoanRequest)
                command(listOf(alice.publicKey,bob.publicKey), LoanContract.Commands.LoanRequest())
                verifies()

            }
            transaction {
                // Has correct command type, will verify.
                input(LoanContract.ID, loanStateforLoanApproval)
                output(LoanContract.ID, loanStateforLoanApproval)
                command(listOf(alice.publicKey, bob.publicKey), LoanContract.Commands.LoanApproval())
                verifies()

            }
        }
    }
}