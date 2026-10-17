package com.gangchat.client.ui

import android.widget.Magnifier
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric cannot create the platform magnifier's surface, so showing or
 * dismissing it (Compose text fields do while selecting text) throws. Tests
 * that select text install this no-op shadow.
 */
@Implements(Magnifier::class, minSdk = 28)
class ShadowNoOpMagnifier {
    @Implementation
    protected fun show(sourceCenterX: Float, sourceCenterY: Float) = Unit

    @Implementation
    protected fun show(sourceCenterX: Float, sourceCenterY: Float, magnifierCenterX: Float, magnifierCenterY: Float) = Unit

    @Implementation
    protected fun dismiss() = Unit

    @Implementation
    protected fun update() = Unit
}
