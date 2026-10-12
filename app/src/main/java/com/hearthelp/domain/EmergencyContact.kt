package com.hearthelp.domain

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(val name: String, val number: String)

object EmergencyContacts {
    const val MAX = 5

    fun add(list: List<EmergencyContact>, contact: EmergencyContact): List<EmergencyContact> {
        val number = contact.number.trim()
        require(list.size < MAX) { "You can add up to $MAX contacts" }
        require(PhoneNumbers.digits(number).length >= 7 && number.all { it.isDigit() || it in "+-() " }) {
            "Enter a valid phone number"
        }
        require(list.none { PhoneNumbers.same(it.number, number) }) { "That number is already in your list" }
        return list + EmergencyContact(contact.name.trim().ifBlank { number }, number)
    }

    fun remove(list: List<EmergencyContact>, index: Int): List<EmergencyContact> =
        list.filterIndexed { i, _ -> i != index }

    fun moveUp(list: List<EmergencyContact>, index: Int): List<EmergencyContact> {
        if (index <= 0 || index >= list.size) return list
        return list.toMutableList().apply { add(index - 1, removeAt(index)) }
    }
}
