package northrail

import kotlin.test.Test
import kotlin.test.assertEquals
import northrail.ledger.Entry
import northrail.ledger.LedgerDatabase
import northrail.ledger.LedgerService
import northrail.ledger.TransferService
import java.sql.Connection
import java.time.LocalDateTime

class OutboxEventsTest {

    @Test 
    fun `correct_oubtbox_events_call`() {
        LedgerDatabase.open().use { connection ->
        val fromId = insertAccount(connection, 700L, "wealthsimple")
        val toId = insertAccount(connection, 701L, "td")
        val from = Entry(accountId = fromId, amount = -40, transferId = 10L, timePosted = LocalDateTime.now())
        val to = Entry(accountId = toId, amount = 40, transferId = 10L, timePosted = LocalDateTime.now())

        val before = countEntries(connection, 10L)
        val outboxBefore = countOutBoxEntries(connection, 10L)
        TransferService().storeOutboxEntry(connection, from, to)
        assertEquals(before + 2, countEntries(connection, 10L))
        assertEquals(outboxBefore + 1, countOutBoxEntries(connection, 10L))
        }
    }

    @Test
    fun `incorrect_outbox_events_call`(){
        LedgerDatabase.open().use { connection ->
        val fromId = insertAccount(connection, 702L, "wealthsimple")
        val toId = insertAccount(connection, 703L, "td")
        val from = Entry(accountId = fromId, amount = -40, transferId = 11L, timePosted = LocalDateTime.now())
        val to = Entry(accountId = toId, amount = 50, transferId = 11L, timePosted = LocalDateTime.now())

        val before = countEntries(connection, 11L)
        val outboxBefore = countOutBoxEntries(connection, 11L)
        TransferService().storeOutboxEntry(connection, from, to)
        assertEquals(before, countEntries(connection, 11L))
        assertEquals(outboxBefore, countOutBoxEntries(connection, 11L))
        }

    }

    private fun countOutBoxEntries(connection: Connection, transferId: Long): Int {
        val sql = "select count(*) from ledger.outbox_events where transfer_id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
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

}

