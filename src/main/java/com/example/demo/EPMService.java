package com.example.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.ServiceException.ExceptionCode;
import com.google.gson.reflect.TypeToken;

import io.elastest.epm.client.ApiClient;
import io.elastest.epm.client.ApiException;
import io.elastest.epm.client.JSON;
import io.elastest.epm.client.api.KeyApi;
import io.elastest.epm.client.api.PackageApi;
import io.elastest.epm.client.api.WorkerApi;
import io.elastest.epm.client.model.Key;
import io.elastest.epm.client.model.ResourceGroup;
import io.elastest.epm.client.model.Worker;

@Service
public class EPMService {
    private static final Logger logger = LoggerFactory
            .getLogger(EPMService.class);
    
    private final String PACKAGES_PATH = "/epm-packages";
    private final String WORKERS_PATH = "/epm-workers";    
    private final String WORKER_KEY_JSON = "/key.json";
    private final String TAR_SUFFIX = ".tar";
    private final String ROOT_PATH = "/tmp";
    private final String TARGET_DIRECTORY = "/packages";
    private final int MAX_ATTEMPTS = 10;
    private final int TIME_BETWEEN_ATTEMPTS = 15;
        
    private PackageApi ansibleApiInstance;
    private WorkerApi workerApiInstance;
    private KeyApi keyApiInstance;
    private ApiClient apiClient;
    private JSON json;
    
    public EPMService() {
        apiClient = new ApiClient();
        ansibleApiInstance = new PackageApi();
        ansibleApiInstance.setApiClient(apiClient);
        workerApiInstance = new WorkerApi();
        keyApiInstance = new KeyApi();
        json = new JSON(apiClient);        
    }
    
    public RemoteEnvironment provisionRemoteEnvironment() throws ServiceException {
        logger.info("Provisioning virtual machine.");       
        RemoteEnvironment re = new RemoteEnvironment();
        Class clazz = DemoEpmIntegrationApplication.class;
        ResourceGroup resourceGroup = null;
        Worker worker = null;
        
        try {
            //Providing VM
            createPackage(ROOT_PATH + TARGET_DIRECTORY + TAR_SUFFIX, new File(clazz.getResource(PACKAGES_PATH).getFile()));
            resourceGroup = registerAdapter(ROOT_PATH + TARGET_DIRECTORY + TAR_SUFFIX);
            re.setResourceGroup(resourceGroup);
            logger.debug("Virtual machine provided with id: {}", resourceGroup.getId());
            //Registering privated key
            Key key = addKey(new File(clazz.getResource(WORKERS_PATH + WORKER_KEY_JSON).getFile()));
            logger.debug("Key {} value: {}", key.getName(), key.getKey());
            re.setKey(key);
            //logger.debug("key id: {}", key.getId());
            int currentAttempts = 0;
            boolean registeredWorker = false;
            while(currentAttempts < MAX_ATTEMPTS && !registeredWorker) {
                logger.debug("Attempts: {}", currentAttempts);
                worker = registerWorker(resourceGroup);
                registeredWorker = worker != null ? true : false;
                if (!registeredWorker) {
                    currentAttempts++;
                    TimeUnit.SECONDS.sleep(TIME_BETWEEN_ATTEMPTS);
                }
            }
            
            if (!registeredWorker) {
                throw new ServiceException("Error provioning a new remote environment", ExceptionCode.ERROR_PROVISIONING_VM);
            }
            re.setWorker(worker);
            re.setHostIp(worker.getIp());
            //TimeUnit.SECONDS.sleep(45);
            //worker = registerWorker(resourceGroup);
            logger.info("Worker id: {}", worker.getId());
            String adpaterId = installAdapter(worker.getId(), "docker");            
            
        }catch (ApiException | IOException | InterruptedException e) {
            logger.error("Error: {} ",e.getMessage());
            throw new ServiceException("Error provioning a new remote environment", e.getCause());
        }        
        
        return re;
    }
    
