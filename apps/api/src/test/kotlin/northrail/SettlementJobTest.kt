package northrail

import kotlin.test.Test
import kotlin.test.assertEquals
import northrail.ledger.Entry
import northrail.ledger.LedgerDatabase
import northrail.ledger.LedgerService
import northrail.ledger.SettlementJob
import northrail.ledger.TransferService
import java.io.File
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime

class SettlementJobTest {


    @Test
    fun `visible_is_not_settled_until_job`(){
        LedgerDatabase.open().use { connection ->
        val fromId = insertAccount(connection, 520L, "wealthsimple")
        val toId = insertAccount(connection, 521L, "td")
        val transferId = System.currentTimeMillis()
        val from = Entry(accountId = fromId, amount = -40, transferId = transferId, timePosted = LocalDateTime.now())
        val to = Entry(accountId = toId, amount = 40, transferId = transferId, timePosted = LocalDateTime.now())
        TransferService().storeOutboxEntry(connection, from, to)
        assertEquals(2, countEntries(connection, transferId))
        val balanceFrom = LedgerService().calculateBalance(connection, fromId)
        assertEquals(-40, balanceFrom)
        val balanceTo = LedgerService().calculateBalance(connection, toId)
        assertEquals(40, balanceTo)
        assertEquals(0, countSettlementTransferEntries(connection, transferId))
        val settlementBatches = countBatchEntries(connection, LocalDate.now())
        SettlementJob().settle(connection, LocalDate.now(), "file.txt")
        assertEquals(1, countSettlementTransferEntries(connection, transferId))
        assertEquals(settlementBatches + 1, countBatchEntries(connection, LocalDate.now()))
        assertEquals(-40, LedgerService().calculateBalance(connection, fromId))
        assertEquals(40, LedgerService().calculateBalance(connection, toId))
        assertEquals("Wealthsimple owes TD 40", File("file.txt").readText())
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

    private fun countBatchEntries(connection: Connection, businessDate: LocalDate): Int {
        val sql = "select count(*) from ledger.settlement_batches where business_date = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, businessDate)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
        }
    }

    private fun countSettlementTransferEntries(connection: Connection, transferId: Long): Int {
        val sql = "select count(*) from ledger.settlement_transfers where transfer_id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
        }
    }
}