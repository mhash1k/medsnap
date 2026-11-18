package com.pharmabill.app.domain.stock

data class StockState(val quantity: Long, val needsStockEntry: Boolean)

/** Stock may go negative; the badge clears when a stock entry brings it back to zero or above. */
object StockLedger {
    fun applySale(state: StockState, units: Long): StockState = apply(state, -units)

    fun applyStockIn(state: StockState, units: Long): StockState = apply(state, units)

    fun apply(state: StockState, change: Long): StockState {
        val q = state.quantity + change
        return StockState(q, q < 0)
    }

    /** Rebuilds stock from the movement log. */
    fun rebuild(changes: List<Long>): StockState {
        val q = changes.sum()
        return StockState(q, q < 0)
    }
}
