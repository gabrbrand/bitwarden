package com.x8bit.bitwarden.ui.vault.feature.vault.util

import android.net.Uri
import com.bitwarden.ui.platform.base.util.orNullIfBlank
import com.bitwarden.ui.platform.components.icon.model.IconData
import com.bitwarden.ui.platform.resource.BitwardenDrawable
import com.bitwarden.ui.util.asText
import com.bitwarden.vault.CipherRepromptType
import com.bitwarden.vault.CipherType
import com.bitwarden.vault.CipherView
import com.bitwarden.vault.CollectionView
import com.bitwarden.vault.FolderView
import com.bitwarden.vault.LoginUriView
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.data.vault.repository.model.VaultData
import com.x8bit.bitwarden.ui.vault.feature.util.getFilteredCollections
import com.x8bit.bitwarden.ui.vault.feature.util.getFilteredFolders
import com.x8bit.bitwarden.ui.vault.feature.util.toLabelIcons
import com.x8bit.bitwarden.ui.vault.feature.util.toOverflowActions
import com.x8bit.bitwarden.ui.vault.feature.vault.VaultState
import com.x8bit.bitwarden.ui.vault.feature.vault.model.VaultFilterType
import com.x8bit.bitwarden.ui.vault.model.findVaultCardBrandWithNameOrNull

private const val ANDROID_URI = "androidapp://"
private const val IOS_URI = "iosapp://"

/**
 * The maximum number of no folder items that can be displayed before the UI creates a
 * no folder "folder".
 */
private const val NO_FOLDER_ITEM_THRESHOLD: Int = 100

/**
 * Transforms [VaultData] into [VaultState.ViewState] using the given [vaultFilterType].
 */
@Suppress("LongMethod", "LongParameterList")
fun VaultData.toViewState(
    isPremium: Boolean,
    hasMasterPassword: Boolean,
    isIconLoadingDisabled: Boolean,
    baseIconUrl: String,
    vaultFilterType: VaultFilterType,
    restrictItemTypesPolicyOrgIds: List<String>?,
): VaultState.ViewState {

    val filteredCipherViewListWithDeletedItems =
        cipherViewList
            .applyRestrictItemTypesPolicy(restrictItemTypesPolicyOrgIds ?: emptyList())
            .toFilteredList(vaultFilterType)

    val filteredCipherViewList = filteredCipherViewListWithDeletedItems
        .filter { it.deletedDate == null }

    val filteredFolderViewList = folderViewList
        .toFilteredList(
            cipherList = filteredCipherViewList,
            vaultFilterType = vaultFilterType,
        )
        .getFilteredFolders()

    val filteredCollectionViewList = collectionViewList
        .toFilteredList(vaultFilterType)
        .getFilteredCollections()

    val noFolderItems = filteredCipherViewList
        .filter { it.folderId.isNullOrBlank() }

    val itemTypesCount: Int = CipherType.entries.size

    return if (filteredCipherViewListWithDeletedItems.isEmpty()) {
        VaultState.ViewState.NoItems
    } else {
        val totpItems = filteredCipherViewList.filter { it.login?.totp != null }
        val shouldShowUnGroupedItems = filteredCollectionViewList.isEmpty() &&
            noFolderItems.size < NO_FOLDER_ITEM_THRESHOLD
        val cardCount = filteredCipherViewList.count { it.type == CipherType.CARD }
        VaultState.ViewState.Content(
            itemTypesCount = itemTypesCount,
            totpItemsCount = if (isPremium) {
                totpItems.count()
            } else {
                totpItems.count { it.organizationUseTotp }
            },
            loginItemsCount = filteredCipherViewList.count { it.type == CipherType.LOGIN },
            cardItemsCount = cardCount,
            identityItemsCount = filteredCipherViewList.count { it.type == CipherType.IDENTITY },
            secureNoteItemsCount = filteredCipherViewList
                .count { it.type == CipherType.SECURE_NOTE },
            sshKeyItemsCount = filteredCipherViewList.count { it.type == CipherType.SSH_KEY },
            favoriteItems = filteredCipherViewList
                .filter { it.favorite }
                .mapNotNull {
                    it.toVaultItemOrNull(
                        hasMasterPassword = hasMasterPassword,
                        isIconLoadingDisabled = isIconLoadingDisabled,
                        baseIconUrl = baseIconUrl,
                        isPremiumUser = isPremium,
                    )
                },
            folderItems = filteredFolderViewList
                .map { folderView ->
                    VaultState.ViewState.FolderItem(
                        id = folderView.id,
                        name = folderView.name.asText(),
                        itemCount = filteredCipherViewList
                            .count {
                                !it.id.isNullOrBlank() &&
                                    folderView.id == it.folderId
                            },
                    )
                }
                .let { folderItems ->
                    if (shouldShowUnGroupedItems) {
                        folderItems
                    } else {
                        folderItems.plus(
                            VaultState.ViewState.FolderItem(
                                id = null,
                                name = R.string.folder_none.asText(),
                                itemCount = noFolderItems.size,
                            ),
                        )
                    }
                },
            noFolderItems = noFolderItems
                .mapNotNull {
                    it.toVaultItemOrNull(
                        hasMasterPassword = hasMasterPassword,
                        isIconLoadingDisabled = isIconLoadingDisabled,
                        baseIconUrl = baseIconUrl,
                        isPremiumUser = isPremium,
                    )
                }
                .takeIf { shouldShowUnGroupedItems }
                .orEmpty(),
            collectionItems = filteredCollectionViewList
                .filter { it.id != null }
                .map { collectionView ->
                    VaultState.ViewState.CollectionItem(
                        id = requireNotNull(collectionView.id),
                        name = collectionView.name,
                        itemCount = filteredCipherViewList
                            .count {
                                !it.id.isNullOrBlank() &&
                                    collectionView.id in it.collectionIds
                            },
                    )
                },
            trashItemsCount = filteredCipherViewListWithDeletedItems.count {
                it.deletedDate != null
            },
            showCardGroup = cardCount != 0 || restrictItemTypesPolicyOrgIds == null,
        )
    }
}

