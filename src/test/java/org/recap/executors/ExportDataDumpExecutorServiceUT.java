package org.recap.executors;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.recap.BaseTestCase;
import org.recap.ReCAPConstants;
import org.recap.model.etl.BibPersisterCallable;
import org.recap.model.export.DataDumpRequest;
import org.recap.model.jaxb.BibRecord;
import org.recap.model.jaxb.JAXBHandler;
import org.recap.model.jaxb.marc.BibRecords;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.XmlRecordEntity;
import org.recap.repository.BibliographicDetailsRepository;
import org.recap.util.DBReportUtil;
import org.recap.util.DataDumpUtil;
import org.recap.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by chenchulakshmig on 3/8/16.
 */
public class ExportDataDumpExecutorServiceUT extends BaseTestCase {

    @Mock
    private Map<String, Integer> institutionMap;

    @Autowired
    BibliographicDetailsRepository bibliographicDetailsRepository;

    @Mock
    private Map<String, Integer> collectionGroupMap;

    @Mock
    private Map itemStatusMap;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${etl.dump.directory}")
    private String dumpDirectoryPath;

    private static final Logger logger = LoggerFactory.getLogger(ExportDataDumpExecutorServiceUT.class);

    @Autowired
    ProducerTemplate producer;

    @Autowired
    private ExportDataDumpExecutorService exportDataDumpExecutorService;

    @Autowired
    DBReportUtil dbReportUtil;

    @Value("${ftp.userName}")
    String ftpUserName;

    @Value("${ftp.knownHost}")
    String ftpKnownHost;

    @Value("${ftp.privateKey}")
    String ftpPrivateKey;

    @Value("${ftp.datadump.remote.server}")
    String ftpDataDumpRemoteServer;

    @Value("${datadump.batchsize}")
    private int batchSize;

    private int limitPage;

