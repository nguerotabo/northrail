package northrail.ledger;

import java.sql.Connection
import java.time.LocalDateTime

class TransferService {

    fun storeOutboxEntry(connection: Connection, from: Entry, to: Entry) {
        connection.autoCommit = false
        try {
            insertOne(connection, from.accountId, from.amount, from.transferId, from.timePosted)
            insertOne(connection, to.accountId, to.amount, to.transferId, to.timePosted)
            insertOneOutbox(connection, from.transferId, to.timePosted)
            if (from.amount + to.amount != 0 || !from.transferId.equals(to.transferId)) {
                connection.rollback()
            } else {
                connection.commit()
            }
        } catch (error: Exception) {
            connection.rollback()
            throw error 
        }
    }

    fun insertOne(connection: Connection, accountId: Long, amount: Int, transferId: Long, timePosted: LocalDateTime){
        val sql = "insert into ledger.entries (account_id, amount, transfer_id, time_posted) values (?, ?, ?, ?)"
        connection.prepareStatement(sql).use { statement -> 
            statement.setLong(1, accountId)
            statement.setInt(2, amount)
            statement.setLong(3, transferId)
            statement.setObject(4, timePosted)
            statement.executeUpdate()
        }
    }

    fun insertOneOutbox(connection: Connection, transferId: Long, done: LocalDateTime){
        val sql = "insert into ledger.outbox_events(transfer_id) values (?)"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, transferId)
            statement.executeUpdate()
        }
    }


}

