package com.mkring.james

import com.mkring.james.chatbackend.UniqueChatTarget
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.cancelAndJoin
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory

object RunningJobs {
    val running = mutableMapOf<UniqueChatTarget, Job>()
}

val log = LoggerFactory.getLogger("RunningJobs")
fun abortJob(key: UniqueChatTarget) {
    log.info("going to abort job for target $key")
    RunningJobs.running[key]?.let {
        runBlocking {
            it.cancelAndJoin()
        }
        RunningJobs.running.remove(key)
    }
}
