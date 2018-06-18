package com.example.demo;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.github.dockerjava.api.DockerClient;

import io.elastest.epm.client.model.Key;
import io.elastest.epm.client.model.ResourceGroup;
import io.elastest.epm.client.model.Worker;

@SpringBootApplication
public class DemoEpmIntegrationApplication implements CommandLineRunner {
    @Autowired
    private EPMService epmService;
    private final String PACKAGES_PATH = "/epm-packages";
    private final String WORKERS_PATH = "/epm-workers";
    private final String WORKER_KEY_JSON = "/key.json";
    private final String TAR_SUFFIX = ".tar";
    private final String ROOT_PATH = "/tmp";
    private final String TARGET_DIRECTORY = "/packages";

    private static final Logger logger = LoggerFactory
            .getLogger(DemoEpmIntegrationApplication.class);

    private DockerService dockerService;
    private DockerClient dc;

    public static void main(String[] args) {
        SpringApplication.run(DemoEpmIntegrationApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Class clazz = DemoEpmIntegrationApplication.class;
        ResourceGroup resourceGroup = null;
        Worker worker = null;

        try {
            // Setting test environment
            // epmService.createPackage(ROOT_PATH + TARGET_DIRECTORY +
            // TAR_SUFFIX, new
            // File(clazz.getResource(PACKAGES_PATH).getFile()));
            // resourceGroup = epmService.registerAdapter(ROOT_PATH +
            // TARGET_DIRECTORY + TAR_SUFFIX);
            // Key key = epmService.addKey(new
            // File(clazz.getResource(WORKERS_PATH +
            // WORKER_KEY_JSON).getFile()));
            // logger.info("Key {} value: {}", key.getName(), key.getKey());
            // logger.info("key id: {}", key.getId());
            // TimeUnit.SECONDS.sleep(45);
            // worker = epmService.registerWorker(resourceGroup);
            // logger.info("Worker id: {}", worker.getId());
            // String adpaterId = epmService.installAdapter(worker.getId(),
            // "docker");
            RemoteEnvironment re = epmService.provisionRemoteEnvironment();
            dockerService = new DockerService(re);
            dc = dockerService.getDockerClient();
            String imageName = "hello-world:latest";
            dockerService.runDockerContainer(imageName);
            // Clean test environment
            //epmService.deprovisionRemoteEnvironment(re);
//            epmService.deleteWorker(re.getWorker().getId());
//            epmService.deleteKey(re.getKey().getId());
//            epmService.deleteAdapter(resourceGroup.getId());
        } catch (Exception e) {
            logger.error("Error: {} ", e.getMessage());
            throw e;
        }
    }
}
