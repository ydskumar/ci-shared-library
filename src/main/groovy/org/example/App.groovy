package org.example

class App {
	static void main(String[] args) {
		println "CI/CD Deployment Simulation"
		
		def config = [
			appName : "my-app",
            version : "1.0.0",
            port    : 8081,
            env     : "test"
		]
		
		def pipeline = new Pipeline(config)
		
		try {
            pipeline.execute()
        } catch (Exception e) {
            println "Pipeline failed: ${e.message}"
        }
	}
}
