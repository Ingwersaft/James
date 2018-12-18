package com.mkring.james.chatbackend.rocketchat

import com.tinder.scarlet.StreamAdapter
import com.tinder.scarlet.utils.getRawType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.lang.reflect.Type

class CoroutinesStreamAdapterFactory : StreamAdapter.Factory {

    override fun create(type: Type): StreamAdapter<Any, Any> {
        val rawType = type.getRawType()
        return when (rawType) {
            ReceiveChannel::class.java -> ReceiveChannelStreamAdapter()
            Channel::class.java -> ReceiveChannelStreamAdapter()
            else -> throw IllegalArgumentException()
        }
    }
}