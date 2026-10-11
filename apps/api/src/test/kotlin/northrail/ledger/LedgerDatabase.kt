package northrail.ledger

import java.sql.Connection
import java.sql.DriverManager

/** Opens a connection as ledger_admin. Same url and login as application.yml. */
object LedgerDatabase {
	fun open(): Connection =
		DriverManager.getConnection(
			"jdbc:postgresql://localhost:5432/northrail",
			"ledger_admin",
			"ledger_admin",
		)
}
