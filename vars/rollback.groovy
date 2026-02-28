def call(String previousImage, String containerName, String network, String port) {

    if (previousImage != "none") {
        echo "Rolling back to ${previousImage}"

        sh """
            docker rm -f ${containerName} || true
            docker run -d \
              --network ${network} \
              -p ${port}:${port} \
              --name ${containerName} \
              ${previousImage}
        """
    } else {
        echo "No previous image available for rollback."
    }
}