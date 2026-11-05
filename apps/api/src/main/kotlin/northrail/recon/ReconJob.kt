package northrail.recon

import java.sql.Connection
import java.sql.Types

class ReconJob {

    fun find(connection: Connection) {
        connection.autoCommit = false
        try {
            val sides = recordedSides(connection)
            for (breakRow in classify(connection, sides)) {
                insertBreak(connection, breakRow)
            }
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    private fun recordedSides(connection: Connection): List<Side> {
        val sql = """
            select e.transfer_id, a.institution, sum(e.amount) as amount
            from ledger.entries e
            join ledger.accounts a on a.id = e.account_id
            where not exists (
                select 1
                from ledger.recon_breaks b
                where b.transfer_id = e.transfer_id
            )
            group by e.transfer_id, a.institution
            order by e.transfer_id
        """.trimIndent()

        val sideList = mutableListOf<Side>()

        connection.prepareStatement(sql).use { statement ->
            val results = statement.executeQuery()
            while (results.next()) {
                val transfer_id = results.getLong(1)
                val institution = results.getString(2)
                val amount = results.getInt(3)

                sideList.add(Side(transfer_id, institution, amount))
            }
            return sideList
        }
    }

    private fun classify (connection: Connection, sides: List<Side>): List<Break> {
        val grouped = sides.groupBy { it.transferId }
        val institutions = institutions(connection)
        val breaks = mutableListOf<Break>()

        for ((transferId, group) in grouped) {
            if (group.size == 1) {
                val side = group[0]
                if (side.amount == 0) {
                    continue
                }
                val other = institutions.first { it != side.institution }
                if (side.amount < 0) {
                    breaks.add(Break(transferId, BreakType.missing, side.institution, side.amount, other, null))
                }
                else {
                    breaks.add(Break(transferId, BreakType.unmatched, other, null, side.institution, side.amount))   
                }
            }
            else if (group.size == 2) {
                if (group[0].amount + group[1].amount == 0 ){
                    continue
                }
                val from = group.first { it.amount < 0 }
                val to = group.first { it.amount > 0 }
                breaks.add(Break(transferId, BreakType.wrong_amount, from.institution, from.amount, to.institution, to.amount))
            }
        }
        return breaks
    }

    private fun insertBreak(connection: Connection, breakRow: Break) {
        val sql = "insert into ledger.recon_breaks ( transfer_id, break_type, from_institution, from_amount, to_institution, to_amount, created) values (?, ?, ?, ?, ?, ?, now())"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, breakRow.transferId)
            statement.setObject(2, breakRow.breakType.name)
            statement.setString(3, breakRow.fromInstitution)
            if (breakRow.fromAmount == null){
                statement.setNull(4, Types.INTEGER)
            } else {
                statement.setInt(4, breakRow.fromAmount)
            }
            statement.setString(5, breakRow.toInstitution)
            if (breakRow.toAmount == null){
                statement.setNull(6, Types.INTEGER)
            } else {
                statement.setInt(6, breakRow.toAmount)
            }
            statement.executeUpdate()
        }
    }

    private fun institutions(connection: Connection): List<String> {
        val sql = "select distinct institution from ledger.accounts"

        val institutionList = mutableListOf<String>()
        connection.prepareStatement(sql).use { statement ->
            val results = statement.executeQuery()
            while (results.next()){
                institutionList.add(results.getString(1))
            }
        }
        return institutionList
    }

    private data class Side(val transferId: Long, val institution: String, val amount: Int)

    private data class Break(
    val transferId: Long,
    val breakType: BreakType,
    val fromInstitution: String,
    val fromAmount: Int?,
    val toInstitution: String,
    val toAmount: Int?,
)

}