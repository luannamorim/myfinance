package io.github.luannamorim.myfinance.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.testutil.asset
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CategoryRepositoryTest {

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val repo = CategoryRepository(db, time, BackupTrigger { })
    private val cash = CashRepository(db, time, BackupTrigger { })

    @After
    fun close() = db.close()

    @Test
    fun create_appendsAtTheEnd_andRenameStoresName() = runBlocking {
        val id = repo.create(EntryKind.EXPENSE, "  Pets ", "pets")
        val list = repo.observe(EntryKind.EXPENSE).first()
        assertEquals(id, list.last().id)
        assertEquals("Pets", list.last().name)

        val rent = list.first { it.seedKey == "expense.rent" }
        repo.update(rent.id, "Aluguel do apê", "key")
        assertEquals("Aluguel do apê", repo.observe(EntryKind.EXPENSE).first().first { it.id == rent.id }.name)
    }

    @Test
    fun delete_unused_removes_butUsed_isKept() = runBlocking {
        val unused = repo.create(EntryKind.EXPENSE, "Unused", "category")
        assertEquals(DeleteResult.DELETED, repo.delete(unused))

        val rent = repo.observe(EntryKind.EXPENSE).first().first { it.seedKey == "expense.rent" }
        cash.create(TransactionDraft("Rent", rent.id, EntryKind.EXPENSE, brl("10.00"), d("2026-11-12"), null))
        assertEquals(DeleteResult.IN_USE, repo.delete(rent.id))
        assertNotNull(repo.observe(EntryKind.EXPENSE).first().firstOrNull { it.id == rent.id })
    }

    @Test
    fun archived_isHiddenFromPickers_butListedWhenAsked() = runBlocking {
        val gym = repo.observe(EntryKind.EXPENSE).first().first { it.seedKey == "expense.gym" }
        repo.setArchived(gym.id, true)
        assertTrue(repo.observe(EntryKind.EXPENSE).first().none { it.id == gym.id })
        assertTrue(repo.observe(EntryKind.EXPENSE, includeArchived = true).first().any { it.id == gym.id && it.archived })
    }

    @Test
    fun assetCategories_inUseByAnAsset_cantBeDeleted() = runBlocking {
        val custom = repo.createAssetCategory(AssetGroup.PERSONAL, "Art", "category")
        db.portfolioDao().insertAsset(asset(custom, name = "Painting"))
        assertEquals(DeleteResult.IN_USE, repo.deleteAssetCategory(custom))
        val other = repo.createAssetCategory(AssetGroup.INVESTMENT, "Bonds", "receipt_long")
        assertEquals(DeleteResult.DELETED, repo.deleteAssetCategory(other))
        assertEquals(12 + 1, repo.observeAssetCategories().first().size)
    }
}
