package com.gangchat.client.app

fun matchesConfirmationText(input: String, expectedText: String): Boolean {
    return input.trim() == expectedText
}
