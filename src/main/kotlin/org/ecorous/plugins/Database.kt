package org.ecorous.plugins

import com.password4j.Password
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import me.gosimple.nbvcxz.resources.DictionaryBuilder
import org.ecorous.types.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

lateinit var db: Database

fun Application.configureDatabases() {
	db = Database.connect(
		"jdbc:postgresql://localhost:5432/postgres",
		"org.postgresql.Driver",
		"postgres",
		"example"
	)
	transaction(db) {
		SchemaUtils.drop(Tokens)
		SchemaUtils.create(Users)
		SchemaUtils.create(Tokens)
		SchemaUtils.create(Lodges)
		SchemaUtils.create(LodgeMembers)
		SchemaUtils.create(LodgeCabins)
		SchemaUtils.create(Cabins)
		SchemaUtils.create(CabinMessages)
		SchemaUtils.create(Messages)
	}
	routing {
		get("/users") {
			headers {
				set("Accept", "application/json")
			}
			call.respond(getAllPublicUsers())
		}
		
		post("/user") {
			val json = call.receiveText()
			val input = Json.decodeFromString<UserInput>(json)
			if (input.displayName == null) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf("error" to "400", "message" to "bad request: displayName not present :help:")
				)
				return@post
			}
			if (input.displayName.length > 32) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf(
						"error" to "400",
						"message" to "bad request: displayName too long (max 32 characters) :save_me:"
					)
				)
				return@post
			}
			if (input.password == null) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf("error" to "400", "message" to "bad request: password not present :i_am_not_okay:")
				)
			}
			val user = createUser(input) ?: return@post
			user.push()
			call.respond(HttpStatusCode.Created, user.public())
		}
		
		put("/user") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(HttpStatusCode.Unauthorized)
				return@put
			}
			
			var newDisplayName: String = token.user.displayName
			var newDescription: String = token.user.description
			var newPronouns: String = token.user.pronouns
			
			val json = call.receiveText()
			val input = Json.decodeFromString<UserPutInput>(json)
			
			// Update user properties if provided in the input
			input.description?.let { newDescription = it }
			input.displayName?.let { newDisplayName = it }
			input.pronouns?.let { newPronouns = it }
			
			// Remove the current user from the database
			token.user.remove()
			
			// Create the updated user
			val updatedUser = User(
				id = token.user.id,
				displayName = newDisplayName,
				pronouns = newPronouns,
				description = newDescription,
				password = token.user.password,
				joinDate = token.user.joinDate
			)
			
			// Push the updated user to the database
			updatedUser.push()
			
			call.respond(updatedUser.public())
		}
		
		post("/login") {
			val json = call.receiveText()
			val input = Json.decodeFromString<UserLoginInput>(json)
			if (input.id == null) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf("error" to "400", "message" to "bad request: no id present")
				)
				return@post
			}
			if (input.password == null) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf("error" to "400", "message" to "bad request: no password present")
				)
				return@post
			}
			val user = getUser(input.id)
			if (user == null) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf("error" to "400", "message" to "bad request: invalid user id")
				)
				return@post
			}
			val passwordCorrect = Password.check(input.password, user.password).withArgon2()
			if (!passwordCorrect) {
				call.respond(
					HttpStatusCode.Unauthorized,
					mapOf("error" to "401", "message" to "unauthorized: incorrect password")
				)
			}
			val token = user.getToken()
			call.respond(HttpStatusCode.OK, mapOf("id" to token.user.id.toString(), "token" to token.token))
		}
		
		get("/user/{id}") {
			val token: Token? = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized,
					mapOf("error" to "401", "message" to "unauthorized: invalid token provided (Authorization header)")
				)
				return@get
			} else {
				val id = call.parameters["id"]
				if (id == null) {
					call.respond(
						HttpStatusCode.BadRequest,
						mapOf("error" to "400", "message" to "bad request: id is null")
					)
					return@get
				} else if (token.user.id.toString() != id) {
					call.respond(
						HttpStatusCode.Forbidden,
						mapOf("error" to "403", "message" to "forbidden: attempted to access non-self user")
					)
					return@get
				}
				call.respond(token.user.public())
			}
		}
		get("/user") {
			val token: Token? = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized,
					mapOf("error" to "401", "message" to "unauthorized: invalid token provided (Authorization header)")
				)
				return@get
			}
			call.respond(token.user.public())
		}
		post("/user/reset") {
			val token: Token? = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized,
					mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@post
			}
			token.user.resetToken()
			val newToken = token.user.getToken()
			call.respond(mapOf("id" to newToken.user.id.toString(), "token" to newToken.token))
			
		}
		post("/lodge/{id}/join") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@post
			}
			val param = call.parameters["id"]
			if (param == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: id is null"
					)
				)
				return@post
			}
			val lodge = getLodge(param)
			if (lodge == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid lodge id"
					)
				)
				return@post
			}
			token.user.join(lodge)
		}
		post("/lodge") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@post
			}
			val json = call.receiveText()
			val input = Json.decodeFromString<LodgeInput>(json)
			var description = ""
			var public = true
			if (input.name == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: no name provided"
					)
				)
				return@post
			}
			
			if (input.description != null) {
				description = input.description
			}
			if (input.public != null) {
				public = input.public
			}
			
			val lodge = token.user.createLodge(input.name, description, "", public)
			call.respond(lodge)
		}
		post("/lodge/{id}/cabin") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@post
			}
			val param = call.parameters["id"]
			if (param == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: lodge id not present"
					)
				)
				return@post
			}
			val lodge = getLodge(param)
			if (lodge == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid lodge id"
					)
				)
				return@post
			}
			
			val json = call.receiveText()
			val input = Json.decodeFromString<CabinInput>(json)
			var topic = ""
			var requireAdmin = false
			if (input.name == null) {
				call.respond(
					HttpStatusCode.BadRequest,
					mapOf("error" to "400", "message" to "bad request: no name provided")
				)
				return@post
			}
			input.topic?.let {
				topic = input.topic
			}
			input.requireAdmin?.let {
				requireAdmin = input.requireAdmin
			}
			val cabin = lodge.createCabin(token.user, input.name, topic, requireAdmin)
			if (cabin == null) {
				call.respond(
					HttpStatusCode.Forbidden,
					mapOf("error" to "403", "message" to "forbidden: only lodge admins can create cabins")
				)
				return@post
			}
			call.respond(cabin)
		}
		get("/lodge/{id}") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@get
			}
			val param = call.parameters["id"]
			if (param == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: id is null"
					)
				)
				return@get
			}
			val lodge = getLodge(param)
			if (lodge == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid lodge id"
					)
				)
				return@get
			}
			if (!token.user.isMember(lodge) && !lodge.public) {
				call.respond(
					HttpStatusCode.Forbidden, mapOf(
						"error" to "403",
						"message" to "forbidden: tried to access non-public lodge"
					)
				)
			}
			call.respond(lodge)
		}
		get("/lodge/{lodgeId}/cabin/{cabinId}") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@get
			}
			val lodgeParam = call.parameters["lodgeId"]
			if (lodgeParam == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: lodgeId is null"
					)
				)
				return@get
			}
			val cabinParam = call.parameters["cabinId"]
			if (cabinParam == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: cabinId is null"
					)
				)
				return@get
			}
			val lodge = getLodge(lodgeParam)
			if (lodge == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid lodge id"
					)
				)
				return@get
			}
			val cabin = lodge.getCabin(cabinParam)
			if (cabin == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid cabin id"
					)
				)
				return@get
			}
			call.respond(cabin)
		}
		post("/lodge/{lodgeId}/cabin/{cabinId}/message") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@post
			}
			val json = call.receiveText()
			val input = Json.decodeFromString<MessageInput>(json)
			if (input.content == null) {
				call.respond(HttpStatusCode.BadRequest, mapOf(
					"error" to "400",
					"message" to "bad request: cannot send empty message"
				))
				return@post
			}
			
			val lodgeParam = call.parameters["lodgeId"]
			if (lodgeParam == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: lodgeId is null"
					)
				)
				return@post
			}
			val cabinParam = call.parameters["cabinId"]
			if (cabinParam == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: cabinId is null"
					)
				)
				return@post
			}
			val lodge = getLodge(lodgeParam)
			if (lodge == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid lodge id"
					)
				)
				return@post
			}
			val cabin = lodge.getCabin(cabinParam)
			if (cabin == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid cabin id"
					)
				)
				return@post
			}
			call.respond(cabin.sendMessage(token.user, input.content).public())
		}
		get("/lodge/{lodgeId}/cabin/{cabinId}/messages") {
			val token = tokenFromCall()
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized, mapOf(
						"error" to "401",
						"message" to "unauthorized: invalid token provided (Authorization header)"
					)
				)
				return@get
			}
			val lodgeParam = call.parameters["lodgeId"]
			if (lodgeParam == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: lodgeId is null"
					)
				)
				return@get
			}
			val cabinParam = call.parameters["cabinId"]
			if (cabinParam == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: cabinId is null"
					)
				)
				return@get
			}
			val lodge = getLodge(lodgeParam)
			if (lodge == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid lodge id"
					)
				)
				return@get
			}
			val cabin = lodge.getCabin(cabinParam)
			if (cabin == null) {
				call.respond(
					HttpStatusCode.BadRequest, mapOf(
						"error" to "400",
						"message" to "bad request: invalid cabin id"
					)
				)
				return@get
			}
			call.respond(cabin.getPublicMessages())
		}
	}
}


private suspend fun PipelineContext<Unit, ApplicationCall>.createUser(input: UserInput): User? {
	val selfDictionary = DictionaryBuilder()
		.setDictionaryName("user info")
		.setExclusion(true)
		.addWord(input.displayName, 0)
		.createDictionary()
	
	val estimatorConfig = ConfigurationBuilder()
		.setDictionaries(ConfigurationBuilder.getDefaultDictionaries() + selfDictionary)
		.createConfiguration()
	val nbvcxz = Nbvcxz(estimatorConfig)
	if (!nbvcxz.estimate(input.password).isMinimumEntropyMet) {
		call.respond(
			HttpStatusCode.BadRequest,
			mapOf(
				"error" to "400",
				"message" to "bad request: password too weak :get_professional_help_immediately:"
			)
		)
		return null
	}
	
	val result = Password.hash(input.password).addRandomSalt().withArgon2().result
	
	return User(UUID.randomUUID(), input.displayName!!, "", "", result, Clock.System.now())
}