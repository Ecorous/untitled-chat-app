package org.ecorous.types

import com.catppuccin.Color
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.ecorous.plugins.db
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*

fun User.public(): PublicUser {
	return PublicUser(id, displayName, pronouns, description, joinDate, admin)
}


/**
 * Attempts to get a [User] from a [Database] using it's [id]
 * @return The [User] if it exists, otherwise null
 * @param id The ID of the [User]
 * @see Users
 */
fun getUser(id: String): User? = getUser(UUID.fromString(id))


/**
 * Attempts to get a [User] from a [Database] using it's [id]
 * @return The [User] if it exists, otherwise null
 * @param id The ID of the [User]
 * @see Users
 */
fun getUser(id: UUID): User? = transaction(db) {
	//Users.selectAll().find { it[Users.id] == id }?.userFromRow()
	Users.select(Users.id eq id).singleOrNull()?.user()
}

/**
 * Removes a [User] from a [Database]
 * @see [Users]
 */

fun User.remove() {
	transaction(db) {
		Users.deleteWhere { id eq this@remove.id }
	}
}


/**
 * Pushes a [User] to a [Database]
 * @see Users
 */

fun User.push() = transaction(db) {
	Users.insert {
		it[id] = this@push.id
		it[displayName] = this@push.displayName
		it[pronouns] = this@push.pronouns
		it[description] = this@push.description
		it[password] = this@push.password
		it[joinDate] = this@push.joinDate.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
		it[admin] = this@push.admin
	}
}

/**
 * Gets all [Users][User] from a [Database]
 * @return a [List] of all [User]s
 * @see Users
 */

fun getAllUsers(): List<User> = transaction(db) {
	Users.selectAll().toList().map { it.user() }
}

/**
 * Gets all [PublicUsers][PublicUser] from a [Database]
 * @return a [List] of all [PublicUser]s
 * @see Users
 * @see PublicUser
 */

fun getAllPublicUsers(): List<PublicUser> = getAllUsers().map { it.public() }

/**
 * Gets all admin [Users][User] from a [Database]
 * @return a [List] of all admin [Users][User]
 * @see Users
 */

fun getAllAdmins(): List<User> = transaction(db) {
	Users.select(Users.admin eq true).toList().map { it.user() }
}

/**
 * Generates a 128 char-long [Token] and pushes it to a [Database]
 * @return a [Token]
 * @see Tokens
 */
fun generateToken(user: User): Token {
	val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()
	val random = SecureRandom()
	val sb = StringBuilder(128)
	for (i in 0 until 128) {
		sb.append(chars[random.nextInt(chars.size)])
	}
	val v = sb.toString()
	transaction(db) {
		Tokens.insert {
			it[userID] = user.id
			it[token] = v
		}
	}
	return Token(user, v)
}

/**
 * Resets a [User]'s [Token]
 * @return a fresh, home-generated [Token]
 * @see Tokens
 */

fun User.resetToken(): Token {
	transaction(db) {
		Tokens.deleteWhere {
			userID eq this@resetToken.id
		}
	}
	return generateToken(this)
}

/**
 * Gets a [Map]<[User], [String]> of all [Tokens][Token] from a [Database]
 * @returns a [Map]<[User], [String]> of all [Tokens][Token]
 * @see Tokens
 */
fun getTokenMap(): Map<User, String> = transaction(db) {
	Tokens.selectAll()
		.associateBy(keySelector = { getUser(it[Tokens.userID])!! }, valueTransform = { it[Tokens.token] })
}


/**
 * Gets a [User]'s [Token] from a [Database]
 * If there is no [Token] in the [Database], a new one will be generated
 * @return the generated or found [Token]
 * @see getTokenMap
 * @see generateToken
 * @see Tokens
 */
fun User.getToken(): Token {
	val tokenMap = getTokenMap()
	val token = tokenMap[this]
	return if (token != null) {
		Token(this, token)
	} else {
		generateToken(this)
	}
}