    public String deprovisionRemoteEnvironment(RemoteEnvironment re) throws ServiceException {
        try {
            deleteWorker(re.getWorker().getId());        
            deleteKey(re.getKey().getId());
            deleteAdapter(re.getResourceGroup().getId());
        } catch (FileNotFoundException  | ApiException e) {
            e.printStackTrace();
            throw new ServiceException("Error provioning a new remote environment", e.getCause(), ExceptionCode.ERROR_PROVISIONING_VM);
        } 
        
        return re.getResourceGroup().getId();
    }
    
    public File createPackage(String path, File... files) throws IOException {
        try(TarArchiveOutputStream out = getTarArchiveOutputStream(path)){
            for(File file: files) {
                addToArchiveCompression(out, file, ".");
            }
        }        
        return null;        
    }
    
    private TarArchiveOutputStream getTarArchiveOutputStream(String name) throws FileNotFoundException {
        TarArchiveOutputStream taos = new TarArchiveOutputStream(new FileOutputStream(name));
        taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        taos.setAddPaxHeadersForNonAsciiNames(true);
        return taos;        
    }
    
    private void addToArchiveCompression(TarArchiveOutputStream out, File file, String dir) throws IOException {
        String entry = dir;
        if(file.isFile()) {
            entry = File.separator + file.getName();
            out.putArchiveEntry(new TarArchiveEntry(file, entry));
            try (FileInputStream in = new FileInputStream(file)){
                IOUtils.copy(in, out);                
            }
            out.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for(File child : children) {
                    addToArchiveCompression(out, child, entry);
                }
            }
        } else {
            System.out.println(file.getName() + "is not supported");
        }
    }
    
    private Key parserKeyFromJsonFile(File key) throws FileNotFoundException {        
        InputStream is = new FileInputStream(key);
        Scanner s = new Scanner(is).useDelimiter("\\A");
        String result = s.hasNext() ? s.next() : "";
        result = result.replace("  ", "");
        return json.deserialize(result, new TypeToken<Key>(){}.getType());
    }
    
    public ResourceGroup registerAdapter(String packagePath) throws FileNotFoundException, IOException {
        logger.info("File path: {}", packagePath);        
        File file = new File(packagePath); // File | Package in a multipart form
        ResourceGroup result = null;
        try {
            result = ansibleApiInstance.receivePackage(file);
            logger.info("New instance id: {} ",result.getId());
            logger.info(String.valueOf(result));            
        } catch (ApiException e) {
            System.err.println("Exception when calling PackageApi#receivePackage");
            e.printStackTrace();
        }
        
        return result;
    }
    
    public void deleteAdapter(String id) {
        logger.info("Delete adapter: {}", id);
        try {
            ansibleApiInstance.deletePackage(id);            
        } catch (ApiException e) {
            System.err.println("Exception when calling PackageApi#receivePackage");
            e.printStackTrace();
        }
    }
    
    public Worker registerWorker(ResourceGroup rg) throws ServiceException {        
        Worker worker = new Worker();
        worker.setIp(rg.getVdus().get(0).getIp());
        worker.setUser("ubuntu");
        worker.setEpmIp("localhost");
        worker.setKeyname("tub-ansible");
        worker.passphrase("");
        worker.password("");
        
        Worker result = null;
        try {
            result = workerApiInstance.registerWorker(worker);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling WorkerApi#registerWorker");
            e.printStackTrace();             
        }
        
        return result;
    }
    
    public String deleteWorker(String id) throws ApiException {
        String result = workerApiInstance.deleteWorker(id);
        return result;
    }
    
    public Key addKey(File keyFile) throws FileNotFoundException, ApiException {
        Key key = parserKeyFromJsonFile(keyFile);        
        return keyApiInstance.addKey(key);
    }
        
    public String deleteKey(String id) throws FileNotFoundException, ApiException {        
        return keyApiInstance.deleteKey(id);        
    }
    
    public String installAdapter(String workerId, String type) throws ApiException {
        
        return workerApiInstance.installAdapter(workerId, type);
    }
        
    
    
}
