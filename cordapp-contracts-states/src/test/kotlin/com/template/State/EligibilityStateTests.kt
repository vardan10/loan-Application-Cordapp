package com.template.State

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class EligibilityStateTests{
    val alice = TestIdentity(CordaX500Name("Alice", "", "GB")).party
    val bob = TestIdentity(CordaX500Name("Bob", "", "GB")).party

    @Test
    fun eligibilityStateHasParamsOfCorrectTypeInConstructor() {
        EligibilityState("Jack",alice, bob, null, UniqueIdentifier())
    }

    @Test
    fun eligibilityStateHasGettersForIssuerOwnerAndAmount() {
        var eligibilityState = EligibilityState("Jack",alice, bob, null, UniqueIdentifier())
        assertEquals(alice, eligibilityState.bank)
        assertEquals(bob, eligibilityState.creditRatingAgency)
        assertEquals("Jack", eligibilityState.name)
    }

    @Test
    fun eligibilityStateImplementsContractState() {
        assert(EligibilityState("Jack",alice, bob, null, UniqueIdentifier()) is ContractState)
    }

    @Test
    fun eligibilityStateHasTwoParticipantsTheTheBankAndCreditRatingAgency() {
        var eligibilityState = EligibilityState("Jack",alice, bob, null, UniqueIdentifier())
        assertEquals(2, eligibilityState.participants.size)
        assert(eligibilityState.participants.contains(alice))
        assert(eligibilityState.participants.contains(bob))
    }
}