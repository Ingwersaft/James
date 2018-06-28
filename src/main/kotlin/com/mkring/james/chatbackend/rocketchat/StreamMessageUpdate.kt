package com.mkring.james.chatbackend.rocketchat

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class StreamMessageUpdate {
    @SerializedName("msg")
    @Expose
    var msg: String? = null
    @SerializedName("collection")
    @Expose
    var collection: String? = null
    @SerializedName("id")
    @Expose
    var id: String? = null
    @SerializedName("fields")
    @Expose
    var fields: FieldsGson? = null

    override fun toString(): String {
        return "StreamMessageUpdate(msg=$msg, collection=$collection, id=$id, fields=$fields)"
    }

}


class Argold {
    @SerializedName("_id")
    @Expose
    var id: String? = null
    @SerializedName("rid")
    @Expose
    var rid: String? = null
    @SerializedName("msg")
    @Expose
    var msg: String? = null
    @SerializedName("ts")
    @Expose
    var ts: Ts? = null
    @SerializedName("unew")
    @Expose
    var u: Uold? = null
    @SerializedName("_updatedAt")
    @Expose
    var updatedAt: UpdatedAt? = null

    override fun toString(): String {
        return "Arg(id=$id, rid=$rid, msg=$msg, ts=$ts, unew=$u, updatedAt=$updatedAt)"
    }

}

class FieldsGson {
    @SerializedName("eventName")
    @Expose
    var eventName: String? = null
    @SerializedName("args")
    @Expose
    var args: List<Argold>? = null

    override fun toString(): String {
        return "FieldsGson(eventName=$eventName, args=$args)"
    }


}