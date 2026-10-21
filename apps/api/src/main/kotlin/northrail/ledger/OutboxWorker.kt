package northrail.ledger

import java.sql.Connection

class OutboxWorker {

    fun deliver(connection: Connection, stopBeforeDone: Boolean) {
        connection.autoCommit = false
        try {
            val id = lockNext(connection)
            if (id == null) {
                connection.commit()
                return
            }
            if (stopBeforeDone) {
                connection.rollback()
                return
            }
            setDone(connection, id)
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    private fun lockNext(connection: Connection): Long? {
        val sql = """
            select id
            from ledger.outbox_events
            where done is null
            order by id
            for update skip locked
            limit 1
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            val results = statement.executeQuery()
            if (!results.next()) {
                return null
            }
            return results.getLong(1)
        }
    }

    private fun setDone(connection: Connection, id: Long) {
        val sql = "update ledger.outbox_events set done = now() where id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, id)
            statement.executeUpdate()
        }
    }
}
