def call(String image, String containerName, String network, String port, String version) {

    sh """
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