package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;

public class DockerService {
    
    private static final Logger logger = LoggerFactory
            .getLogger(DockerService.class);
    
    private DockerClient dockerClient;
    
    public DockerService(RemoteEnvironment re) {
        configureDocker(re);
    }

    public DockerClient configureDocker(RemoteEnvironment re) {
        logger.info("DockerService configuration.");
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://" + re.getHostIp() + ":2376")
                .build();
            dockerClient = DockerClientBuilder.getInstance(config).build();
        return dockerClient;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public void setDockerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }
    
    public String runDockerContainer (String imageName) {
        logger.info("Running hello-world container.");
        
        dockerClient.pullImageCmd(imageName).exec(new PullImageResultCallback())
        .awaitSuccess();
        
        CreateContainerCmd createContainer = dockerClient
                .createContainerCmd(imageName);        

        createContainer = createContainer.withPublishAllPorts(true);
        CreateContainerResponse container = createContainer.exec();

        dockerClient.startContainerCmd(container.getId()).exec();
        
        logger.info("Id del contenedor:" + container.getId());

        return container.getId();
    }
    
  
}
