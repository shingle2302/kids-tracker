package com.shingle.kids.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KidsTrackerBackendApplication

fun main(args: Array<String>) {
    runApplication<KidsTrackerBackendApplication>(*args)
}