    @Before
    public void setUp() {
        limitPage = System.getProperty(ReCAPConstants.DATADUMP_LIMIT_PAGE)==null ? 0 : Integer.parseInt(System.getProperty(ReCAPConstants.DATADUMP_LIMIT_PAGE));
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getFullDumpWithSingleThread()throws Exception{
        DataDumpRequest dataDumpRequest = new DataDumpRequest();
        dataDumpRequest.setNoOfThreads(1);
        dataDumpRequest.setBatchSize(1000);
        dataDumpRequest.setFetchType(0);
        List<Integer> cgIds = new ArrayList<>();
        cgIds.add(1);
        cgIds.add(2);
        dataDumpRequest.setCollectionGroupIds(cgIds);
        List<String> institutionCodes = new ArrayList<>();
        institutionCodes.add("PUL");
        dataDumpRequest.setInstitutionCodes(institutionCodes);
        dataDumpRequest.setTransmissionType(0);
        exportDataDumpExecutorService.exportDump(dataDumpRequest);
        Long totalRecordCount = bibliographicDetailsRepository.countByInstitutionCodes(dataDumpRequest.getCollectionGroupIds(),dataDumpRequest.getInstitutionCodes());
        int loopCount = limitPage == 0 ? getLoopCount(totalRecordCount,batchSize):(limitPage-1);
        Thread.sleep(100);
        File file;
        logger.info("file count---->"+loopCount);
        for(int fileCount=1;fileCount<=loopCount;fileCount++){
            file = new File(dumpDirectoryPath + File.separator + ReCAPConstants.DATA_DUMP_FILE_NAME+fileCount+ ReCAPConstants.XML_FILE_FORMAT);
            boolean fileExists = file.exists();
            assertTrue(fileExists);
            file.delete();
        }
    }

    @Test
    public void getFullDumpWithMultipleThreads()throws Exception{
        DataDumpRequest dataDumpRequest = new DataDumpRequest();
        dataDumpRequest.setNoOfThreads(5);
        dataDumpRequest.setBatchSize(10000);
        dataDumpRequest.setFetchType(0);
        List<Integer> cgIds = new ArrayList<>();
        cgIds.add(1);
        cgIds.add(2);
        dataDumpRequest.setCollectionGroupIds(cgIds);
        List<String> institutionCodes = new ArrayList<>();
        institutionCodes.add("PUL");
        institutionCodes.add("NYPL");
        dataDumpRequest.setInstitutionCodes(institutionCodes);
        dataDumpRequest.setTransmissionType(0);
        exportDataDumpExecutorService.exportDump(dataDumpRequest);
        Long totalRecordCount = bibliographicDetailsRepository.countByInstitutionCodes(dataDumpRequest.getCollectionGroupIds(),dataDumpRequest.getInstitutionCodes());
        int loopCount = limitPage == 0 ? getLoopCount(totalRecordCount,batchSize):(limitPage-1);
        Thread.sleep(1000);
        File file;
        logger.info("file count---->"+loopCount);
        for(int fileCount=1;fileCount<=loopCount;fileCount++){
            file = new File(dumpDirectoryPath + File.separator + ReCAPConstants.DATA_DUMP_FILE_NAME+fileCount+ ReCAPConstants.XML_FILE_FORMAT);
            boolean fileExists = file.exists();
            assertTrue(fileExists);
            file.delete();
            Thread.sleep(2000);
        }
    }

    @Test
    public void getIncrementalDumpWithInstitutionCodesAndLastUpdatedDateAsInput() throws Exception{
        DataDumpRequest dataDumpRequest = new DataDumpRequest();
        String inputDate = "08-18-2016";
        dataDumpRequest.setNoOfThreads(5);
        dataDumpRequest.setBatchSize(1000);
        List<Integer> cgIds = new ArrayList<>();
        cgIds.add(1);
        cgIds.add(2);
        dataDumpRequest.setCollectionGroupIds(cgIds);
        List<String> institutionCodes = new ArrayList<>();
        institutionCodes.add("PUL");
        dataDumpRequest.setInstitutionCodes(institutionCodes);
        dataDumpRequest.setFetchType(1);
        dataDumpRequest.setDate(inputDate);
        dataDumpRequest.setTransmissionType(0);
        String outputString = exportDataDumpExecutorService.exportDump(dataDumpRequest);
        Long totalRecordCount = bibliographicDetailsRepository.countByInstitutionCodesAndLastUpdatedDate(dataDumpRequest.getCollectionGroupIds(),institutionCodes, DateUtil.getDateFromString(inputDate, ReCAPConstants.DATE_FORMAT_MMDDYYY));
        int loopCount = limitPage == 0 ? getLoopCount(totalRecordCount,batchSize):(limitPage-1);
        Thread.sleep(1000);
        File file;
        logger.info("file count---->"+loopCount);
        for(int fileCount=1;fileCount<=loopCount;fileCount++){
            file = new File(dumpDirectoryPath + File.separator + ReCAPConstants.DATA_DUMP_FILE_NAME+fileCount+ ReCAPConstants.XML_FILE_FORMAT);
            boolean fileExists = file.exists();
            assertTrue(fileExists);
            file.delete();
            Thread.sleep(1000);
        }
    }

    @Test
    public void getIncrementalDumpWithXmlTransmissionType() throws Exception{
        DataDumpRequest dataDumpRequest = new DataDumpRequest();
        String inputDate = "2016-08-30 11:20";
        dataDumpRequest.setNoOfThreads(5);
        dataDumpRequest.setBatchSize(1000);
        List<Integer> cgIds = new ArrayList<>();
        cgIds.add(1);
        cgIds.add(2);
        dataDumpRequest.setCollectionGroupIds(cgIds);
        List<String> institutionCodes = new ArrayList<>();
        institutionCodes.add("NYPL");
        dataDumpRequest.setInstitutionCodes(institutionCodes);
        dataDumpRequest.setFetchType(1);
        dataDumpRequest.setDate(inputDate);
        dataDumpRequest.setTransmissionType(0);
        String outputString = exportDataDumpExecutorService.exportDump(dataDumpRequest);
        Long totalRecordCount = bibliographicDetailsRepository.countByInstitutionCodesAndLastUpdatedDate(dataDumpRequest.getCollectionGroupIds(),institutionCodes, DateUtil.getDateFromString(inputDate, ReCAPConstants.DATE_FORMAT_MMDDYYY));
        int loopCount = limitPage == 0 ? getLoopCount(totalRecordCount,batchSize):(limitPage-1);
        Thread.sleep(1000);
        File file;
        logger.info("file count---->"+loopCount);
        for(int fileCount=1;fileCount<=loopCount;fileCount++){
            file = new File(dumpDirectoryPath + File.separator + ReCAPConstants.DATA_DUMP_FILE_NAME+fileCount+ ReCAPConstants.XML_FILE_FORMAT);
            boolean fileExists = file.exists();
            assertTrue(fileExists);
            file.delete();
            Thread.sleep(1000);
        }
    }
    private int getLoopCount(Long totalRecordCount,int batchSize){
        int quotient = Integer.valueOf(Long.toString(totalRecordCount)) / (batchSize);
        int remainder = Integer.valueOf(Long.toString(totalRecordCount)) % (batchSize);
        int loopCount = remainder == 0 ? quotient : quotient + 1;
        return loopCount;
    }

    @Test
    public void uploadDataDumpXmlToFTP()throws Exception{
        DataDumpUtil dataDumpUtil = new DataDumpUtil();
        DataDumpRequest dataDumpRequest = new DataDumpRequest();
        Mockito.when(institutionMap.get("NYPL")).thenReturn(3);
        Mockito.when(itemStatusMap.get("Available")).thenReturn(1);
        Mockito.when(collectionGroupMap.get("Open")).thenReturn(2);

        Map<String, Integer> institution = new HashMap<>();
        institution.put("NYPL", 3);
        Mockito.when(institutionMap.entrySet()).thenReturn(institution.entrySet());

        Map<String, Integer> collection = new HashMap<>();
        collection.put("Open", 2);
        Mockito.when(collectionGroupMap.entrySet()).thenReturn(collection.entrySet());

        BibliographicEntity bibliographicEntity1 = getBibliographicEntity("singleRecord.xml");
        assertNotNull(bibliographicEntity1);
        BibliographicEntity savedBibliographicEntity1 = bibliographicDetailsRepository.saveAndFlush(bibliographicEntity1);
        entityManager.refresh(savedBibliographicEntity1);
        assertNotNull(savedBibliographicEntity1);
        List<BibliographicEntity> bibliographicEntityList = new ArrayList<>();
        bibliographicEntityList.add(savedBibliographicEntity1);
        List<Integer> cgIds = new ArrayList<>();
        cgIds.add(1);
        cgIds.add(2);
        dataDumpRequest.setCollectionGroupIds(cgIds);
        BibRecords bibRecords = dataDumpUtil.getBibRecords(bibliographicEntityList);
        String fileName = "final-Generated-Data-Dump.xml";
        producer.sendBodyAndHeader("seda:dataDumpQ", bibRecords, "fileName", fileName);
        Thread.sleep(1000);
        File file = new File(dumpDirectoryPath + File.separator + fileName);
        assertTrue(file.exists());
        Thread.sleep(1000);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:getUploadedDataDump")
                        .pollEnrich("sftp://" +ftpUserName + "@" + ftpDataDumpRemoteServer + "?privateKeyFile="+ ftpPrivateKey + "&knownHostsFile=" + ftpKnownHost + "&fileName=final-Generated-Data-Dump.xml");
            }
        });

        String response = producer.requestBody("seda:getUploadedDataDump", "", String.class);
        Thread.sleep(1000);
        assertNotNull(response);


    }

    private BibliographicEntity getBibliographicEntity(String xmlFileName) throws URISyntaxException, IOException {
        XmlRecordEntity xmlRecordEntity = new XmlRecordEntity();
        xmlRecordEntity.setXmlFileName(xmlFileName);

        URL resource = getClass().getResource(xmlFileName);
        assertNotNull(resource);
        File file = new File(resource.toURI());
        assertNotNull(file);
        assertTrue(file.exists());
        BibRecord bibRecord = null;
        try {
            bibRecord = (BibRecord) JAXBHandler.getInstance().unmarshal(FileUtils.readFileToString(file, "UTF-8"), BibRecord.class);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        assertNotNull(bibRecord);

        BibliographicEntity bibliographicEntity = null;

        BibPersisterCallable bibPersisterCallable = new BibPersisterCallable();
        bibPersisterCallable.setItemStatusMap(itemStatusMap);
        bibPersisterCallable.setInstitutionEntitiesMap(institutionMap);
        bibPersisterCallable.setCollectionGroupMap(collectionGroupMap);
        bibPersisterCallable.setXmlRecordEntity(xmlRecordEntity);
        bibPersisterCallable.setBibRecord(bibRecord);
        bibPersisterCallable.setDBReportUtil(dbReportUtil);
        Map<String, Object> map = (Map<String, Object>) bibPersisterCallable.call();
        if (map != null) {
            Object object = map.get("bibliographicEntity");
            if (object != null) {
                bibliographicEntity = (BibliographicEntity) object;
            }
        }
        return bibliographicEntity;
    }

}