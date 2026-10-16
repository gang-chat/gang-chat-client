package com.gangchat.client.shell

import com.gangchat.client.app.CloseBehavior
import com.gangchat.client.app.CloseBehaviorStore
import com.gangchat.client.app.closeBehaviorFromStorageValue
import com.gangchat.client.app.storageValue
import com.gangchat.client.plugins.SharedPreferences

class LocalCloseBehaviorStore : CloseBehaviorStore() {
    override suspend fun read(): CloseBehavior {
        val prefs = SharedPreferences.getInstance()
        return closeBehaviorFromStorageValue(prefs.getString(_closeBehaviorKey))
    }

    override suspend fun write(behavior: CloseBehavior) {
        val prefs = SharedPreferences.getInstance()
        prefs.setString(_closeBehaviorKey, behavior.storageValue)
    }

    private companion object {
        const val _closeBehaviorKey = "gang.closeBehavior"
    }
}
