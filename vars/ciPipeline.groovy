def call(Map config = [:]) {

    pipeline {
        agent any

        options {
            disableConcurrentBuilds()
            timestamps()
        }

        environment {
            IMAGE_NAME      = "${config.appName}"
            CONTAINER_NAME  = "${config.containerName ?: config.appName}"
            DOCKER_NETWORK  = "${config.network ?: 'jenkins-custom_default'}"
            PORT            = "${config.port ?: "8080"}"
            DOCKER_USER     = "${config.dockerUser}"
            DOCKER_CRED_ID  = "${config.dockerCredId}"
            BASE_URL= "http://${env.CONTAINER_NAME}:${env.PORT}"
        }

        stages {

            stage('Checkout') {
                steps {
                    checkout scm
                }
            }

            stage('Prepare Metadata') {
                steps {
                    script {
                        def commit = sh(
                                script: "git rev-parse --short HEAD",
                                returnStdout: true
                        ).trim()

                        env.IMAGE_TAG = "${env.BUILD_NUMBER}-${commit}"
                        echo "Image Tag: ${env.IMAGE_TAG}"
                    }
                }
            }

            stage('Docker Login') {
                steps {
                    dockerLogin(env.DOCKER_CRED_ID)
                }
            }

            stage('Build & Push') {
                steps {
                    buildAndPush(
                            env.DOCKER_USER,
                            env.IMAGE_NAME,
                            env.IMAGE_TAG
                    )
                }
            }

            stage('Capture Previous Image') {
                steps {
                    script {
                        env.PREVIOUS_IMAGE = sh(
                                script: "docker inspect --format='{{.Config.Image}}' ${env.CONTAINER_NAME} 2>/dev/null || echo 'none'",
                                returnStdout: true
                        ).trim()

                        echo "Previous image: ${env.PREVIOUS_IMAGE}"
                    }
                }
            }

            stage('Deploy') {
                steps {
                    script {
                        deployContainer(
                                "${env.DOCKER_USER}/${env.IMAGE_NAME}:${env.IMAGE_TAG}",
                                env.CONTAINER_NAME,
                                env.DOCKER_NETWORK,
                                env.PORT,
                                env.IMAGE_TAG
                        )
                    }
                }
            }

            stage('Health Check') {
                steps {
                    script {
                        def status = healthCheck("http://${env.CONTAINER_NAME}:${env.PORT}/health")

                        if (status != 0) {
                            rollback(
                                    env.PREVIOUS_IMAGE,
                                    env.CONTAINER_NAME,
                                    env.DOCKER_NETWORK,
                                    env.PORT
                            )
                            error("Health check failed.")
                        }
                    }
                }
            }
       

            // If tests exist in same repo 
            stage('API Tests (Local)') {
                steps {
                    script {

                        def result = runApiTests(config.apiTestCommand ?: "echo 'No API tests configured'")

                        if (result != 0) {
                            echo "API tests failed. Initiating rollback..."

                            rollback(
                                    env.PREVIOUS_IMAGE,
                                    env.CONTAINER_NAME,
                                    env.DOCKER_NETWORK,
                                    env.PORT
                            )

                            error("API tests failed after deployment.")
                        }
                    }
                }
            }

            stage('API Tests (QA Repo)') {
                when {
                    expression { config.qaRepoUrl != null }
                }
                steps {
                    script {

                        dir("qa-tests") { // Create a subdirectory for QA tests as main workspace contains the app repo and QA repo should not overwrite it

                            sh """
                                BASE_URL=http://my-app:${env.PORT} mvn clean test
                            """
                            checkout([
                                    $class: 'GitSCM',
                                    branches: [[name: config.qaBranch ?: '*/master']],
                                    userRemoteConfigs: [[
                                            url: config.qaRepoUrl,
                                            credentialsId: config.qaCredId
                                    ]]
                            ])

                             def result = sh(
                                script: "mvn clean test",
                                returnStatus: true
                            )

                            sh 'ls -R target || true'

                            if (result != 0) {
                                echo "QA API tests failed. Rolling back..."

                                rollback(
                                        env.PREVIOUS_IMAGE,
                                        env.CONTAINER_NAME,
                                        env.DOCKER_NETWORK,
                                        env.PORT
                                )

                                error("QA API tests failed after deployment.")
                            }
                        }
                    }
                }

                post {
                    always {

                        junit allowEmptyResults: true,
                            testResults: 'qa-tests/target/surefire-reports/*.xml'

                        allure([
                            includeProperties: false,
                            jdk: '',
                            reportBuildPolicy: 'ALWAYS',
                            commandline: 'allure',
                            results: [[path: 'qa-tests/target/allure-results']]
                        ])

                        archiveArtifacts artifacts: 'qa-tests/target/**/*', fingerprint: true

                    }
                }
            }
            
        }
        
        post {
            success {
                echo "Release ${IMAGE_TAG} deployed successfully."
            }
            failure {
                script {
                    rollback(
                            env.PREVIOUS_IMAGE,
                            env.CONTAINER_NAME,
                            env.DOCKER_NETWORK,
                            env.PORT
                    )
                }
            }
        }
    }
}