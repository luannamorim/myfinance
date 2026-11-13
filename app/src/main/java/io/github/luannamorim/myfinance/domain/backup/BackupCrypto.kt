package io.github.luannamorim.myfinance.domain.backup

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.util.SecretBytes
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class BackupError(message: String) : Exception(message) {
    class WrongPassword : BackupError("wrong password")
    class Corrupted(reason: String) : BackupError("corrupted backup: $reason")
    class UnsupportedVersion(val found: Int) : BackupError("unsupported backup version $found")
    class NoPassword : BackupError("backup password not set")
    class FolderUnavailable(reason: String) : BackupError("backup folder unavailable: $reason")
}

/**
 * Plain header written before the ciphertext (and authenticated as associated data):
 * "MYFINBAK" | u16 version | u8 kdf | u32 iterations | 16 B salt | 16 B key-check | u64 createdAtMs.
 */
data class BackupHeader(
    val version: Int = VERSION,
    val kdf: Int = KDF_PBKDF2_SHA256,
    val iterations: Int,
    val salt: ByteArray,
    val keyCheck: ByteArray,
    val createdAtMs: Long,
) {
    fun bytes(): ByteArray {
        val buffer = ByteArrayOutputStream(SIZE)
        DataOutputStream(buffer).use { out ->
            out.write(MAGIC)
            out.writeShort(version)
            out.writeByte(kdf)
            out.writeInt(iterations)
            out.write(salt)
            out.write(keyCheck)
            out.writeLong(createdAtMs)
        }
        return buffer.toByteArray()
    }

    override fun equals(other: Any?): Boolean = other is BackupHeader && bytes().contentEquals(other.bytes())
    override fun hashCode(): Int = bytes().contentHashCode()

    companion object {
        val MAGIC = "MYFINBAK".toByteArray(Charsets.US_ASCII)
        const val VERSION = 1
        const val KDF_PBKDF2_SHA256 = 1
        const val SALT_SIZE = 16
        const val KCV_SIZE = 16
        const val SIZE = 8 + 2 + 1 + 4 + SALT_SIZE + KCV_SIZE + 8

        /** Reads and validates the header; throws [BackupError]. */
        fun read(input: InputStream): BackupHeader {
            val data = DataInputStream(input)
            try {
                val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
                if (!magic.contentEquals(MAGIC)) throw BackupError.Corrupted("not a MyFinance backup")
                val version = data.readUnsignedShort()
                if (version > VERSION) throw BackupError.UnsupportedVersion(version)
                val kdf = data.readUnsignedByte()
                if (kdf != KDF_PBKDF2_SHA256) throw BackupError.Corrupted("unknown kdf $kdf")
                val iterations = data.readInt()
                if (iterations !in 1..10_000_000) throw BackupError.Corrupted("bad iterations")
                val salt = ByteArray(SALT_SIZE).also { data.readFully(it) }
                val kcv = ByteArray(KCV_SIZE).also { data.readFully(it) }
                val createdAt = data.readLong()
                return BackupHeader(version, kdf, iterations, salt, kcv, createdAt)
            } catch (e: EOFException) {
                throw BackupError.Corrupted("truncated header")
            }
        }
    }
}

object KeyDerivation {
    const val DEFAULT_ITERATIONS = 600_000
    const val KEY_SIZE = 32

    /** PBKDF2-HMAC-SHA256 over the NFKC-normalized password. */
    fun derive(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val normalized = Normalizer.normalize(String(password), Normalizer.Form.NFKC).toCharArray()
        val spec = PBEKeySpec(normalized, salt, iterations, KEY_SIZE * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            normalized.fill('\u0000')
        }
    }

    /** Key-check value: HKDF(key, info="myfinance-kcv"), never the content key itself. */
    fun keyCheck(key: ByteArray): ByteArray =
        Hkdf.computeHkdf("HMACSHA256", key, ByteArray(0), "myfinance-kcv".toByteArray(), BackupHeader.KCV_SIZE)
}

/** Authenticated streaming encryption (AES-256-GCM-HKDF, 1 MiB segments) bound to the header. */
object BackupCrypto {
    private const val SEGMENT_SIZE = 1 shl 20

    private fun aead(key: ByteArray) = AesGcmHkdfStreaming.create(
        AesGcmHkdfStreamingKey.create(
            AesGcmHkdfStreamingParameters.builder()
                .setKeySizeBytes(KeyDerivation.KEY_SIZE)
                .setDerivedAesGcmKeySizeBytes(KeyDerivation.KEY_SIZE)
                .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
                .setCiphertextSegmentSizeBytes(SEGMENT_SIZE)
                .build(),
            SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get()),
        ),
    )

    fun encrypt(key: ByteArray, header: BackupHeader, plaintext: InputStream, out: OutputStream) {
        val headerBytes = header.bytes()
        out.write(headerBytes)
        aead(key).newEncryptingStream(NonClosing(out), headerBytes).use { plaintext.copyTo(it) }
        out.flush()
    }

    /** True when [key] matches the header's key-check value. */
    fun keyMatches(key: ByteArray, header: BackupHeader): Boolean =
        MessageDigest.isEqual(KeyDerivation.keyCheck(key), header.keyCheck)

    /**
     * Decrypts the ciphertext that follows an already-read [header] into [out].
     * Throws [BackupError.WrongPassword] or [BackupError.Corrupted] (tampering, truncation).
     */
    fun decrypt(key: ByteArray, header: BackupHeader, ciphertext: InputStream, out: OutputStream) {
        if (!keyMatches(key, header)) throw BackupError.WrongPassword()
        try {
            aead(key).newDecryptingStream(ciphertext, header.bytes()).use { it.copyTo(out) }
        } catch (e: IOException) {
            throw BackupError.Corrupted(e.message ?: "authentication failed")
        } catch (e: GeneralSecurityException) {
            throw BackupError.Corrupted(e.message ?: "authentication failed")
        }
    }

    private class NonClosing(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }
}
