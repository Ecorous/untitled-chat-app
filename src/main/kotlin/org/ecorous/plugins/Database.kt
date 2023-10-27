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

fun Application.configureDatabases() {
	val database = Database.connect(
		"jdbc:postgresql://localhost:5432/postgres",
		"org.postgresql.Driver",
		"postgres",
		"example"
	)
	transaction(database) {
		SchemaUtils.create(Users)
		SchemaUtils.create(Tokens)
	}
	val userManager = UserManager(database)
	val tokenManager = TokenManager(database)
	routing {
		get("/users") {
			headers {
				set("Accept", "application/json")
			}
			call.respond(userManager.getPublicUsers())
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
			userManager.pushToDB(user)
			call.respond(HttpStatusCode.Created, user.public())
		}
		
		put("/user") {
			val tokenS = call.request.headers["Authorization"]
			if (tokenS == null) {
				call.respond(HttpStatusCode.Unauthorized)
				return@put
			}
			
			val token = tokenManager.getTokenByString(tokenS)
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
			userManager.removeFromDB(token.user)
			
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
			userManager.pushToDB(updatedUser)
			
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
			val user = userManager.getUserById(input.id)
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
			val token = tokenManager.getToken(user)
			call.respond(HttpStatusCode.OK, mapOf("id" to token.user.id.toString(), "token" to token.token))
		}
		
		get("/user/{id}") {
			val token: Token? = getTokenFromCall(tokenManager)
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
			val token: Token? = getTokenFromCall(tokenManager)
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized,
					mapOf("error" to "401", "message" to "unauthorized: invalid token provided (Authorization header)")
				)
				return@get
			} else {
				call.respond(token.user.public())
			}
		}
		post("/user/reset") {
			val token: Token? = getTokenFromCall(tokenManager)
			if (token == null) {
				call.respond(
					HttpStatusCode.Unauthorized,
					mapOf("error" to "401", "message" to "unauthorized: invalid token provided (Authorization header)")
				)
				return@post
			} else {
				tokenManager.resetToken(token.user)
				val newToken = tokenManager.getToken(token.user)
				call.respond(mapOf("id" to newToken.user.id.toString(), "token" to newToken.token))
			}
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
			mapOf("error" to "400", "message" to "bad request: password too weak :get_professional_help_immediately:")
		)
		return null
	}
	
	val result = Password.hash(input.password).addRandomSalt().withArgon2().result
	
	return User(UUID.randomUUID(), input.displayName!!, "", "", result, Clock.System.now())
}