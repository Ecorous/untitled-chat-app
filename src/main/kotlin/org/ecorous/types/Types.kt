package org.ecorous.types

import com.catppuccin.Color
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.UUID


//#region Serializers
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
//#endregion

//#region Users
@Serializable
data class UserInput(val displayName: String?, val password: String?)

@Serializable
data class UserLoginInput(@Serializable(with = UUIDSerializer::class) val id: UUID?, val password: String?)

@Serializable
data class UserPutInput(val displayName: String?, val pronouns: String?, val description: String?)

@Serializable
data class PublicUser(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val displayName: String,
	val pronouns: String,
	val description: String,
	@Serializable(with = InstantSerializer::class) val joinDate: Instant,
)

@Serializable
data class User(
	@Serializable(with = UUIDSerializer::class) val id: UUID,
	val displayName: String,
	val pronouns: String,
	val description: String,
	val password: String?,
	@Serializable(with = InstantSerializer::class) val joinDate: Instant,
)

fun User.public(): PublicUser {
	return PublicUser(id, displayName, pronouns, description, joinDate)
}

object Users : Table() {
	val id = uuid("id")
	val displayName = varchar("displayName", 32)
	val pronouns = varchar("pronouns", 16)
	val description = varchar("description", 256)
	val password = varchar("password", 256).nullable()
	val joinDate = datetime("joinDate")
	
	override val primaryKey = PrimaryKey(id)
}

class UserManager(private val database: Database) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	init {
		logger.info("UserManager created")
	}
	
	fun getUserById(id: String): User? = getUserById(UUID.fromString(id))
	
	fun getUserById(id: UUID): User? = transaction(database) {
		//Users.selectAll().find { it[Users.id] == id }?.userFromRow()
		Users.select(Users.id eq id).singleOrNull()?.userFromRow()
	}
	
	fun getUsers(): List<User> = transaction(database) {
		Users.selectAll().map { it.userFromRow() }
	}
	
	fun getPublicUsers(): List<PublicUser> = getUsers().map { it.public() }
	
	fun pushToDB(user: User) {
		transaction(database) {
			Users.insert {
				it[id] = user.id
				it[displayName] = user.displayName
				it[pronouns] = user.pronouns
				it[description] = user.description
				it[password] = user.password
				it[joinDate] = user.joinDate.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
			}
		}
	}
	
	fun removeFromDB(user: User) {
		transaction(database) {
			Users.deleteWhere { id eq user.id }
		}
	}
	
	fun ResultRow.userFromRow(): User = User(
		this[Users.id],
		this[Users.displayName],
		this[Users.pronouns],
		this[Users.description],
		this[Users.password],
		this[Users.joinDate].toKotlinLocalDateTime().toInstant(TimeZone.currentSystemDefault())
	)
}

//#endregion

//#region Tokens
object Tokens : Table() {
	val userID = uuid("user_id")
	val token = varchar("token", 32)
}

data class Token(
	val user: User, val token: String
)

class TokenManager(private val database: Database) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val userManager = UserManager(database)
	
	init {
		logger.info("$this created")
	}
	
	private fun generateToken(user: User): Token {
		val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()
		val random = SecureRandom()
		val sb = StringBuilder(32)
		for (i in 0 until 32) {
			sb.append(chars[random.nextInt(chars.size)])
		}
		val v = sb.toString()
		transaction(database) {
			Tokens.insert {
				it[userID] = user.id
				it[token] = v
			}
		}
		return Token(user, v)
	}
	
	fun resetToken(user: User): Token {
		transaction(database) {
			Tokens.deleteWhere { userID eq user.id }
		}
		return generateToken(user)
	}
	
	fun getTokenMap(): Map<User, String> = transaction(database) {
		Tokens.selectAll().associateBy(
			keySelector = { userManager.getUserById(it[Tokens.userID])!! },
			valueTransform = { it[Tokens.token] }
		)
	}
	
	fun getTokenByUserId(id: UUID): Token? {
		val tokenMap = getTokenMap()
		val user = userManager.getUserById(id)
		
		return if (user != null) {
			val token = tokenMap[user]
			token?.let { Token(user, it) }
		} else {
			null
		}
	}
	
	fun getTokenByString(token: String): Token? {
		val map = getTokenMap()
		val e = map.entries.find { it.value == token }
		return e?.let { Token(e.key, e.value) }
	}
	
	fun getTokenByUser(user: User): Token? = getTokenByUserId(user.id)
	
	fun ResultRow.tokenFromRow(): Token {
		return Token(userManager.getUserById(this[Tokens.userID])!!, this[Tokens.token])
	}
	
	fun getToken(user: User): Token {
		val mapValue = getTokenMap()[user]
		return if (mapValue != null) Token(user, mapValue) else {
			generateToken(user)
		}
	}
	
	
}

//#endregion Tokens

//#region Utils
fun Color.getKotlinColor(): kotlinx.css.Color = kotlinx.css.Color("#$hex")
//#endregion
fun PipelineContext<Unit, ApplicationCall>.getTokenFromCall(tokenManager: TokenManager): Token? {
	val tokenS = call.request.headers["Authorization"] ?: return null
	return tokenManager.getTokenByString(tokenS)
}