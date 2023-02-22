package com.vaticle.typedb.iam.simulation.common.concept

import com.vaticle.typedb.iam.simulation.typedb.Labels.NUMBER
import com.vaticle.typedb.iam.simulation.typedb.Labels.RECORD
import com.vaticle.typedb.simulation.common.seed.RandomSource

data class Record(val number: String) {
    fun asObject(): Object {
        return Object(RECORD, NUMBER, number)
    }

    companion object {
        fun initialise(randomSource: RandomSource): Record {
            val number = randomSource.nextInt(1000000000).toString()
            return Record(number)
        }
    }
}