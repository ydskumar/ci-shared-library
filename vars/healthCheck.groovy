def call(String url, int retries = 20) {

    return sh(
            script: """
                for i in {1..${retries}}; do
                    status=\$(curl -s -o /dev/null -w "%{http_code}" ${url})
                    if [ "\$status" = "200" ]; then exit 0; fi
                    sleep 2
                done
                exit 1
            """,
            returnStatus: true
    )
}