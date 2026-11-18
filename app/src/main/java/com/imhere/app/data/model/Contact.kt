package com.imhere.app.data.model

import kotlinx.serialization.Serializable

/**
 * A saved recipient. The phone number is always stored in E.164 format.
 * Only one number per contact is supported in the first version.
 */
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val phoneE164: String,
    val isSos: Boolean = false,
)
