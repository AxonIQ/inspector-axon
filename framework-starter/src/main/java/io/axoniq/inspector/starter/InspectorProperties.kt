package io.axoniq.inspector.starter

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties("axon.inspector")
class InspectorProperties {
//    TODO - provide documentation
    var host: String = "localhost"
    var port: Int = 7000
    var secure: Boolean = true
    var workspaceId: String = ""
    var environmentId: String = ""
    var accessToken: String = ""
}