/**
 * Method to build the icon data for login item icons.
 */
fun List<LoginUriView>?.toLoginIconData(
    isIconLoadingDisabled: Boolean,
    baseIconUrl: String,
    usePasskeyDefaultIcon: Boolean,
): IconData {
    val defaultIconRes = if (usePasskeyDefaultIcon) {
        BitwardenDrawable.ic_bw_passkey
    } else {
        BitwardenDrawable.ic_globe
    }

    var uri = this
        ?.map { it.uri }
        ?.firstOrNull { uri -> uri?.contains(".") == true }
        ?: return IconData.Local(defaultIconRes)

    if (uri.startsWith(ANDROID_URI)) {
        return IconData.Local(BitwardenDrawable.ic_android)
    }

    if (uri.startsWith(IOS_URI)) {
        return IconData.Local(BitwardenDrawable.ic_ios)
    }

    if (isIconLoadingDisabled) {
        return IconData.Local(defaultIconRes)
    }

    if (!uri.contains("://")) {
        uri = "http://$uri"
    }

    val iconUri = Uri.parse(uri)
    val hostname = iconUri.host

    val url = "$baseIconUrl/$hostname/icon.png"

    return IconData.Network(
        uri = url,
        fallbackIconRes = defaultIconRes,
    )
}

/**
 * Transforms a [CipherView] into a [VaultState.ViewState.VaultItem].
 */
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
private fun CipherView.toVaultItemOrNull(
    hasMasterPassword: Boolean,
    isIconLoadingDisabled: Boolean,
    baseIconUrl: String,
    isPremiumUser: Boolean,
): VaultState.ViewState.VaultItem? {
    val id = this.id ?: return null
    return when (type) {
        CipherType.LOGIN -> VaultState.ViewState.VaultItem.Login(
            id = id,
            name = name.asText(),
            username = login?.username?.asText(),
            startIcon = login?.uris.toLoginIconData(
                isIconLoadingDisabled = isIconLoadingDisabled,
                baseIconUrl = baseIconUrl,
                usePasskeyDefaultIcon = false,
            ),
            overflowOptions = toOverflowActions(
                hasMasterPassword = hasMasterPassword,
                isPremiumUser = isPremiumUser,
            ),
            extraIconList = toLabelIcons(),
            shouldShowMasterPasswordReprompt = hasMasterPassword &&
                reprompt == CipherRepromptType.PASSWORD,
        )

        CipherType.SECURE_NOTE -> VaultState.ViewState.VaultItem.SecureNote(
            id = id,
            name = name.asText(),
            overflowOptions = toOverflowActions(
                hasMasterPassword = hasMasterPassword,
                isPremiumUser = isPremiumUser,
            ),
            extraIconList = toLabelIcons(),
            shouldShowMasterPasswordReprompt = hasMasterPassword &&
                reprompt == CipherRepromptType.PASSWORD,
        )

        CipherType.CARD -> VaultState.ViewState.VaultItem.Card(
            id = id,
            name = name.asText(),
            brand = card?.brand?.findVaultCardBrandWithNameOrNull(),
            lastFourDigits = card?.number
                ?.takeLast(4)
                ?.asText(),
            overflowOptions = toOverflowActions(
                hasMasterPassword = hasMasterPassword,
                isPremiumUser = isPremiumUser,
            ),
            extraIconList = toLabelIcons(),
            shouldShowMasterPasswordReprompt = hasMasterPassword &&
                reprompt == CipherRepromptType.PASSWORD,
        )

        CipherType.IDENTITY -> VaultState.ViewState.VaultItem.Identity(
            id = id,
            name = name.asText(),
            fullName = when {
                identity?.firstName.isNullOrBlank() -> identity?.lastName?.orNullIfBlank()
                identity?.lastName.isNullOrBlank() -> identity?.firstName
                else -> "${identity?.firstName} ${identity?.lastName}"
            }
                ?.asText(),
            overflowOptions = toOverflowActions(
                hasMasterPassword = hasMasterPassword,
                isPremiumUser = isPremiumUser,
            ),
            extraIconList = toLabelIcons(),
            shouldShowMasterPasswordReprompt = hasMasterPassword &&
                reprompt == CipherRepromptType.PASSWORD,
        )

        CipherType.SSH_KEY -> VaultState.ViewState.VaultItem.SshKey(
            id = id,
            name = name.asText(),
            publicKey = sshKey
                ?.publicKey
                .orEmpty()
                .asText(),
            privateKey = sshKey
                ?.privateKey
                .orEmpty()
                .asText(),
            fingerprint = sshKey
                ?.fingerprint
                .orEmpty()
                .asText(),
            overflowOptions = toOverflowActions(
                hasMasterPassword = hasMasterPassword,
                isPremiumUser = isPremiumUser,
            ),
            extraIconList = toLabelIcons(),
            shouldShowMasterPasswordReprompt = hasMasterPassword &&
                reprompt == CipherRepromptType.PASSWORD,
        )
    }
}

