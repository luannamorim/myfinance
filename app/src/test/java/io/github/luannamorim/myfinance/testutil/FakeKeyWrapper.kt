package io.github.luannamorim.myfinance.testutil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.luannamorim.myfinance.data.backup.BackupKeyStore
import io.github.luannamorim.myfinance.data.backup.KeyWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files

/** XOR "wrapping" that can be broken on demand to simulate a lost Keystore key. */
class FakeKeyWrapper : KeyWrapper {
    var broken = false
    override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (broken) throw IllegalStateException("keystore key lost")
        return wrapped.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

fun testKeyStore(wrapper: FakeKeyWrapper = FakeKeyWrapper()): BackupKeyStore {
    val dir = Files.createTempDirectory("backupkey").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    return BackupKeyStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "backup_key.preferences_pb") }, wrapper)
}

const val TEST_ITERATIONS = 1_000
