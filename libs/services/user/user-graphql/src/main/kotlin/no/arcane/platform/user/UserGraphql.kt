package no.arcane.platform.user

import io.ktor.application.*
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import no.arcane.platform.user.UserService.fetchUser
import no.arcane.platform.utils.graphql.GraphqlModulesRegistry
import no.arcane.platform.utils.graphql.readResource

fun Application.module() {
    GraphqlModulesRegistry.registerSchema(readResource("/user.graphqls"))
    GraphqlModulesRegistry.registerDataFetcher("user") { env ->
        val userId = UserId(env.graphQlContext["userId"])
        async { userId.fetchUser() }.asCompletableFuture()
    }
}