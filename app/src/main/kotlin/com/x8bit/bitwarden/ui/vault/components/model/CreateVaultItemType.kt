package com.x8bit.bitwarden.ui.vault.components.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bitwarden.ui.platform.resource.BitwardenDrawable
import com.bitwarden.ui.platform.resource.BitwardenString

/**
 * Enumerated values to represent a create vault item option.
 */
enum class CreateVaultItemType(
    @field:StringRes val selectionText: Int,
    @field:DrawableRes val icon: Int,
) {
    /**
     * A login cipher.
     */
    LOGIN(selectionText = BitwardenString.log_in_noun, icon = BitwardenDrawable.ic_globe),

    /**
     * A card cipher.
     */
    CARD(selectionText = BitwardenString.type_card, icon = BitwardenDrawable.ic_payment_card),

    /**
     * An identity cipher.
     */
    IDENTITY(selectionText = BitwardenString.type_identity, icon = BitwardenDrawable.ic_id_card),

    /**
     * A secure note cipher.
     */
    SECURE_NOTE(selectionText = BitwardenString.type_secure_note, icon = BitwardenDrawable.ic_note),

    /**
     * An SSH key cipher.
     */
    SSH_KEY(selectionText = BitwardenString.type_ssh_key, icon = BitwardenDrawable.ic_ssh_key),

    /**
     * A cipher item folder
     */
    FOLDER(selectionText = BitwardenString.folder, icon = BitwardenDrawable.ic_folder),
}
