def call(String credId) {
    withCredentials([usernamePassword(
            credentialsId: credId,
            usernameVariable: 'D_USER',
            passwordVariable: 'D_PASS'
    )]) {
        sh 'echo $D_PASS | docker login -u $D_USER --password-stdin'
    }
}