package io.github.luannamorim.myfinance.domain.backup

import com.google.crypto.tink.subtle.Hkdf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {
    private val iterations = 1_000 // fast for tests
    private val salt = Random(1).nextBytes(16)
    private val key = KeyDerivation.derive("senha forte".toCharArray(), salt, iterations)
    private val header = BackupHeader(iterations = iterations, salt = salt, keyCheck = KeyDerivation.keyCheck(key), createdAtMs = 1_731_400_000_000)
    private val plain = Random(2).nextBytes(3 * 1024 * 1024 + 123)

    private fun encrypted(): ByteArray = ByteArrayOutputStream().also { BackupCrypto.encrypt(key, header, ByteArrayInputStream(plain), it) }.toByteArray()

    private fun decrypt(bytes: ByteArray, password: String = "senha forte"): ByteArray {
        val input = ByteArrayInputStream(bytes)
        val h = BackupHeader.read(input)
        val k = KeyDerivation.derive(password.toCharArray(), h.salt, h.iterations)
        return ByteArrayOutputStream().also { BackupCrypto.decrypt(k, h, input, it) }.toByteArray()
    }

    private inline fun <reified T : BackupError> assertFails(block: () -> Unit) {
        try {
            block()
            fail("expected ${T::class.simpleName}")
        } catch (e: BackupError) {
            assertTrue("got $e", e is T)
        }
    }

    @Test
    fun roundTrip_3MB() {
        assertArrayEquals(plain, decrypt(encrypted()))
    }

    @Test
    fun header_readBack() {
        val h = BackupHeader.read(ByteArrayInputStream(encrypted()))
        assertEquals(header, h)
        assertEquals(BackupHeader.SIZE, h.bytes().size)
    }

    @Test
    fun header_isMyFinanceFormat_withKcvFromItsOwnInfo() {
        val bytes = encrypted()
        assertEquals("MYFINBAK", String(bytes, 0, 8, Charsets.US_ASCII))
        assertEquals(55, BackupHeader.SIZE)
        val expected = Hkdf.computeHkdf("HMACSHA256", key, ByteArray(0), "myfinance-kcv".toByteArray(), 16)
        assertArrayEquals(expected, KeyDerivation.keyCheck(key))
    }

    @Test
    fun wrongPassword_detectedByKcv() = assertFails<BackupError.WrongPassword> { decrypt(encrypted(), "outra senha") }

    @Test
    fun normalizedPassword_unicodeForms_match() {
        val composed = KeyDerivation.derive("ação".toCharArray(), salt, iterations)
        val decomposed = KeyDerivation.derive("ação".toCharArray(), salt, iterations)
        assertArrayEquals(composed, decomposed)
    }

    @Test
    fun flippedCiphertextByte_corrupted() = assertFails<BackupError.Corrupted> {
        val bytes = encrypted()
        bytes[BackupHeader.SIZE + 2_000_000] = (bytes[BackupHeader.SIZE + 2_000_000].toInt() xor 1).toByte()
        decrypt(bytes)
    }

    @Test
    fun tamperedHeaderCreatedAt_corrupted() = assertFails<BackupError.Corrupted> {
        val bytes = encrypted()
        bytes[BackupHeader.SIZE - 1] = (bytes[BackupHeader.SIZE - 1].toInt() xor 1).toByte()
        decrypt(bytes)
    }

    @Test
    fun truncatedFile_corrupted() = assertFails<BackupError.Corrupted> {
        val bytes = encrypted()
        decrypt(bytes.copyOf(bytes.size - 100))
    }

    @Test
    fun badMagic_corrupted() = assertFails<BackupError.Corrupted> {
        val bytes = encrypted()
        bytes[0] = 'X'.code.toByte()
        decrypt(bytes)
    }

    @Test
    fun headerVersion2_unsupported() = assertFails<BackupError.UnsupportedVersion> {
        val bytes = encrypted()
        bytes[9] = 2 // low byte of the u16 version
        decrypt(bytes)
    }
}
