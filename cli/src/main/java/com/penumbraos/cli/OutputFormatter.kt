package com.penumbraos.cli

object OutputFormatter {
    
    fun formatData(data: Any?, indent: Int = 0) {
        val prefix = "  ".repeat(indent)
        when (data) {
            is Map<*, *> -> {
                data.forEach { (key, value) ->
                    print("${prefix}${key}: ")
                    when (value) {
                        is Map<*, *>, is List<*> -> {
                            println()
                            formatData(value, indent + 1)
                        }
                        null -> println("(null)")
                        else -> println(value)
                    }
                }
            }
            is List<*> -> {
                if (data.isEmpty()) {
                    println("${prefix}(empty)")
                } else {
                    data.forEachIndexed { index, item ->
                        print("${prefix}[$index]: ")
                        when (item) {
                            is Map<*, *>, is List<*> -> {
                                println()
                                formatData(item, indent + 1)
                            }
                            null -> println("(null)")
                            else -> println(item)
                        }
                    }
                }
            }
            else -> println("${prefix}$data")
        }
    }
}