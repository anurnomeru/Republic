package ink.anur.core.raft

import java.util.*

/**
 * Created by Anur on 2020/7/11
 */
class GenerationAndOffset(val generation: Long, val offset: Long) {
    val INVALID = GenerationAndOffset(-1, -1)

    operator fun next(): GenerationAndOffset? {
        return if (offset == Long.MAX_VALUE) {
            GenerationAndOffset(generation + 1, 1)
        } else {
            GenerationAndOffset(generation, offset + 1)
        }
    }

    override fun equals(other: Any?): Boolean {
        return Optional.ofNullable(other)
                .filter { it is GenerationAndOffset }
                .map { it as GenerationAndOffset }
                .map { it.generation == generation && it.offset == offset }
                .orElse(false)
    }

    override fun hashCode(): Int {
        return (generation * 17 + offset * 17).toInt()
    }

    operator fun compareTo(o: GenerationAndOffset): Int {
        return when {
            generation > o.generation -> {
                1
            }
            generation == o.generation -> {
                when {
                    offset > o.offset -> {
                        1
                    }
                    offset == o.offset -> {
                        0
                    }
                    else -> {
                        -1
                    }
                }
            }
            else -> {
                -1
            }
        }
    }

    override fun toString(): String {
        return "- {" +
                "generation=" + generation +
                ", offset=" + offset +
                "} -"
    }
}