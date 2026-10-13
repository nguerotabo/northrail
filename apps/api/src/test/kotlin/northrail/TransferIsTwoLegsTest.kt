package northrail

import kotlin.test.Test
import kotlin.test.assertEquals
import northrail.ledger.Entry
import northrail.ledger.LedgerDatabase
import northrail.ledger.LedgerService
import java.sql.Connection
import java.time.LocalDateTime

class TransferIsTwoLegsTest {

    @Test
    fun `transfer_is_atomic_two_legs`() {
        LedgerDatabase.open().use { connection ->
            val fromId = insertAccount(connection, 301L)
            val toId = insertAccount(connection, 302L)
            val from = Entry(accountId = fromId, amount = -40, transferId = 7L, timePosted = LocalDateTime.now())
            val to = Entry(accountId = toId, amount = 40, transferId = 7L, timePosted = LocalDateTime.now())

            val before = countEntries(connection, 7L)
            LedgerService().storeEntry(connection, from, to)
            assertEquals(before + 2, countEntries(connection, 7L))
        }
    }

    @Test
    fun `transfer_amount_is_incorrect`() {
        LedgerDatabase.open().use { connection ->
            val fromId = insertAccount(connection, 400L)
            val toId = insertAccount(connection, 401L)
            val from = Entry(accountId = fromId, amount = -40, transferId = 2L, timePosted = LocalDateTime.now())
            val to = Entry(accountId = toId, amount = 50, transferId = 2L, timePosted = LocalDateTime.now())

            LedgerService().storeEntry(connection, from, to)
            assertEquals(0, countEntries(connection, 2L))
        }
    }

    @Test
    fun `transferId_does_not_match`() {
        LedgerDatabase.open().use { connection ->
            val fromId = insertAccount(connection, 500L)
            val toId = insertAccount(connection, 501L)
            val from = Entry(accountId = fromId, amount = -40, transferId = 3L, timePosted = LocalDateTime.now())
            val to = Entry(accountId = toId, amount = 40, transferId = 4L, timePosted = LocalDateTime.now())

            LedgerService().storeEntry(connection, from, to)
            assertEquals(0, countEntries(connection, 3L))
            assertEquals(0, countEntries(connection, 4L))
        }
    }

    private fun insertAccount(connection: Connection, customerId: Long): Long {
        val sql = "insert into ledger.accounts (customer_id) values (?) returning id"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, customerId)
            val results = statement.executeQuery()
            results.next()
            return results.getLong(1)
        }
    }

    private fun countEntries(connection: Connection, transferId: Long): Int {
        val sql = "select count(*) from ledger.entries where transfer_id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
        }
    }
}
