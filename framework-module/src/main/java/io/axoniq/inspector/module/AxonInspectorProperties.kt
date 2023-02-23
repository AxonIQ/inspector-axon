package io.axoniq.inspector.module

class AxonInspectorProperties(
    val host: String,
    val port: Int,
    val secure: Boolean,
    val workspaceId: String,
    val environmentId: String,
    val accessToken: String,
    val applicationName: String,
)
