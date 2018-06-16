package com.example.demo;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

public class DockerService {
    
    private DockerClient dockerClient;
    
    public DockerService(RemoteEnvironment re) {
        configureDocker(re);
    }

    public DockerClient configureDocker(RemoteEnvironment re) {
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
    
  
}
