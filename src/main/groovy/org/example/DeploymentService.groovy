package org.example

class DeploymentService {

    String previousVersion = "0.9.0"

    void deploy(Map config) {
        println "Deploying ${config.appName}:${config.version} to ${config.env} environment"
    }

    boolean healthCheck(Map config) {
        println "Checking application health..."
        return true  // Change to false to simulate failure
    }

    void rollback() {
        println "Rolling back to version ${previousVersion}"
    }
}
