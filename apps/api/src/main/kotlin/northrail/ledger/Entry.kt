package northrail.ledger

import java.time.LocalDateTime

data class Entry(val accountId: Long, val amount: Int, val transferId: Long, val timePosted: LocalDateTime)