/**
 * Attempts to find a [Token] from a [String]
 * This string should be the [Token]'s value.
 * @return a [Token] if one is found, otherwise null
 * @see Tokens
 */
fun findToken(token: String): Token? {
	val map = getTokenMap()
	val e = map.entries.find { it.value == token }
	return e?.let { Token(e.key, e.value) }
}

/**
 * Attempts to get a [Lodge] from a [Database] using it's [id]
 * @param id The ID of the requested Lodge
 * @return The Lodge if found, otherwise null
 * @see Lodges
 */

fun getLodge(id: UUID): Lodge? = transaction(db) {
	Lodges.select(Lodges.id eq id).singleOrNull()?.lodge()
}

/**
 * Attempts to get a [Lodge] from a [Database] using it's [id]
 * This is a wrapper of [getLodge]
 * @param id The ID of the requested Lodge
 * @return The Lodge if found, otherwise null
 * @see Lodges
 */
fun getLodge(id: String): Lodge? = getLodge(UUID.fromString(id))


/**
 * Gets all [Lodges][Lodge] from a [Database]
 * @return A [List]<[Lodge]> of all [Lodges][Lodge] found in the [Database]
 * @see Lodges
 */
fun getAllLodges(): List<Lodge> = transaction(db) {
	Lodges.selectAll().toList().map { it.lodge() }
}


/**
 * Gets all admin [Users][User] of a [Lodge]
 * @return a [List]<[User]> of every admin [User] in the specified [Lodge]
 * @see Lodges
 */
fun Lodge.getAdmins(): List<User> = transaction(db) {
	LodgeMembers.select(
		(LodgeMembers.lodgeId eq this@getAdmins.id) and (LodgeMembers.admin eq true)
	).map { getUser(it[LodgeMembers.userID])!! }
}

/**
 * Checks whether a [User] is an admin within a [Lodge]
 * @param user The [User] to check
 * @return whether the [User] is an admin or not
 * @see Lodges
 */
fun Lodge.isAdmin(user: User) = transaction(db) {
	LodgeMembers.select((LodgeMembers.userID eq user.id) and (LodgeMembers.lodgeId eq this@isAdmin.id) and (LodgeMembers.admin eq true))
		.singleOrNull() != null
}

/**
 * Attempts to get a [Cabin] from a [Database] using it's [id]
 * @param id The ID of the requested Cabin
 * @return The Cabin if found, otherwise null
 * @see Cabins
 */
fun getCabin(id: UUID): Cabin? = transaction(db) {
	Cabins.select(Cabins.id eq id).singleOrNull()?.cabin()
}

/**
 * Attempts to get a [Cabin] from a [Database] using it's [id]
 * This is a wrapper of [getCabin]
 * @param id The ID of the requested Cabin
 * @return The Cabin if found, otherwise null
 * @see Cabins
 */
fun getCabin(id: String): Cabin? = getCabin(UUID.fromString(id))

/**
 * Lists all [Cabins][Cabin] within a [Lodge]
 * @return a [List]<[Cabin]> of the [Cabins][Cabin] within a [Lodge]
 * @see Cabins
 */
fun Lodge.getCabins(): List<Cabin> = transaction(db) {
	LodgeCabins.select(LodgeCabins.lodgeId eq this@getCabins.id).toList().map {
		it.cabin()
	}
}

/**
 * Gets all [Message]s from a [Database]
 * This should be chronologically descending
 * @return A [List]<[Message]> of all [Message]s, chronologically descending
 * @see Messages
 */

fun Cabin.getMessages(): List<Message> = transaction(db) {
	CabinMessages.select(CabinMessages.cabinId eq this@getMessages.id)
		.mapNotNull { getMessage(it[CabinMessages.messageId]) }.sortedByDescending { it.creationDate }
}

/**
 *  Sends a [Message] to a [Cabin]
 *  @param user The [User] who sent the [Message]
 *  @param content The contents of the [Message]
 *  @see Messages
 */

