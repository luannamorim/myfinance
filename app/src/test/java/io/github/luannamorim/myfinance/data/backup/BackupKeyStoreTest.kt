package io.github.luannamorim.myfinance.data.backup

import io.github.luannamorim.myfinance.testutil.FakeKeyWrapper
import io.github.luannamorim.myfinance.testutil.TEST_ITERATIONS
import io.github.luannamorim.myfinance.testutil.testKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeyStoreTest {
    private val wrapper = FakeKeyWrapper()
    private val store = testKeyStore(wrapper)

    @Test
    fun setPassword_thenKey_returnsSameKey() = runBlocking {
        assertEquals(KeyState.NONE, store.state())
        val created = store.setPassword("minha senha".toCharArray(), TEST_ITERATIONS)
        val loaded = store.key()!!
        assertArrayEquals(created.key, loaded.key)
        assertArrayEquals(created.salt, loaded.salt)
        assertEquals(TEST_ITERATIONS, loaded.iterations)
        assertEquals(KeyState.READY, store.state())
    }

    @Test
    fun unwrapFailure_returnsNull_andUnlockRestores() = runBlocking {
        val created = store.setPassword("minha senha".toCharArray(), TEST_ITERATIONS)
        wrapper.broken = true
        assertNull(store.key())
        assertEquals(KeyState.LOCKED, store.state())
        wrapper.broken = false
        assertTrue(store.unlock("minha senha".toCharArray()))
        assertArrayEquals(created.key, store.key()!!.key)
    }

    @Test
    fun unlock_wrongPassword_false() = runBlocking {
        store.setPassword("minha senha".toCharArray(), TEST_ITERATIONS)
        assertFalse(store.unlock("errada".toCharArray()))
    }
}
