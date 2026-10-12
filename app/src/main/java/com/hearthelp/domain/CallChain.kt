package com.hearthelp.domain

/** Decides who to call next. One pass through [numbers]; stops on an answered call or [stop]. */
class CallChain(private val numbers: List<String>, private val answeredSeconds: Int = 20) {
    sealed interface Step {
        data class Call(val index: Int, val number: String) : Step
        data object Done : Step
    }

    private var index = 0
    var isStopped = false
        private set

    fun start(): Step {
        index = 0
        return current()
    }

    fun onCallEnded(connectedSeconds: Int): Step {
        if (connectedSeconds >= answeredSeconds) isStopped = true
        index++
        return current()
    }

    fun stop() { isStopped = true }

    private fun current(): Step {
        if (isStopped || index >= numbers.size) {
            isStopped = true
            return Step.Done
        }
        return Step.Call(index, numbers[index])
    }
}
