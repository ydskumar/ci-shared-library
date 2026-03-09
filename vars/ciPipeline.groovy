def call(Map config = [:]) {

pipeline {

    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        IMAGE_NAME     = "${config.appName}"
        CONTAINER_NAME = "${config.containerName ?: config.appName}"
        DOCKER_NETWORK = "${config.network ?: 'jenkins-custom_default'}"
        PORT           = "${config.port ?: "8080"}"
        DOCKER_USER    = "${config.dockerUser}"
        DOCKER_CRED_ID = "${config.dockerCredId}"
        BASE_URL       = "http://${env.CONTAINER_NAME}:${env.PORT}"
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

                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"

                    echo "Image Tag: ${env.IMAGE_TAG}"
                    echo "Application URL: ${env.BASE_URL}"
                }
            }
        }

        stage('Docker Login') {
            steps {
                dockerLogin(env.DOCKER_CRED_ID)
            }
        }

        stage('Build & Push Image') {
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

        stage('Deploy Container') {
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

        stage('Wait for Application') {
            steps {
                script {

                    sh """
                    echo "Waiting for application..."

                    for i in {1..30}; do

                        status=\$(curl -s -o /dev/null -w "%{http_code}" ${env.BASE_URL}/health)

                        if [ "\$status" = "200" ]; then
                            echo "Application ready."
                            exit 0
                        fi

                        echo "Still starting (\$i)..."
                        sleep 2

                    done

                    echo "Application failed to start"
                    exit 1
                    """

                }
            }
        }

        stage('Health Check') {
            steps {
                script {

                    def status = healthCheck("${env.BASE_URL}/health")

                    if (status != 0) {
                        error("Health check failed.")
                    }

                }
            }
        }

        stage('API Tests') {

            parallel {

                stage('Local API Tests') {
                    steps {
                        script {

                            echo "Running local tests against ${env.BASE_URL}"

                            def result = runApiTests(
                                config.apiTestCommand ?: "echo 'No local API tests configured'"
                            )

                            if (result != 0) {
                                error("Local API tests failed.")
                            }

                        }
                    }
                }

                stage('QA Repo Tests') {

                    when {
                        expression { config.qaRepoUrl != null }
                    }

                    agent {
                        docker {
                            image 'maven:3.9-eclipse-temurin-17'
                            args "--network ${env.DOCKER_NETWORK}"
                            reuseNode true
                        }
                    }

                    steps {

                        dir("qa-tests") {

                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: config.qaBranch ?: '*/master']],
                                userRemoteConfigs: [[
                                    url: config.qaRepoUrl,
                                    credentialsId: config.qaCredId
                                ]]
                            ])

                            sh """
                            echo "Running QA automation against ${env.BASE_URL}"
                            BASE_URL=${env.BASE_URL} mvn clean test
                            """

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

        }

        stage('Cleanup') {
            steps {

                sh '''
                echo "Cleaning temporary resources..."
                docker container prune -f || true
                docker image prune -f || true
                '''

                cleanWs()

            }
        }

    }

    post {

        success {
            echo "Release ${env.IMAGE_TAG} deployed successfully."
        }

        failure {

            script {

                echo "Pipeline failed. Rolling back..."

                sh "docker logs ${env.CONTAINER_NAME} || true"

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