package northrail


import northrail.ledger.LedgerDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime

class LedgerEntryCannotBeEditedTest {

    @Test
    fun `entry_amount_cannot_be_edited`() {
        DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/northrail",
            "northrail",
            "northrail",
        ).use { connection ->
            val accountId = insertAccount(connection, 50L)
            val transferId = System.currentTimeMillis()
            val entryId = insertEntry(connection, accountId, 40, transferId)

            assertFails {
                val sql = "update ledger.entries set amount = 1 where id = ?"
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, entryId)
                    statement.executeUpdate()
                }
            }
            assertFails {
                val sql = "delete from ledger.entries where id = ?"
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, entryId)
                    statement.executeUpdate()
                }
            }
            assertEquals(40, readAmount(connection, entryId))
        }
    }

    @Test
    fun `ledger_admin_cannot_edit_entry`() {
        LedgerDatabase.open().use { connection ->
        val accountId = insertAccount(connection, 51L)
        val entryId = insertEntry(connection, accountId, 40, System.currentTimeMillis())

        assertFails {
            val sql = "update ledger.entries set amount = 1 where id = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, entryId)
                statement.executeUpdate()
            }
        }

        assertFails {
            val sql = "delete from ledger.entries where id = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, entryId)
                statement.executeUpdate()
            }
        }
        assertEquals(40, readAmount(connection, entryId))
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

    private fun insertEntry(connection: Connection, accountId: Long, amount: Int, transferId: Long): Long {
        val sql = """
            insert into ledger.entries (account_id, amount, transfer_id, time_posted)
            values (?, ?, ?, ?)
            returning id
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, accountId)
            statement.setInt(2, amount)
            statement.setLong(3, transferId)
            statement.setObject(4, LocalDateTime.now())
            val results = statement.executeQuery()
            results.next()
            return results.getLong(1)
        }
    }

    private fun readAmount(connection: Connection, entryId: Long): Int {
        val sql = "select amount from ledger.entries where id = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, entryId)
            val results = statement.executeQuery()
            results.next()
            return results.getInt(1)
        }
    }
}
