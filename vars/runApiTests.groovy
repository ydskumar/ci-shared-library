def call(String testCommand) {

    echo "Running API tests..."

    return sh(
            script: """
                ${testCommand}
            """,
            returnStatus: true
    )
}