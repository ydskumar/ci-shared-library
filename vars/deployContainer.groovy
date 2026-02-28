def call(String image, String containerName, String network, String port, String version) {

    sh """
        # Stop any container using this port
        EXISTING=\$(docker ps --filter "publish=${port}" --format "{{.ID}}")
        if [ ! -z "\$EXISTING" ]; then
            echo "Stopping container using port ${port}"
            docker rm -f \$EXISTING
        fi
        
        docker rm -f ${containerName} || true
        docker pull ${image}
        docker run -d \
          --network ${network} \
          -e APP_VERSION=${version} \
          -p ${port}:${port} \
          --name ${containerName} \
          ${image}
    """
}