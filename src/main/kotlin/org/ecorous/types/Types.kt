package org.ecorous.types

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*


object UUIDSerializer : KSerializer<UUID> {
	override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
	
	override fun deserialize(decoder: Decoder): UUID {
		return UUID.fromString(decoder.decodeString())
	}
	
	override fun serialize(encoder: Encoder, value: UUID) {
		encoder.encodeString(value.toString())
	}
}

object InstantSerializer : KSerializer<Instant> {
	override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
	
	override fun deserialize(decoder: Decoder): Instant {
		return Instant.fromEpochSeconds(decoder.decodeLong())
	}
	
	override fun serialize(encoder: Encoder, value: Instant) {
		encoder.encodeLong(value.epochSeconds)
	}
}

@Serializable
data class UserInput(val displayName: String?, val password: String?)

@Serializable
data class UserLoginInput(@Serializable(with = UUIDSerializer::class) val id: UUID?, val password: String?)

@Serializable
data class UserPutInput(val displayName: String?, val pronouns: String?, val description: String?)

@Serializable
data class LodgeInput(val name: String?, val description: String?, val public: Boolean? = true)

@Serializable
data class CabinInput(val name: String?, val topic: String?, val requireAdmin: Boolean? = false)

@Serializable
data class MessageInput(val content: String?)

@Serializable
data class PublicUser(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val displayName: String,
	val pronouns: String,
	val description: String,
	@Serializable(with = InstantSerializer::class) val joinDate: Instant,
	val admin: Boolean = false
)

@Serializable
data class User(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val displayName: String,
	val pronouns: String,
	val description: String,
	val password: String?,
	@Serializable(with = InstantSerializer::class) val joinDate: Instant,
	val admin: Boolean = false
)

object Users : Table() {
	val id = uuid("id")
	val displayName = varchar("display_name", 32)
	val pronouns = varchar("pronouns", 16)
	val description = varchar("description", 256)
	val password = varchar("password", 256).nullable()
	val joinDate = datetime("join_date")
	val admin = bool("admin")
	
	override val primaryKey = PrimaryKey(id)
}

object Tokens : Table() {
	val userID = uuid("user_id")
	val token = varchar("token", 128)
}

data class Token(
	val user: User, val token: String
)

object Lodges : Table() {
	val id = uuid("id")
	val name_ = varchar("name", 32)
	val description = varchar("description", 256).nullable()
	val iconUrl = text("icon_url").nullable()
	val creationDate = datetime("creation_date")
	val public_ = bool("public")
	
	override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Lodge(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val name: String,
	val description: String?,
	val iconUrl: String?,
	@Serializable(with = InstantSerializer::class) val creationDate: Instant,
	val public: Boolean = true
)

object LodgeMembers : Table() {
	val lodgeId = uuid("lodge_id")
	val userID = uuid("user_id")
	val joinDate = datetime("join_date")
	val admin = bool("admin")
}

object LodgeCabins : Table() {
	val lodgeId = uuid("lodge_id")
	val cabinId = uuid("cabin_id")
}

object Cabins : Table() {
	val id = uuid("id")
	val name_ = varchar("name", 16)
	val topic = varchar("topic", 128)
	val lodgeId = uuid("lodge_id")
	val creationDate = datetime("creation_date")
	val requireAdmin = bool("require_admin")
	
	override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Cabin(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val name: String,
	val topic: String,
	val lodge: Lodge,
	@Serializable(with = InstantSerializer::class) val creationDate: Instant,
	val requireAdmin: Boolean = false
)

object CabinMessages : Table() {
	val cabinId = uuid("cabin_id")
	val messageId = uuid("message_id")
}

object Messages : Table() {
	val id = uuid("id")
	val userId = uuid("user_id")
	val content = varchar("content", 2048)
	val lodgeId = uuid("lodge_id")
	val cabinId = uuid("cabin_id")
	val creationDate = datetime("creation_date")
}

@Serializable
data class Message(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val user: User,
	val content: String,
	val lodge: Lodge,
	val cabin: Cabin,
	@Serializable(with = InstantSerializer::class) val creationDate: Instant
)

@Serializable
data class PublicMessage(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val user: PublicUser,
	val content: String,
	val lodge: Lodge,
	val cabin: Cabin,
	@Serializable(with = InstantSerializer::class) val creationDate: Instant
)