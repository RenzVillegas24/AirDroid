package io.moonbase.airdroid

/** Direction of a tracked AirDrop file transfer. */
enum class TransferDirection {
    SENT,
    RECEIVED,
}

/**
 * Per-file transfer state. WAITING/OFFERED/ACCEPTED/TRANSFERRING are live;
 * COMPLETE/FAILED/CANCELLED are terminal and belong in history.
 */
enum class TransferStatus {
    WAITING,
    OFFERED,
    ACCEPTED,
    TRANSFERRING,
    COMPLETE,
    FAILED,
    CANCELLED,
    ;

    val isActive: Boolean
        get() = this == WAITING || this == OFFERED || this == ACCEPTED ||
            this == TRANSFERRING

    val isTerminal: Boolean
        get() = !isActive
}

/** One file inside a multi-file transfer group. */
data class TransferFile(
    val id: String,
    val name: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0,
    val status: TransferStatus = TransferStatus.WAITING,
    val localPath: String? = null,
)

/**
 * One file in the Sent or Received list. [id] is stable across helper events,
 * UI updates, and notification updates.
 */
data class TransferItem(
    val id: String,
    val direction: TransferDirection,
    val peerName: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val error: String? = null,
    val localPath: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    /** First upload/download event, excluding the AirDrop prompt wait. */
    val uploadStartedAt: Long? = null,
    /** Child files when this card represents a multi-file batch. */
    val files: List<TransferFile> = emptyList(),
) {
    val progressPercent: Int?
        get() = if (totalBytes > 0) {
            ((transferredBytes.coerceAtLeast(0) * 100) / totalBytes)
                .toInt()
                .coerceIn(0, 100)
        } else {
            null
        }

    val averageBytesPerSecond: Long?
        get() {
            val start = uploadStartedAt ?: return null
            val end = completedAt ?: return null
            val elapsedMs = end - start
            if (elapsedMs <= 0 || transferredBytes <= 0) return null
            return transferredBytes * 1000 / elapsedMs
        }

    val isComplete: Boolean
        get() = status == TransferStatus.COMPLETE

    val canCancel: Boolean
        get() = when (direction) {
            TransferDirection.SENT -> status.isActive
            TransferDirection.RECEIVED ->
                status == TransferStatus.ACCEPTED || status == TransferStatus.TRANSFERRING
        }
}
