package northrail.ledger

import java.io.File
import java.sql.Connection
import java.time.LocalDate

class SettlementJob {

    fun settle(connection: Connection, businessDate: LocalDate, filePath: String) {
        connection.autoCommit = false
        try {
            val transferIds = unsettledTransfers(connection, businessDate)
            if (transferIds.isEmpty()) {
                connection.commit()
                return
            }
            val nets = netByBank(connection, transferIds)
            val batchId = insertBatch(connection, businessDate)
            for (transferId in transferIds) {
                insertMembership(connection, batchId, transferId)
            }
            writeFile(filePath, nets)
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    private fun unsettledTransfers(connection: Connection, businessDate: LocalDate): List<Long> {
        val sql = """
            select distinct transfer_id
            from ledger.entries
            where time_posted::date = ?
              and transfer_id not in (select transfer_id from ledger.settlement_transfers)
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, businessDate)
            val results = statement.executeQuery()
            val ids = mutableListOf<Long>()
            while (results.next()) {
                ids.add(results.getLong(1))
            }
            return ids
        }
    }

    private fun netByBank(connection: Connection, transferIds: List<Long>): Map<Pair<String, String>, Int> {
        val nets = mutableMapOf<Pair<String, String>, Int>()
        for (transferId in transferIds) {
            val leg = readTransfer(connection, transferId)
            val key = if (leg.owes <= leg.owed) leg.owes to leg.owed else leg.owed to leg.owes
            val signed = if (leg.owes <= leg.owed) leg.amount else -leg.amount
            nets[key] = (nets[key] ?: 0) + signed
        }
        return nets
    }

    private fun readTransfer(connection: Connection, transferId: Long): TransferLeg {
        val sql = """
            select e.amount, a.institution
            from ledger.entries e
            join ledger.accounts a on a.id = e.account_id
            where e.transfer_id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            val results = statement.executeQuery()
            var owes: String? = null
            var owed: String? = null
            var amount = 0
            while (results.next()) {
                val rowAmount = results.getInt(1)
                val institution = results.getString(2)
                if (rowAmount < 0) {
                    owes = institution
                } else if (rowAmount > 0) {
                    owed = institution
                    amount = rowAmount
                }
            }
            return TransferLeg(owes!!, owed!!, amount)
        }
    }

    private fun insertBatch(connection: Connection, businessDate: LocalDate): Long {
        val sql = """
            insert into ledger.settlement_batches (business_date, created)
            values (?, now())
            returning id
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, businessDate)
            val results = statement.executeQuery()
            results.next()
            return results.getLong(1)
        }
    }

    private fun insertMembership(connection: Connection, batchId: Long, transferId: Long) {
        val sql = "insert into ledger.settlement_transfers (batch_id, transfer_id) values (?, ?)"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, batchId)
            statement.setLong(2, transferId)
            statement.executeUpdate()
        }
    }

    private fun writeFile(filePath: String, nets: Map<Pair<String, String>, Int>) {
        val lines = mutableListOf<String>()
        for ((pair, net) in nets) {
            if (net > 0) {
                lines.add("${display(pair.first)} owes ${display(pair.second)} $net")
            } else if (net < 0) {
                lines.add("${display(pair.second)} owes ${display(pair.first)} ${-net}")
            }
        }
        File(filePath).writeText(lines.joinToString("\n"))
    }

    private fun display(institution: String): String {
        return when (institution) {
            "wealthsimple" -> "Wealthsimple"
            "td" -> "TD"
            else -> institution
        }
    }

    private data class TransferLeg(val owes: String, val owed: String, val amount: Int)
}
