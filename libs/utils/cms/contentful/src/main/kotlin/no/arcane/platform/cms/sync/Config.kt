package no.arcane.platform.cms.sync

import no.arcane.platform.utils.config.loadConfig

data class ContentfulAlgoliaSyncConfig(
    val contentful: ContentfulConfig,
    val algolia: AlgoliaConfig,
)

data class ContentfulConfig(
    val spaceId: String,
    val token: String,
)

data class AlgoliaConfig(
    val applicationId: String,
    val apiKey: String,
)