fun Cabin.sendMessage(user: User, content: String): Message {
	val message = Message(
		UUID.randomUUID(), user, content, lodge, this, Clock.System.now()
	)
	message.push()
	transaction(db) {
		CabinMessages.insert {
			it[cabinId] = this@sendMessage.id
			it[messageId] = message.id
		}
	}
	return message
}

/**
 * Pushes a [Message] to a [Database]
 * @return The [Message] on which it was called
 * @see Messages
 */
fun Message.push(): Message {
	transaction(db) {
		Messages.insert {
			it[id] = this@push.id
			it[userId] = user.id
			it[content] = this@push.content
			it[lodgeId] = lodge.id
			it[cabinId] = cabin.id
			it[creationDate] =
				this@push.creationDate.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
		}
	}
	return this
}

/**
 * Attempts to get a [User] from a [Database] using it's [id]
 * @return The [User] if it exists, otherwise null
 * @param id The ID of the [User]
 * @see Users
 */
fun getMessage(id: UUID): Message? = transaction(db) {
	Messages.select(Messages.id eq id).singleOrNull()?.message()
}

/**
 * Attempts to get a [Message] from a [Database] using it's [id]
 * This is a wrapper of [getMessage]
 * @return The [Message] if it exists, otherwise null
 * @param id The ID of the [Message]
 * @see Messages
 */
fun getMessage(id: String): Message? = getMessage(UUID.fromString(id))

/**
 * Checks whether a [User] is a member of a [Lodge]
 * @return whether the [User] is a member of the [Lodge]
 * @param lodge
 * @see LodgeMembers
 */
fun User.isMember(lodge: Lodge): Boolean = transaction(db) {
	LodgeMembers.select(
		(LodgeMembers.lodgeId eq lodge.id) and (LodgeMembers.userID eq this@isMember.id)
	).singleOrNull() != null
}

/**
 * @return a [List]<[User]> of all [User]s within a [Lodge]
 * @see LodgeMembers
 */
fun Lodge.getAllMembers(): List<User> = transaction(db) {
	LodgeMembers.select(LodgeMembers.lodgeId eq this@getAllMembers.id).mapNotNull { getUser(it[LodgeMembers.userID]) }
}

/**
 * @return a [List]<[Lodge]> of all public [Lodge]s
 * @see Lodges
 */
fun getAllPublicLodges(): List<Lodge> = transaction(db) {
	Lodges.select(Lodges.public_ eq true).map {
		it.lodge()
	}
}

/**
 * Joins a [User] to a [Lodge]
 * @see Lodges
 * @see LodgeMembers
 */
fun User.join(lodge: Lodge) {
	if (!isMember(lodge)) {
		transaction(db) {
			LodgeMembers.insert {
				it[lodgeId] = lodge.id
				it[userID] = this@join.id
				it[admin] = lodge.getAllMembers().isEmpty()
				it[joinDate] = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
			}
		}
	}
}

/**
 * Creates a [Lodge] and adds it to a [Database]
 * @param name The name of the [Lodge]
 * @param description (optional) The description of the [Lodge]
 * @param iconUrl (optional) The icon url for the [Lodge]
 * @param public (optional, defaults to true) Whether the [Lodge] can be discovered without an invitation/id
 * @return the created [Lodge]
 * @see Lodges
 */
fun User.createLodge(name: String, description: String = "", iconUrl: String = "", public: Boolean = true): Lodge {
	val lodge = Lodge(
		UUID.randomUUID(), name, description, iconUrl, Clock.System.now(), public
	)
	lodge.push()
	this.join(lodge)
	return lodge
}


/**
 * Creates a [Cabin] within a [Lodge] and adds it to a [Database]
 * @param user The [User] creating the [Cabin]
 * @param topic (optional) The topic of the [Cabin]
 * @param requireAdmin (optional, defaults to false) Whether the [Cabin] requires admin permissions to access
 * @return the created [Cabin] if the [User] has permissions, otherwise null
 */
