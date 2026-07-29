package com.splitcruiser.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Message.kind] and the pickup accessors.
 *
 * The chat screen used to decide what a message was by calling `text.startsWith("[PROPOSAL]")`,
 * and recover the spot and time with `substringAfter`. Two things that guards:
 * a user typing a message that happens to start with that literal, and conversations written
 * before the `type` field existed, which still have to render as proposals rather than as raw
 * bracket text.
 */
class MessageTypeTest {

    @Test
    fun plainMessageIsText() {
        assertEquals(MessageType.TEXT, Message(text = "on my way").kind)
    }

    @Test
    fun typedProposalCarriesItsOwnSpotAndTime() {
        val message = Message(
            text = "Pickup proposal: Main entrance at 8:15 am",
            type = MessageType.PICKUP_PROPOSAL,
            pickupSpot = "Main entrance",
            pickupTime = "8:15 am",
        )

        assertEquals(MessageType.PICKUP_PROPOSAL, message.kind)
        assertEquals("Main entrance", message.spot)
        assertEquals("8:15 am", message.time)
    }

    @Test
    fun typedConfirmationCarriesItsOwnSpotAndTime() {
        val message = Message(
            text = "Confirmed: meet at Gate B at 9:00 am",
            type = MessageType.PICKUP_CONFIRMED,
            pickupSpot = "Gate B",
            pickupTime = "9:00 am",
        )

        assertEquals(MessageType.PICKUP_CONFIRMED, message.kind)
        assertEquals("Gate B", message.spot)
        assertEquals("9:00 am", message.time)
    }

    @Test
    fun messageWrittenBeforeTheTypeFieldExistedStillRendersAsAProposal() {
        val legacy = Message(text = "[PROPOSAL] Location: Snell steps | Time: 7:45 am", type = "")

        assertEquals(MessageType.PICKUP_PROPOSAL, legacy.kind)
        assertEquals("Snell steps", legacy.spot)
        assertEquals("7:45 am", legacy.time)
    }

    @Test
    fun legacyConfirmationStillRendersAsAConfirmation() {
        val legacy = Message(text = "[CONFIRMED] Meet at Snell steps at 7:45 am", type = "")

        assertEquals(MessageType.PICKUP_CONFIRMED, legacy.kind)
        assertEquals("Snell steps", legacy.spot)
        assertEquals("7:45 am", legacy.time)
    }

    @Test
    fun userTypingTheLiteralPrefixIsStillJustText() {
        // The whole point of the field: a typed message is text whatever it says. Only a message
        // with no type at all falls back to prefix-sniffing.
        val typed = Message(text = "[PROPOSAL] is a weird thing to type", type = MessageType.TEXT)

        assertEquals(MessageType.TEXT, typed.kind)
        assertEquals("", typed.spot)
    }

    @Test
    fun anUnknownTypeFromANewerClientDegradesToText() {
        val future = Message(text = "something new", type = "ride_receipt")

        assertEquals(MessageType.TEXT, future.kind)
    }
}
