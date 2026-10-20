package com.mvp.core.securestorage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Une entrée Keychain « mot de passe générique » par clé, sous le service de l'app.
 * Accessible après le premier déverrouillage, liée à l'appareil : jamais dans les sauvegardes
 * iCloud / Finder ni transférée vers un autre iPhone.
 *
 * Le Keychain survit à la désinstallation de l'app : au premier lancement d'une installation,
 * les entrées laissées par la précédente sont effacées (session et profil d'un ancien utilisateur).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("CAST_NEVER_SUCCEEDS") // casts String <-> NSString, valides grâce au pont Kotlin / Objective-C
internal class KeychainSecureStorage(private val service: String = SERVICE) : SecureStorage {

    init {
        wipeLeftoversFromPreviousInstall()
    }

    override fun get(key: String): String? = cf {
        val query = dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to retain(service),
            kSecAttrAccount to retain(key),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)
                    // NSString et String Kotlin sont interchangeables sur iOS (pont automatique).
                    ?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) as String? }
                errSecItemNotFound -> null
                else -> error("Lecture Keychain impossible (OSStatus $status)")
            }
        }
    }

    override fun put(key: String, value: String) = cf {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Encodage de la valeur impossible")
        val serviceRef = retain(service)
        val accountRef = retain(key)
        val dataRef = retain(data)
        val query = dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceRef,
            kSecAttrAccount to accountRef,
        )
        val changes = dictionary(
            kSecValueData to dataRef,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        when (val status = SecItemUpdate(query, changes)) {
            errSecSuccess -> Unit
            errSecItemNotFound -> {
                val attributes = dictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to serviceRef,
                    kSecAttrAccount to accountRef,
                    kSecValueData to dataRef,
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                )
                val added = SecItemAdd(attributes, null)
                check(added == errSecSuccess) { "Écriture Keychain impossible (OSStatus $added)" }
            }
            else -> error("Écriture Keychain impossible (OSStatus $status)")
        }
    }

    override fun remove(key: String) = cf {
        val status = SecItemDelete(
            dictionary(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to retain(service),
                kSecAttrAccount to retain(key),
            ),
        )
        check(status == errSecSuccess || status == errSecItemNotFound) { "Suppression Keychain impossible (OSStatus $status)" }
    }

    private fun wipeLeftoversFromPreviousInstall() {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.boolForKey(INSTALLED_FLAG)) return
        val status = cf { SecItemDelete(dictionary(kSecClass to kSecClassGenericPassword, kSecAttrService to retain(service))) }
        // Keychain pas encore disponible : on réessaiera au prochain lancement.
        if (status == errSecSuccess || status == errSecItemNotFound) defaults.setBool(true, forKey = INSTALLED_FLAG)
    }

    private companion object {
        const val SERVICE = "com.mvp.securestorage"
        // Simple indicateur « déjà lancé », sans donnée personnelle : UserDefaults suffit.
        const val INSTALLED_FLAG = "com.mvp.securestorage.installed"
    }
}

/** Objets Core Foundation créés pour une requête Keychain, tous libérés à la fin de [cf]. */
@OptIn(ExperimentalForeignApi::class)
private class CfScope {
    private val refs = mutableListOf<CFTypeRef>()

    fun retain(value: Any): CFTypeRef? = CFBridgingRetain(value)?.also { refs += it }

    // Sans callbacks de rétention : les valeurs restent vivantes jusqu'à la fin du scope.
    fun dictionary(vararg entries: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dictionary = CFDictionaryCreateMutable(null, entries.size.convert(), null, null)
        entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
        dictionary?.let { refs += it }
        return dictionary
    }

    fun releaseAll() = refs.asReversed().forEach { CFRelease(it) }
}

private fun <T> cf(block: CfScope.() -> T): T {
    val scope = CfScope()
    try {
        return scope.block()
    } finally {
        scope.releaseAll()
    }
}
