package northrail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import northrail.ledger.Entry
import northrail.ledger.LedgerDatabase
import northrail.ledger.LedgerService
import northrail.ledger.TransferService
import northrail.ledger.OutboxWorker
import java.sql.Connection
import java.time.LocalDateTime

class OutboxWorkerTest{

    @Test
    fun `worker_crash_still_applies_once`() {
        LedgerDatabase.open().use { connection ->
        closeUnfinished(connection)
        val fromId = insertAccount(connection, 11L, "wealthsimple")
        val toId = insertAccount(connection, 12L, "td")
        val transferId = System.currentTimeMillis()
        val from = Entry(accountId = fromId, amount = -40, transferId = transferId, timePosted = LocalDateTime.now())
        val to = Entry(accountId = toId, amount = 40, transferId = transferId, timePosted = LocalDateTime.now())
        TransferService().storeOutboxEntry(connection, from, to)
        val receiverBalance = LedgerService().calculateBalance(connection, toId)
        OutboxWorker().deliver(connection, true)
        assertEquals(null, getDoneState(connection, from.transferId))
        OutboxWorker().deliver(connection, false)
        assertNotNull(getDoneState(connection, from.transferId))
        assertEquals(receiverBalance, LedgerService().calculateBalance(connection, toId))
        assertEquals(2, countEntries(connection, transferId))
        }
    }

    @Test
    fun `same_key_never_double_posts`() {
        LedgerDatabase.open().use { connection ->
            closeUnfinished(connection)
            val fromId = insertAccount(connection, 21L, "wealthsimple")
            val toId = insertAccount(connection, 22L, "td")
            val transferId = System.currentTimeMillis()
            val from = Entry(accountId = fromId, amount = -40, transferId = transferId, timePosted = LocalDateTime.now())
            val to = Entry(accountId = toId, amount = 40, transferId = transferId, timePosted = LocalDateTime.now())
            TransferService().storeOutboxEntry(connection, from, to)
            val receiverBalance = LedgerService().calculateBalance(connection, toId)
            OutboxWorker().deliver(connection, false)
            OutboxWorker().deliver(connection, false)
            assertEquals(receiverBalance, LedgerService().calculateBalance(connection, toId))
            assertEquals(2, countEntries(connection, transferId))
            assertNotNull(getDoneState(connection, transferId))
        }
    }

    private fun insertAccount(connection: Connection, customerId: Long, institution: String): Long {
        val sql = "insert into ledger.accounts (customer_id, institution) values (?, ?) returning id"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, customerId)
            statement.setString(2, institution)
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

    private fun getDoneState(connection: Connection, transferId: Long): LocalDateTime? {
        val sql = "select done from ledger.outbox_events where transfer_id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            val results = statement.executeQuery()
            results.next()
            val done = results.getTimestamp(1)
            if (results.wasNull()) {
                return null
            } else {
                return done.toLocalDateTime()
            }
        }
    }

    private fun closeUnfinished(connection: Connection) {
        val sql = "update ledger.outbox_events set done = now() where done is null"
        connection.prepareStatement(sql).use { statement ->
            statement.executeUpdate()
        }
    }

}