package northrail

import kotlin.test.Test
import kotlin.test.assertEquals
import northrail.ledger.Entry
import northrail.ledger.LedgerDatabase
import northrail.ledger.LedgerService
import northrail.ledger.SettlementJob
import northrail.ledger.TransferService
import northrail.recon.ReconJob
import java.io.File
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime


class ReconJobTest{

    @Test
    fun `missing_credit_becomes_break`() {
        LedgerDatabase.open().use { connection ->
        val fromId = insertAccount(connection, 530L, "wealthsimple")
        val toId = insertAccount(connection, 531L, "td")
        val transferId = System.currentTimeMillis()
        val from = Entry(accountId = fromId, amount = -40, transferId = transferId, timePosted = LocalDateTime.now())
        val to = Entry(accountId = toId, amount = 40, transferId = transferId, timePosted = LocalDateTime.now())
        val toBalance = LedgerService().calculateBalance(connection, toId)
        TransferService().storeOutboxEntry(connection, from, to, true)
        assertEquals(1, countEntries(connection, transferId))
        assertEquals(-40, LedgerService().calculateBalance(connection, fromId))
        assertEquals(toBalance, LedgerService().calculateBalance(connection, toId))
        ReconJob().find(connection)
        assertEquals(1, countReconEntries(connection, transferId))
        assertEquals(1, countBreakTypeEntries(connection, transferId, "missing"))
        }
    }

    @Test
    fun `wrong_amount_becomes_break`() {
        LedgerDatabase.open().use { connection ->
            val fromId = insertAccount(connection, 532L, "wealthsimple")
            val toId = insertAccount(connection, 533L, "td")
            val transferId = System.nanoTime()
            val posted = LocalDateTime.now()
            LedgerService().insertOne(connection, fromId, -40, transferId, posted)
            LedgerService().insertOne(connection, toId, 30, transferId, posted)
            assertEquals(2, countEntries(connection, transferId))
            assertEquals(-40, LedgerService().calculateBalance(connection, fromId))
            assertEquals(30, LedgerService().calculateBalance(connection, toId))
            ReconJob().find(connection)
            assertEquals(1, countReconEntries(connection, transferId))
            assertEquals(1, countBreakTypeEntries(connection, transferId, "wrong_amount"))
        }
    }

    @Test
    fun `unmatched_becomes_break`() {
        LedgerDatabase.open().use { connection ->
            val fromId = insertAccount(connection, 534L, "wealthsimple")
            val toId = insertAccount(connection, 535L, "td")
            val transferId = System.nanoTime()
            LedgerService().insertOne(connection, toId, 40, transferId, LocalDateTime.now())
            assertEquals(1, countEntries(connection, transferId))
            assertEquals(0, LedgerService().calculateBalance(connection, fromId))
            assertEquals(40, LedgerService().calculateBalance(connection, toId))
            ReconJob().find(connection)
            assertEquals(1, countReconEntries(connection, transferId))
            assertEquals(1, countBreakTypeEntries(connection, transferId, "unmatched"))
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

    private fun countReconEntries(connection: Connection, transferId: Long): Int {
        val sql = "select count(*) from ledger.recon_breaks where transfer_id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
        }
    }

    private fun countBreakTypeEntries(connection: Connection, transferId: Long, breakType: String): Int {
        val sql = "select count(*) from ledger.recon_breaks where transfer_id = ? and break_type = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            statement.setString(2, breakType)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
        }
    }


}