package com.template.flows

import com.template.Contract.EligibilityContract
import com.template.State.EligibilityState
import com.template.service.Oracle
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Rule
import org.junit.Test
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifyCreditServices {
    private val oracleIdentity = TestIdentity(CordaX500Name("Oracle", "New York", "US"))
    private val dummyServices = MockServices(listOf("com.template.flows","com.template.Contract"), oracleIdentity)
    private val oracle = Oracle(dummyServices)
    private val PartyB = TestIdentity(CordaX500Name("PartyB", "", "GB"))
    private val PartyC = TestIdentity(CordaX500Name("PartyC", "", "GB"))
    private val notaryIdentity = TestIdentity(CordaX500Name("Notary", "", "GB"))

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `oracle returns correct credit rating`() {
        assertEquals(600, oracle.query("FLFPK1672D"))
        assertEquals(500, oracle.query("ABCDE1234F"))
        assertEquals(300, oracle.query("APKDG9999F"))
    }

    @Test
    fun `oracle rejects invalid values of PanCardNo`() {
        assertFailsWith<IllegalArgumentException> { oracle.query("VARDHANPAN") }
        assertFailsWith<IllegalArgumentException> { oracle.query("IAMNOTTENDIGIT") }
        assertFailsWith<IllegalArgumentException> { oracle.query("FLFPK167DD") }

    }

    @Test
    fun `oracle signs transactions including a Credit Rating`() {
        val command = Command(EligibilityContract.Commands.GenerateRating("FLFPK1672D", 600), listOf(oracleIdentity.publicKey))
        val state = EligibilityState("prasanna",PartyB.party,"FLFPK1672D",PartyC.party,600, UniqueIdentifier(), UniqueIdentifier())
        val stateAndContract = StateAndContract(state,EligibilityContract.ID)
        val ftx = TransactionBuilder(notaryIdentity.party)
                .withItems(stateAndContract, command)
                .toWireTransaction(dummyServices)
                .buildFilteredTransaction(Predicate {
                    when (it) {
                        is Command<*> -> oracle.services.myInfo.legalIdentities.first().owningKey in it.signers && it.value is EligibilityContract.Commands.GenerateRating
                        else -> false
                    }
                })
        val signature = oracle.sign(ftx)
        assert(signature.verify(ftx.id))
    }

    @Test
    fun `oracle does not sign transactions including an incorrect Credit Rating`() {
        val command = Command(EligibilityContract.Commands.GenerateRating("FLFPK1672D", 609), listOf(oracleIdentity.publicKey))
        val state = EligibilityState("prasanna",PartyB.party,"FLFPK1672D",PartyC.party,600, UniqueIdentifier(), UniqueIdentifier())
        val stateAndContract = StateAndContract(state,EligibilityContract.ID)
        val ftx = TransactionBuilder(notaryIdentity.party)
                .withItems(stateAndContract, command)
                .toWireTransaction(oracle.services)
                .buildFilteredTransaction(Predicate {
                    when (it) {
                        is Command<*> -> oracle.services.myInfo.legalIdentities.first().owningKey in it.signers && it.value is EligibilityContract.Commands.GenerateRating
                        else -> false
                    }
                })
        assertFailsWith<IllegalArgumentException> { oracle.sign(ftx) }
    }
}