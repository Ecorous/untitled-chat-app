package org.ecorous.plugins

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.mustache.Mustache
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*
import kotlinx.html.*

fun Application.configureTemplating() {
	install(Mustache) {
		mustacheFactory = DefaultMustacheFactory("templates/mustache")
	}
	routing {
		get("/html-dsl") {
			call.respondHtml {
				body {
					h1 { +"HTML" }
					ul {
						for (n in 1..10) {
							li { +"$n" }
						}
					}
				}
			}
		}
		get("/styles.css") {
			call.respondCss {
				body {
					backgroundColor = Color.darkBlue
					margin(0.px)
				}
				rule("h1.page-title") {
					color = Color.white
				}
			}
		}
		
		get("/html-css-dsl") {
			call.respondHtml {
				head {
					link(rel = "stylesheet", href = "/styles.css", type = "text/css")
				}
				body {
					h1(classes = "page-title") {
						+"Hello from Ktor!"
					}
				}
			}
		}
		get("/html-mustache") {
			call.respond(MustacheContent("index.hbs", mapOf("user" to MustacheUser(1, "user1"))))
		}
	}
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
	this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}


data class MustacheUser(val id: Int, val name: String)
