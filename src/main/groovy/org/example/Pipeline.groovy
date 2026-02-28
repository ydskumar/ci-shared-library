package org.example

class Pipeline {
	
	Map config
	DeploymentService deploymentService
	
	Pipeline(Map config) {
		this.config = config
		this.deploymentService = new DeploymentService()
	}

	void execute() {
		validate()
		build()
		deploy()
		healthCheck()
		println("Pipeline execution completed successfully.")
	}

	private void validate() {
		println("Validating configuration...")
		if (!config.containsKey('appName') || !config.containsKey('version')) {
			throw new IllegalArgumentException("Configuration must include 'appName' and 'version'.")
		}
		println("Configuration validated successfully.")
	}

	private void build() {
		println "Building Docker image ${config.appName}:${config.version}"
		println("Build completed successfully.")
	}

	private void deploy() {
		println "Deploying ${config.appName}:${config.version} to environment ${config.env}"
		deploymentService.deploy(config)
		println("Deployment completed successfully.")
	}

	private void healthCheck() {
		println "Performing health check for ${config.appName}:${config.version}"
		boolean isHealthy = deploymentService.healthCheck(config)
		if (!isHealthy) {
			println "Health check failed. Rolling back..."
            deploymentService.rollback()
            throw new RuntimeException("Deployment unstable")
		}
		println("Health check passed successfully.")
	}
}
