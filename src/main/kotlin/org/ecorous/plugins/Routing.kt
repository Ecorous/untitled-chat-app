package org.ecorous.plugins

import com.catppuccin.Palette
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*
import kotlinx.html.*
import org.ecorous.types.getKotlinColor

fun Application.configureRouting() {
	install(StatusPages) {
		exception<Throwable> { call, cause ->
			call.respond(
				HttpStatusCode.InternalServerError, mapOf("error" to "500", "message" to cause.localizedMessage)
			)
		}
		status(HttpStatusCode.MethodNotAllowed) { call, code ->
			call.respond(
				code, mapOf(
					"error" to code.value.toString(), "message" to "method not allowed"
				)
			)
		}
	}
	
	val mocha = Palette.MOCHA
	routing {
		staticResources("/web/js", "js", index = "index.js")
		get("/") {
			call.respondRedirect("/web", true)
		}
		get("/web/css/style.css") {
			call.respondCss {
				root {
					backgroundColor = mocha.base.getKotlinColor()
					color = mocha.text.getKotlinColor()
					fontFamily = "monospace"
				}
				"input, textarea" {
					backgroundColor = mocha.surface0.getKotlinColor()
					color = mocha.subtext0.getKotlinColor()
					minWidth = LinearDimension("28rem")
					borderColor = mocha.surface1.getKotlinColor()
				}
				
				"#logout" {
					display = Display.none
				}
				
				button {
					padding(LinearDimension("10px"))
					backgroundColor = mocha.surface0.getKotlinColor()
					borderRadius = LinearDimension("10px")
					border = "none"
					color = mocha.text.getKotlinColor()
				}
				"label.userinfo-label" {
					fontSize = LinearDimension("large")
					fontWeight = FontWeight.bold
				}
				textarea {
					resize = Resize.none
				}
				"svg.lucide" {
					backgroundColor = mocha.surface0.getKotlinColor()
					padding(LinearDimension("5px"))
					borderRadius = LinearDimension("10px")
				}
				"svg.lucide:hover" {
					cursor = Cursor.pointer
				}
			}
		}
		get("/web") {
			call.respondHtml {
				head {
					link(rel = "stylesheet", href = "/web/css/style.css", type = "text/css")
				}
				body {
					onLoad = "onjsload()"
					h1 {
						id = "wawa_header"
						text("hey!")
					}
					i {
						attributes["alt"] = "Manage User"
						attributes["float"] = "right"
						attributes["data-lucide"] = "settings"
						
						onClick = "window.location.href = '/web/user'"
					}
					a {
						href = "/web/user"
						text("Manage User")
					}
					script { src = "/web/js" }
					script { src = "https://unpkg.com/lucide@latest" }
				}
			}
		}
		get("/web/user") {
			call.respondHtml {
				head {
					link(rel = "stylesheet", href = "/web/css/style.css", type = "text/css")
				}
				body {
					onLoad = "loadUserPage()"
					h1 {
						id = "welcome-header"
						text("Welcome, {}!")
					}
					label(classes = "userinfo-label") {
						text("DISPLAY NAME")
					}
					br{}
					input {
						type = InputType.text
						placeholder = "Set your username"
						id = "displayName-input"
					}
					br{}
					br{}
					label(classes = "userinfo-label") {
						text("PRONOUNS")
					}
					br{}
					input {
						type = InputType.text
						placeholder = "Share how you'd like to be referred to"
						id = "pronouns-input"
					}
					br{}
					br{}
					label(classes = "userinfo-label") {
						text("BIO")
					}
					br{}
					textArea {
						placeholder = "Describe yourself"
						rows = "5"
						id = "bio-input"
					}
					br {}
					br {}
					button {
						id = "update-button"
						onClick = "updateUser()"
						text("Update")
					}
					button {
						id = "logout-button"
						onClick = "logout()"
						text("Logout")
					}
					script { src = "/web/js" }
					script { src = "/web/js/user.js" }
					script { src = "https://unpkg.com/lucide@latest" }
				}
			}
		}
		
		get("/web/login") {
			call.respondHtml {
				head {
					link(rel = "stylesheet", href = "/web/css/style.css", type = "text/css")
				}
				body {
					onLoad = "globalLoad()"
					div {
						id = "login-div"
						input(classes = "input_userid") {
							type = InputType.text
							id = "input_userid"
							placeholder = "User ID (e.g. 0354429c-f60b-4364-b298-0ecfed113348)"
						}
						br {}
						input(classes = "input_password") {
							type = InputType.password
							id = "input_password"
							placeholder = "Password"
						}
						br {}
						br {}
						button {
							onClick = "loginButton()"
							text("Login")
						}
						p { id = "snuggles" }
						div {
							img {
								id = "errorimg"
								style = "display:none"
							}
							p {
								id = "errortext"
								style = "display:none"
							}
						}
					}
					button {
						id = "logout"
						onClick = "logout()"
						text("Logout")
					}
					script { src = "/web/js" }
					script { src = "/web/js/login.js" }
					script { src = "https://unpkg.com/lucide@latest" }
				}
			}
		}
		
	}
}