fun Lodge.createCabin(user: User, name: String, topic: String = "", requireAdmin: Boolean = false): Cabin? {
	return if (this.isAdmin(user)) {
		val cabin: Cabin = Cabin(
			UUID.randomUUID(), name, topic, this@createCabin, Clock.System.now(), requireAdmin
		)
		transaction(db) {
			LodgeCabins.insert {
				it[cabinId] = cabin.id
				it[lodgeId] = this@createCabin.id
			}
		}
		cabin.push()
	} else {
		null
	}
}

/**
 * Attempts to get [Cabin] within a [Lodge] by its id
 * @param id The ID of the [Cabin]
 * @return The [Cabin] if found, otherwise null
 */
fun Lodge.getCabin(id: UUID): Cabin? = transaction(db) {
	Cabins.select(
		(Cabins.id eq id) and (Cabins.lodgeId eq this@getCabin.id)
	).singleOrNull()?.cabin()
}

/**
 * Attempts to get [Cabin] within a [Lodge] by its id
 * This is wrapper of [Lodge.getCabin]
 * @param id The ID of the [Cabin]
 * @return The [Cabin] if found, otherwise null
 */
fun Lodge.getCabin(id: String): Cabin? = this.getCabin(UUID.fromString(id))

/**
 * Takes a [Message], and turns into a [PublicMessage]
 * @return a PublicMessage
 * @see PublicMessage
 */
fun Message.public(): PublicMessage {
	return PublicMessage(id, user.public(), content, lodge, cabin, creationDate)
}

/**
 * @return all [PublicMessage]s within a [Cabin]
 * @see Message.public
 * @see getMessages
 */
fun Cabin.getPublicMessages(): List<PublicMessage> = getMessages().map { it.public() }

fun Lodge.push(): Lodge {
	transaction(db) {
		Lodges.insert {
			it[id] = this@push.id
			it[name_] = this@push.name
			it[description] = this@push.description
			it[iconUrl] = this@push.iconUrl
			it[creationDate] =
				this@push.creationDate.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
			it[public_] = public
		}
	}
	return this
}

fun Cabin.push(): Cabin {
	transaction(db) {
		Cabins.insert {
			it[id] = this@push.id
			it[name_] = this@push.name
			it[topic] = this@push.topic
			it[lodgeId] = lodge.id
			it[creationDate] =
				this@push.creationDate.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
			it[requireAdmin] = this@push.requireAdmin
		}
	}
	return this
}

fun ResultRow.user(): User = User(
	this[Users.id],
	this[Users.displayName],
	this[Users.pronouns],
	this[Users.description],
	this[Users.password],
	this[Users.joinDate].instantFromColumn()
)

fun ResultRow.token(): Token = Token(
	getUser(this[Tokens.userID])!!, this[Tokens.token]
)

fun ResultRow.lodge(): Lodge = Lodge(
	this[Lodges.id],
	this[Lodges.name_],
	this[Lodges.description],
	this[Lodges.iconUrl],
	this[Lodges.creationDate].instantFromColumn(),
	this[Lodges.public_]
)

fun ResultRow.cabin(): Cabin = Cabin(
	this[Cabins.id],
	this[Cabins.name_],
	this[Cabins.topic],
	getLodge(this[Cabins.lodgeId])!!,
	this[Cabins.creationDate].instantFromColumn(),
	this[Cabins.requireAdmin]
)

fun ResultRow.message(): Message = Message(
	this[Messages.id],
	getUser(this[Messages.userId])!!,
	this[Messages.content],
	getLodge(this[Messages.lodgeId])!!,
	getCabin(this[Messages.cabinId])!!,
	this[Messages.creationDate].instantFromColumn()
)

fun PipelineContext<Unit, ApplicationCall>.tokenFromCall(): Token? {
	val tokenS = call.request.headers["Authorization"] ?: return null
	return findToken(tokenS)
}

fun Color.getKotlinColor(): kotlinx.css.Color = kotlinx.css.Color("#$hex")

fun LocalDateTime.instantFromColumn() = toKotlinLocalDateTime().toInstant(TimeZone.currentSystemDefault())