/**
 * Filters out all [CipherView]s that are not part of the given [VaultFilterType].
 */
@JvmName("toFilteredCipherList")
fun List<CipherView>.toFilteredList(
    vaultFilterType: VaultFilterType,
): List<CipherView> =
    this
        // Filter out any items with invalid IDs in the unlikely case they exist
        .filterNot { it.id.isNullOrBlank() }
        .filter {
            when (vaultFilterType) {
                VaultFilterType.AllVaults -> true
                VaultFilterType.MyVault -> it.organizationId == null
                is VaultFilterType.OrganizationVault -> {
                    it.organizationId == vaultFilterType.organizationId
                }
            }
        }

/**
 * Filters out all [FolderView]s that are not part of the given [VaultFilterType].
 */
@JvmName("toFilteredFolderList")
fun List<FolderView>.toFilteredList(
    cipherList: List<CipherView>,
    vaultFilterType: VaultFilterType,
): List<FolderView> =
    this
        .filter { folder ->
            when (vaultFilterType) {
                VaultFilterType.AllVaults,
                VaultFilterType.MyVault,
                    -> true

                // Only include folders containing an item associated with this organization.
                is VaultFilterType.OrganizationVault -> {
                    cipherList.any { it.folderId == folder.id }
                }
            }
        }

/**
 * Filters out all [CollectionView]s that are not part of the given [VaultFilterType].
 */
@JvmName("toFilteredCollectionList")
fun List<CollectionView>.toFilteredList(
    vaultFilterType: VaultFilterType,
): List<CollectionView> =
    this
        .filter {
            when (vaultFilterType) {
                VaultFilterType.AllVaults -> true
                VaultFilterType.MyVault -> false
                is VaultFilterType.OrganizationVault -> {
                    it.organizationId == vaultFilterType.organizationId
                }
            }
        }

/**
 * Filters out [CipherType.CARD] [CipherView]s that are in [restrictItemTypesPolicyOrgIds] list.
 * When [restrictItemTypesPolicyOrgIds] is not empty, individual vault items are also removed.
 */
fun List<CipherView>.applyRestrictItemTypesPolicy(
    restrictItemTypesPolicyOrgIds: List<String>,
): List<CipherView> =
    this
        .filterNot { cipherView ->
            if (restrictItemTypesPolicyOrgIds.isEmpty()) {
                // No policy, so don't apply removal
                false
            } else if (cipherView.type != CipherType.CARD) {
                // Policy only for cards
                false
            } else {
                // If a policy is enable for a given organization then
                // also hide cards from individual vault
                cipherView.organizationId.isNullOrEmpty() ||
                    restrictItemTypesPolicyOrgIds.contains(cipherView.organizationId)
            }
        }
